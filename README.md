# A12 Kernel — a hands-on demo

> **Validation rules and computed fields, written as data** — in a bilingual, analyst-friendly DSL — compiled by the kernel to Java/TypeScript/Groovy and run against your form data.

This is a **personal project** — a comprehensive set of hands-on showcases for the features and API of the **A12 Kernel**, the model-and-DSL engine behind mgm's *A12* low-code platform for complex business forms.
The premise of the Kernel is: instead of hand-coding validation, you *declare* the **error scenarios** (and computed fields) as data and let the kernel compile them into executable validators. I wrote one small, runnable Java program per capability — to see each one work and compare it against the validators most of us reach for (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation), in **[`COMPARISON.md`](./COMPARISON.md)**.

> **Not an official A12 artifact.** It isn't part of any mgm A12 release and isn't affiliated with or endorsed by mgm — it's a personal exploration, shared in case it's useful to others evaluating or integrating the kernel. *(Java only for now — the TypeScript side isn't wired up; see [Setup](#setup-one-time).)*

## Quick start

After the one-time setup (run the [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap) kit — see [Setup](#setup-one-time) below):

- **`gradle demo`** — the basics: build a Purchase-Order model + sample data, validate it, and print the errors/warnings.
- **`gradle showcases`** — nine feature demos (repeating-group rules, computations, cross-array "SQL-like" logic, the Document API, code generation, …); a tenth, typed accessors, runs on its own via **`gradle runTyped`**. All ten are detailed in **[`SHOWCASE.md`](./SHOWCASE.md)**, and weighed against five mainstream validators (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation) in **[`COMPARISON.md`](./COMPARISON.md)**.

For how the engine works under the hood, see **[`KERNEL-DEV-GUIDE.md`](./KERNEL-DEV-GUIDE.md)** — the model → compile → validate pipeline.

## The base demo, in two phases

The base demo is split into the two phases of real-world kernel use — and it's the **only** demo split this way, to teach the **author-once / validate-often** persistence-and-decoupling story (the ten feature demos in [`SHOWCASE.md`](./SHOWCASE.md) are instead single self-contained `main`s that build *and* validate inline):

1. **Author once (`BuilderMain`)** — build a **Purchase-Order DocumentModel** in code (fields + validation rules whose conditions are written in the A12 DSL), build three sample documents, and **persist them all as JSON** under `output/`. Models are created upfront so they can be re-used.
2. **Validate often (`ValidatorMain`)** — **load** the persisted model + documents from JSON and run validation, printing the resulting **errors and warnings**. Fully decoupled from authoring: it consumes JSON via the public kernel API only.

It uses the **dynamic** validation path (the kernel generates + compiles validation code on the fly); everything resolves from your **local Maven repository + Maven Central** — public sources only.

| Entry point | Class | Does |
|---|---|---|
| builder | [`BuilderMain.java`](src/main/java/com/example/a12demo/purchaseorder/BuilderMain.java) | build model + documents → write `output/purchase-order/*.json` |
| validator | [`ValidatorMain.java`](src/main/java/com/example/a12demo/purchaseorder/ValidatorMain.java) | load `output/purchase-order/*.json` → validate → print messages |
| model | [`PurchaseOrderModel.java`](src/main/java/com/example/a12demo/purchaseorder/PurchaseOrderModel.java) | the model definition (used by the builder) |
| showcases | [`SHOWCASE.md`](./SHOWCASE.md) + `*Demo.java` | ten feature demos (repeating groups, computations, DSL tour, extensions, codegen, cross-array logic, Document API, partial validation, language features, typed accessors) |

## The model & rules

One group `Order` with fields `CustomerName` (text), `OrderDate`/`DeliveryDate` (date), `Quantity`/`UnitPrice` (number, price allows 2 decimals), `Express` (boolean). Each rule states the **error** scenario (the condition is true when the data is *invalid*):

| Rule | Severity | Condition (DSL) |
|---|---|---|
| `CUSTOMER_REQUIRED` | ERROR | `FieldNotFilled(CustomerName)` |
| `QTY_POSITIVE` | ERROR | `FieldFilled(Quantity) And [Quantity] <= 0` |
| `DELIVERY_BEFORE_ORDER` | ERROR | `FieldFilled(OrderDate) And FieldFilled(DeliveryDate) And [DeliveryDate] < [OrderDate]` |
| `LARGE_ORDER` | WARNING | `FieldFilled(Quantity) And [Quantity] > 1000` |

Defined in [`PurchaseOrderModel.java`](src/main/java/com/example/a12demo/purchaseorder/PurchaseOrderModel.java); the sample datasets are in [`BuilderMain.java`](src/main/java/com/example/a12demo/purchaseorder/BuilderMain.java).

## Setup (one-time)

> ⚠️ **Java only — TypeScript is not wired up.** The [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap) kit builds the kernel's TypeScript packages (`@com.mgmtp.a12.kernel/*`) too, but keeps them as npm tarballs in its own state directory and installs them nowhere npm consumers would find them. Only the **Java** artifacts land in `~/.m2`, and this demo uses only those.

The kernel and its A12 dependencies must be in your **local Maven repo** (`~/.m2`); their prebuilt **artifacts aren't published to a public Maven repository** (the source is public on GitHub). The separate **[`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap)** kit builds the release from **pinned public commits**, verifies it, and then installs its Maven artifacts into `~/.m2`. Clone it **next to this demo**, check the toolchain, and run it:

```sh
ROOT="$HOME/oss/a12"                                          # the shared parent dir (adjust to your checkout)
git clone https://github.com/mbackschat/a12-kernel-bootstrap "$ROOT/a12-kernel-bootstrap"
"$ROOT/a12-kernel-bootstrap/bootstrap.sh" --doctor            # reports each prerequisite as OK or MISSING
"$ROOT/a12-kernel-bootstrap/bootstrap.sh"                     # build + verify -> install into ~/.m2
```

- **Prerequisites:** **JDK 21 and JDK 25** (the base modules need 25; the kernel targets 21), **Node.js 24** with npm, **Python 3.12+**, and **Git**. `--doctor` checks them without building anything and suggests fixes.
- **Gradle 9.8.2** is downloaded and checked against its published SHA-256 by the kit itself; you don't install it.
- **State** (sources, logs, the downloaded Gradle) lives in `$ROOT/.a12-bootstrap/2026.06-ext1` by default.
- **Install into `~/.m2`** happens only after a successful, verified build. It follows `publishToMavenLocal` semantics: this release's coordinates are replaced, every other version (e.g. 2025.06-ext5) is left in place.

See the **[`a12-kernel-bootstrap` README](https://github.com/mbackschat/a12-kernel-bootstrap#readme)** for what it builds, how it verifies the result, and its env-var overrides.

## Compatibility — kernel versions

This demo is built and verified against the **2026.06-ext1 / kernel 31.1.1** A12 release line:

| Artifact | Version |
|---|---|
| A12 kernel (`com.mgmtp.a12.kernel:*`) | **31.1.1** |
| `a12-base` (`com.mgmtp.a12.base:*`) | **30.0.2** |
| `kernel-core-runtime-api` | 33.0.2 |
| `kernel-core-customfieldtype-api` | 29.0.2 |
| Groovy (runtime, dynamic path) | 3.0.25 |
| JDK / Gradle | 21 / 9.8.2 |

[`build.gradle`](./build.gradle) pins only the kernel version, on its single kernel dependency `kernel-md-facade`; the other versions follow from the facade's published metadata. The kit builds exactly this release from the commits recorded in its release manifest, so `~/.m2` receives these versions and no others; the **artifact versions above are the contract**.

Because `~/.m2` keeps older releases side by side, a stale coordinate does not fail the build — it silently resolves the old jar. Kernel 31 folded several former modules (`kernel-md-model`, `kernel-md-a12internal`, `kernel-md-typed-accessor-gen`, …) into `kernel-md-facade`; their 30.8.1 jars stay in `~/.m2`, so when adapting another project, check its dependency report for leftover 30.8.1 / 29.3.0 entries.

## Build & run

The demo is Java-only (no Node needed to run it). The demo's `build.gradle` declares only `mavenLocal()` + `mavenCentral()`; run it with a fresh Gradle home (one without init scripts) so a global init script can't add a private repository:

```sh
ROOT="$HOME/oss/a12"                                                    # parent of a12-kernel-bootstrap / a12-kernel-demo (adjust to your checkout)
export GRADLE_USER_HOME="$ROOT/.gradle-demo"                            # any directory without init scripts
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"                      # macOS; or point at any JDK 21
GRADLE="$ROOT/.a12-bootstrap/2026.06-ext1/tools/gradle-9.8.2/bin/gradle"  # the Gradle the kit downloaded

"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon buildModels   # phase 1: author + persist the model & documents
"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon validate      # phase 2: load from output/ and validate
# or both at once:
"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon demo
```

`validate` (also the default `run` task) loads whatever is already in `output/`, so once the model is built you can re-validate repeatedly without rebuilding it. (If your default `gradle`/JDK already resolve from public repos on JDK 21, plain `gradle buildModels` / `gradle validate` work too.)

## Expected output

```
=== valid ===
  result: VALID (no errors)
  (no messages)

=== invalid ===
  result: INVALID (errors present)
  [ERROR  ] CUSTOMER_REQUIRED      Customer name is required.   (rule: /Order/CustomerRequiredRule)
  [ERROR  ] QTY_POSITIVE           Quantity must be greater than 0.   (rule: /Order/QuantityPositiveRule)
  [ERROR  ] DELIVERY_BEFORE_ORDER  Delivery date must not be before the order date.   (rule: /Order/DeliveryAfterOrderRule)

=== large ===
  result: VALID (no errors)
  [WARNING] LARGE_ORDER            Large quantity (> 1000) — please double-check.   (rule: /Order/LargeOrderRule)
```

`noErrorOccurred()` is `true` for the valid and large datasets (a WARNING is not an error) and `false` for the invalid one.

## The model & documents as JSON

Two copies exist, on purpose:

- **[`examples/`](./examples/)** — a **committed, annotated reference set** you can browse without building anything. One folder **per demo** (named after the demo, not the model) — `purchase-order/` (the base demo) plus the showcase folders (`iteration/`, `computation/`, `dsl-tour/`, `extension/`, `cross-array/`, `document-api/`, `partial-validation/`, `language/`, `typed-accessor/`) — each holding its model `*.dm.json` and sample documents. See [`examples/README.md`](./examples/README.md) for the full, guided tour.
- **`output/`** — where the demos write **fresh** copies on each run, one folder per demo (e.g. `output/purchase-order/purchase-order.dm.json` + `valid/invalid/large.json`). Git-ignored, so running the demos never dirties the repo; `validate` reads from `output/purchase-order/`, and `gradle refreshExamples` copies `output/**/*.json` into `examples/`.

## Learn more

- **[`KERNEL-DEV-GUIDE.md`](./KERNEL-DEV-GUIDE.md)** — developer walkthrough of *what's actually going on*: DocumentModel/Document, the DSL, and the parse → generate → compile → execute validation pipeline, mapped onto this demo's code and output.
- **[`SHOWCASE.md`](./SHOWCASE.md)** — the ten feature-showcase demos in depth (code excerpts per feature), plus the "interesting language features" and "powerful but not wired up here" sections.
- **[`COMPARISON.md`](./COMPARISON.md)** — how the kernel stacks up against five mainstream validators (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation), each introduced from scratch, with a feature-by-feature table.
- **[`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md)** — how the validation language evaluates: truth values, missing data, the "required" property, and the numeric/date/string/repetition/computation rules that determine when a rule fires.
- **[`KERNEL-GLOSSARY.md`](./KERNEL-GLOSSARY.md)** — new to the kernel's vocabulary? Every term used here (DocumentModel, repeating group, the DSL operators, formal validation, typed accessors, …) explained, with pointers into the kernel docs.
- A12 docs & forum: https://geta12.com/#/docs

## License

This demo is released under the **MIT License** — see [`LICENSE`](./LICENSE). That covers *this repository's own* code and docs. The **A12 Kernel** and the A12 OSS repositories this project builds on (built from source by the separate [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap) kit, where the small build-time patches now live) are mgm's, licensed separately — see those repositories for their terms.
