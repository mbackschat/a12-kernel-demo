# Reading this release's validation results

This reference describes the demo's checks. Use the kernel documentation for the complete language contract for the selected release.

## Rules and messages

A rule condition describes a problem. In the purchase-order model, a missing customer, a non-positive supplied quantity, and reversed dates are separate checks with separate error codes. The large-order check produces a warning instead of an error.

Read the severity, code, and field location together. A document with a warning can still have no blocking errors. The sample documents and the [README](../README.md) show the expected results.

## Missing and invalid inputs

Presence checks and value comparisons serve different purposes. The quantity rule checks presence before comparing the value with zero. That rule does not by itself make quantity mandatory.

Field-type constraints are checked separately from authored business rules. A formally invalid input can prevent a dependent check from reaching a result. No message from one rule is therefore insufficient evidence that the complete document is valid.

## Repeated data and derived values

The [showcase](SHOWCASE.md) explains row-level errors, aggregate checks, and calculations using the models included in this release. Follow the source for each example rather than assuming that a later release supports exactly the same APIs or expressions.

Reference: Validation-Language guide → Rules; Models → Treatment of non-specified values; Appendix → Validation and formal validation. Developer guide → Entry points to kernel-md → Validating a document.
