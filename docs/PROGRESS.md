# 织雀 (WeaverNest) Development Progress

## Current iteration

- Version: `0.2.0-alpha`
- Status: Android creator Alpha built; native capability and template-assembler verification pending
- Updated: 2026-07-28

## Milestones

| Milestone | Status | Outcome |
| --- | --- | --- |
| Product boundary | Completed | Android 10+ phone/tablet creator, local template APK packaging, private APK sharing, permission-gated native bridge, project-level signing, data continuity, and user-provided LLM keys. |
| Technical spike | In progress | Core policy tests and signed creator APK pass; native capability and template-assembler checks remain. |
| MVP studio | In progress | Editor, preview, saved projects, capability settings, AI settings, templates, and release update settings are implemented. |
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
