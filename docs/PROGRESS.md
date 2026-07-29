# 织雀 (WeaverNest) Development Progress

## Current iteration

- Version: `0.5.0-alpha`
- Status: 离线产品功能与数据恢复边界完成；BLE 深度桥、真实实验模板、Room 项目索引、编辑器诊断和加密数据备份已完成，Android 设备矩阵待执行
- Updated: 2026-07-29

## Milestones

| Milestone | Status | Outcome |
| --- | --- | --- |
| Product boundary | Completed | Android 10+ phone/tablet creator, local template APK packaging, private APK sharing, permission-gated native bridge, project-level signing, data continuity, and user-provided LLM keys. |
| Technical spike | In progress | Core/runtime/app tests、Room schema、template resource compilation and encrypted data-backup contracts pass; device permission and generated-APK installation checks remain. |
| MVP studio | Completed for alpha | 粘贴/文件导入、CodeMirror 工作区与诊断、运行预览、持久化与加密备份、能力设置、AI/Apilot 设置、更新下载、项目复制/回收/导出和返回栈已实现。 |
| Android export | In progress | Fixed template assembler, per-project signing, v2/v3 verification and backup flows are implemented; installation, overwrite and data retention remain device gates. |
| AI assistance | Completed for alpha | User-provided OpenAI-compatible configuration, bilingual prompt pack, diff/preview confirmation and redacted runtime diagnostics are implemented. |

## Active work

- [x] Create project governance and non-`C:` environment policy.
- [x] Compare viable open-source foundations.
- [x] Confirm target user, host platform, and local packaging direction.
- [x] Define the first shippable MVP and user flow.
- [ ] Run the local APK template-packaging technical spike on Android 10+ devices.
- [x] Add the supplied launcher icon and stable local creator signing key.
- [x] Add GitHub Releases update check, release notes, and APK download setting.
- [x] Add direct paste-to-editor-to-run workflow with runtime status and logs.
- [x] Add Apilot V2 API Profile pick/export integration with minimal default scopes.
- [x] Stabilize system back behavior, Apilot availability guidance, document-start `weaver` bootstrap, capability-comment import, and bilingual prompt copying.
- [x] Implement Runtime 2.0 P0 native capability calls against the permission diagnostic fixture; device matrix remains open.
- [x] Implement BLE scan/connect/discovery/read/write/notification lifecycle and experimental capability templates.
- [x] Move project metadata index into Room while retaining workspace files and legacy rollback backups.
- [x] Add encrypted public runtime-data backup/restore for preview and generated applications; private runtime config remains device-local.

## Current verification boundary

- Automated evidence: core/runtime/app unit tests, all Android module Kotlin compilation, Room schema generation, workspace migration/rollback, ZIP import/export, runtime bridge contracts, BLE API contracts, encrypted data backups, event dispatch, prompt parity, and signing-policy tests pass.
- Not claimed yet: hardware permission results, Apilot installed-device round trip, APK installation/overwrite data preservation, TalkBack, screenshot baselines, and performance measurements.
