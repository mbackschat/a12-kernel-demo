# Provenance review

Reviewed on 2026-11-16. Source revision: `3e2937cc0b726b3a35d87fb1bf76c1c33bffe2ad`. This report covers the demo's code, documentation, configuration, and committed model/document JSON at that revision. Links use the repository's current layout.

## Result

No upstream implementation bodies, guide quotations, or distinctive guide example scenarios were identified in the reviewed demo files. The repository contains application code using A12 APIs and model/document data authored by the demos. It does not track A12 runtime packages, generated validator source, generated accessor source, or source patches against A12.

MIT remains the license for the project's own contributions. A12 dependencies retain their separate EUPL 1.2 or commercial licensing terms. Using those dependencies does not grant permission to distribute their covered material under MIT. The [EUPL text](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32017D0863) and the European Commission's [interoperability guidance](https://interoperable-europe.ec.europa.eu/collection/eupl/licence-compatibility-permissivity-reciprocity-and-interoperability) explain the relevant distinction between independent components and covered derivative material.

## Source and data

The Java and TypeScript sources implement the demo's purchase-order, playlist, routing, timesheet, registration, product, inspection, loan, recipe, subscription, and runtime-parity scenarios. No copied kernel engine implementation was identified. Imports, interface signatures, and operator names are necessary API references.

[RuntimeConfig.java](../../src/main/java/com/example/a12demo/RuntimeConfig.java) supplies the shared runtime configuration and synchronized compiled-model storage. It implements the public runtime interfaces and is used by both the purchase-order validator and the showcase helpers. Its source has no contiguous ten-token match against the local guide Markdown.

The 32 model/document JSON files contain eleven model files, including the duplicated subscription model, and 21 sample documents. Their 37 rule, calculation, and precondition strings trace to literals in the demo sources. The subscription model copies are byte-identical, and the Java and TypeScript computed timesheet documents agree as data. The JSON format follows A12's serialization contract; the business fields, rules, and samples trace to the demo builders.

Generation is demonstrated by calling A12's generators. Emitted code and locally installed dependencies stay in ignored output, build, and vendor directories. This review does not license those external components or assess a future distribution that bundles them.

## Documentation

The [semantics reference](../KERNEL-SEMANTICS.md) uses source-linked demo scenarios and a separate rounding illustration. The [comparison guide](../COMPARISON.md) offers a checklist based on those scenarios and links to other projects' primary documentation. The [version guide](../KERNEL-CHANGELOG.md) records dependency versions and model formats from this repository's release branches. No contiguous ten-token matches were found between these documents and the local guide Markdown.

The remaining documents describe this repository's setup, APIs, runnable examples, terminology, and contributor rules. Their code excerpts refer to the demo implementation. References to A12 documentation identify technical contracts by chapter and section title.

## Coverage

The review includes the complete contents of the following groups, including comments, code examples, and documentation passages:

- **Purchase-order source:** `BuilderMain.java`, `PurchaseOrderModel.java`, and `ValidatorMain.java` under `src/main/java/com/example/a12demo/purchaseorder/`.
- **Shared source:** `Demos.java` and `RuntimeConfig.java` under `src/main/java/com/example/a12demo/`.
- **Showcase source:** `CodegenDemo.java`, `ComputationDemo.java`, `CrossArrayDemo.java`, `DocumentApiDemo.java`, `DslTourDemo.java`, `ExtensionDemo.java`, `IterationDemo.java`, `JsExport.java`, `LanguageDemo.java`, `PartialValidationDemo.java`, `SubscriptionModel.java`, and `SubscriptionModelWriter.java` in the same package.
- **Other executable source:** `src/typedAccessor/java/com/example/a12demo/TypedAccessorDemo.java` and `ts/src/parity.ts`.
- **Documentation:** [README](../../README.md), [contributor guidance](../../CLAUDE.md), [showcase](../SHOWCASE.md), [developer guide](../KERNEL-DEV-GUIDE.md), [glossary](../KERNEL-GLOSSARY.md), [semantics](../KERNEL-SEMANTICS.md), [comparison](../COMPARISON.md), [version guide](../KERNEL-CHANGELOG.md), and [example descriptions](../../examples/README.md). `AGENTS.md` is a symlink to the contributor guidance.
- **Model and sample data:** every JSON file under `examples/` and `src/main/resources/models/`, with the individual examples described in [examples/README.md](../../examples/README.md).
- **Configuration and license:** `build.gradle`, `settings.gradle`, `.gitignore`, `.github/workflows/build.yml`, `ts/package.json`, `ts/tsconfig.json`, `ts/pnpm-workspace.yaml`, and `LICENSE`.

## Verification and limits

The review combined source and documentation reading, comparison with available A12 source and guides, and tracing serialized data to the demo builders. The comparison corpus comprised 25 snapshots of 15 related repositories and local merged guides. Automated text matching was supplemented by manual inspection; a missing match does not prove independent authorship or establish ownership rights.

The Java build, purchase-order validation, all Java showcases, and typed-accessor demo completed successfully. TypeScript type checking passed, and all eight parity results matched Java. The shared runtime adapter also compiled against kernel 30.8.1, 31.1.1, and 31.3.1. Documentation links and the identified source-content checks passed for each published release snapshot.

This is a technical provenance review of this repository. It does not cover the separate bootstrap repository, establish contributors' authority to license their work, or provide legal clearance for separately distributed upstream components or generated code.
