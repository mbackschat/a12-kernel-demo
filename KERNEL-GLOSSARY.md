# Kernel glossary

The terms you'll meet reading this demo, [`SHOWCASE.md`](./SHOWCASE.md), and the example JSON — explained for someone new to the A12 Kernel. Each entry points, where useful, to the kernel documentation by **chapter → section** in its two guides: the *Validation-Language guide* ("Kernel Language" — for business analysts) and the *Developer guide* ("Kernel" — architecture and the Java / TypeScript APIs). The public docs at geta12.com number these chapters; here they're cited by title. Where a term is illustrated by a runnable showcase, the entry marks it *Shown in `SomeDemo`* on its second line — see [`SHOWCASE.md`](./SHOWCASE.md).

> The key idea to grasp first: **a rule states the *error* scenario.** Its condition is written so that `true` means the data is **invalid**. `FieldNotFilled(CustomerName)` is the rule "the customer name is missing" — when it's true, that's an error.

## The model and the data

- **DocumentModel (DM)** — the *schema*: the groups, fields, validation rules, and computations that describe one kind of form. Authored once (in this demo, in code via the builder API), serialized to JSON, and reused.

  *Docs:* Developer guide → *Entry points to kernel-md → Document model deserialization*
- **Document** (here `DocumentV2`) — an *instance* of data for a DocumentModel: the field values a user entered. Validated against the DM.

  *Shown in* `DocumentApiDemo`  ·  *Docs:* Developer guide → *Document API V2 (Java)*
- **Group** — a named container of fields (and sub-groups), e.g. `Order`. The model is a tree of groups.

  *Docs:* Validation-Language guide → *Models → Model structure*
- **Repeating group / repeatability** — a group that can occur many times (a list), e.g. order *lines* or *tracks*. `repeatability` is the max number of rows (1 = non-repeating).

  *Shown in* `IterationDemo`  ·  *Docs:* Validation-Language guide → *Models → Repeatable Groups and Fields*
- **Field** — a single typed value inside a group (e.g. `Quantity`).

  *Docs:* Validation-Language guide → *Models → Field types*
- **Field type** — the kind of value a field holds: `StringType`, `NumberType`, `DateType`, `BooleanType`, `EnumerationType` (a fixed value set), `ConfirmType` (a checkbox), `CustomFieldType` (project-defined, validated by your code).

  *Enumerations in `DslTourDemo`/`LanguageDemo`; a `CustomFieldType` in `ExtensionDemo`.*  ·  *Full list of types + variants + per-type evaluation meaning:* [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md) → *The field types at a glance*.  ·  *Docs:* Validation-Language guide → *Models → Field types*
- **Index field** — a field that *keys* the rows of a repeating group (e.g. `Warehouse`), enabling associative access and parallel iteration.

  *Shown in* `CrossArrayDemo`  ·  *Docs:* Validation-Language guide → *Rules → Rules and repeatability*
- **Required / mandatory** — a field marked *required* in the field editor (optionally "only if the parent group is filled") behaves like a `FieldNotFilled` rule and, when empty, raises a *formal error* — so the field becomes "unknown". The *mandatory star* on a label is a separate heuristic: not every mandatory field is starred.

  *Docs:* [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md) §4  ·  Validation-Language guide → *Appendix → Mandatory fields have an asterisk in their label*

## Rules and the DSL

- **DSL (domain-specific language) / validation language** — the small, bilingual language rule conditions are written in. Authored as model *data*, not code.

  *Shown in* `DslTourDemo`  ·  *Docs:* Validation-Language guide (the whole guide)
- **`conditionLanguage`** — a model setting selecting whether the DSL *keywords* are English or German (the same rule is `AllFieldsFilled(...)` or `AlleFelderAngegeben(...)`).

  *Docs:* Developer guide → *Supported Languages*
- **Rule** — a named validation: an `errorCondition` (the DSL), an `errorCode`, a `severity`, the **error field** it attaches to, and a localized `errorMessage`.

  *The base Purchase-Order demo has four.*  ·  *Docs:* Validation-Language guide → *Rules*
- **Error field (errorEntity)** — the field a rule's message is attached to (so a UI can highlight it). Must be *referenced* in the rule's own condition.

  *Per-row pointers in `IterationDemo`.*  ·  *Docs:* Validation-Language guide → *Rules → Error Field*
- **Severity** — `ERROR` (blocks), `WARNING` ("are you sure?"), or `INFO`. Model metadata on the rule, not part of the condition.

  *The base demo shows ERROR and WARNING.*  ·  *Docs:* Validation-Language guide → *Rules → Error texts for Rules*
- **Field-value operator `[Field]`** — reads a field's value in a condition: `[Quantity] <= 0`. Compare with `FieldFilled(x)`, which tests *presence*.

  *Shown in* `DslTourDemo`  ·  *Docs:* Validation-Language guide → *Operators and language constructs → The Field value operator*
- **Paths & short names** — fields are addressed by path: absolute `/Order/Quantity`, relative `../Other`, or a bare short name when `fieldRefByShortNameAllowed` is on. Prefer absolute paths across sibling groups.

  *Absolute paths in `CrossArrayDemo`.*  ·  *Docs:* Validation-Language guide → *Models → Paths*
- **`RuleGroup` / `@From`** — `RuleGroup` is "the group this rule lives in"; `@From <group>` scopes a cross-row check (e.g. uniqueness) to that group.

  *Shown in* `IterationDemo`  ·  *Docs:* Validation-Language guide → *Rules → Rule Group*
- **Asterisk operator `*`** — flattens a repeating group into the *list of all its rows* for an aggregate: `Sum(Tracks*/Seconds)`. (`**` flattens a whole subtree.)

  *Shown in* `IterationDemo`  ·  *Docs:* Validation-Language guide → *Models → Field lists*
- **`Having` (filter operator)** — restricts an aggregate to the rows matching a predicate: `Sum(Lines*/Qty Having [Lines/Rush]==True)`.

  *Shown in* `IterationDemo` and `CrossArrayDemo`  ·  *Docs:* Validation-Language guide → *Operators and language constructs → Functional language constructs*
- **`$`-operator** — inside a `Having`, pins a reference to the *current* row of the iterating group while filtering another list (a correlated subquery). Must precede the whole path: `[$/Shipment/Stops/Eta]`.

  *Shown in* `CrossArrayDemo`  ·  *Docs:* Validation-Language guide → *Operators and language constructs → Functional language constructs*
- **`CurrentRepetition`** — the 1-based index of the current row; combined with `$` it expresses "all preceding rows", e.g. for order checks.

  *Shown in* `CrossArrayDemo`  ·  *Docs:* Validation-Language guide → *Operators and language constructs → Functional language constructs*
- **Semantic index `For`** — addresses a row by its index-field value instead of position: `Addresses For "Delivery"`. (Documented; not demoed here.)

  *Docs:* Validation-Language guide → *Models → Paths*
- **Parallel iteration** — when a rule references two repeating groups that share an index field, the kernel iterates them *joined by that key* (an outer join).

  *Shown in* `CrossArrayDemo`  ·  *Docs:* Validation-Language guide → *Rules → Rules and repeatability*
- **Enumeration categories / `->`** — enum values can carry a named attribute vector; `[Country -> AdministrationArea]` reads that attribute, so you branch on a *group* of values.

  *Shown in* `LanguageDemo`  ·  *Docs:* Validation-Language guide → *Models → Field types*
- **Error-text parameters** — `$Field.value$` (the value) and `$Field$` (the label) interpolate into messages; `$index(x).value$` and `$#Group$` reach iteration context.

  *Shown in* `IterationDemo`  ·  *Docs:* Validation-Language guide → *Rules → Error texts for Rules*
- **Computation / computed field** — a *derived* value defined in the model as a `Precondition | Calculation` table (e.g. `[Price]*[Quantity]`), evaluated by the engine. A computed field is also re-validated against its formula.

  *Shown in* `ComputationDemo`; the self-validating aspect in `LanguageDemo`  ·  *Docs:* Validation-Language guide → *Computation Rules*

## Validating, and reading results

- **Full vs partial validation** — `validateFull` checks the whole document; `validatePart` checks only a "relevant" subset (e.g. the current wizard page) and holds back errors you can't yet fix.

  *Shown in* `PartialValidationDemo`  ·  *Docs:* Validation-Language guide → *Appendix → Full Validation and Partial Validation*
- **Formal validation / formal error** — the field-type checks the kernel performs for free (e.g. a decimal in an integer field). While a formal error stands, the field is **"unknown"** and cannot be evaluated by *any* rule. These carry the rule path `formalePruefung` (German for "formal check" — a handful of kernel-internal identifiers and default error codes are German), distinct from your rule errors.

  *Shown in* `ExtensionDemo`  ·  *Docs:* Validation-Language guide → *Appendix → Validation and formal validation*
- **`IMessage`** — one validation result: `getSeverity()`, `getErrorCode()`, `getErrorText()`, `getMessageType()`, `getErrorFieldPointer()` (which field), `getRulePath()` (which rule).

  *Printed by every demo via `Demos.report`.*  ·  *Docs:* Developer guide → *Further Resources → Validating a document*
- **`messageType`** — `VALUE_ERROR` (a value is wrong) vs `OMISSION_ERROR` (something required is missing).

  *Docs:* Developer guide → *Further Resources → Validating a document*
- **`noErrorOccurred()`** — `true` when there are no `ERROR`-severity messages (WARNING/INFO don't count).

  *The base demo's `large` document is VALID despite a WARNING.*  ·  *Docs:* Developer guide → *Further Resources → Validating a document*
- **Dynamic vs static code generation** — *dynamic* (this demo): the kernel generates + compiles validation code on first use and caches it. *Static*: pre-generate the code at build time and ship it.

  *Generating standalone code is shown in `CodegenDemo`.*  ·  *Docs:* Developer guide → *Getting started* and *Architecture*
- **Groovy (runtime)** — the dynamic path generates server-side validation code as Groovy, so Groovy must be on the runtime classpath.

  *Docs:* Developer guide → *Architecture*

## Documents and APIs (Java)

- **`DocumentV2`** — the immutable document data structure: every edit (`withFieldValue`) returns a *new* document that shares the unchanged subtrees (copy-on-write).

  *Shown in* `DocumentApiDemo`  ·  *Docs:* Developer guide → *Document API V2 (Java) → Updating Documents and Groups*
- **Auto-vivification** — writing a deep path (`Ingredients[3]/Name`) auto-creates the missing ancestor groups and lower rows.

  *Shown in* `DocumentApiDemo`  ·  *Docs:* Developer guide → *Document API V2 (Java)*
- **`DocumentPointer` / `DocumentMultiPointer`** — typed addresses into a document; repetition index `0` is the "all rows" wildcard.

  *Shown in* `PartialValidationDemo` and `DocumentApiDemo`  ·  *Docs:* Developer guide → *Document API V2 (Java) → DocumentPointer*
- **DM-aware document service** — model-aware helpers: `convertToJavaTypeV2` (coerce a raw string to the model's Java type), `removeUnknowns` (strip fields not in the model), structural-consistency checks.

  *Shown in* `DocumentApiDemo`  ·  *Docs:* Developer guide → *Document API V2 (Java) → Additional Utilities*
- **Custom condition** (`ICustomCondition`) — your Java predicate, invoked from a rule via `CustomCondition <Name>` and given the whole document.

  *Shown in* `ExtensionDemo`  ·  *Docs:* Developer guide → *Custom Condition*
- **Custom field validator** (`ICustomFieldValidator`) — your Java validator bound to a `CustomFieldType`, run automatically wherever that field type is used.

  *Shown in* `ExtensionDemo`  ·  *Docs:* Developer guide → *Custom Field Type*
- **Code generation** — `generateValidationCode` compiles a model to standalone Java / JavaScript / Groovy validators.

  *Shown in* `CodegenDemo`  ·  *Docs:* Developer guide → *Further Resources → Generate validation code from document model*
- **Typed accessors (overlay)** — generated, compile-time-typed view/pointer classes for a specific model, so navigating a document is autocompleted and DM drift becomes a compile error.

  *Shown in* `TypedAccessorDemo`  ·  *Docs:* Developer guide → *Typed Accessor Classes for Compile-Time Type Checks*
- **Mapping / structural mapping** — transferring data between models, with per-field provenance. (Not built in this demo's setup.)

  *Docs:* Developer guide → *Mapping*
- **Model migration** — upgrading older DocumentModel JSON to the version your kernel supports (the DM JSON format is versioned independently of the kernel).

  *Docs:* Developer guide → *Further Resources → Migration of document models*

## Evaluation semantics

How the language treats truth values, missing data, numbers, dates, the "required" property, and more — the behaviours not evident from the syntax — is documented in full, with examples, in [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md). A few facts that recur constantly:

- **No empty string** — `[F] == ""` is never satisfied; test absence with `FieldNotFilled(F)`.
- **Numbers default to 0** — an unset *number* counts as `0` in comparisons (so `[Amount] < 100` fires on an empty field); guard with `FieldFilled`.
- **No negation operator** — there is no `Not`; use the paired predicates (`NoFieldFilled`, `NotAllFieldsFilled`, …).
- **Boolean is three-state** — `[Flag] == False` is not "unchecked"; an unset Boolean satisfies neither `== True` nor `== False`.
- **Keyword collisions** — a field named like a keyword (e.g. `Date`) must be single-quoted in a path: `Order/'Date'`.
- **`MVK_*` codes** — the catalog of parser/validation diagnostics emitted when a rule won't compile.

See [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md) for these and the rest — Boolean/Confirm details, the "required" property, decimal-scale and rounding rules, date arithmetic, repetition and path rules, computations, and the full/partial validation model.

## Project / build jargon

- **`internal` / `a12internal` tier** — kernel API packages that are *not* part of the supported public contract (they can change between versions). This demo touches them only to *build* models in code; the consumption path is pure public API. See [`SHOWCASE.md`](./SHOWCASE.md) §0 and [`KERNEL-DEV-GUIDE.md`](./KERNEL-DEV-GUIDE.md).
- **`betterbuilders`** — the internal-tier fluent builder API (`DocumentModelBuilder`, `FieldBuilder`, `RuleBuilder`, …) used here to assemble a model in Java. (There is no *public* in-code model builder; the public alternative is authoring the model as JSON.)
- **`kernel-md-*` / `kernel-rt-*` / `kernel-tool-*` (the module families)** — the abbreviations in the artifact names: **`md`** = *model & document* (the model/document API — the consumer front door), **`rt`** = *runtime* (executes the generated validation/computation code), **`tool`** = *compile-time tooling* (parses the DSL and generates code). The pipeline is `tool` → `md` → `rt`.

  *Docs:* Developer guide → *Architecture*
- **`kernel-md-facade`** — the single public entry-point artifact a consumer depends on (`md` = model & document, per above); it transitively pulls in model/runtime/serializer.
- **`mavenLocal` / the bootstrap** — the kernel's source is public (on GitHub) but its prebuilt artifacts aren't published to a public Maven repository, so `setup/bootstrap.sh` builds them from those open-source repositories and publishes them to your local Maven repo (`~/.m2`). See [`README.md`](./README.md) → Setup and [`setup/README.md`](setup/README.md).

---

For the full reference, see the A12 docs & forum: https://geta12.com/#/docs
