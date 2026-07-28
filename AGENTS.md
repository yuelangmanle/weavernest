# WeaverNest Project Rules

## Iteration discipline

Every completed iteration must update all of the following in the same change:

1. `VERSION` with the next semantic version.
2. `CHANGELOG.md` with user-visible additions, changes, fixes, and known limitations.
3. `docs/DEVELOPMENT.md` with the current architecture, technical decisions, and environment requirements.
4. `docs/PROGRESS.md` with milestone and task status.

Use semantic versioning. Before native Android packaging exists, `VERSION` is the source of truth. Once Android projects exist, their `versionName` and `versionCode` must be updated together with it.

## Local environment policy

Do not install SDKs, JDKs, Node.js distributions, Gradle caches, Android SDK components, package caches, build artifacts, emulators, or generated APKs on `C:`.

Keep project-managed tooling under `E:\weavernest\.local`:

- Android SDK: `.local\android-sdk`
- JDK: `.local\jdk`
- Gradle user home: `.local\gradle`
- Node/npm cache: `.local\npm-cache`
- Temporary build files: `.local\tmp`
- APK/AAB output: `artifacts\`

Before invoking a tool that normally writes to a user profile or `C:`, configure its cache, home, and output directories explicitly. Do not rely on a tool default that violates this policy.

## Working style

Use lightweight requirements clarification and proportionate verification. Do not require a heavyweight workflow unless it provides concrete value for the current change.
