# A12 Kernel — a hands-on demo

> **Validation rules and computed fields, written as data** — in a bilingual, analyst-friendly DSL — compiled by the kernel to Java/TypeScript/Groovy and run against your form data.

This is a **personal project** — a comprehensive set of hands-on showcases for the features and API of the **A12 Kernel**, the model-and-DSL engine behind mgm's *A12* low-code platform for complex business forms.
The premise of the Kernel is: instead of hand-coding validation, you *declare* the **error scenarios** (and computed fields) as data and let the kernel compile them into executable validators. I wrote one small, runnable Java program per capability — to see each one work and compare it against the validators most of us reach for (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation), in **[`COMPARISON.md`](./COMPARISON.md)**.

> **Not an official A12 artifact.** It isn't part of any mgm A12 release and isn't affiliated with or endorsed by mgm — it's a personal exploration, shared in case it's useful to others evaluating or integrating the kernel. *(Mostly Java; one showcase runs the same rules in Node.js with the kernel's TypeScript runtime.)*

## Quick start

After the one-time setup (run the [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap) kit — see [Setup](#setup-one-time) below):

- **`gradle demo`** — the basics: build a Purchase-Order model + sample data, validate it, and print the errors/warnings.
- **`gradle showcases`** — nine feature demos: repeating-group rules, computations, cross-array "SQL-like" logic, the Document API, code generation, and more.
- **`gradle runTyped`** — typed accessors: compile-time-typed access to documents.
- **`gradle runTs`** — the same rules in Node.js with the kernel's [TypeScript runtime](./KERNEL-GLOSSARY.md), compared result by result with Java. Needs Node.js 24 and pnpm; see [Run the TypeScript showcase](#run-the-typescript-showcase).

All eleven feature demos are explained in **[`SHOWCASE.md`](./SHOWCASE.md)**, and weighed against five mainstream validators (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation) in **[`COMPARISON.md`](./COMPARISON.md)**.

For how the engine works under the hood, see **[`KERNEL-DEV-GUIDE.md`](./KERNEL-DEV-GUIDE.md)** — the model → compile → validate pipeline.

## The base demo, in two phases

The base demo is split into the two phases of real-world kernel use — and it's the **only** demo split this way, to teach the **author-once / validate-often** persistence-and-decoupling story (the Java feature demos in [`SHOWCASE.md`](./SHOWCASE.md) are instead self-contained programs that build *and* validate inline):

1. **Author once (`BuilderMain`)** — build a **Purchase-Order DocumentModel** in code (fields + validation rules whose conditions are written in the A12 DSL), build three sample documents, and **persist them all as JSON** under `output/`. Models are created upfront so they can be re-used.
2. **Validate often (`ValidatorMain`)** — **load** the persisted model + documents from JSON and run validation, printing the resulting **errors and warnings**. Fully decoupled from authoring: it consumes JSON via the public kernel API only.

It uses the **dynamic** validation path (the kernel generates + compiles validation code on the fly); everything resolves from your **local Maven repository + Maven Central** — public sources only.

| Entry point | Class | Does |
|---|---|---|
| builder | [`BuilderMain.java`](src/main/java/com/example/a12demo/purchaseorder/BuilderMain.java) | build model + documents → write `output/purchase-order/*.json` |
| validator | [`ValidatorMain.java`](src/main/java/com/example/a12demo/purchaseorder/ValidatorMain.java) | load `output/purchase-order/*.json` → validate → print messages |
| model | [`PurchaseOrderModel.java`](src/main/java/com/example/a12demo/purchaseorder/PurchaseOrderModel.java) | the model definition (used by the builder) |
| showcases | [`SHOWCASE.md`](./SHOWCASE.md) + `*Demo.java` | eleven feature demos (repeating groups, computations, DSL tour, extensions, codegen, cross-array logic, Document API, partial validation, language features, typed accessors, TypeScript parity) |

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

The demo needs the A12 kernel in your local Maven repository (`~/.m2`). The kernel's source code is public on GitHub, but its build artifacts aren't published to any public Maven repository, so you build them yourself with the companion kit, [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap).

The kit builds one A12 release from fixed (pinned) commits of the public repositories, checks the result, and then installs the Java artifacts into `~/.m2`.

### 1. Check the prerequisites

You need:

- JDK 21 and JDK 25 (the A12 base modules build with 25; the kernel itself targets 21)
- Node.js 24 with npm
- Python 3.12 or newer
- Git

You don't need to install Gradle: the kit downloads Gradle 9.8.2 itself and verifies its checksum.

### 2. Clone and run the kit

Clone the kit into the same parent directory as this demo, then run it:

```sh
ROOT="$HOME/oss/a12"    # the parent directory of this demo; adjust to your checkout
git clone https://github.com/mbackschat/a12-kernel-bootstrap "$ROOT/a12-kernel-bootstrap"
"$ROOT/a12-kernel-bootstrap/bootstrap.sh" --doctor    # checks the prerequisites, builds nothing
"$ROOT/a12-kernel-bootstrap/bootstrap.sh"             # builds, verifies, installs into ~/.m2
```

`--doctor` lists each prerequisite as OK or MISSING and suggests a fix for anything that's missing.

The kit keeps its working files (sources, logs, and the downloaded Gradle) in `$ROOT/.a12-bootstrap/2026.06-ext2`.

### What ends up in `~/.m2`

The kit writes to `~/.m2` only after a successful, verified build. It adds the new release and leaves every other release in place, so older artifacts, such as those of 2025.06, stay available next to 2026.06.

To learn what the kit builds, how it verifies the build, and which environment variables it reads, see the [kit's README](https://github.com/mbackschat/a12-kernel-bootstrap#readme).

> **TypeScript packages.** The kit also builds the kernel's TypeScript packages, but it doesn't publish them to any npm registry; it keeps them as npm tarballs in its working directory. The TypeScript showcase copies them from there (see [Run the TypeScript showcase](#run-the-typescript-showcase)).

## Compatibility

This demo is built and tested against A12 release **2026.06-ext2** (kernel 31.3.1). For earlier releases, use their branches:

- 2026.06-ext1 (kernel 31.1.1): `release/2026.06-ext1`
- 2025.06-ext5 (kernel 30.8.1): `release/2025.06`

| Artifact | Version |
|---|---|
| A12 kernel (`com.mgmtp.a12.kernel:*`) | **31.3.1** |
| `a12-base` (`com.mgmtp.a12.base:*`) | **30.1.1** |
| `kernel-core-runtime-api` | 33.2.1 |
| `kernel-core-customfieldtype-api` | 29.0.2 |
| Groovy (runtime, dynamic path) | 3.0.25 |
| JDK / Gradle | 21 / 9.8.2 |

[`build.gradle`](./build.gradle) declares a single kernel dependency, `kernel-md-facade`, and pins only the kernel version. The facade brings in matching versions of the other artifacts in the table, including Groovy.

> **Upgrading your own project from 2025.06?** `~/.m2` keeps old releases, so an outdated dependency doesn't fail the build. Gradle quietly resolves the old jar instead.
>
> Kernel 31 merged several modules into `kernel-md-facade`: `kernel-md-model`, `kernel-md-a12internal`, and `kernel-md-typed-accessor-gen`. Check your dependency report for leftover 30.8.1 or 29.3.0 entries.

> **Upgrading from 2026.06-ext1?** Kernel 31.3 renamed the model builders' `RuleBuilder.field(Field)` and `ComputationBuilder.field(Field)` to `fieldWithDefaultValuesForTest`, a test helper that seeds a default condition or computation. To attach a rule's errors to a field, use `errorFieldToRelPathReference(ElementUtils.getPath(field), field)`. To declare a computation, pass its rows directly: `field(field, List.of(Pair.of(precondition, operation)))`.
>
> For everything else that changed in the kernel between these releases, see [`KERNEL-CHANGELOG.md`](./KERNEL-CHANGELOG.md).

## Build & run

To run the demo you need only a JDK 21; Node.js isn't required.

Run Gradle with a fresh Gradle home: a directory without init scripts. That way Gradle uses only the two repositories that `build.gradle` declares, your local Maven repository and Maven Central. No global setting can add a private repository.

```sh
ROOT="$HOME/oss/a12"                                  # the parent directory of this demo; adjust to your checkout
export GRADLE_USER_HOME="$ROOT/.gradle-demo"          # any directory without init scripts
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"    # macOS; elsewhere, point at any JDK 21
GRADLE="$ROOT/.a12-bootstrap/2026.06-ext2/tools/gradle-9.8.2/bin/gradle"    # the Gradle the kit downloaded

"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon buildModels   # phase 1: build the model and documents, save them as JSON
"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon validate      # phase 2: load them from output/ and validate
# or both at once:
"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon demo
```

`validate` (also the default `run` task) reads whatever is in `output/`. Once you've built the models, you can validate as often as you like without rebuilding them.

If your default `gradle` already uses public repositories only and runs on JDK 21, plain `gradle demo` works too.

### Run the TypeScript showcase

`gradle runTs` validates the demo documents in Node.js and compares every result with Java's. Before the first run, install two more tools:

- Node.js 24 or newer
- [pnpm](https://pnpm.io/installation)

Then run the task in the same shell as the commands above, so `$GRADLE` and `$ROOT` are still set:

```sh
"$GRADLE" -p "$ROOT/a12-kernel-demo" --no-daemon runTs
```

The task does three things:

1. It copies the A12 npm packages from the kit's working directory into `ts/vendor/`.
2. It installs them with pnpm.
3. It downloads the remaining packages from the public npm registry: the TypeScript compiler, the Node.js type definitions, and `big.js`.

If you moved the kit's working directory, set `A12_BOOTSTRAP_HOME` to the full path of its release folder, for example `$ROOT/.a12-bootstrap/2026.06-ext2`.

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

- **[`examples/`](./examples/)** — a **committed, annotated reference set** you can browse without building anything. One folder **per demo** (named after the demo, not the model) — `purchase-order/` (the base demo) plus the showcase folders (`iteration/`, `computation/`, `dsl-tour/`, `extension/`, `cross-array/`, `document-api/`, `partial-validation/`, `language/`, `typed-accessor/`, `typescript/`) — each holding its model `*.dm.json` and sample documents. See [`examples/README.md`](./examples/README.md) for the full, guided tour.
- **`output/`** — where the demos write **fresh** copies on each run, one folder per demo (e.g. `output/purchase-order/purchase-order.dm.json` + `valid/invalid/large.json`). Git-ignored, so running the demos never dirties the repo; `validate` reads from `output/purchase-order/`, and `gradle refreshExamples` copies `output/**/*.json` into `examples/`.

## Learn more

- **[`KERNEL-DEV-GUIDE.md`](./KERNEL-DEV-GUIDE.md)** — developer walkthrough of *what's actually going on*: DocumentModel/Document, the DSL, and the parse → generate → compile → execute validation pipeline, mapped onto this demo's code and output.
- **[`SHOWCASE.md`](./SHOWCASE.md)** — the eleven feature-showcase demos in depth (code excerpts per feature), plus the "interesting language features" and "powerful but not wired up here" sections.
- **[`COMPARISON.md`](./COMPARISON.md)** — how the kernel stacks up against five mainstream validators (Zod, Pydantic, JSON Schema, Bean Validation, FluentValidation), each introduced from scratch, with a feature-by-feature table.
- **[`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md)** — how the validation language evaluates: truth values, missing data, the "required" property, and the numeric/date/string/repetition/computation rules that determine when a rule fires.
- **[`KERNEL-CHANGELOG.md`](./KERNEL-CHANGELOG.md)** — what changed in the kernel between the releases this demo supports, grouped by topic: what to do before you upgrade, what's new, and which behavior changed.
- **[`KERNEL-GLOSSARY.md`](./KERNEL-GLOSSARY.md)** — new to the kernel's vocabulary? Every term used here (DocumentModel, repeating group, the DSL operators, formal validation, typed accessors, …) explained, with pointers into the kernel docs.
- A12 docs & forum: https://geta12.com/#/docs

## License

This demo is released under the **MIT License** — see [`LICENSE`](./LICENSE). That covers *this repository's own* code and docs. The **A12 Kernel** and the A12 OSS repositories this project builds on (built from source by the separate [`a12-kernel-bootstrap`](https://github.com/mbackschat/a12-kernel-bootstrap) kit, where the small build-time patches now live) are mgm's, licensed separately — see those repositories for their terms.
