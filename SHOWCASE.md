# Feature showcase

Beyond the basic Purchase-Order demo, this repo ships ten **standalone, runnable** demos — one per kernel feature area. Each is a single `*Demo` `main` with its own self-contained model (built in code), sample documents, and a rich result print-out, on its **own original domain** (playlist, timesheet, event registration, product catalog, shipment routing, recipe, loan application, vehicle inspection, subscription). The kernel docs were only a guide to *which* features exist.

Every demo persists its built model and documents to `output/` (git-ignored); those are copied into the committed [`examples/`](./examples) so you can read the real JSON without running anything (see [`examples/README.md`](./examples/README.md)). Doc references below point into the two kernel guides by chapter → section title:

- The *Validation-Language guide* ("Kernel Language") — for business analysts.
- The *Developer guide* ("Kernel") — architecture and the Java/TypeScript APIs.

New to the kernel's vocabulary (DocumentModel, repeating group, the DSL operators, formal validation, …)? Keep [`KERNEL-GLOSSARY.md`](./KERNEL-GLOSSARY.md) open alongside this.

Start with **§0**, the base Purchase-Order demo (the README walks it through end to end); the ten that follow form a progression in two clusters — **A. authoring validation logic** (§1–§5, increasing power) and **B. working with the engine & documents** (§6–§10). The `§` column links to each demo's in-depth section below (same order).

| § | Demo | What it demonstrates | Domain | Run (`gradle …`) |
|---|---|---|---|---|
| 0 | `PurchaseOrderModel` *(base)* | The basics — author → persist → load → validate (two-phase) | Purchase order | `demo` |
| 1 | `DslTourDemo` | A tour of the DSL | Event registration | `runDsltour` |
| 2 | `IterationDemo` | Structural rules over a repeating group | Playlist + tracks | `runIteration` |
| 3 | `CrossArrayDemo` | Cross-array logic (SQL-like queries over arrays) | Shipment routing | `runCrossarray` |
| 4 | `ComputationDemo` | Computed / derived fields | Weekly timesheet | `runComputation` |
| 5 | `LanguageDemo` | Interesting language features | Vehicle inspection | `runLanguage` |
| 6 | `ExtensionDemo` | Extending the kernel | Product catalog | `runExtension` |
| 7 | `DocumentApiDemo` | The document is a real data structure | Recipe + ingredients | `runDocapi` |
| 8 | `PartialValidationDemo` | Incremental "wizard" validation | Loan-application wizard | `runPartial` |
| 9 | `CodegenDemo` | Compile a model to code | (reuses playlist) | `runCodegen` |
| 10 | `TypedAccessorDemo` | Typed accessor overlays | Subscription + add-ons | `runTyped` |

`gradle showcases` runs the nine pure-runtime demos (§1–§9); the tenth (`gradle runTyped`) is separate because it adds an isolated build-time codegen step. Shared plumbing (model bridge, runtime wiring, bilingual messages, and the rich `IMessage` printer) lives in [`Demos.java`](src/main/java/com/example/a12demo/Demos.java).

---

## 0. Start here — `PurchaseOrderModel` (the basics)

The base demo is the hello-world: a small Purchase-Order model (a few String/Number/Date/Boolean fields + four plain rules). It's the one the [README](./README.md) walks through end to end (setup → `gradle buildModels` → `gradle validate` → expected output), so this section just places it in context.

It's also the **only two-phase demo**, and that's deliberate: `BuilderMain` (author + persist) and `ValidatorMain` (load + validate) are split to teach the **author-once / validate-often** workflow — models are built and serialized up front, then loaded and validated separately, fully **decoupled** from how they were authored (the validator only consumes JSON via the public API). The ten showcases below are instead **single self-contained `main`s** that build *and* validate inline (via `Demos.check`), to keep each one focused on the single feature it illustrates.

Run it with `gradle demo` (runs `buildModels` then `validate`).

---

# A. Authoring validation logic

## 1. `DslTourDemo` — A tour of the DSL

**The scenario.** A conference *Event registration* form — the sort of thing you'd normally pepper with hand-written `if` statements scattered across controllers. Here every business rule is **one line of the A12 DSL, authored as model data**, so it reads like a requirement rather than code.

**What it shows.** A breadth sampler of the language's single-field constructs, one rule each, so you can see the vocabulary at a glance. The demo validates two documents: a *complete* registration that passes cleanly, and an *incomplete* one deliberately crafted to trip nine rules at once. Keep the kernel's golden rule in mind — **a rule states the *error* scenario**, so its condition is written to be `true` exactly when the data is *invalid*:

```java
// string slicing — ticket must be issued in the EU region
"FieldFilled(TicketCode) And RangeAsString(TicketCode, 1, 2) != \"EU\""
// regex (cross-language-safe subset)
"FieldFilled(PromoCode) And [PromoCode] PatternViolated \"[A-Z]{2}[0-9]{4}\""
// string length
"FieldFilled(AccessPin) And Length(AccessPin) != 5"
// first-class tolerance comparison
"[QuotedFee] DiffersWithToleranceRange5 [PaidFee]"
// value-list membership (sold-out seat blocks)
"FieldValueIncludedInValueList(SeatBlock, \"VIP\", \"BACKSTAGE\", \"PRESS\")"
// date arithmetic — passport must stay valid 6+ months
"FieldFilled(PassportExpiry) And DifferenceInMonths(Today, PassportExpiry) < 6"
// enum comparison + conditional requiredness
"[Decision] == \"DECLINED\" And FieldNotFilled(DeclineReason)"
// "at least one of N"
"NoFieldFilled(Phone, Email)"
```

Each reads like business language but is authored as model data; several (string slicing, tolerance, "at least one of N") have no built-in equivalent in mainstream validators. `Decision` is an `EnumerationType` with bilingual value labels, compared by its stored value. Note the recurring `FieldFilled(x) And …` guard: it stops a rule from firing on an *empty* field (that's a separate concern — see the null semantics in "More interesting language features" below).

Run it with `gradle runDsltour`: the incomplete document prints nine `[ERROR]`/`[WARNING]` lines — each naming its error code, message, and the exact field it attaches to — while the complete one prints `VALID`. The takeaway isn't any one operator; it's that *all* of this expressiveness is declarative data a business analyst can read and change, not branching code a developer has to maintain.

**Docs.**

- Validation-Language guide → *Operators and language constructs*:
    - *String operations* — `RangeAsString`, `PatternViolated`
    - *Comparison operations* — `DiffersWithToleranceRange`
    - *Predicate language constructs* — `FieldValueIncludedInValueList`, `NoFieldFilled`, `FieldNotFilled`
    - *Date and Time language constructs* — `Today`, `DifferenceInMonths`
- Validation-Language guide → *Models* → *Field types* (enumerations)
- Validation-Language guide → *Parser messages* → *Pattern* / *Date* (catalogued parser/limit details)

## 2. `IterationDemo` — Structural rules over a repeating group

**The scenario.** Real forms have *lists* — order lines, contacts, tracks. Validating across the rows ("no duplicates", "the total mustn't exceed X") is where most validators make you drop into a loop. This demo is a music *Playlist* with a repeating list of *Tracks*, and three rules that work over the rows **entirely declaratively** — the kernel does the looping, including pointing each error at the right row.

**What it shows.** First, how a repeating group is declared — one builder call sets the max repetitions, then fields are added by path:

```java
GroupBuilder.with(dm).name("/Playlist/Tracks").repeat(200).build();   // max 200 rows
Field title = FieldBuilder.with(dm).name("/Playlist/Tracks/Title").ft(StringTypeBuilder.builder()).build();
```

**Per-row uniqueness.** The rule lives *inside* the repeating group and attaches its error to `Title`, so the kernel iterates the rows and reports the error on the *specific* duplicated row(s) — you don't compute the offending index yourself:

```java
RuleBuilder.with(dm).name("/Playlist/Tracks/UniqueTitleRule").field(title).modify(rb -> rb
        .errorCondition("RepetitionNotUnique(Title @From RuleGroup)")
        .errorCode("DUPLICATE_TITLE").severity(Severity.ERROR)
        .errorMessage(Demos.text("The track '$Title.value$' appears more than once.", "...")));
```

Two tracks named "Echo" produce two messages, each pointing at its own row (the prettified pointer reads `Playlist[1]/Tracks[1]/Title` vs `Playlist[1]/Tracks[2]/Title`), and the message interpolates the offending value via `$Title.value$`.

**Aggregate and filtered aggregate.** `Sum` over all rows (the `*` flattens the repetition), and `Sum(... Having ...)` aggregates only the subset matching a predicate evaluated per row:

```java
.errorCondition("FieldFilled(Name) And Sum(Tracks*/Seconds) > 3600")                              // total runtime > 1h
.errorCondition("FieldFilled(Name) And Sum(Tracks*/Seconds Having [Tracks/Explicit]==True) > 600") // explicit runtime > 10m
```

(The `FieldFilled(Name)` conjunct is there because a rule's error field — here the playlist `Name` the aggregate attaches to — must be referenced in its own condition.)

Why this matters: `RepetitionNotUnique` and the `Sum(… Having …)` filter are *primitives*, not patterns you assemble by hand. In a code-first validator like Zod you'd write a `superRefine` that loops the array, tracks seen values in a `Set`, and manually pushes an issue at `ctx.addIssue({ path: [i, 'title'] })`; JSON Schema can't express a per-field cross-row key or a filtered aggregate at all. Run `gradle runIteration` to see the duplicate-title document produce two errors, one per offending row — the natural next rung is the cross-array logic in §3.

**Docs.**

- Validation-Language guide → *Models* → *Repeatable Groups and Fields*
- Validation-Language guide → *Rules* → *Rules and repeatability*, and *Error texts for Rules* (the `$Field.value$` token)
- Validation-Language guide → *Operators and language constructs* → *Functional language constructs* (`Sum`, `RepetitionNotUnique`, the `Having` filter and the `*` asterisk operator)

## 3. `CrossArrayDemo` — Cross-array logic (SQL-like queries over arrays)

**The scenario.** Now the rules span *two* lists, or relate a row to *other* rows — the territory where you'd normally reach for nested loops or even an in-memory join. A *shipment routing* document holds warehouse `Demand` and `Capacity` (two lists), a list of route `Stops`, and a `Holidays` calendar. Everything lives under one top-level `Shipment` group, because a rule must belong to a group and these rules reach across its sub-lists.

**What it shows.** Four rules, each a declarative one-liner that a code-first validator would express as a hand-written loop — and that JSON Schema can't express at all. It's the advanced end of the iteration ladder from §2, and the clearest "a validator that does SQL-like queries" example in the repo.

**Parallel iteration — join two arrays by a key.** `Demand` and `Capacity` are two repeating groups that each declare a `Warehouse` *index field*; the kernel iterates them **joined by that key** (an outer join, inferred purely from the shared index — you set it with `GroupBuilder…​.indexField("Warehouse")`):

```java
// both-or-neither, per warehouse:
"FieldsNotCollectivelyFilled(Demand/Units, Capacity/Units)"
// demand must not exceed capacity, comparing the joined rows:
"FieldFilled(Demand/Units) And FieldFilled(Capacity/Units) And [Demand/Units] > [Capacity/Units]"
```

**Correlated filter — a subquery via the `$`-operator.** For each routing `Stop`, `$` pins the current stop while filtering the `Holidays` array, flagging a stop whose ETA lands on a holiday:

```java
"FieldFilled(/Shipment/Stops/Eta) And NumberOfFilledFields(/Shipment/Holidays*/HolidayDate Having [/Shipment/Holidays/HolidayDate] == [$/Shipment/Stops/Eta]) > 0"
```

**Row-order check — `CurrentRepetition`.** Each stop is compared against *all its predecessors*, so the ETAs must be chronologically ordered:

```java
"MaxValue(/Shipment/Stops*/Eta Having CurrentRepetition(/Shipment/Stops) < CurrentRepetition($/Shipment/Stops)) >= [/Shipment/Stops/Eta]"
```

If you think in SQL: parallel iteration is an *outer join* on the index field, the `$`-filter is a *correlated subquery*, and `CurrentRepetition` lets a row compare itself to its *predecessors* — all as declarative rule text. `gradle runCrossarray`'s "problems" document trips all four, each pointing at the offending row (`Demand[2]` HAM unmatched, `Demand[3]` CGN over-capacity, `Stops[2]` out of order, `Stops[3]` on a holiday). These depend on precise path, `$`-operator and asterisk syntax — get them wrong and the model fails at code generation with an `MVK_*` error.

**Docs.**

- Validation-Language guide → *Rules* → *Rules and repeatability* (parallel iteration)
- Validation-Language guide → *Operators and language constructs* → *Functional language constructs* (the `Having` filter, the `$`-operator, `CurrentRepetition`, `MaxValue`/`NumberOfFilledFields`)

## 4. `ComputationDemo` — Computed / derived fields

**The scenario.** Validation is only half of what the kernel does; the other half is **deriving** values. A weekly *Timesheet* with repeating *Days* needs a per-day pay, a week total, and a tiered bonus — numbers you'd normally compute in service code and hope stay consistent with the data. Here they're part of the model.

**What it shows.** Computations are declared in the model as a `Precondition | Calculation` table, evaluated by the *same generated code* as validation, and applied back onto the (immutable) document. The three computations build on each other — a per-row calc, an aggregate of a computed field, and a tiered calc chosen by mutually-exclusive preconditions:

```java
computation(dm, "/Sheet/Days/DayPayComp", dayPay, alt(null, "[Hours]*[Rate]"));        // per row
computation(dm, "/Sheet/WeekPayComp",     weekPay, alt(null, "Sum(Days*/DayPay)"));     // aggregate of a computed field
computation(dm, "/Sheet/BonusComp", bonus,                                             // tiered: mutually-exclusive preconditions
        alt("[WeekPay] >= 1000",                       "RoundDown([WeekPay] * 0.10, 2)"),
        alt("[WeekPay] >= 500 And [WeekPay] < 1000",   "RoundDown([WeekPay] * 0.05, 2)"),
        alt("[WeekPay] < 500",                         "0.00"));
```

`WeekPay` depends on the computed `DayPay` (a cross-level dependency the engine orders for you), and `Bonus` selects a rate by mutually-exclusive preconditions. Only the inputs are supplied; the engine fills the rest:

```java
IDocumentComputationResult comp = rt.compute(input, cfg);
DocumentV2 computed = comp.applyTo(input);          // immutable — a NEW document with the derived values
Object weekPay = computed.fieldValue("Sheet[1]/WeekPay");   // 1040
Object bonus   = computed.fieldValue("Sheet[1]/Bonus");     // 104  (>=1000 tier -> 10%)
```

Note the decimal-place discipline: `Bonus` is a 2-fraction-digit field, so the calculation rounds to 2 places (`RoundDown(..., 2)`) to match — a mismatch is itself an error. `gradle runComputation` feeds only hours and rates, then prints the engine-filled `DayPay` per day, `WeekPay = 1040`, and `Bonus = 104`. Because the calculations live in the model, a business analyst can change the bonus tiers without touching code — and (as §5 shows) the kernel even re-validates the derived values against their own formula.

**Docs.**

- Validation-Language guide → *Computation Rules* → *Computation tables*, *Preconditions*, *Common Precondition*, *Number of decimal places*, *Calculation cycles*
- Developer guide → *Further Resources* → *Validating a document / Computations on a document* (the `compute(...)` / `applyTo(...)` API)

## 5. `LanguageDemo` — Interesting language features

**The scenario.** A *vehicle inspection* form, chosen to surface three behaviours that differ markedly from how mainstream validators work.

**What it shows.** Three standout language features in one model:

```java
// self-validating computation: TotalScore = Score1 + Score2 is ALSO a validation rule.
// A document that carries TotalScore=99 (≠ 4+5) is an error — derived values are re-checked.
ComputationBuilder…field(total).modify(cb -> cb.computationAlternatives(
        List.of(ComputationAlternative.builder().operation("[Score1]+[Score2]").build())) …);

// enum categories + ->: enum values carry an attribute vector; branch on the GROUP, not each value.
"[FuelType -> Emissions] == \"COMBUSTION\" And FieldNotFilled(EmissionsCert)"

// Valid()/Invalid(): assemble a date from parts and detect impossible calendar dates (31 Feb).
"Invalid(Date(Day, Month, Year))"
```

The self-validating computation is the standout: a computed field isn't just *filled* by the engine, it's also *checked* — if a document carries a `TotalScore` that disagrees with `Score1 + Score2`, that's an error, so derived values can't silently drift out of sync. `gradle runLanguage`'s second document carries `TotalScore = 99` against scores of 4 and 5, picks `DIESEL` without an emissions cert, and assembles `31 February` — and trips all three rules at once. Derived-fields-that-validate-themselves, attribute matrices on enum members, and calendar-validity of assembled dates are all outside the vocabulary of mainstream validators.

**Docs.**

- Validation-Language guide → *Computation Rules* → *Computation Rules as Validation Rules*
- Validation-Language guide → *Models* → *Field types* (enumeration categories)
- Validation-Language guide → *Operators and language constructs* → *Date and Time language constructs* (`Valid`/`Invalid`/`Date`)

---

# B. Working with the engine & documents

## 6. `ExtensionDemo` — Extending the kernel

**The scenario.** Sooner or later a check needs real code — a checksum, an external format, an algorithm the DSL doesn't (and shouldn't) have. A *Product catalog* entry has a barcode that must pass an EAN-13 checksum and a colour that must be a hex code. This is the bridge from cluster A (authoring rules) to cluster B (working with the engine in Java).

**What it shows.** The two escape hatches — both registered on the processing config, so they're injected without changing the model — plus the type checking the kernel does for free:

```java
DocumentProcessingConfig cfg = DocumentProcessingConfig.builder(Locale.US)
        .customConditionFactory(ICustomConditionFactory.fromMap(Map.of(
                "Ean13Invalid", new Ean13ChecksumCondition("/Product/Barcode"))))
        .customFieldTypeFactory(ICustomFieldTypeFactory.fromMap(Map.of(
                "HexColor", new HexColorValidator())))
        .build();
```

**Custom condition** — a Java predicate called from a rule as `CustomCondition Ean13Invalid`. It computes an EAN-13 barcode checksum (returning `true` = the rule fires = invalid):

```java
public boolean check(DocumentV2 document, Set<? extends DocumentMultiPointer> relevantEntities,
        Set<DocumentPointer> formallyIncorrectEntities, PartiallyKnownDocumentMultiPointer errorEntityInstance) {
    Object value = document.fieldValue(fieldPath);
    return value != null && !isValidEan13(value.toString());   // mod-10 check digit
}
```
```java
.errorCondition("FieldFilled(Barcode) And CustomCondition Ean13Invalid")
```

**Custom field validator** — a field of a project-defined type `HexColor`, validated by a Java `ICustomFieldValidator` the kernel invokes during normal validation:

```java
public Optional<ICustomFieldTypeCheckError> validate(String value, ICustomFieldTypeValidationParam p, boolean isDisplayValue) {
    if (value == null || value.isEmpty() || value.matches("#[0-9A-Fa-f]{6}")) return Optional.empty();
    return Optional.of(new CheckError("HEX_COLOR_INVALID", "Color must be a hex code like #1A2B3C."));
}
```

**Free formal errors.** A fractional value in an integer field needs no rule — the kernel's field-type checking reports it (with rule path `formalePruefung`, distinct from your rule errors):

```
[ERROR VALUE_ERROR] zahlHatDezimalTrenner  The value must be integer.   rule: formalePruefung
[ERROR VALUE_ERROR] HEX_COLOR_INVALID       Color must be a hex code like #1A2B3C.
[ERROR VALUE_ERROR] BARCODE_CHECKSUM        Barcode failed its EAN-13 checksum.   rule: /Product/BarcodeRule
```

The distinction between the two hatches matters: a **custom condition** is invoked *from a rule* (`CustomCondition Ean13Invalid`) and gets the whole document, so it can correlate fields; a **custom field validator** is bound to a *field type* and runs automatically wherever that type is used. `gradle runExtension`'s malformed product produces all three — the formal error (rule path `formalePruefung`), the hex-colour error, and the barcode-checksum error — showing how project code and the kernel's own field checks land in the same result list. This is the closest analog to a Zod `.refine`, but invoked from a separate, data-authored rule layer.

**Docs.**

- Developer guide → *Custom Condition*
- Developer guide → *Custom Field Type* → *Create a field of type Custom*, *Implement your own logic*, *Create/Provide your Custom Field Type Factory*
- Validation-Language guide → *Operators and language constructs* → *CustomCondition*
- Validation-Language guide → *Appendix* → *Validation and formal validation* (the formal-vs-rule distinction)

## 7. `DocumentApiDemo` — The document is a real data structure

**The scenario.** A validator checks *your* objects and hands them back. The A12 kernel is different: it owns **the document** — a first-class, immutable data structure you build, read, diff, and sanitise through its API. This demo uses a `Recipe` with a repeating `Ingredients` list to show what that buys you, with no validation rules at all.

**What it shows.** `DocumentV2` is a persistent (copy-on-write) tree with structural sharing, plus model-aware tooling — capabilities that sit outside what a code-first validator offers, because it never has a document of its own:

```java
// auto-vivifying write: setting Ingredients[3] creates empty rows [1] and [2]
DocumentV2 v1 = DocumentV2.empty(DM_ID)
        .withFieldValue("Recipe[1]/Title", "Pancakes")
        .withFieldValue("Recipe[1]/Ingredients[3]/Name", "Milk");          // -> 3 rows

DocumentV2 v2 = v1.withFieldValue("Recipe[1]/Title", "Fluffy Pancakes");  // a NEW document...
assert v1 != v2;
assert v1.group("Recipe[1]/Ingredients[3]") == v2.group("Recipe[1]/Ingredients[3]"); // ...that REUSES the unchanged subtree

// built-in diff:
DocumentChanges diff = DocumentV2Utils.compare(v1, v2, CompareConfig.builder().emptyAndNullAsAbsent(true).build());
// -> UPDATED Recipe/Title : "Pancakes" -> "Fluffy Pancakes"

// DM-aware tooling: coerce a raw string to the model's Java type, and strip fields not in the model
Object grams = dmAware.convertToJavaTypeV2("/Recipe/Ingredients/Grams", "120", notes::add); // BigDecimal 120
DocumentV2 cleaned = dmAware.removeUnknowns(docWithStrayField);
```

Each line is a distinct capability: **auto-vivification** means you set a deep path and the kernel materialises the missing parents; **structural sharing** means an edit copies only the changed spine and physically reuses the rest (the `==` assertion proves it — cheap snapshots and diffs); **`compare`** gives you change-tracking out of the box; and **`convertToJavaTypeV2`/`removeUnknowns`** turn raw inbound JSON into model-typed, model-clean data. `gradle runDocapi` prints each step. A persistent/immutable document tree with verifiable structural sharing, plus diffing and schema-driven coercion/sanitisation, is a data-framework feature set, not a validation one.

**Docs.**

- Developer guide → *Document API V2 (Java)* → *Updating Documents and Groups*, *DocumentPointer*, *Additional Utilities*
- Developer guide → *Further Resources*

## 8. `PartialValidationDemo` — Incremental "wizard" validation

**The scenario.** Multi-page forms have a UX trap: validate everything up front and page 1 lights up with errors about page 3; validate nothing and the user sails past mistakes. A 3-page *loan application* (Applicant → Employment → Loan) shows the kernel's answer — validate exactly what's currently in scope.

**What it shows.** `validatePart` takes the set of fields the user can currently see and reports an error only if its rule's error field **and** every field the rule references are "relevant" — so the user is never nagged about a page they haven't reached, *and* a cross-page rule stays quiet until both its pages are in scope:

```java
Set<DocumentMultiPointer> page1 = Set.of(DocumentMultiPointer.ofPathWithAllWildcards("Applicant"));
rt.validatePart(doc, page1, cfg);   // only Applicant-page errors
```

`gradle runPartial` runs the *same* document five ways and the contrast is the whole point: `validateFull` → NAME_REQUIRED + NOT_AFFORDABLE; page 1 → NAME_REQUIRED only; page 3 (Loan) **alone** → clean, because the affordability rule also references income (page 2), so it's held back; the review step (Employment + Loan together) → NOT_AFFORDABLE. The guarantee is one-directional and documented: partial validation never reports an error you can't currently fix, at the cost of deliberately *skipping* checks that aren't yet in scope. Mainstream validators are all-or-nothing per object; nothing there matches this relevance-scoped mode.

**Docs.** Validation-Language guide → Appendix → *Full Validation and Partial Validation*.

## 9. `CodegenDemo` — Compile a model to code

**The scenario.** Everything so far ran the rules *dynamically* (the kernel generates and caches validation code on first use). But the same compiler can emit **standalone source** you ship and run yourself — and crucially, in more than one language. That's the kernel's headline differentiator: author the rules once, as data, and run them identically on the JVM backend *and* in the browser.

**What it shows.** The same Playlist model is compiled to both a Java and a JavaScript validator from one call each:

```java
IDocumentModelService svc = new DocumentModelServiceFactory().createDocumentModelService();
byte[] javaZip = svc.generateValidationCode(model, () -> ProgrammingLanguage.JAVA, new Properties(), notes::add);
byte[] jsZip   = svc.generateValidationCode(model, jsConfig /* JAVASCRIPT + WRAPPED_IN_FUNCTION_EXTENDED */, null, notes::add);
```

Each call returns a ZIP. The Java target is a package of sources plus a Gradle build; the JS target is a `.js` validator with a `.d.ts` typings file (the enum is `JAVASCRIPT`, not `TYPESCRIPT`). `gradle runCodegen` writes both ZIPs to `output/iteration/generated/` (it reuses `IterationDemo`'s model) and lists their entries; compiling/running the generated code is a separate step. The significance for an evaluator: a single business-analyst-authored rule-set becomes a backend validator *and* a matching frontend validator with identical semantics — no library here is multi-language (JSON Schema is portable data, but you still need a different validator engine per language and lose parity as soon as custom keywords appear).

**Docs.**

- Developer guide → *Further Resources* → *Generate validation code from document model*
- Developer guide → *Supported Languages*, and *Architecture* (Java vs TS/JS artifacts)

## 10. `TypedAccessorDemo` — Typed accessor overlays

**The scenario.** `DocumentV2` is dynamic — `doc.fieldValue("Subscription[1]/Tier")` returns an `Object` and you have to *know* the path and the type. For developers who want IDE autocomplete and compile-time safety, the kernel can generate typed views for a specific model. This *Subscription* demo contrasts the untyped access with the generated typed access over the very same document.

**What it shows.** A build-time generator emits view + pointer classes for the model; you wrap an ordinary document and navigate it with real types. It's the inverse of inferring a type *from* a schema (Zod's `z.infer`): the kernel emits a typed navigation API *over* a dynamic document, so a rename in the model turns into a **compile error** instead of a runtime `null`.

```java
DocumentV2 doc = DocumentV2.empty("subscription")
        .withFieldValue("Subscription[1]/Tier", "PRO")
        .withFieldValue("Subscription[1]/Addons[1]/Name", "Extra Seats")
        .withFieldValue("Subscription[1]/Addons[1]/MonthlyFee", new BigDecimal("9.99"));

doc.fieldValue("Subscription[1]/Tier");              // untyped: Object, string path, you must know the type

Subscription view = Subscription._viewOf(doc);       // generated overlay over the SAME document (no copy)
view.subscription().tier();                          // a generated `Tier` enum — autocompleted
for (var addon : view.subscription().addons()) {     // List<Addons>, strongly typed
    addon.name();        // String
    addon.monthlyFee();  // BigDecimal  — rename the field in the model and this stops COMPILING
}
```

**How it's wired (and why it's safe).** The generator is the `kernel-md-typed-accessor-gen` CLI, run by an **isolated** Gradle task (`genTypedAccessors`) over a committed model JSON (`src/main/resources/models/subscription.dm.json`, produced by `genSubscriptionModel`). The generated classes + the demo live in their own `typedAccessor` source set, so the other demos compile and run completely independently — a codegen failure can't break the rest of the build. (This is the one feature that needs build-time code generation; everything else here is pure runtime.)

**Docs.** Developer guide → *Typed Accessor Classes for Compile-Time Type Checks* (overlay mode).

---

## How it compares to mainstream validators

The recurring theme across these demos is that the kernel ships as *declarative operators* what most validators leave you to hand-write. For a feature-by-feature comparison against five mainstream validators — **Zod** (TypeScript), **Pydantic** (Python), **JSON Schema**, **Jakarta Bean Validation / Hibernate** (Java), and **FluentValidation** (.NET), each introduced from scratch — see **[`COMPARISON.md`](./COMPARISON.md)**.

In short, the kernel's clearest differentiators are: one data-authored rule-set compiled to **Java + JavaScript + Groovy** (`CodegenDemo`); **filtered aggregation** (`Sum/Count/Max … Having`, `IterationDemo`/`CrossArrayDemo`); **cross-row uniqueness with an automatic per-row error pointer** (`IterationDemo`); and **computed fields + localized messages authored as model data** (`ComputationDemo`, every bilingual message).

## More interesting language features (explainer — not separate demos)

A few kernel behaviours read better as notes than as their own demo (the full evaluation semantics — truth values, missing data, the "required" property, numeric/date rules — are in [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md)):

- **The DSL keywords are bilingual.** `conditionLanguage` selects the language the *rule keywords themselves* are written in — the same rule is `AllFieldsFilled(...)` in an `en_US` model and `AlleFelderAngegeben(...)` in a `de_DE` one. Localization elsewhere is message-only.
- **No negation operator.** There is no `Not`; instead a vocabulary of paired positive/negative predicates (`AllFieldsFilled`/`NotAllFieldsFilled`, `NoFieldFilled`, `AtLeastOneFieldFilled`, `NotExactlyOneFieldFilled`, …) — a deliberate readability/decidability trade.
- **Three-valued null semantics, and no empty string.** A missing *number* defaults to `0` (so `[Amount] < 100` fires when Amount is empty — guard with `FieldNotFilled(Amount) Or …`); missing string/date/enum make a comparison "not evaluated"; and `[F] == ""` is *never* true.
- **Decimal-scale discipline.** Comparing two numbers of different max-fraction-digits via `==`/`!=` is itself an error unless you round; `RoundAccounting` rounds half *away from zero* (not banker's rounding). Accounting-grade numeric strictness in a schema language.
- **Authoring gotchas worth knowing** — don't name a field after a DSL keyword (e.g. `Date`); a rule must live in a group; a rule's error field must be referenced in its own condition; prefer absolute paths across sibling groups; the `$`-operator precedes the whole path. Each surfaces as a specific `MVK_*` code-generation error.

## Powerful, but not wired up in this demo

These are real, documented kernel capabilities we deliberately did **not** demo here — either they need a field type the in-code builder doesn't expose, build-time code generation, or a kernel subtree this OSS build doesn't produce. Listed so you know they exist:

- **Imprecise / partially-known dates** — `DateFragment` (e.g. `YYYY`, `MM-DD`), date precision (`DAY_OPTIONAL`), `ValueAsDate(F, LastDay)`, and a model-level `BaseYear`. No mainstream library can represent "born in February, day unknown." *Why not here:* the docs state these field types are import-only (not creatable via the builder this demo uses).
- **Date-range overlap** — a `DateRange` field type plus `DateRangesOverlap(...)` to detect overlapping intervals across rows (e.g. double-booked periods) in one line. *Why not here:* needs the `DateRange` field type, which the builder may not expose.
- **Mapping with provenance** — `IMappingService.transferData(...)` transfers data between models and returns which source field produced each target value (data lineage). *Why not here:* the structural-mapping subtree isn't built in this OSS setup.
- **Model migration & modular models** — versioned DM JSON migrated forward by `IDocumentModelMigrator`; models composed from includes/type-definitions and `expand`-ed into a self-contained DM. Both work in Java but need a stale or multi-file model to be worth showing.

For the full surface, see the A12 docs & forum: https://geta12.com/#/docs
