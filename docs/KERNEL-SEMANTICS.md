# A12 Kernel — understanding validation results

Use this reference to interpret the rules and results in this repository. It describes the kernel 31.3.1 behavior relevant to the demos, with examples taken from their source and sample documents. It is not a replacement for the complete language specification.

Executable examples point to this repository's source; the rounding illustration is a separate arithmetic example. References to the A12 guides identify the technical contract. See the [provenance audit](research/provenance-audit.md) for the review's scope and findings.

Start with the [glossary](KERNEL-GLOSSARY.md) for terminology, or the [showcase](SHOWCASE.md) to run the examples.

## The field types at a glance

Choose a field type before writing its business rules. Type configuration determines which values can enter evaluation and which operators a rule may use.

- Text fields support length and pattern constraints. A custom field type adds application-supplied validation to text-like values.
- Number fields carry decimal precision and optional bounds. The purchase-order model allows two fractional digits for its unit price, while its quantity uses integer precision.
- Boolean fields distinguish a stored false value from an absent value. Confirm fields have different absence behavior; do not substitute one type for the other merely because both may be presented as checkboxes.
- Enumeration fields separate stored codes from localized labels. Rule comparisons use the codes.
- Date-related types distinguish complete dates, times, partial dates, and ranges. Their available operations differ; a range is not interchangeable with a single date.
- A referenced type definition supplies the effective field configuration. Check the resolved model when diagnosing a type-dependent result.

Older models can be migrated when loaded. In particular, kernel 31 replaces the former Unspecified type with a string representation. This does not mean that every unrecognized field type is accepted as text.

Reference: Validation-Language guide → Models → Field types; Developer guide → Entry points to kernel-md → Migration of document models.

## 1. Truth values and logical evaluation

Read a rule as a test for a problem. When its condition is satisfied, the kernel reports the rule's message. For example, [PurchaseOrderModel.java](../src/main/java/com/example/a12demo/purchaseorder/PurchaseOrderModel.java) reports a missing customer separately from an invalid quantity.

Do not equate the absence of a message with a successful comparison. A check can be prevented from reaching a decision because an input is unavailable, formally invalid, or outside the scope of partial validation.

Use parentheses to express grouping when combining `And` and `Or`. The language does not supply the usual programming-language precedence between them. It also has no general-purpose `Not` operator: choose the appropriate negative predicate and check its treatment of absent values.

Presence and truth are different questions. In [IterationDemo.java](../src/main/java/com/example/a12demo/IterationDemo.java), a track whose Explicit field contains false is still a track with that field filled. A predicate that asks whether the field exists is not a test of the flag's value.

Reference: Validation-Language guide → Operators and language constructs → Logical operators; Models → Field types → Boolean.

## 2. Non-specified (empty) values

A comparison involving an absent number can use zero as the number's replacement. The purchase-order quantity check therefore includes a presence guard:

```text
FieldFilled(Quantity) And [Quantity] <= 0
```

This rule reports a supplied zero or negative quantity. It does not make quantity mandatory. That would require a separate presence requirement.

Do not extend the numeric replacement rule to every type or operation. Missing text, date, Boolean, enumeration, and custom values do not acquire a comparable value in the same way. Confirm fields have their own false-value treatment. Minimum and maximum operations also handle missing inputs differently from numeric comparisons.

For text, test absence through the presence predicates. Comparing with an empty string is not a substitute for checking whether the field is filled.

Reference: Validation-Language guide → Models → Treatment of non-specified values; Arithmetic language constructs → Maximum and Minimum.

## 3. Formal errors and the unknown state

The kernel checks field-level requirements as well as authored rules. A value can fail because it has the wrong type, violates a pattern, exceeds decimal precision, or fails another configured field constraint. Such a formal error can prevent rules using that field from being evaluated.

[ExtensionDemo.java](../src/main/java/com/example/a12demo/ExtensionDemo.java) makes this distinction visible. Its malformed product contains a fractional stock quantity in an integer field, a non-hexadecimal color, and an invalid barcode. The stock error comes from the number configuration; the color check is supplied through a custom field validator; the barcode check is an authored rule calling a custom condition.

When troubleshooting, first resolve formal errors in the inputs. A business rule that produced no message while an input was unusable has not necessarily established that the business requirement holds.

Reference: Validation-Language guide → Appendix → Validation and formal validation; Developer guide → Custom Field Type.

## 4. The required property

There are two distinct ways to express a missing-value problem: a model's required-field configuration and an authored validation rule. Their effects on subsequent evaluation are not interchangeable.

The editor's parent-group option makes requiredness depend on group state. The generated condition also depends on the field's position in the model. Inspect the resulting condition and the actual hierarchy together, especially for nested or repeating groups; the checkbox caption alone is insufficient to reconstruct that condition.

The loan example uses explicit rules instead. [PartialValidationDemo.java](../src/main/java/com/example/a12demo/PartialValidationDemo.java) declares an applicant-name rule with its own error code and localized message. This makes the source of that requirement visible without relying on the editor's required-field setting.

A required-field violation can be a formal error, whereas an ordinary rule message does not make a field formally unusable. Similarly, a mandatory marker displayed by a UI is not a complete logical analysis of every possible rule. An absent marker does not establish that a field is optional.

Reference: Validation-Language guide → Appendix → Validation and formal validation → Required by Checkbox; Appendix → Mandatory fields have an asterisk in their label.

## 5. Numbers and decimals

Decimal precision is part of a number field's contract. It affects both the values accepted by formal validation and the legality of calculations and comparisons. Equality checks may be rejected during model compilation when the operands have incompatible declared scales, even if particular input values would compare cleanly.

The timesheet model demonstrates precision control in a calculation. [ComputationDemo.java](../src/main/java/com/example/a12demo/ComputationDemo.java) rounds its percentage-based bonus to the precision declared for the result field. Decide the required rounding policy as a business rule, rather than relying on whatever formatting a UI applies later.

For a fresh arithmetic illustration, consider rounding a credit adjustment of `-7.235` to two decimal places. `RoundDown` gives `-7.24`, `RoundUp` gives `-7.23`, and `RoundAccounting` gives `-7.24`. Down and up follow the number line; accounting rounding selects the nearest result and resolves an exact midpoint away from zero. For the positive adjustment `7.235`, the accounting result is `7.24`.

Guard operations whose inputs can make them unevaluable. In particular, a division with a zero denominator does not provide a normal comparison result. A missing error message is not a safe way to infer that the denominator was valid.

Reference: Validation-Language guide → Operators and language constructs → Comparison operations → Comparing numbers; Arithmetic language constructs → Rounding; Arithmetic operations → Treatment of division by zero.

## 6. Dates and time

Keep document representation separate from language syntax. The purchase-order sample stores dates as ISO strings because that is the model's configured field format. The validation language has its own conventions for date constants and constructors; a document's serialized date format is not a general recipe for writing a literal in a rule.

The purchase-order rule compares two supplied date fields. The event-registration example instead compares an event date with `Today` and uses `DifferenceInMonths` for passport validity. These express different requirements: chronological ordering, a current-date boundary, and a whole-month interval.

Calendar calculations need explicit edge-case decisions. Adding months or years is not the same operation as adding a fixed number of days, and leap-day handling can distinguish month-based and year-based calculations. Check the chosen operation before using it for age, expiry, or anniversary logic.

[LanguageDemo.java](../src/main/java/com/example/a12demo/LanguageDemo.java) constructs an inspection date from day, month, and year fields. The malformed sample supplies a day that February cannot contain. `Invalid(Date(Day, Month, Year))` reports that impossible combination, separately from whether each numeric part fits its own field bounds.

For partial dates, date ranges, time zones, and daylight-saving boundaries, use the operation-specific contract. The demos do not establish that every date operation behaves identically across those cases.

Reference: Validation-Language guide → Constants and data types; Date and Time language constructs.

## 7. Strings and patterns

The registration example treats three text requirements separately: a ticket prefix, a promotional-code pattern, and a PIN length. This is useful when each failure needs a different message.

Pattern predicates check the field value against the language's supported regular-expression syntax. Keep expressions compatible with the selected execution targets and avoid ambiguous nested repetition that can make matching expensive. A character-count constraint also need not match the number of visual symbols a reader perceives in Unicode text.

Date-shaped constants need particular care in string expressions because the parser can recognize them as dates. Avoid constructing a date-looking string through a rule without checking how each operand is typed.

Reference: Validation-Language guide → Models → Field types → String; Operators and language constructs → String operations; Miscellaneous → PatternMatched and PatternViolated.

## 8. Enumerations

Use the vehicle inspection's fuel fields to distinguish a code from its label. The model stores a fuel code, presents a localized label, and compares the code with the value read from the registration data. A label that looks equivalent to a person is still a different string.

The same demo groups fuel codes through an Emissions category. Its rule requires a certificate for the category representing combustion, so the condition does not need a separate comparison for each fuel code.

Kernel 31 permits comparisons that some earlier releases rejected when enumerations with display texts were involved. When migrating, check the stored inputs as well as whether the model now compiles. Compilation does not normalize labels into codes.

Reference: Validation-Language guide → Models → Field types; Operators and language constructs → Comparison operations → Comparing strings and enumerations; Kernel changelog → 31.0.0.

## 9. Repetition, iteration, and cross-array evaluation

The location of a rule's error field determines where messages can attach and whether the rule is evaluated for individual repetitions. In the playlist, the uniqueness rule belongs to the tracks and identifies the title field. Duplicate titles therefore produce row-specific messages. The duration rules attach to the playlist name and describe aggregate problems.

An error field must be referenced by its condition, directly or through a permitted enclosing reference. The presence guard on the playlist name serves this structural requirement for an aggregate rule; it is not part of summing track durations.

### The filter operator `Having`, the `$` correlation, and aggregation

The playlist's filtered total is a concrete example from [IterationDemo.java](../src/main/java/com/example/a12demo/IterationDemo.java):

```text
FieldFilled(Name) And Sum(Tracks*/Seconds Having [Tracks/Explicit]==True) > 600
```

The repetition path supplies track durations, the filter selects flagged tracks, and the sum is compared with the rule's limit. An aggregate's treatment of missing inputs is part of that aggregate's contract; do not infer minimum or maximum behavior from `Sum`.

`SumOfTerms` applies a numeric expression to each selected row before adding the results. The timesheet uses it to combine labor and mileage without adding another stored helper field. See [ComputationDemo.java](../src/main/java/com/example/a12demo/ComputationDemo.java) for the expression and [SHOWCASE.md](SHOWCASE.md) for the recorded empty-input measurements.

In [CrossArrayDemo.java](../src/main/java/com/example/a12demo/CrossArrayDemo.java), a holiday filter must compare each holiday with the delivery stop currently being checked. The `$` reference preserves that outer context. `CurrentRepetition` supplies a row position for a separate ordering rule; it is not the same thing as a business key.

### Parallel iteration

The routing model gives demand and capacity rows a shared warehouse index. Parallel iteration aligns the two lists by that index instead of assuming that equal row positions refer to the same warehouse. A demand without a corresponding capacity row remains a meaningful case to validate.

A missing partner has no ordinary document position. The message API can supply a partially known pointer and the missing index value. The demo prints that value so a consumer can identify which row should be added. Do not confuse this with an existing row whose fields happen to be empty.

Reference: Validation-Language guide → Rules → Rules and repeatability; Operators and language constructs → Functional language constructs; Developer guide → Entry points to kernel-md → Validating a document.

## 10. Paths and references

Distinguish model paths, rule references, and document-instance paths. A model identifies the field definition; a document pointer also identifies particular repetitions. A wildcard may refer to many possible instances, including ones that are not currently filled.

The routing rules use absolute model paths when they cross group boundaries. Other demos allow short field names through model configuration. Neither choice removes the need to check which group establishes the rule's reference context.

Treat keyword escaping, wildcard placement, and `$` references as language syntax, not interchangeable string formatting. A malformed reference is an authoring problem that can prevent validation code generation before any sample document is checked.

Reference: Validation-Language guide → Models → Paths; Rules → Rule condition; Rules and repeatability → Filter operator.

## 11. Computations

Computations describe derived values, but their results are also subject to validation. The inspection demo intentionally supplies a total that disagrees with its two scores. The computation's self-check reports that mismatch; the presence of a stored total does not make it authoritative.

Separate calculating results from applying them to a document. The Java timesheet calls `compute`, then applies the returned result to its input. Because `DocumentV2` is immutable, callers must use the returned document to observe the applied values.

Conditional calculation branches need compatible result types and non-overlapping preconditions. Also consider whether an empty input should produce a result at all. A calculation that can treat a missing number as zero may need a presence precondition when the business meaning is “not enough information yet.”

Reference: Validation-Language guide → Computation Rules; Developer guide → Entry points to kernel-md → Computations on a document.

## 12. The validation model

Severity belongs to the message contract. An error, warning, and information message can arise from the same kind of condition, but only error-severity messages make `noErrorOccurred()` false. The large purchase order illustrates a warning while remaining valid according to that result flag.

Partial validation adds a relevance boundary. The loan demo checks applicant, employment, and loan groups separately, then combines groups for review. Its affordability rule needs both the loan amount and income; checking only the loan page can leave that rule unreported.

The API's relevance rules are more nuanced than “all referenced fields must always be relevant.” Some errors can still be reported when the kernel knows that an omitted value could not change the outcome, and global fields are included in relevance automatically. Do not use a clean partial result as a certificate that the whole document is valid. Run full validation at the appropriate completion boundary.

Reference: Validation-Language guide → Appendix → Full Validation and Partial Validation; Developer guide → Entry points to kernel-md → Validating a document; runtime API contracts for `validatePart` and `noErrorOccurred`.

## 13. Error-message interpolation

Model messages can include field labels and values. The playlist's duplicate-title message inserts the offending title, which makes otherwise similar messages useful for individual rows.

Keep the rule's references and message parameters consistent. A field used in message interpolation must satisfy the language's reference restrictions; a message is not an unrestricted query over the document. Formatting also depends on the model and locale, so displayed text need not be identical to the raw stored representation.

When comparing runtime results, retain the stable error code and pointer as well as the rendered message. [The TypeScript parity check](../ts/src/parity.ts) compares those properties explicitly instead of treating the text alone as the identity of an error.

Reference: Validation-Language guide → Rules → Error texts for Rules; Developer guide → Entry points to kernel-md → Validating a document.

## 14. CustomCondition — the escape hatch

The product catalog delegates its barcode checksum to application code registered on `DocumentProcessingConfig`. Its custom condition returns whether the barcode should be reported as invalid. The surrounding DSL rule still controls the message and its error field.

The color validator takes a different route: it implements a custom field-type check, so every field using that type can receive the check without duplicating the barcode-style rule. Both implementations are in [ExtensionDemo.java](../src/main/java/com/example/a12demo/ExtensionDemo.java).

Custom conditions have placement restrictions, including in calculations and filters. Use the supported extension interface for the intended purpose; registering a callback does not make every location in the language accept it.

Reference: Developer guide → Custom Condition; Custom Field Type; Validation-Language guide → Operators and language constructs → CustomCondition.
