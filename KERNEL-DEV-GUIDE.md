# KERNEL-DEV-GUIDE — what's actually going on in this demo

A developer-level walkthrough of how the A12 Kernel turns a **DocumentModel + validation rules** into the **errors and warnings** this demo prints. Read [`README.md`](./README.md) first for how to set up and run it.

> ⚠️ **Java only — TypeScript not yet supported.** The `setup/` patches and bootstrap build and publish only the kernel's **Java** artifacts. The kernel also ships a parallel **TypeScript** runtime (`@com.mgmtp.a12.kernel/*`), but it is **not** built/published by this demo's setup (its `@com.mgmtp.a12.utils/*` + devtools npm dependencies aren't wired up — that's an open task). Everything below describes the **Java** path.

## 1. The mental model

A12 is **model-driven validation**. You don't write validation code; you describe:

- a **DocumentModel** — the *schema*: groups, fields (with types), and **rules** whose conditions are written in a small **DSL**; and
- a **Document** — an *instance* of form data (field values).

The kernel then **compiles** the model's DSL rules into executable code and **runs** that code against a Document, producing structured **messages** (errors / warnings / infos). The key idea: **a rule's condition describes the *error* scenario** — it evaluates to `true` exactly when the data is *invalid*. E.g. `FieldNotFilled(CustomerName)` is the rule "customer name is missing", and firing it means *error*.

## 2. The building blocks (mapped to this demo)

| Concept | In this demo | Kernel type |
|---|---|---|
| DocumentModel | `purchase-order` (group `Order` + fields + 4 rules) | `IDocumentModel` (built via the internal builder API in [`PurchaseOrderModel.java`](src/main/java/com/example/a12demo/purchaseorder/PurchaseOrderModel.java)) |
| Field | `CustomerName`, `Quantity`, `OrderDate`, … | `Field` + a `FieldType` (`StringType`/`NumberType`/`DateType`/`BooleanType`) |
| Rule | `CUSTOMER_REQUIRED`, `QTY_POSITIVE`, … | `Rule` = DSL `errorCondition` + `errorCode` + `severity` + localized `errorMessage` + `errorEntity` (which field the error attaches to) |
| Document | `document-valid/invalid/large` | `DocumentV2` (immutable; values set via `withFieldValue("Order[1]/Field", value)`) |

A field value's type matters: strings are `String`, numbers are `BigDecimal`, dates are **ISO `yyyy-MM-dd` strings** (not `LocalDate`) — the runtime parses them according to the field type.

## 3. The validation pipeline (what `validateFull(...)` really does)

This demo uses the **dynamic** path: the kernel generates and compiles the validation code on demand (no pre-generated classes). On the first `validateFull` for a model, the kernel runs, roughly, this pipeline (compile-time → runtime):

```
DocumentModel (rules as DSL text)
      │
      ▼  ① PARSE          ANTLR condition parser (kernel-core-parser)
   parse tree per rule condition  →  e.g. FieldFilled(Quantity) And [Quantity] <= 0
      │
      ▼  ② GENERATE CODE  StringTemplate4 templates (kernel-core-codegen*)
   executable validation code for the whole model   (dynamic path → Groovy)
      │
      ▼  ③ COMPILE + CACHE   compiled into the IModelCodeCache you provide
      │                      (so the next validateFull reuses it)
      ▼  ④ EXECUTE         kernel runtime (kernel-core-runtime-30-8, via Groovy)
   evaluate every rule condition against the Document's field values
   + run FORMAL checks (field-type constraints) on each value
      │
      ▼  ⑤ COLLECT
   IDocumentValidationResult  →  List<IMessage> (severity, errorCode, rendered text, rule path, field pointer)
```

Two distinct kinds of message come out of ④:

- **Rule errors/warnings** — your DSL rules. When a condition is `true`, the kernel emits a message carrying that rule's `errorCode`, `severity`, and **rendered** `errorMessage` (any `$Field.value$`/`$Field$` parameters substituted), plus a pointer to the error field.
- **Formal errors** — built-in field-type constraints, independent of your rules (error code like `zahlHatDezimalTrenner`, rule path `formalePruefung`). The demo deliberately runs into one: a `NumberType` defaults to *integers only*, so a price of `9.99` would be a formal error — which is why `PurchaseOrderModel` sets `maxFractionalDigits(2)` on `UnitPrice`.

The pipeline stages live in the `kernel-tool/*` (parse + codegen) and `kernel-rt/*` (runtime) module groups; the model/Document API and the `validateFull` entry point are in `kernel-md/*`.

## 4. The two demo phases, in kernel terms

**Phase 1 — author (`BuilderMain`)**, the "create models upfront, reuse them" workflow:
1. Build the model in memory with the builder API → an internal `DocumentModel`.
2. Serialize it to canonical **DocumentModel JSON** (`purchase-order.dm.json`) — the persistable, reusable artifact.
3. Bridge it to the public `IDocumentModel` by deserializing that JSON (the builder yields the *internal* model type; the runtime wants the *public* interface).
4. Build the sample `DocumentV2`s with `withFieldValue(...)` and serialize each to JSON.

**Phase 2 — validate (`ValidatorMain`)**, the everyday consumption path:
1. Load the model JSON → `IDocumentModel`; load each document JSON → `DocumentV2` (public serializers only — fully decoupled from how they were authored).
2. Build the dynamic runtime: `new DocumentRtServiceFactory(resolver).createDocumentRtService(dynamicConfig)`, where `dynamicConfig` supplies the `IModelCodeCache` (stage ③ above).
3. `rt.validateFull(doc, DocumentProcessingConfig.builder(Locale.US).build())` runs stages ①–⑤. (The processing locale must be one the model supports — the model uses `en_US`, so `Locale.US`, **not** `Locale.ENGLISH`.)
4. Read `result.getMessages()` and print each `IMessage`.

> 🟢 **Good-citizen note — public vs. internal-tier APIs.** Phase 2 (the consumption path: load JSON → `validateFull` → read `IMessage`s) uses **only public API** — `…md.facade.*`, `…md.model.api.*`, `…md.rt.api.*`, `…md.document.api/apiV2.*`. That is the part a real consumer writes, and it's the pattern to copy.
>
> Phase 1 is different on purpose. `PurchaseOrderModel` and `BuilderMain` reach into **internal-tier** packages — the `…md.model.internal.betterbuilders.*` builders and the `…md.model.a12internal.*` / `…core.tool.a12internal.*` / `…md.serializer.model.a12internal.*` types — because this kernel has **no public builder for assembling a `DocumentModel` in code**. These tiers are not part of the supported public contract and can change between versions.
>
> So treat the in-code builder as a *demo convenience*, not a recommended pattern. The good-citizen approach for production is to **author the model as JSON** (hand-written, or exported once — this demo writes `examples/purchase-order.dm.json` precisely so you have that artifact) and load it through the **public** `DocumentModelSerializer.deserialize(...)` — then no internal-tier import appears anywhere in your code. The demo already routes through that public serialize→deserialize bridge before validating, so the model re-enters as a public `IDocumentModel` regardless of how it was built.

## 5. Reading the demo's output

Each printed line traces straight back to a rule condition evaluated in stage ④:

| Document | Output | Why |
|---|---|---|
| valid | `VALID`, no messages | every rule condition is `false`; all field types satisfied |
| invalid | 3 ERRORs | `FieldNotFilled(CustomerName)` true; `[Quantity] <= 0` true (`-3`); `[DeliveryDate] < [OrderDate]` true |
| large | `VALID` + 1 WARNING | only `[Quantity] > 1000` true (`5000`); a WARNING isn't an error, so `noErrorOccurred()` stays `true` |

## 6. Dynamic vs. static, and why Groovy

- **Dynamic** (this demo): generate + compile validation code at runtime, cache it. Less integration effort; first call pays the codegen cost. The generated server-side code is **Groovy**, so Groovy must be on the runtime classpath.
- **Static**: pre-generate the code (Java/TS/Groovy) at build time and ship it on the classpath — faster first call, more integration. The generated code's kernel version **must** match the runtime artifacts' version, or the runtime throws.

## 7. Working with documents (`DocumentV2` basics)

A `DocumentV2` is the immutable data the kernel validates. In production it usually arrives as JSON from a form, but the API is small and worth knowing:

- **Build / update** — `DocumentV2.empty(dmId)`, then chained `withFieldValue(path, value)` calls; each returns a *new* document (copy-on-write). Repeating rows use a 1-based index, e.g. `Order[1]/Lines[2]/Sku`, and writing a deep path auto-creates the missing parents.
- **Values are typed** — strings as `String`, numbers as `BigDecimal`, booleans as `Boolean`, dates as ISO `yyyy-MM-dd` **strings**; the runtime parses each per its field type. (To turn a raw inbound string into the right Java type, `IDmAwareDocService.convertToJavaTypeV2` does it — see `DocumentApiDemo`.)
- **Read back** — `doc.fieldValue("Order[1]/Quantity")` returns the value as `Object`; `doc.group(...)` / `doc.groupAllRepetitions("…[0]")` walk groups (repetition index `0` means "all rows").
- **Serialize** — `new DocumentServiceFactory(resolver).createDocumentV2Serializer()` round-trips a document to/from JSON.

`DocumentApiDemo` (SHOWCASE §7) goes further — structural sharing, diffing, stripping unknown fields.

## 8. Adapting this to your own project

A real consumer needs far less than this demo's two-phase scaffolding. The minimal shape:

1. **Depend on one artifact** — `com.mgmtp.a12.kernel:kernel-md-facade` (plus Groovy on the runtime classpath for the dynamic path). It transitively pulls in the model, runtime, and serializer APIs.
2. **Author the model as JSON**, not in code — hand-written or exported once (this demo's `examples/*/*.dm.json` are exactly such artifacts) — and load it with the public `new DocumentModelServiceFactory().createDocumentModelSerializer().deserialize(reader)`. No internal-tier import then appears anywhere in your code (see §4's good-citizen note).
3. **Wire the runtime once** and reuse it:

```java
IDocumentModelResolver resolver = id -> modelsById.get(id);     // look a model up by its id
IModelCodeCache cache = /* a long-lived, shared cache */;
IDocumentRtService rt = new DocumentRtServiceFactory(resolver)
        .createDocumentRtService(dynamicConfig(cache));         // build ONCE, reuse for all documents
```

4. **Validate per request**: `rt.validateFull(doc, DocumentProcessingConfig.builder(locale).build())`, then read `result.getMessages()`. Use a `locale` the model supports (the demo's model is `en_US`, so `Locale.US` — **not** `Locale.ENGLISH`).

**Resolver and the code cache.** The `IDocumentModelResolver` maps a document-model id → the loaded `IDocumentModel` (a document references its model by id). The `IModelCodeCache` holds the generated, compiled validation code: **create it once and keep it for the life of your service.** The first `validateFull` for a model pays the parse → generate → compile cost (stages ①–③); every call after that reuses the cached code, so validation is cheap. A throwaway cache per request would recompile every time.

**Two kinds of failure — throw vs. return.** Keep these apart:
- A **malformed *model*** — a rule that won't parse/compile (a keyword-named field, an unreferenced error field, bad path syntax) — fails at **code generation**, i.e. the *first* `validateFull`, by throwing `DocumentValidationException` whose message lists `MVK_*` codes. That's an *authoring* problem.
- **Invalid *data*** never throws — it comes back as `IMessage`s in the result; `noErrorOccurred()` tells you whether any were `ERROR` severity.

In short: an exception means "your model is wrong"; messages mean "this document is wrong".

## 9. Beyond this demo — the feature showcases

This Purchase-Order demo is the "hello world". The kernel does a great deal more, and **[`SHOWCASE.md`](./SHOWCASE.md)** has ten standalone, runnable demos (each its own `*Demo` `main`, fresh domain, with code excerpts), **[`COMPARISON.md`](./COMPARISON.md)** weighs the kernel against five mainstream validators (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation), and **[`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md)** documents how the validation language evaluates (truth values, missing data, the "required" property, numeric/date rules). The same pipeline (§3) and APIs (`Demos.java`) underlie all of them. Highlights worth a developer's time:

- **Structural rules over repeating groups** (`IterationDemo`) — uniqueness with per-row error pointers, `Sum`, filtered `Sum(… Having …)`.
- **Cross-array logic** (`CrossArrayDemo`) — parallel iteration (join two arrays by an index field), a `$`-correlated filter, and a `CurrentRepetition` row-order check. The "a validator does SQL-like queries" story.
- **Computed/derived fields** (`ComputationDemo`) — `compute()` → `applyTo()` → read back, including a tiered computation and a cross-level aggregate.
- **The immutable Document API** (`DocumentApiDemo`) — copy-on-write `DocumentV2` with structural sharing, auto-vivifying writes, built-in diff, and DM-aware coercion/strip.
- **Incremental "wizard" validation** (`PartialValidationDemo`) — `validatePart` validates only the current page, holding back errors the user can't yet fix.
- **Extending the kernel** (`ExtensionDemo`) — a custom condition + a custom field validator in Java, plus free formal type errors.
- **Model → code** (`CodegenDemo`) — compile one model to standalone Java *and* JavaScript validators.
- **DSL tour + language surprises** (`DslTourDemo`, `LanguageDemo`) — slicing/regex/tolerance/dates/enums; self-validating computations; enum categories; `Valid()/Invalid()` dates.
- **Typed accessor overlays** (`TypedAccessorDemo`) — build-time codegen of compile-time-typed, autocompleting views over a document, so model/code drift is a compile error (in an isolated source set).

## 10. Where to look next

- The feature showcases + the "why this is hard elsewhere" comparison: [`SHOWCASE.md`](./SHOWCASE.md)
- Building the kernel from source (what `setup/` automates): see [`setup/README.md`](./setup/README.md)
- Official docs & community forum: https://geta12.com/#/docs
