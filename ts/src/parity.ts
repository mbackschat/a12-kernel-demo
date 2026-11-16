/**
 * TypeScript parity showcase: run the demo's validation rules and computations in Node.js with the A12 kernel's
 * TypeScript runtime, and check that every result matches what the Java runtime reported for the same documents.
 *
 * Inputs (written by `gradle exportJs`, see JsExport.java):
 * - output/typescript/generated/<model-id>.js — each model compiled to plain JavaScript by the Java kernel,
 *   because the TypeScript kernel has no code generator of its own;
 * - output/typescript/generated/java-results.tsv — the Java results to compare against;
 * - the sample documents the Java demos persisted under output/<demo>/.
 */
import { readFileSync, writeFileSync } from "node:fs";

import {
	DocumentRtServiceFactory,
	GeneratedCodeAccessorFactory,
	type Document,
	type DocumentRtService,
	type EntityInstancePath,
	type Message,
} from "@com.mgmtp.a12.kernel/kernel-md-facade";
import { defaultLocalizerFactory } from "@com.mgmtp.a12.utils/utils-localization";

const OUTPUT = new URL("../../output/", import.meta.url);
const GENERATED = new URL("typescript/generated/", OUTPUT);

// The models' message texts are bilingual (en_US, de_DE); the Java demos report in en_US, so this does too.
const localize = defaultLocalizerFactory({ locale: { language: "en", country: "US" } });

const services = new Map<string, DocumentRtService>();

/** One runtime service per model, backed by the JavaScript the Java kernel generated for it. */
function serviceFor(modelId: string): DocumentRtService {
	let service = services.get(modelId);
	if (!service) {
		const script = readFileSync(new URL(`${modelId}.js`, GENERATED), "utf8");
		// createScriptAccessor evaluates the script with `new Function`; only STANDARD-mode output is accepted.
		const accessor = new GeneratedCodeAccessorFactory().createScriptAccessor(script);
		service = DocumentRtServiceFactory.createDocumentRtService(accessor);
		services.set(modelId, service);
	}
	return service;
}

function readDocument(file: string): Document {
	return JSON.parse(readFileSync(new URL(file, OUTPUT), "utf8")) as Document;
}

/** Renders a path the way the Java demos print it: groups carry their repetition index, the field does not. */
function formatPath(path: EntityInstancePath): string {
	return path.map((segment, i) => (i < path.length - 1 ? `${segment.elementName}[${segment.index}]` : segment.elementName)).join("/");
}

/** Java writes tab-free, single-line TSV cells (JsExport#record), so the TypeScript side normalizes the same way. */
function cell(value: string): string {
	return value.replace(/[\t\r\n]+/g, " ");
}

/** The fields both runtimes report, in the column order of java-results.tsv. */
function describe(message: Message): string[] {
	return [
		message.severity,
		message.messageType,
		message.errorCode,
		formatPath(message.entityInstance),
		message.rulePath ?? "",
		localize(...message.errorText) ?? "",
	].map(cell);
}

/**
 * Compares as multisets: the kernel documents no message order. (Both runtimes did emit the same order when
 * this was measured on 2026-11-02 with kernel 31.1.1, so an order-only difference would be new.)
 */
function sameMessages(actual: string[][], expected: string[][]): boolean {
	const normalize = (rows: string[][]) => rows.map((row) => row.join("\t")).sort();
	return JSON.stringify(normalize(actual)) === JSON.stringify(normalize(expected));
}

/** Flattens a plain document into `Group[1]/Field` → value, the path format the demos print. */
function flatten(node: unknown, prefix = "", out = new Map<string, unknown>()): Map<string, unknown> {
	for (const [name, value] of Object.entries(node as Record<string, unknown>)) {
		const rows = Array.isArray(value) ? value : value !== null && typeof value === "object" ? [value] : undefined;
		if (rows) {
			rows.forEach((row, i) => flatten(row, `${prefix}${name}[${i + 1}]/`, out));
		} else {
			out.set(`${prefix}${name}`, value);
		}
	}
	return out;
}

/** Numbers compare by value: JSON keeps no decimal scale, so Java's 50.00 and TypeScript's 50 are the same rate. */
function sameValue(a: unknown, b: unknown): boolean {
	if (typeof a === "number" || typeof b === "number") {
		return a !== undefined && b !== undefined && a !== null && b !== null && Number(a) === Number(b);
	}
	return a === b;
}

let checks = 0;
let mismatches = 0;

function verdict(same: boolean, what: string): void {
	checks++;
	if (same) {
		console.log(`  ✔ same ${what} as Java`);
	} else {
		mismatches++;
		console.log(`  ✘ ${what} differ from Java`);
	}
}

function printMessages(messages: string[][]): void {
	for (const [severity, type, code, field, rule, text] of messages) {
		console.log(`  [${severity.padEnd(7)} ${type.padEnd(13)}] ${code.padEnd(16)} ${text}`);
		console.log(`        at field: ${field}${rule ? `   rule: ${rule}` : ""}`);
	}
}

function validate(file: string, modelId: string, javaNoError: boolean, javaMessages: string[][]): void {
	const result = serviceFor(modelId).validateFull(readDocument(file));
	console.log(`\n=== ${file} ===`);
	console.log(result.noErrorOccurred ? "  result: VALID (no blocking errors)" : "  result: INVALID (errors present)");
	const messages = result.messages.map(describe);
	if (messages.length === 0) {
		console.log("  (no messages)");
	}
	printMessages(messages);
	const same = result.noErrorOccurred === javaNoError && sameMessages(messages, javaMessages);
	verdict(same, "messages");
	if (!same) {
		console.log(`  Java reported (noErrorOccurred: ${javaNoError}):`);
		printMessages(javaMessages);
	}
}

function compute(inputFile: string, modelId: string, javaComputedFile: string): void {
	const input = readDocument(inputFile);
	const result = serviceFor(modelId).compute(input);
	const computed = result.appliedTo(input);

	console.log(`\n=== ${inputFile} → compute() ===`);
	// A JSON round trip turns runtime number types (big.js) into what a client would send or store.
	const ours = flatten(JSON.parse(JSON.stringify(computed)));
	const java = flatten(readDocument(javaComputedFile));
	for (const field of result.computedFieldInstancesWithoutErrors) {
		const path = formatPath(field.path);
		console.log(`  ${path.padEnd(22)} = ${String(ours.get(path)).padEnd(8)} (Java: ${java.get(path)})`);
	}
	// The whole documents are compared, so a field that only one runtime computed counts as a difference.
	const differing = [...new Set([...ours.keys(), ...java.keys()])].sort().filter((path) => !sameValue(ours.get(path), java.get(path)));
	for (const path of differing) {
		console.log(`  differs: ${path}   TypeScript: ${ours.get(path)}   Java: ${java.get(path)}`);
	}
	verdict(result.noErrorOccurred && differing.length === 0, "computed fields");

	const relative = `typescript/${modelId}-computed-by-ts.json`;
	writeFileSync(new URL(relative, OUTPUT), `${JSON.stringify(computed, null, 2)}\n`);
	console.log(`  wrote output/${relative}`);
}

// java-results.tsv: a `document` line is followed by the `message` lines Java reported for it; the closing
// `end` line carries the number of checks, so a truncated file fails instead of passing on fewer checks.
type Pending = { file: string; modelId: string; noError: boolean; messages: string[][] };
let pending: Pending | undefined;
let expectedChecks: number | undefined;

function flush(): void {
	if (pending) {
		validate(pending.file, pending.modelId, pending.noError, pending.messages);
		pending = undefined;
	}
}

for (const line of readFileSync(new URL("java-results.tsv", GENERATED), "utf8").split(/\r?\n/)) {
	const [kind, ...fields] = line.split("\t");
	if (kind === "document") {
		flush();
		pending = { file: fields[0], modelId: fields[1], noError: fields[2] === "true", messages: [] };
	} else if (kind === "message") {
		pending?.messages.push(fields);
	} else if (kind === "compute") {
		flush();
		compute(fields[0], fields[1], fields[2]);
	} else if (kind === "end") {
		flush();
		expectedChecks = Number(fields[0]);
	} else if (kind !== "") {
		throw new Error(`java-results.tsv: unknown record type "${kind}"`);
	}
}
flush();

if (expectedChecks === undefined || checks !== expectedChecks) {
	mismatches++;
	console.log(`\n✘ java-results.tsv announced ${expectedChecks ?? "no"} checks, but ${checks} ran.`);
}
console.log(mismatches === 0 ? `\nAll ${checks} results match the Java runtime.` : `\n${mismatches} result(s) differ from the Java runtime.`);
process.exitCode = mismatches === 0 ? 0 : 1;
