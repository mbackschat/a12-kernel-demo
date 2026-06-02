# A12 Kernel vs. mainstream validators

How does authoring validation as *data* in the A12 Kernel compare to the validation libraries most teams actually reach for? This document weighs the kernel, feature by feature, against five well-known validators — one per major ecosystem — then introduces each of them from scratch (it assumes **no prior familiarity** with any of them).

The five contenders, each detailed in [The five contenders](#the-five-contenders) below:

- **Zod** (TypeScript)
- **Pydantic** (Python)
- **JSON Schema** (language-agnostic)
- **Jakarta Bean Validation / Hibernate Validator** (Java)
- **FluentValidation** (.NET / C#)

The goal is an *honest* comparison — where a mainstream library matches the kernel, the table says so.

## Capability comparison

A feature-by-feature comparison against the five contenders (introduced in detail below). **BUILT-IN** = a declarative operator the library ships; **custom** = doable, but you hand-write the predicate and the error path; **hard** = impractical or structurally unsupported. Each capability links back to the demo that exercises it.

| Capability (kernel operator / demo) | Zod | Pydantic | JSON Schema | Bean Validation | FluentValidation |
|---|---|---|---|---|---|
| Cross-row uniqueness with a **per-row error pointer** (`RepetitionNotUnique`; `IterationDemo`) | custom | custom | hard¹ | custom | custom |
| Aggregate over array items **with an inline filter** (`Sum(... Having ...)`; `IterationDemo`, `CrossArrayDemo`) | custom | custom | hard | custom | custom |
| Declarative **computed / derived fields**, then re-validated (`ComputationDemo`) | custom | custom² | hard | custom | custom |
| **Date-range overlap across array items** (`CrossArrayDemo`) | custom | custom | hard | custom | custom |
| **Parallel iteration** — join two repeating groups by a shared key (`CrossArrayDemo`) | custom | custom | hard | custom | custom |
| **Tolerance** comparison as a first-class op (`DiffersWithToleranceRange`; `DslTourDemo`) | custom | custom | hard | custom | custom |
| Localized, value-interpolated messages **authored as model data** (every bilingual message) | partial | partial | partial³ | **built-in**⁴ | **built-in**⁴ |
| One **data-authored** rule-set running identically in **Java + JS + Groovy** (`CodegenDemo`) | hard | hard | partial⁵ | hard | hard |
| "Exactly one of N" / all-or-none field groups (`LanguageDemo`) | custom | custom | partial⁶ | custom | custom |
| **Relevance-scoped partial validation** — never reports an error you can't currently fix (`PartialValidationDemo`) | custom | custom | hard | partial⁷ | partial⁷ |

- **¹** JSON Schema's `uniqueItems` is whole-item only — no per-field key and no pointer to the offending row.
- **²** None of the five *compute* derived values as part of validation: Zod's `.transform` and Pydantic's `@computed_field` produce derived output but don't re-validate it against a formula; JSON Schema, Bean Validation, and FluentValidation validate already-computed values, leaving the computation to application code. (See each contender's *On computation* note.)
- **³** JSON Schema itself carries no messages; error text comes from the validator engine (e.g. Ajv, with the `ajv-errors` plugin for custom text) — not localized data inside the schema.
- **⁴** Hibernate Validator (`ValidationMessages_xx.properties` + `{param}` / EL) and FluentValidation (`.WithMessage` + resource-file localization) both have genuinely strong i18n — but the messages are authored by *developers* in code / resource bundles, whereas the kernel's are authored by a *business analyst, in the model*.
- **⁵** A JSON Schema is portable data, but you still need a separate validator engine per language, semantics diverge once custom keywords appear, and there is no computation or Groovy target.
- **⁶** JSON Schema expresses "exactly one of N" with `oneOf` and all-or-none with `dependentRequired` / `dependencies`. (Among code-first libraries, **Joi** — omitted here — uniquely ships `object.xor/oxor/and/nand` as real built-ins, matching the kernel on this row.)
- **⁷** Bean Validation's validation `groups` / `@GroupSequence` and FluentValidation's `RuleSet` / `When` scope rules to a context, but neither guarantees "never report an error the user can't currently fix" — that one-directional guarantee is the kernel's. Zod, Pydantic, and JSON Schema have no built-in notion of a relevant-field subset.

## Where the A12 Kernel is most clearly differentiated

The recurring pattern above — column after column of "custom" — is the point: the kernel ships as *declarative operators* what the others leave you to hand-write. Five differences stand out:

1. **One rule-set, authored as data, compiled to Java + JavaScript + Groovy.** Every code-first library here is single-language; JSON Schema is portable data but needs a separate engine per language and can neither compute nor target Groovy (`CodegenDemo`).
2. **Declarative filtered aggregation** — `Sum / Count / Max … Having` — no mainstream library has "aggregate over a *filtered subset* of array rows" as a primitive (`IterationDemo`, `CrossArrayDemo`).
3. **Cross-row uniqueness with an automatic per-row error pointer** — everyone can *detect* duplicates; the kernel hands you the offending row, ready to highlight, for free (`IterationDemo`).
4. **Computed fields and localized messages as model data**, editable by non-programmers rather than baked into code (`ComputationDemo`, and every bilingual message in the demos).
5. **Rules authored in a bilingual DSL.** `conditionLanguage` selects whether the rule *keywords themselves* are English or German — the same rule is `AllFieldsFilled(...)` or `AlleFelderAngegeben(...)`. No mainstream validator localizes the rule language itself, only its messages.

None of this makes the others "worse" — they're excellent at the common case and most are far lighter-weight to adopt. The kernel's bet is specifically about **complex business forms**: many cross-field and cross-row rules, computed values, and bilingual messages, all owned by analysts rather than developers, and reused across a Java backend and a JS frontend without re-implementation.

## The five contenders

One representative validator from each of five ecosystems, chosen to balance popularity with capability (see [What's deliberately left out](#whats-deliberately-left-out) for the ones we dropped and why). Each gets a short "what it is, who uses it, how it reads" introduction, then the one or two features that best reveal its origin and way of thinking.

### Zod — TypeScript

**Zod** is the de-facto schema-validation library of the modern TypeScript world. You build a *schema as a value*, then parse data through it; crucially, the static TypeScript type is **inferred from the schema** (`z.infer`), so a single definition gives you both runtime validation and a compile-time type. Custom and cross-field rules go through `.refine` / `.superRefine`.

```ts
import { z } from "zod";

const Order = z.object({
  customerName: z.string().min(1),
  quantity:     z.number().int().positive(),
  orderDate:    z.date(),
  deliveryDate: z.date(),
}).refine(o => o.deliveryDate >= o.orderDate, {
  message: "Delivery date must not be before the order date.",
  path: ["deliveryDate"],
});

Order.parse(input);   // throws ZodError, or use .safeParse for a result object
```

**What's distinctive**

- **The schema is the single source of truth for the type.** `z.infer` derives the static TypeScript type *from* the schema, so one definition yields both the runtime validator and the compile-time type — the property that made schema-first validation popular in TypeScript (and the reason Zod is inherently single-language).
  ```ts
  type Order = z.infer<typeof Order>;   // { customerName: string; quantity: number; … }
  ```
- **`.refine` / `.superRefine` are the escape hatch** for logic the declarative operators don't cover — a custom predicate with an explicit error `path`. Most kernel capabilities marked "custom" in the table above would be hand-written here.
  ```ts
  z.array(Track).superRefine((rows, ctx) => {
    const seen = new Set<string>();
    rows.forEach((r, i) => {
      if (seen.has(r.title)) ctx.addIssue({ code: "custom", path: [i, "title"], message: "duplicate title" });
      seen.add(r.title);
    });
  });
  ```
- **Parse, don't validate.** `.parse` returns a new, *typed* value and `.transform` can reshape it on the way through — Zod is a parser that emits typed output, not only a pass/fail check.
  ```ts
  z.string().transform(s => s.trim()).pipe(z.string().min(1));   // clean, then validate
  ```

**Also notable:** coercion (`z.coerce`); discriminated unions (`z.discriminatedUnion`); branded/nominal types (`.brand`); schema derivation (`.pick` / `.omit` / `.partial` / `.extend` / `.merge`); defaults and `.catch` fallbacks; recursive schemas (`z.lazy`); async parsing (`.parseAsync`); customizable error maps for i18n.

**On computation:** `.transform` / `.pipe` produce a derived, typed *output* during parsing — the closest equivalent to a computed value — but a derived field is not re-validated against a formula.

### Pydantic — Python

**Pydantic** is the dominant data-validation library in Python, built on Python's type hints. You declare a *model as a class* whose annotated fields define the schema; constructing the model validates and coerces the input in one step. Field- and model-level validators (`@field_validator`, `@model_validator`) add custom and cross-field checks, and `@computed_field` exposes derived values. It's the validation layer inside FastAPI, among many others.

```python
from pydantic import BaseModel, Field, model_validator

class Order(BaseModel):
    customer_name: str = Field(min_length=1)
    quantity:      int = Field(gt=0)
    order_date:    date
    delivery_date: date

    @model_validator(mode="after")
    def delivery_not_before_order(self):
        if self.delivery_date < self.order_date:
            raise ValueError("Delivery date must not be before the order date.")
        return self

Order(**input)   # raises ValidationError on bad input
```

**What's distinctive**

- **Type hints *are* the schema, with coercion.** The annotations you'd write anyway define the validation, and inputs are coerced to the declared types (`"3"` → `3`); validation is an extension of ordinary Python typing. (Pydantic v2's core is implemented in Rust for speed.)
- **Validators are the escape hatch.** `@field_validator` (single field) and `@model_validator` (cross-field) carry anything beyond the declarative constraints — where the table's "custom" cells live.
  ```python
  @field_validator("quantity")
  @classmethod
  def positive(cls, v):
      if v <= 0:
          raise ValueError("must be > 0")
      return v
  ```
- **The model is also the (de)serialization layer — and emits JSON Schema.** `model_dump()` / `model_dump_json()` round-trip the data, and `model_json_schema()` generates a JSON Schema *from* the model — the bridge that lets FastAPI auto-document its APIs.
  ```python
  Order.model_json_schema()   # → a JSON Schema dict
  order.model_dump()          # → a plain dict
  ```

**Also notable:** `@computed_field` (derived, serialized) values; strict mode (opt out of coercion); field constraints (`gt` / `lt` / `max_length` / `pattern` / …); validation & serialization aliases; discriminated unions (`Field(discriminator=…)`); generic models; `pydantic-settings` for env/config (`BaseSettings`); `TypeAdapter` and `@validate_call` (validate values or function arguments without a full model); custom serializers (`@field_serializer`).

**On computation:** `@computed_field` exposes a derived value (chiefly for serialization); it is computed, not re-validated against a formula (table footnote ²).

### JSON Schema — language-agnostic

**JSON Schema** is not a library but a **standard** (an IETF specification): you describe the shape and constraints of JSON data *as JSON itself*. Validator engines then enforce it — [Ajv](https://ajv.js.org/) (JavaScript) is the most widely used, but engines exist for nearly every language. Because the schema is plain, portable data, it is the mainstream artifact **closest in spirit to the kernel's "rules as data."** Its limit: it describes structure and value constraints, not computations or cross-row logic, and each language needs its own engine.

```json
{
  "type": "object",
  "required": ["customerName", "quantity"],
  "properties": {
    "customerName": { "type": "string", "minLength": 1 },
    "quantity":     { "type": "integer", "exclusiveMinimum": 0 }
  }
}
```

**What's distinctive**

- **Composition and reuse, as data.** `$ref` points at another schema (local or remote) and `allOf` / `anyOf` / `oneOf` combine them, so large schemas are built from shared, versionable fragments — with no code.
  ```json
  { "allOf": [ { "$ref": "#/$defs/money" }, { "maximum": 1000 } ] }
  ```
- **Conditional validation is declarative too.** `if` / `then` / `else` and `dependentRequired` express "if A, then B is required" — the same family behind the "exactly one of N" partial in the table.
  ```json
  { "if":   { "properties": { "kind": { "const": "express" } } },
    "then": { "required": ["deliveryDate"] } }
  ```
- **One schema, many engines — but custom keywords break parity.** The same document validates via Ajv (JS), networknt (Java), Python, and more; that portability is its defining strength and what makes it the closest mainstream analogue to "rules as data." Extending it with custom keywords or `format` vocabularies, however, re-binds it to a specific engine — the caveat behind its "partial" multi-language rating.

**Also notable:** the `format` vocabulary (`email`, `date-time`, `uri`, …); `enum` / `const`; `pattern` (regex); `additionalProperties` / `patternProperties` / `propertyNames`; `contains` / `minContains` / `maxContains`; `not`; `$defs` with `$id` / `$anchor` for reuse; `unevaluatedProperties` / `unevaluatedItems`; recursive schemas (`$dynamicRef` / `$dynamicAnchor`); annotations (`title`, `description`, `default`, `examples`, `deprecated`, `readOnly`).

**On computation:** none — JSON Schema validates structure and values only; `default` supplies a static default, and derived values are computed by the application, outside the schema.

### Jakarta Bean Validation / Hibernate Validator — Java

**Jakarta Bean Validation** (formerly `javax.validation`; the JSR 380 standard) is Java's declarative-validation standard, and **Hibernate Validator** is its reference implementation — the validator behind Spring Boot. You annotate the fields of a Java class with constraints (`@NotBlank`, `@Min`, `@Size`, …); the runtime validates an instance and returns a set of violations. Cross-field and complex rules use a class-level constraint backed by a custom `ConstraintValidator`. Messages live in per-locale `ValidationMessages.properties` files with `{param}` / EL interpolation — strong internationalization, authored by developers.

```java
public class Order {
    @NotBlank String customerName;
    @Min(1)         int quantity;
    // cross-field (delivery ≥ order) needs a class-level @interface + ConstraintValidator
}

Set<ConstraintViolation<Order>> violations = validator.validate(order);
```

**What's distinctive**

- **Composed, reusable constraints.** A custom annotation is *assembled from existing constraints*, giving a domain vocabulary you apply like a built-in (here `@PromoCode` = not-blank + a pattern).
  ```java
  @NotBlank @Pattern(regexp = "[A-Z]{2}[0-9]{4}")
  @Constraint(validatedBy = {})
  @Target(FIELD) @Retention(RUNTIME)
  public @interface PromoCode {}
  ```
- **Validation groups + `@GroupSequence`.** Constraints are tagged with groups, so one model validates differently per context (e.g. `Draft` vs `Submit`) and in a defined order — its answer to contextual/partial validation.
  ```java
  @NotBlank(groups = Submit.class) String shippingAddress;
  validator.validate(order, Submit.class);
  ```
- **Constraints on container elements and method parameters.** `List<@Email String>` validates each element, and constraints can guard method arguments and return values — the checks live in the type system rather than a separate rule file.

**Also notable:** a rich built-in constraint set (`@NotNull`, `@Size`, `@Email`, `@Pattern`, `@Past`/`@Future`, `@Positive`, `@Digits`, …); `@Valid` cascading into nested objects; cross-parameter (method) constraints; constraint `payload` for carrying metadata; custom messages and error nodes via `ConstraintValidatorContext`; pluggable value extractors for custom container types; `@ScriptAssert` (Hibernate) for script-based class checks; fail-fast mode and programmatic constraint declaration (Hibernate).

**On computation:** none — it validates an existing object; derived values live in the bean's own code (e.g. getters), not in constraints.

### FluentValidation — .NET / C#

**FluentValidation** is the most popular validation library in the .NET / C# ecosystem, a richer alternative to .NET's built-in `DataAnnotations` attributes. Rules live in a **dedicated validator class per model**, expressed with a *fluent (chained) API* and type-safe lambda property selectors — keeping validation decoupled from the domain object and easy to unit-test. Custom logic uses `.Must()` / `.Custom()`; messages use `.WithMessage()` with resource-file localization.

```csharp
public class OrderValidator : AbstractValidator<Order> {
    public OrderValidator() {
        RuleFor(o => o.CustomerName).NotEmpty();
        RuleFor(o => o.Quantity).GreaterThan(0);
        RuleFor(o => o.DeliveryDate).GreaterThanOrEqualTo(o => o.OrderDate)
            .WithMessage("Delivery date must not be before the order date.");
    }
}
```

**What's distinctive**

- **`.Must()` / `.Custom()` with type-safe cross-field lambdas** are the escape hatch beyond the built-in rules — the table's "custom" cells, in .NET.
  ```csharp
  RuleFor(o => o.Quantity).Must((order, qty) => qty <= order.StockOnHand);
  ```
- **Conditional rules and rule sets.** `When` / `Unless` gate rules, and named `RuleSet`s run a subset per context — its take on contextual validation.
  ```csharp
  When(o => o.Express, () => RuleFor(o => o.DeliveryDate).NotEmpty());
  RuleSet("Submit", () => RuleFor(o => o.ShippingAddress).NotEmpty());
  ```
- **Composable, testable validators.** `SetValidator` plugs a child validator into a parent (for nested objects/collections), and a first-class testing API makes validators unit-testable in isolation — the "validation as separate objects" philosophy.
  ```csharp
  RuleForEach(o => o.Lines).SetValidator(new LineValidator());
  ```

**Also notable:** a rich built-in validator set (`NotNull`, `Length`, `EmailAddress`, `InclusiveBetween`, `Matches`, `CreditCard`, …); cascade modes (stop on first failure); severity levels (`.WithSeverity` — Error / Warning / Info); async validators (`MustAsync` / `ValidateAsync`); message customization and localization (`.WithMessage` / `.WithName` / placeholders); `DependentRules`; `Transform` before validating; inheritance validation (`SetInheritanceValidator`); first-class ASP.NET Core integration.

**On computation:** none — it validates an existing object; derived values are computed in the model/application, not in the validator.

## What's deliberately left out

To keep the table medium-broad rather than exhaustive, several validators were considered and dropped:

- **Yup** (TypeScript) — a schema validator with a profile near-identical to Zod's, which has largely superseded it. Omitted to avoid a redundant column.
- **Joi** (JavaScript) — a mature, powerful *server-side* schema validator (the Hapi ecosystem). Omitted as a *second* JS-ecosystem entry, since Zod already represents the TypeScript/JavaScript world. Worth knowing it's the one library that matches the kernel on "exactly one of N" via `object.xor/oxor/and/nand` (footnote 6).
- **class-validator** (TypeScript) — decorator-based validation popular with NestJS. Omitted, again, as JS-ecosystem-redundant with Zod.
- **Ajv** — not a separate model but the most common *engine* for JSON Schema, represented by the JSON Schema column.

The five we kept — Zod, Pydantic, JSON Schema, Bean Validation, and FluentValidation — give one strong, widely-used representative each from the TypeScript, Python, language-agnostic, Java, and .NET worlds.
