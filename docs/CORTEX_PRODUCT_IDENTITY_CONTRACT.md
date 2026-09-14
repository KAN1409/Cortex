# Cortex Product Identity Contract

This line is for **Cortex**, not Cortex Prime and not LifeOS.

## Non-negotiable identity

- Android application id: `com.kareem.cortex`
- Visible app label: `Cortex`
- Main launcher: `NowActivity`
- Primary product destinations: `NOW`, `WORK`, `MEMORY`, `CAPTURE`
- Global interaction surface: Cortex Dock; it is an interaction surface, not a fifth permanent navigation destination.
- Product interaction: observe/capture -> understand -> connect -> judge -> surface -> propose/execute with explicit action truth.
- Existing user data must remain update-compatible.

`NowActivity` is the intentional v146 launcher because the new product opens on the decision surface: what deserves attention now. Capture remains a primary destination, while quick input/Ask Cortex are unified contextually through the Cortex Dock. This replaces the older document-only expectation that `InputActivity` must own the launcher.

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

The v146 product architecture is incremental: reliable storage, attention, capture, Work Vault and intelligence engines remain in place while the user-facing shell migrates toward Compose and the canonical destination/action contracts.

## Surface policy

Activities are implementation details, not product taxonomy.

Production navigation is defined by `CortexDestinationRegistry`.

User intent identity is defined by `CortexActionRegistry`.

Internal diagnostic activities may remain available for engineering or deliberately hidden Developer Mode, but normal production UX gets one understandable `System Health` entry.

## Release verification

Before any APK is handed to Karim, automated gates and artifact inspection must verify:

1. package id is `com.kareem.cortex`;
2. app label is `Cortex`;
3. launcher resolves to the currently approved product contract (`NowActivity` for v146 until an intentional shell migration updates code, tests and this document together);
4. version code is update-compatible with the installed Cortex baseline;
5. APK uses the permanent Cortex signing identity;
6. no uninstall or user-data reset is required;
7. canonical destination/action registries contain no duplicate IDs or conflicting ownership;
8. production navigation does not expose forbidden internal test/lab surfaces;
9. VERIFIED action states have acceptable evidence;
10. upgrade/install, critical navigation, accessibility, performance and embedded E2E gates meet the release acceptance criteria.
