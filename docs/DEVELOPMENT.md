# 织雀 (WeaverNest) Development Book

## Product intent

织雀 is an Android-first creator for people who do not program. A user can describe a small tool to an AI, paste HTML/CSS/JavaScript or an entire ZIP project from another AI, preview it locally, save it, and package it as an installable APK directly on a phone or tablet.

Generated applications expose a deliberate, permission-gated bridge from web code to supported Android capabilities. APKs are intended for private installation and sharing, not app-store publication.

## Alpha implementation status

The `0.2.1-alpha` creator builds with the local JDK 17, Gradle 8.10.2, and Android SDK 35 toolchain under `.local`. It includes the project workspace, persistent WebView preview, core capability registry, import analysis, prompt pack, encrypted AI settings, supplied launcher icon, GitHub Releases update settings, and signed APK output. The preview bridge currently exposes persistent data and explicit unavailable states for native operations whose device modules have not passed the Android 10+ matrix yet.

The next validation gate is the real template APK assembler: it must generate a second installable package with a different project identity, selected Manifest permissions, and a project signing key without requiring a full Android IDE toolchain on the target device.

## Current decisions

- The creator is an Android 10 (API 29) or newer application. Editing, previewing, and APK packaging happen on the phone or tablet without a computer or a self-hosted build server.
- The creator defaults to Simplified Chinese and offers English as an application-language option. The language of a generated application is controlled by its project content and prompt, independently of the creator UI.
- APKs use a lightweight local template packager, not a full Gradle/JDK Android IDE. A fixed, precompiled native runtime is assembled with each project's web assets, metadata, selected permissions, icon, and signature.
- Android permissions are selected per project during packaging and declared in the generated application's manifest. Dangerous and special permissions still require Android's own runtime prompt or settings flow.
- The capability directory targets all Android capabilities available to ordinary apps. Signature-only, privileged, or device-policy capabilities are explicitly unavailable; restricted features must report unsupported devices or system denials clearly.
- A web page must not receive unrestricted native access. Native functions are exposed through a versioned `weaver` bridge with per-capability, per-project, and runtime permission checks.
- Preview is a first-class persistent runtime, not a disposable browser view. Each project has an isolated preview data store that survives creator restarts and supports reset, encrypted backup, and restore. Preview invokes the same `weaver` bridge as exported APKs; permission prompts belong to the creator during preview and to the generated APK after export. Preview data is never included in an APK by default; the creator can explicitly include it as initial application data.
- Each project owns a unique package identifier and signing key. A generated package identifier may be edited before the first APK export, then locks to preserve update compatibility; changing it creates a different Android application. The same project preserves its key and increments `versionCode` on each export, enabling data-preserving Android updates. The creator may edit the displayed semantic version and icon for each export. Before its first APK export, the creator must set a backup password. 织雀 uses it to encrypt the project's signing-key backup so the project can be recovered after a device change or a reinstall.
- Generated applications retain their private data across compatible updates and always include a data-management module. The module provides encrypted export, restore, and migration even when 织雀 is not installed. When it is installed, 织雀 may inspect and manage the generated application's data only through a bridge that verifies 织雀's fixed release signing certificate; no general data provider is exposed to other applications.
- AI assistance uses a user-configured large-language-model API key. The key is stored in the creator's secure storage and is never included in an exported APK. Every AI change is shown as a change summary and preview before the creator applies it.
- Public runtime API endpoints may ship with a project. Private runtime API credentials are requested from each installed application's user and stored locally; they are not embedded in shared APKs.
- Failed previews and builds support manual or optional automatic AI diagnosis using only a redacted error report and relevant code. An AI repair still requires preview and confirmation before it changes a project.
- The creator home offers three project-entry actions: AI creation, direct code/ZIP import, and templates. A project workspace has dedicated AI, Files, Preview, Capabilities, Build, and Data areas. The Files area is a full-screen editor with a file tree and resource import, always accessible but not imposed on a zero-code creator.
- Templates are presented in a searchable, categorized template center rather than as a long home-screen list. The catalog covers image/media, files/data, location/sensors, Bluetooth/nearby devices, Wi-Fi/networking, system capabilities, and AI/automation. Templates for restricted features such as Wi-Fi connection or local hotspot state the Android version, device support, and required user/system confirmation before use.
- 织雀 maintains versioned code-generation prompt packs. A matching prompt is automatically included in its own AI requests and is also available to copy for external models such as DeepSeek. The prompt pack specifies the supported project structure, mobile UI requirements, relative asset paths, the `weaver` capability API, runtime configuration handling, and prohibited native-access patterns. Imported or generated code is additionally validated, with AI conversion and a preview proposed before it is applied.

## Versioning

`VERSION` is the canonical semantic version. Every iteration updates it alongside `CHANGELOG.md`, this document, and `docs/PROGRESS.md`.

## Environment policy

All project-managed SDKs, caches, temporary files, toolchains, and generated artifacts must remain outside `C:`. The project root for these files is `E:\weavernest\.local`; generated release files belong in `E:\weavernest\artifacts`.

Any command or script that invokes Android, Gradle, Java, Node/npm, or another build tool must set its cache and home locations explicitly before execution.

## Open-source reuse strategy

织雀 should assemble proven, permissively licensed components instead of recreating commodity features. Every dependency must be pinned, tracked in a third-party notice, and reviewed for Android compatibility before use.

Candidate components for validation:

- APK template editing and binary resources: `REAndroid/ARSCLib` and `REAndroid/APKEditor` (Apache-2.0).
- Web runtime and native plugin model: `ionic-team/capacitor` (MIT), wrapped behind the project-owned `weaver` API.
- Code editor: CodeMirror (MIT) inside the project Files WebView.
- ZIP import and export: `srikanth-lingala/zip4j` (Apache-2.0).
- Bluetooth LE: `NordicSemiconductor/Android-BLE-Library` (BSD-3-Clause), behind a project-owned bridge module.
- QR and barcode scanning: `zxing/zxing` (Apache-2.0).
- Network requests: OkHttp (Apache-2.0).
- Backup and signing-key encryption: Tink (Apache-2.0) plus Android Keystore.
- General Android capabilities: AndroidX libraries and Android framework APIs (Apache-2.0).
- Optional map rendering: MapLibre Native (BSD-2-Clause).

Do not base distributable code on GPL/AGPL projects such as AndroidIDE. Avoid the LGPL Sora editor by default; CodeMirror provides a simpler permissive alternative. Acode and MIT App Inventor are useful product references, not default code bases.
