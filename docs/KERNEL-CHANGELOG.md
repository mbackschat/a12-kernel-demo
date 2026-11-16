# Kernel versions used by this demo

This page records the dependency versions and model formats present in this repository's release branches. It is a guide to reproducing the demo, not a summary of the upstream release notes.

| Demo branch | A12 release | Kernel dependency | Committed model format |
| --- | --- | --- | --- |
| `release/2025.06-ext5` | 2025.06-ext5 | 30.8.1 | 28.4.0 |
| `release/2026.06-ext1` | 2026.06-ext1 | 31.1.1 | 29.4.0 |
| `release/2026.06-ext2` | 2026.06-ext2 | 31.3.1 | 29.8.0 |

The dependency versions come from each branch's build configuration. The model format is the `header.modelVersion` property in its committed document models. Choose a matching bootstrap release when building a branch.

## Checking a change of kernel version

1. Build the selected A12 dependencies with the separate bootstrap project.
2. Run the purchase-order builder and validator.
3. Run the [feature showcases](SHOWCASE.md), including the typed-accessor and TypeScript examples when their prerequisites are available.
4. Compare the persisted model and document JSON with the committed examples.
5. Investigate changes in error codes, severities, document locations, and computed values before refreshing the examples.

The [semantics reference](KERNEL-SEMANTICS.md) describes the current demo's evaluation behavior. For changes outside these scenarios, consult the official Kernel Changelog and migration instructions for the selected A12 release.
