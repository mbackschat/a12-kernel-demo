# Comparing validation approaches with this demo

Use this repository's runnable scenarios as acceptance tests when comparing A12 with another validation library. A useful comparison measures the model, the error results, and the code needed to connect them to an application.

The [showcase](SHOWCASE.md) explains the scenarios and points to their source. Keep the input documents and expected business outcomes fixed while trying another implementation.

## Scenarios to compare

- **Purchase order:** check missing customer data, a non-positive quantity, date ordering, and a large-order warning. Check whether warnings and errors remain distinguishable in the result.
- **Playlist:** identify repeated titles and calculate totals across tracks, including a total restricted to flagged tracks. Check whether a reported problem identifies the affected row.
- **Shipment routing:** relate demand and capacity by warehouse identity and check delivery dates against a separate holiday list. Check how the result identifies a missing matching row.
- **Timesheet:** derive amounts from hours, rates, and mileage, then apply the calculated values to a document. Distinguish validating an existing total from calculating a new one.
- **Loan application:** validate one form section at a time and then validate the complete application. Check when a rule needing values from multiple sections becomes evaluable.
- **Java and TypeScript parity:** feed the same persisted models and documents into both runtimes. Compare stable error codes, field locations, severities, and computed values.

## Questions for an implementation

1. Where are rules authored: in model data, application code, annotations, or configuration?
2. Which requirements are expressed directly, and which need application callbacks?
3. How are field errors, row errors, and cross-section errors identified?
4. What happens when a value is missing or has the wrong type?
5. Who calculates derived values, and when are those values applied?
6. How much rule logic must be repeated across execution languages?

These questions are a comparison method, not a claim that another library cannot implement the scenarios. Count the supporting code and integration work as part of the result.

## Primary documentation for other approaches

- [Zod API](https://zod.dev/api): a starting point for a TypeScript implementation.
- [Pydantic validators](https://docs.pydantic.dev/latest/concepts/validators/): a starting point for a Python implementation.
- [JSON Schema](https://json-schema.org/learn/getting-started-step-by-step): a starting point for a schema-based implementation.
- [Hibernate Validator](https://docs.hibernate.org/validator/8.0/reference/en-US/html_single/): a starting point for a Java implementation.
- [FluentValidation](https://docs.fluentvalidation.net/en/latest/start.html): a starting point for a .NET implementation.

Consult each project's documentation for its current API and supported versions. This page contains no implementation examples from those projects.
