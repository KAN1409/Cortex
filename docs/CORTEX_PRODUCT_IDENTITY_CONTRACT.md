# Cortex Product Identity Contract

This line is for **Cortex**, not Cortex Prime and not LifeOS.

## Non-negotiable identity

- Android application id: `com.kareem.cortex`
- Visible app label: `Cortex`
- Main launcher: `CortexShellActivity`
- Primary product destinations: `NOW`, `WORK`, `MEMORY`, `CAPTURE`
- Global interaction surface: Cortex Dock; it is an interaction surface, not a fifth permanent navigation destination.
- Product interaction: observe/capture -> understand -> connect -> judge -> surface -> propose/execute with explicit action truth.
- Existing user data must remain update-compatible.

`CortexShellActivity` is the intentional v146 launcher. It owns the four primary destinations and Cortex Dock in one Compose product path. Legacy Activities may remain temporarily as detail/workflow implementations while their unique behavior is migrated; they are not parallel tabs.

## Forbidden release drift

A release must fail review if any of the following becomes true:

- visible application label becomes anything other than `Cortex`;
- a Deep Review / Test Lab / audit / engineering diagnostic becomes the main launcher;
- a diagnostic/lab surface becomes a normal primary navigation destination;
- the four primary product destinations are no longer exactly NOW / WORK / MEMORY / CAPTURE without an explicit product-contract change;
- a different application id is used as a replacement for `com.kareem.cortex`;
- the permanent Cortex signing lineage is replaced;
- an update requires uninstalling the existing Cortex app;
- normal navigation exposes engineering test/lab surfaces directly;
- an external Android handoff is represented as VERIFIED merely because an Intent launched successfully;
- a local mutation is represented as VERIFIED without acceptable read-back/evidence under the action contract.

## v146 baseline

`v146/hard-explicit-request-boundary` was created directly from the green v145 head `237756e35c057f90effaaf710c5e1bbc38aaf6d2`.

The v146 product architecture is incremental: reliable storage, attention, capture, Work Vault and intelligence engines remain in place while the user-facing shell migrates to Compose and canonical destination/action contracts.

## Surface policy

Activities are implementation details, not product taxonomy.

Production navigation is defined by `CortexDestinationRegistry` and primary navigation resolves into `CortexShellActivity` with a semantic destination ID.

User intent identity is defined by `CortexActionRegistry`.

Cortex Dock is the only permanent primary interaction surface for Ask + text + voice + photo + file + screen context. Existing capture/ask Activities can remain as bounded workflow/detail executors until their unique behavior is migrated behind the Dock.

Production Surface Acceptance derives its primary UI matrix from `CortexDestinationRegistry.primary()` instead of treating every manifest Activity as a product surface. Internal diagnostic runtime coverage lives in `CortexInternalDiagnosticCoverageTest` and does not grant production visibility.

Internal diagnostic activities may remain available for engineering or deliberately hidden Developer Mode, but normal production UX gets one understandable `System Health` entry.

## Release verification

Before any APK is handed to Karim, automated gates and artifact inspection must verify:

1. package id is `com.kareem.cortex`;
2. app label is `Cortex`;
3. launcher resolves to `CortexShellActivity` and each primary semantic destination renders inside the shell;
4. version code is update-compatible with the installed Cortex baseline;
5. APK uses the permanent Cortex signing identity;
6. no uninstall or user-data reset is required;
7. canonical destination/action registries contain no duplicate IDs or conflicting ownership;
8. production navigation does not expose forbidden internal test/lab surfaces;
9. production surface acceptance and internal diagnostic coverage remain separate gates;
10. VERIFIED action states have acceptable evidence;
11. upgrade/install, critical navigation, accessibility, performance and embedded E2E gates meet the release acceptance criteria.
