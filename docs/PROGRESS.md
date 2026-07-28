# 织雀 (WeaverNest) Development Progress

## Current iteration

- Version: `0.3.1-alpha`
- Status: 阻断性稳定化已完成：返回、Apilot 检测/引导、运行时环境注入、能力注释识别和模板诚实性已修复；商业 UI、P0 Runtime 和模板组装器待继续
- Updated: 2026-07-28

## Milestones

| Milestone | Status | Outcome |
| --- | --- | --- |
| Product boundary | Completed | Android 10+ phone/tablet creator, local template APK packaging, private APK sharing, permission-gated native bridge, project-level signing, data continuity, and user-provided LLM keys. |
| Technical spike | In progress | Core policy tests and signed creator APK pass; native capability and template-assembler checks remain. |
| MVP studio | In progress | 粘贴/文件导入、代码工作区、运行预览、保存项目、能力设置、AI/Apilot 设置、更新下载和阻断性返回/环境修复已实现；商业 UI、CodeMirror 和 P0 Runtime 待后续增强。 |
| Android export | In progress | Creator APK is signed with the local key and uses the supplied icon; generated-project APK assembly remains. |
| AI assistance | In progress | User-provided OpenAI-compatible API configuration and prompt pack are implemented; patch review flow remains to harden. |

## Active work

- [x] Create project governance and non-`C:` environment policy.
- [x] Compare viable open-source foundations.
- [x] Confirm target user, host platform, and local packaging direction.
- [ ] Define the first shippable MVP and user flow.
- [ ] Run the local APK template-packaging technical spike on Android 10+ devices.
- [x] Add the supplied launcher icon and stable local creator signing key.
- [x] Add GitHub Releases update check, release notes, and APK download setting.
- [x] Add direct paste-to-editor-to-run workflow with runtime status and logs.
- [x] Add Apilot V2 API Profile pick/export integration with minimal default scopes.
- [x] Stabilize system back behavior, Apilot availability guidance, document-start `weaver` bootstrap, capability-comment import, and bilingual prompt copying.
- [ ] Implement Runtime 2.0 P0 native capability calls against the permission diagnostic fixture.
