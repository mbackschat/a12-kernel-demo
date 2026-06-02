# CLAUDE.md — a12-kernel-demo

Guidance for any agent/contributor working in this demo project.

## ⚠️ HARD RULE — never leak machine-specific data into committed files

Do **NOT** put anything specific to this computer or developer into source, docs, configs, output, commit messages, or any file that could be shared — no absolute home paths, usernames, hostnames, machine names, email addresses, tokens/credentials, or personal directory layouts. Use generic, portable forms instead:

- **Paths:** prefer relative paths, or a reader-defined variable, or `$HOME` — e.g. `ROOT="$HOME/oss/a12"`. **Never** `/Users/<name>/…` or any path containing a username.
- **JDK / toolchains:** use `"$(/usr/libexec/java_home -v 21)"` (macOS) or wording like "any JDK 21" — **never** a hardcoded `/opt/homebrew/Cellar/openjdk@…/<version>/…` path.
- **Anything identifying the developer or this machine** (name, email, host) stays out.

Before writing/committing a file, scan it for `/Users/`, usernames, and other local specifics and replace them with placeholders.

## What this is

A runnable A12 Kernel validation demo:
- `BuilderMain` — authors a Purchase-Order DocumentModel and sample documents, and **persists them as JSON** under `output/purchase-order/` (models are created upfront, to be re-used).
- `ValidatorMain` — **loads** those JSON files and runs validation, printing errors/warnings.

See [`README.md`](./README.md) for how to run it, and [`SHOWCASE.md`](./SHOWCASE.md) for the feature-showcase demos.

## RULE — every built model and document is persisted for inspection

Any demo that builds a DocumentModel **must serialize the model and all its sample documents to `output/<demo>/`** — one folder **per demo**, named after the demo (not the model), holding `<model-id>.dm.json` plus that demo's documents (use the helpers in [`Demos.java`](src/main/java/com/example/a12demo/Demos.java): `writeModel(built, folder, dmId)`, `writeDoc(serializer, folder, fileName, doc)`, and `check(..., folder, ...)` which writes-then-validates — each takes the **demo folder name** as `folder`, kept distinct from the model's `dmId` so the `.dm.json` keeps the model's domain id, e.g. `output/iteration/playlist.dm.json`). `output/` is git-ignored.

Those artifacts are then **copied into the committed `examples/` directory** (same per-demo folder structure) so readers can browse the real model/document JSON on GitHub without building anything. Run `gradle refreshExamples` (copies `output/**/*.json` → `examples/`; binary code-gen ZIPs, which live under `output/<demo>/generated/`, are not `.json` and so are skipped). Whenever the set of demos/documents changes, re-run it and **update [`examples/README.md`](examples/README.md)** to describe the new files.

## RULE — SHOWCASE.md is in-depth

[`SHOWCASE.md`](./SHOWCASE.md) is the deep reference for the feature demos. For each feature it must include: (1) a **code excerpt** from the relevant demo (the rule/condition or API call), (2) a **comprehensive explanation** of what the kernel is doing and why it matters, and (3) a pointer to the **kernel documentation** **by chapter/section title** (e.g. *Validation-Language guide → Operators and language constructs → Functional language constructs*). For *kernel-doc* references use chapter/section titles only — **no file paths and no URLs**. (Linking to this repo's own docs — README, KERNEL-DEV-GUIDE.md, SHOWCASE.md — by relative path is fine.)
