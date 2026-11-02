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

/** The fields both runtimes report, in the column order of java-results.tsv. */
function describe(message: Message): string[] {
	return [
		message.severity,
		message.messageType,
		message.errorCode,
		formatPath(message.entityInstance),
		message.rulePath ?? "",
		localize(...message.errorText) ?? "",
	];
}

function sameMessages(actual: string[][], expected: string[][]): boolean {
	const normalize = (rows: string[][]) => rows.map((row) => row.join("\t")).sort();
	return JSON.stringify(normalize(actual)) === JSON.stringify(normalize(expected));
}

/** Reads one field value from a plain document; repeatable groups are arrays, indexes are 1-based. */
function valueAt(document: Document, path: EntityInstancePath): unknown {
	let node: unknown = document;
	for (const { elementName, index } of path) {
		const next = (node as Record<string, unknown> | undefined)?.[elementName];
		node = Array.isArray(next) ? next[index - 1] : next;
	}
	return node;
}

let mismatches = 0;

function verdict(same: boolean, what: string): void {
	if (same) {
		console.log(`  ✔ same ${what} as Java`);
	} else {
		mismatches++;
		console.log(`  ✘ ${what} differ from Java`);
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
	for (const [severity, type, code, field, rule, text] of messages) {
		console.log(`  [${severity.padEnd(7)} ${type.padEnd(13)}] ${code.padEnd(16)} ${text}`);
		console.log(`        at field: ${field}${rule ? `   rule: ${rule}` : ""}`);
	}
	verdict(result.noErrorOccurred === javaNoError && sameMessages(messages, javaMessages), "messages");
}

function compute(inputFile: string, modelId: string, javaComputedFile: string): void {
	const input = readDocument(inputFile);
	const result = serviceFor(modelId).compute(input);
	const computed = result.appliedTo(input);
	const javaComputed = readDocument(javaComputedFile);

	console.log(`\n=== ${inputFile} → compute() ===`);
	let same = result.noErrorOccurred;
	for (const field of result.computedFieldInstancesWithoutErrors) {
		const ours = Number(field.value);
		const java = Number(valueAt(javaComputed, field.path));
		same &&= ours === java;
		console.log(`  ${formatPath(field.path).padEnd(22)} = ${String(ours).padEnd(8)} (Java: ${java})`);
	}
	verdict(same, "computed values");

	const target = new URL(`typescript/${modelId}-computed-by-ts.json`, OUTPUT);
	writeFileSync(target, `${JSON.stringify(computed, null, 2)}\n`);
	console.log(`  wrote ${target.pathname.split("/output/")[1]}`);
}

// java-results.tsv: a `document` line is followed by the `message` lines Java reported for it.
type Pending = { file: string; modelId: string; noError: boolean; messages: string[][] };
let pending: Pending | undefined;
const flush = () => pending && validate(pending.file, pending.modelId, pending.noError, pending.messages);

for (const line of readFileSync(new URL("java-results.tsv", GENERATED), "utf8").split("\n")) {
	const [kind, ...fields] = line.split("\t");
	if (kind === "document") {
		flush();
		pending = { file: fields[0], modelId: fields[1], noError: fields[2] === "true", messages: [] };
	} else if (kind === "message") {
		pending?.messages.push(fields);
	} else if (kind === "compute") {
		flush();
		pending = undefined;
		compute(fields[0], fields[1], fields[2]);
	}
}
flush();

console.log(mismatches === 0 ? "\nAll results match the Java runtime." : `\n${mismatches} result(s) differ from the Java runtime.`);
process.exitCode = mismatches === 0 ? 0 : 1;
