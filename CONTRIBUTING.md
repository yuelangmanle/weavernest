# 贡献织雀

## 开发约束

- 所有 SDK、JDK、Gradle、缓存和产物放在 `E:\weavernest\.local` 或 `artifacts`，不要写入 C 盘。
- 每个迭代同步更新 `VERSION`、`CHANGELOG.md`、`docs/DEVELOPMENT.md` 和 `docs/PROGRESS.md`。
- 新行为先写失败测试，再实现最小通过版本。
- 外部代码只能通过版本化 `weaver` 能力桥调用 Android 能力。
- 不提交 API Key、签名密钥、私有项目数据或本地配置。

## 提交前检查

```powershell
.\scripts\build.ps1 -Task ':core:test'
.\scripts\build.ps1 -Task ':app:assembleDebug'
```

请在 Issue 或 Pull Request 中说明 Android API 版本、设备型号和权限行为。
