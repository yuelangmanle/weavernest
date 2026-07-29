# 织雀 (WeaverNest) Development Book

## Product intent

织雀 is an Android-first creator for people who do not program. A user can describe a small tool to an AI, paste HTML/CSS/JavaScript or an entire ZIP project from another AI, preview it locally, save it, and package it as an installable APK directly on a phone or tablet.

Generated applications expose a deliberate, permission-gated bridge from web code to supported Android capabilities. APKs are intended for private installation and sharing, not app-store publication.

## Alpha implementation status

The `0.5.0-alpha` creator builds with the local JDK 17, Gradle 8.10.2, and Android SDK 35 toolchain under `.local`. It includes paste/import review, a bundled CodeMirror 6 editor with local parse diagnostics, persistent or ephemeral WebView preview, Runtime 2.0 capability handlers including BLE GATT read/write/notifications, bilingual Prompt Pack 2.0, encrypted AI settings, supplied launcher icon, GitHub Releases direct download states, Apilot V2 package visibility and confirmation guidance, Room metadata index, project copy/recycle/export, and signed creator APK output. The preview and generated template use the same runtime module. Native permissions and restricted system flows still require the documented Android 10+ matrix before a template can be called verified.

The next validation gate is the device matrix for the real template APK assembler: it must generate a second installable package with a different project identity, selected Manifest permissions, and a project signing key without requiring a full Android IDE toolchain on the target device. Offline assembly and v2/v3 verification are implemented; installation and update preservation remain unverified until the requested test session.

## Current decisions

- The creator is an Android 10 (API 29) or newer application. Editing, previewing, and APK packaging happen on the phone or tablet without a computer or a self-hosted build server.
- The creator defaults to Simplified Chinese and offers English as an application-language option. The language of a generated application is controlled by its project content and prompt, independently of the creator UI.
- APKs use a lightweight local template packager, not a full Gradle/JDK Android IDE. A fixed, precompiled native runtime is assembled with each project's web assets, metadata, selected permissions, icon, and signature.
- Android permissions are selected per project during packaging and declared in the generated application's manifest. Dangerous and special permissions still require Android's own runtime prompt or settings flow.
- The capability directory targets all Android capabilities available to ordinary apps. Signature-only, privileged, or device-policy capabilities are explicitly unavailable; restricted features must report unsupported devices or system denials clearly.
- A web page must not receive unrestricted native access. Native functions are exposed through a versioned `weaver` bridge with per-capability, per-project, and runtime permission checks.
- Preview is a first-class persistent runtime, not a disposable browser view. Each project has an isolated preview data store that survives creator restarts, supports reset, and is never included in an APK by default. Preview invokes the same `weaver` bridge as exported APKs; permission prompts belong to the creator during preview and to the generated APK after export. Users can export and restore a password-encrypted backup of public `weaver.data` and `weaver.storage` data; encrypted private config and browser caches never leave the device.
- Each project owns a unique package identifier and signing key. A generated package identifier may be edited before the first APK export, then locks to preserve update compatibility; changing it creates a different Android application. The same project preserves its key and increments `versionCode` on each export, enabling data-preserving Android updates. The creator may edit the displayed semantic version and icon for each export. Before its first APK export, the creator must set a backup password. 织雀 uses it to encrypt the project's signing-key backup so the project can be recovered after a device change or a reinstall.
- Generated applications retain their private data in their own Android sandbox and include a data-management screen for clear/reset plus password-encrypted public runtime-data export and restore. The interchange intentionally excludes private runtime config, browser caches and any creator-to-app general data provider. APK install/overwrite retention remains a device-release gate.
- AI assistance uses a user-configured large-language-model API key. The key is stored in the creator's secure storage and is never included in an exported APK. Every AI change is shown as a change summary and preview before the creator applies it.
- Public runtime API endpoints may ship with a project. Private runtime API credentials are requested from each installed application's user and stored locally; they are not embedded in shared APKs.
- Failed previews and builds support manual or optional automatic AI diagnosis using only a redacted error report and relevant code. An AI repair still requires preview and confirmation before it changes a project.
- The creator home offers AI creation, direct code paste/import, and templates. The paste screen reads the Android clipboard or files, identifies HTML/CSS/JavaScript shape and device-API conversion suggestions, and requires the user to confirm creation before it writes a project. A workspace has dedicated AI, Files, Preview, Capabilities, Build, and Data areas. Files use locally packaged CodeMirror resources with language highlighting, syntax diagnostics, find/replace, undo/redo, 500ms auto-save and a 2MB read-only guard.
- Preview has an explicit lifecycle: Run increments a session token and loads the current project in WebView, Stop clears the WebView, and JavaScript/WebView errors are surfaced in a bounded per-session log. Preview data remains isolated by project and persists across creator restarts.
- GitHub release checking evaluates three public outcomes: current-or-older release, newer release with an APK, and newer release missing an APK. Only the second outcome renders a direct Android DownloadManager action; checking never redirects the user to a release page.
- Apilot uses its V2 API Profile actions with explicit package `com.example.api_manager`. The Manifest declares package/Intent queries required by Android 11+ package visibility. Pick requests default to `connection` and `models.default`; `secret.api_key` is included only after an explicit user toggle. Pick payloads can arrive in the result extra or a temporary URI and are read immediately. Export uses a one-time FileProvider URI and always opens Apilot's own confirmation. If Apilot is absent, 织雀 presents an in-app, cancellable three-second confirmation before the user can open its GitHub installation guide.
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
