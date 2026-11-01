# examples/ — models and documents, as study objects

These are **committed reference artifacts** — the exact models and documents the demos produce — kept here so you can read real A12 Kernel JSON without building or running anything. They are copies of what the demos write to the git-ignored `output/` directory, promoted here with `gradle refreshExamples`; running the demos never touches these committed copies.

If you only read one thing: a *rule states the **error** scenario* — its condition is written so that **`true` ⇒ the data is invalid**. Keep that inverted reading in mind throughout.

## What's here

Each demo contributes its model (`<id>.dm.json`) and sample documents. For a full explanation of every feature — with code excerpts and documentation references — see [`../SHOWCASE.md`](../SHOWCASE.md).

Each demo gets its own folder, **named after the demo** (not the model), holding that model's `<id>.dm.json` and its sample documents. The `.dm.json` keeps the model's own domain id, so e.g. `iteration/` holds `playlist.dm.json`:

| Folder | Files | Shows | Source |
|---|---|---|---|
| [`purchase-order/`](./purchase-order) | `purchase-order.dm.json` + `valid/invalid/large.json` | basic fields + rules (annotated walkthrough below) | base demo |
| [`iteration/`](./iteration) | `playlist.dm.json` + `balanced/repeated-title/too-long.json` | repeating group: uniqueness, Σ, filtered Σ | `IterationDemo` |
| [`computation/`](./computation) | `timesheet.dm.json` + `input/computed.json` | computed fields (inputs vs. the computed result) | `ComputationDemo` |
| [`dsl-tour/`](./dsl-tour) | `event-registration.dm.json` + `complete/incomplete.json` | DSL tour (slicing, regex, dates, enums, …) | `DslTourDemo` |
| [`extension/`](./extension) | `product-catalog.dm.json` + `valid/malformed.json` | custom condition + custom field type | `ExtensionDemo` |
| [`cross-array/`](./cross-array) | `shipment-routing.dm.json` + `clean/problems.json` | cross-array: parallel iteration, correlated `Having` filter, row-order | `CrossArrayDemo` |
| [`document-api/`](./document-api) | `recipe.dm.json` + `recipe-built/recipe-updated.json` | (no rules) immutable DocumentV2: structural sharing, diff, DM-aware coercion | `DocumentApiDemo` |
| [`partial-validation/`](./partial-validation) | `loan-application.dm.json` + `application.json` | incremental/wizard validation (`validatePart` per page) | `PartialValidationDemo` |
| [`language/`](./language) | `vehicle-inspection.dm.json` + `clean/surprises.json` | self-validating computation, enum categories `->`, `Valid()/Invalid()` | `LanguageDemo` |
| [`typed-accessor/`](./typed-accessor) | `subscription.dm.json` + `subscription.json` | compile-time-typed, autocompleting document access (generated overlays) | `TypedAccessorDemo` |

The `purchase-order` model is walked through in detail below as a first read. The others share the same JSON shape with richer features worth opening alongside `SHOWCASE.md`: a repeating group (`iteration/`'s `playlist` model — note `Group.repeatability` and the `RepetitionNotUnique` rule), `Computation` nodes (`computation/`'s `timesheet` — derived fields, and the `…-computed.json` document showing the filled-in results), an `EnumerationType` with bilingual value labels (`dsl-tour/`'s `event-registration`), and a `CustomFieldType` field (`extension/`'s `product-catalog`).

---

## 1. The DocumentModel — `purchase-order.dm.json`

A DocumentModel has two top-level parts, `header` and `content`.

### `header` — identity & languages

```json
"header": {
  "id": "purchase-order",
  "modelType": "document",
  "modelVersion": "29.4.0",
  "locales": [ { "code": "en_US" } ]
}
```

- **`id`** — how Documents reference this model, and how the runtime's model resolver looks it up.
- **`modelVersion`** — the DocumentModel *format* version the serializer wrote: kernel 31.1.1 writes `29.4.0`. Models in an older format are migrated automatically when loaded (the 2025.06 kernel wrote `28.4.0`; for these models the migrations in between change nothing but this number).
- **`locales`** — the languages the model declares. This matters at runtime: you must validate with a locale the model supports (the demo uses `Locale.US`). Error messages (below) are keyed by these locale codes.

### `content.modelConfig` — how values and conditions are interpreted

```json
"modelConfig": {
  "decimalSeparator": ".",
  "timeZone": "Europe/Berlin",
  "conditionLanguage": { "code": "en_US" },
  "fieldRefByShortNameAllowed": true
}
```

- **`conditionLanguage`** selects the **DSL language** the rule conditions are written in. The A12 validation DSL is bilingual (EN/DE); `en_US` here means conditions use the English keywords (`FieldNotFilled`, `And`, …). Switch this to German and the same rules would be written with German keywords.
- **`fieldRefByShortNameAllowed`** lets conditions refer to a field by its short name (`Quantity`) instead of its full path (`/Order/Quantity`) — which is why the rules below can write `[Quantity]`.

### `content.modelRoot.rootGroups[]` — the field/rule tree

The model's body is a tree of typed nodes. Every node carries a `type` discriminator (`"Group"`, `"Field"`, or `"Rule"`), an `id` (its absolute path), a `name`, and a nested object **named after its type** holding the type-specific config:

```json
{ "type": "Group", "id": "/Order", "name": "Order", "Group": { "repeatability": 1, "elements": [ … ] } }
```

Here there is one group, `Order`, containing six fields followed by four rules (fields and rules are siblings in the same `elements` array).

#### Fields — typed slots for data

Each field's `Field.fieldType` carries a `type` plus an optional same-named config object:

| Field | Type | Notable config |
|---|---|---|
| `CustomerName` | `StringType` | — |
| `OrderDate` | `DateType` | `format: "yyyy-MM-dd"` |
| `DeliveryDate` | `DateType` | `format: "yyyy-MM-dd"` |
| `Quantity` | `NumberType` | — |
| `UnitPrice` | `NumberType` | `maxFractionalDigits: 2` (so `9.99` is valid, `9.999` is a formal error) |
| `Express` | `BooleanType` | — |

The field types alone already enforce *formal* constraints (a non-date string in `OrderDate`, or too many decimals in `UnitPrice`, fails before any rule runs). The **business** constraints live in the rules.

#### Rules — the validation logic

A rule is `{ errorEntityRelPath, errorCode, errorCondition, severity, errorMessage[] }`:

- **`errorCondition`** — the DSL text. **`true` means invalid.**
- **`errorEntityRelPath`** — which field the resulting message is attached to (e.g. `../Quantity`), so a UI can highlight the right control.
- **`errorCode`** — a stable machine-readable id for the violation.
- **`severity`** — `ERROR` or `WARNING`. This is **model metadata, not part of the condition** — the same condition shape can be an error in one rule and a warning in another. A `WARNING` does *not* make the document invalid.
- **`errorMessage[]`** — the human text, one entry per locale.

The four rules, and how to read each condition:

| Rule (`errorCode`) | Severity | `errorCondition` | Reads as "invalid when…" |
|---|---|---|---|
| `CUSTOMER_REQUIRED` | ERROR | `FieldNotFilled(CustomerName)` | customer name is empty |
| `QTY_POSITIVE` | ERROR | `FieldFilled(Quantity) And [Quantity] <= 0` | quantity is present **and** ≤ 0 |
| `DELIVERY_BEFORE_ORDER` | ERROR | `FieldFilled(OrderDate) And FieldFilled(DeliveryDate) And [DeliveryDate] < [OrderDate]` | both dates present **and** delivery precedes order |
| `LARGE_ORDER` | WARNING | `FieldFilled(Quantity) And [Quantity] > 1000` | quantity is present **and** exceeds 1000 |

Note the recurring `FieldFilled(…) And …` guard: without it, a comparison against an *empty* field would also trip. Guarding means "only complain about a bad value, not a missing one" — missing-value complaints are the job of a separate `FieldNotFilled` rule (as with `CustomerName`).

---

## 2. The Documents — data validated against the model

A Document mirrors the model's group/field structure as plain nested JSON: `{ "Order": { "<FieldName>": <value>, … } }`. Dates are ISO `yyyy-MM-dd` strings; numbers are JSON numbers. A field that is simply **absent** counts as *not filled*.

### `purchase-order/valid.json` → **VALID, 0 messages**

```json
{ "Order": { "CustomerName": "ACME Corp", "OrderDate": "2026-01-10",
             "DeliveryDate": "2026-01-20", "Quantity": 5, "UnitPrice": 9.99, "Express": false } }
```

Every rule's condition is **false**: customer is filled, quantity `5 > 0`, delivery (`01-20`) is after order (`01-10`), quantity is well under 1000. Nothing fires.

### `purchase-order/invalid.json` → **INVALID, 3 ERRORs**

```json
{ "Order": { "OrderDate": "2026-01-20", "DeliveryDate": "2026-01-10", "Quantity": -3 } }
```

Three conditions evaluate to true at once:

- `CUSTOMER_REQUIRED` — `CustomerName` is absent ⇒ `FieldNotFilled` is true.
- `QTY_POSITIVE` — `Quantity` is `-3`, which is filled and `<= 0`.
- `DELIVERY_BEFORE_ORDER` — delivery `01-10` is **before** order `01-20`.

`LARGE_ORDER` stays silent (`-3` is not `> 1000`). `noErrorOccurred()` returns `false`.

### `purchase-order/large.json` → **VALID, 1 WARNING**

```json
{ "Order": { "CustomerName": "BigBuyer Ltd", "OrderDate": "2026-01-10",
             "DeliveryDate": "2026-02-10", "Quantity": 5000 } }
```

All three ERROR conditions are false, so the document is **valid**. Only `LARGE_ORDER` fires (`5000 > 1000`) — but it's a `WARNING`, so `noErrorOccurred()` is still `true`. This is the file to study to see the **error-vs-warning distinction**: a warning surfaces a message without invalidating the data.

---

## See also

- [`../KERNEL-DEV-GUIDE.md`](../KERNEL-DEV-GUIDE.md) — how these files are produced and consumed, and the parse → generate → compile → execute pipeline behind validation.
- [`../README.md`](../README.md) — build & run instructions and the expected console output.
- [`../SHOWCASE.md`](../SHOWCASE.md) — per-feature deep dive (code excerpts + documentation references).
- A12 docs & forum: https://geta12.com/#/docs
