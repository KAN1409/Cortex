# Cortex Product Identity Contract

This recovery line is for **Cortex**, not Cortex Prime and not LifeOS.

## Non-negotiable identity

- Android application id: `com.kareem.cortex`
- Visible app label: `Cortex`
- Main launcher: `InputActivity`
- Product interaction: capture/speak -> understand -> use relevant Cortex context -> propose or execute a grounded action.
- Existing user data must remain update-compatible.

## Forbidden release drift

A release must fail review if any of the following becomes true:

- visible application label becomes `Cortex Prime`
- a Deep Review / Test Lab / review workflow becomes the main launcher
- LifeOS-style passive dashboard replaces Cortex's active assistant interaction
- a different application id is used as a replacement for `com.kareem.cortex`
- an update requires uninstalling the existing Cortex app

## Recovery baseline

This branch starts from commit `c8c49ccc71c6b367dfecaff76351279f256d5c2d` and keeps the original Cortex package and launcher family.

## Transfer policy

Work from the discarded Prime recovery line may be copied only when it is product-neutral and useful to Cortex. In particular, ASR benchmark metrics, shadow comparison infrastructure, clean local ASR candidates, and on-device brain capability probes can be transferred. Prime/Deep Review UI, review-product identity, launcher choices, and product-flow assumptions must not be transferred.

## Release verification

Before any APK is handed to the user, CI and manual artifact inspection must verify:

1. package id is `com.kareem.cortex`;
2. app label is `Cortex`;
3. launcher resolves to `InputActivity`;
4. version code is greater than the installed baseline intended for update;
5. APK uses the permanent Cortex signing identity;
6. no uninstall is required;
7. startup UI is Cortex capture/assistant UI, not a Deep Review/Test Lab surface.
