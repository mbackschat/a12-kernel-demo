# Kernel changes by A12 release

This page summarizes what changed in the A12 kernel between the releases this demo supports. It's written for people who author models and rules or integrate the kernel from Java or TypeScript.

The official *Kernel Changelog* on [geta12.com](https://geta12.com/#/releases/releases-overview/kernel) lists every ticket. This page leaves out internal refactorings, dependency upgrades, and options meant for specific projects. It groups the rest by topic, and it links to the demo that shows a feature where one exists. New to a term? See [`KERNEL-GLOSSARY.md`](./KERNEL-GLOSSARY.md).

| A12 release | Kernel | Model format the kernel writes | Branch of this demo |
|---|---|---|---|
| 2025.06-ext5 | 30.8.1 | 28.4.0 | `release/2025.06` |
| 2026.06-ext1 | 31.1.1 | 29.4.0 | `release/2026.06-ext1` |
| 2026.06-ext2 | 31.3.1 | 29.8.0 | `main` |

The kernel migrates a model in an older format automatically when it loads it. For the models in [`examples/`](./examples/README.md), each migration changed only the format number.

## 2025.06-ext5 → 2026.06-ext1 (kernel 30.8.1 → 31.1.1)

Kernel 31 is a major release that removes many deprecated APIs. The kernel ships migration tools for it: an OpenRewrite script for Java and a codemod for TypeScript. Use the versions from kernel 31.1.1 or later, which fix problems in both.

### Before you upgrade

**Dependencies**

- Depend on `kernel-md-facade` only. Several former modules were merged into it, and the remaining internal modules are now named `kernel-internal-*`.
- The command-line tools are now jars of `kernel-md-facade`, published with the classifiers `migrator-cli`, `typed-accessor-gen-cli`, and `MappingCodeGenerator-CLI`.
- The kernel uses Jackson 3 instead of Jackson 2.

**Documents**

- The old document API (Document V1: `IDocument` and its services) is gone. Use `DocumentV2`.
- Reading document JSON with a malformed node now throws `DocumentSerializationException`. Before, the kernel dropped the node and returned a partial document.
- `IDocumentV2Serializer#serializeV2` no longer closes the writer you pass in. Close it yourself.

**Runtime and extensions**

- Custom field types and custom conditions are now passed with each call, through `DocumentProcessingConfig` (`customFieldTypeFactory`, `customConditionFactory`). The static registries, such as `CustomFieldTypeService` and `DocumentRtCustomExtensionService`, are gone. See [`SHOWCASE.md` §6](./SHOWCASE.md#6-extensiondemo--extending-the-kernel).
- A custom field type now implements `ICustomFieldValidator`. `ICustomFieldType` is gone.
- `validateFull`, `validatePart`, and `compute` no longer accept a bare `Locale`. Pass a `DocumentProcessingConfig`. It also replaces `MappingConfig`.

**Models**

- Variants are gone from fields, rules, computations, and code generation. The kernel migrates older models automatically.
- The field type `Unspecified` is gone. An older model's `Unspecified` field loads as a String field; see [`KERNEL-SEMANTICS.md`](./KERNEL-SEMANTICS.md).
- In the model JSON, a group's include settings moved under a new `includeConfig` node, and `modelAlias` is no longer used. In Java, `IIncludeDetails` became `IIncludeConfig`.
- `IDocumentModelService#expand`, `#collapse`, and `#extractImportedTypeDefinitions` are gone, and so is `BatchDocumentModelExpander`. To turn a model built from includes and imports into one self-contained model, use *A12 Workspace Conversion*.
- `IDocumentModelMigrator.getOldestSupportedSourceVersion` is now `getFirstSupportedSourceVersion`.

**Generated code**

- Generated Java validation code now comes as a standalone Gradle project, with `src/main/java/`, `build.gradle`, and `settings.gradle`. See [`SHOWCASE.md` §9](./SHOWCASE.md#9-codegendemo--compile-a-model-to-code).
- Typed accessors: enum classes always start with a capital letter, so a field `myEnum` now yields the class `MyEnum`.
- Typed accessors: the `pojo` mode is gone. Only the `overlay` mode remains; see [`SHOWCASE.md` §10](./SHOWCASE.md#10-typedaccessordemo--typed-accessor-overlays).
- Typed accessors: all fields that use the same type definition share one generated class, under `<prefix>/typedefs/<typedef-id>/`.

**TypeScript**

- Import only from a package's top-level exports.
- `DocumentRtService#applyComputationResult` is gone. Use `DocumentComputationResult#appliedTo`.
- `DocumentComputationResult#computedFieldInstances` is gone. Use `computedFieldInstancesWithoutErrors`.

### New

**Validation language**

- `SumOfTerms(Path*, Term)` evaluates any numeric term once per row and adds up the results, so a total needs no helper field per row. It's meant to replace `SumOfProducts`. See [`SHOWCASE.md` §4](./SHOWCASE.md#4-computationdemo--computed--derived-fields).
- A rule can compare a string with an enumeration that has display texts. The kernel compares the stored code. Kernel 30 rejected some of these comparisons. See [`SHOWCASE.md` §5](./SHOWCASE.md#5-languagedemo--interesting-language-features).

**Models**

- *Document uniqueness criteria* (`content/documentUniquenessCriteria`) declare which fields identify a document. The consistency check validates them. This demo doesn't use them.
- `content/modelInfo` has a new optional text property, `packet`.

**Java API**

- `DocumentModelServiceFactory.createBackwardDocumentModelMigrator` converts a model to an older format, for consumers that still run an older kernel.
- `IDocumentDynamicServiceConfig#getModelCodeHashProvider` lets you control when cached generated code counts as outdated.
- The mapping API (`MappingServiceFactory`) is no longer experimental.
- The kernel is also tested on Java 25. Its jars still target Java 21.

**Typed accessors**

- A string field with a hint list gets a class of constants, one inner class per locale.
- The generator handles type definitions from imported and included models. Pass the imported models with `--typedef-models`. A model that contains only type definitions now gets its classes too.

**TypeScript**

- An error for a required field carries two localization keys: `documentModel.fieldRequiredErrorMessage.<modelId>.<fieldFullPath>` first, then the general `kernel.formalErrors.PFLICHT_FELD_FEHLER`. So you can word the message per field.

### Changed behavior worth knowing

- The model consistency check now reports problems instead of throwing, for example for duplicate element names or unresolved index fields. It also reports problems in type definitions.
- A number field's `minValue` and `maxValue` no longer have to be valid values of the field. Only `maxValue >= minValue` is still checked.
- The kernel parses locale strings more strictly.
- Dates before 1 April 1893 in the Berlin time zone no longer shift when a document is read and written again.
- An error message that shows the value of a row that has no match in parallel iteration shows an empty value instead of throwing.
- An error message that shows a custom field type's value with `$<field>.value$` no longer throws (kernel 31.1.1).
- TypeScript: `baseYear` in filter conditions and in `SumOfTerms` now evaluates correctly.
- The custom field type `IdNrVerifier` no longer reports the special error code `testIDNummer` for test IDs.

## 2026.06-ext1 → 2026.06-ext2 (kernel 31.1.1 → 31.3.1)

A feature release without removals. Most of its additions help you find the cause of an error.

### Before you upgrade

- If you build models in code with the kernel's builders, `RuleBuilder.field(Field)` and `ComputationBuilder.field(Field)` were renamed to `fieldWithDefaultValuesForTest`. They are test helpers that add a placeholder condition or computation. The official changelog doesn't list this change. The replacements are `RuleBuilder.errorFieldToRelPathReference(path, field)` and `ComputationBuilder.field(field, preconditionOperationPairs)`.
- `IComputation#getErrorCodesToSuppress` is deprecated. `getAdditionalParserInformation` replaces it and returns the computation's whole preamble text, such as `@SuppressWarning(...)`.
- `PartiallyKnownDocumentMultiPointer.of(String, int[])` is deprecated. Use `of(String, List<Integer>)`. The factories now throw `IllegalArgumentException` if the path and the list of repetitions differ in length.
- The date-type setting `isYoungerThan1900Check` is deprecated and no longer evaluated. In TypeScript, the error codes `DATUM_ADDITIONAL_CHECK` and `DATUM_BEREICH_DATUM_ADDITIONAL_CHECK` are deprecated for the same reason.
- TypeScript: `EntityInstancePath.hash` uses a new algorithm, so don't persist its values. `equals` and `hash` now also compare the key of a missing row (see below).
- TypeScript: interfaces marked `@OnlyForUsage` are for calling only. Don't implement them, because new members may appear in any release.

### New

**Which missing row caused an error**

When a rule iterates two repeating groups in parallel and a row has no partner, the kernel can't point at the missing row. Its pointer then carries the repetition index `-5`, meaning "unknown". New in kernel 31.2:

- `IMessage#getGroupsOfPossiblyCausingEntities()` (Java) and `Message.groupsOfPossiblyCausingEntities` (TypeScript) list the groups of fields that may have caused the error.
- For a missing row, the pointer also carries that row's index value. In Java, read it with `semanticIndexValueForUnknownIndexGroup()`.
- A new rule property, `filterForPossiblyCausingEntities`, narrows which entities that list reports.

For example, in `CrossArrayDemo`, a `Demand` row for warehouse `HAM` without a matching `Capacity` row produces a message whose possible causes include `Capacity/Units[-5]` with the index value `HAM` (measured on 2026-11-06 with kernel 31.3.1). The message's referenced fields point at `Capacity/Units[-5]` as well, but without the index value. See [`SHOWCASE.md` §3](./SHOWCASE.md#3-crossarraydemo--cross-array-logic-sql-like-queries-over-arrays).

**Validation language**

- `@SuppressWarning(...)` accepts a comma-separated list of codes, and the new code `MVK_ERROR_INVALID_ENUM_CALCULATION`. It turns off the check that an enumeration field is computed only from constants or enumerations whose values fit the field.
- When a model with several root groups is included with `includeLevel = MODEL_ROOT`, rules and computations can reach a sibling root group with a relative path, such as `../../RG2/x`.

**Models**

- A computation can set its own `errorCode`. The kernel uses it for the validation rule that checks the computed value. Without it, the rule's error code is the computation's name, as before. See [`SHOWCASE.md` §5](./SHOWCASE.md#5-languagedemo--interesting-language-features).
- Every model element and type definition can carry a `changeNote` that records why it was changed.

**Java API**

- `IDocumentModelSearchService` has typed lookups, so you no longer cast the result of `getByPath`: `getGroupByPath`, `getFieldByPath`, `getRuleByPath`, `getComputationByPath`, and `getGroupRepeatability`.
- `getAllElementsById()` and `getAllElementsByPath()` return every element of a model as a map.

**Custom field types**

- `IBAN` and `BIC` support Serbia.
- A new custom field type, `ESStErmittlungsnummer`.

### Changed behavior worth knowing

- Computations now apply filter conditions inside `SumOfTerms` the same way validation does. Before kernel 31.2, a computation could skip that step. A simple filter such as `SumOfTerms(Days* Having [Km] > 30, …)` gave the same, correct result on kernels 31.1.1 and 31.3.1 (measured on 2026-11-06), so the fix affects less common filters.
- TypeScript: rules and computations that parse many dates, for example through filter conditions over large repeating groups, run much faster.
- Kernel 31.3.1 itself only upgrades the Jackson library to close a security vulnerability.
