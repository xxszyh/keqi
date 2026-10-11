# 维护者发布流程

开发者自行构建与 CI 产物使用自己的调试签名。官方 Release 应持续使用维护者同一签名，密钥存放在仓库之外，不能上传 Git 或作为公开构建物。

## 准备版本

1. 更新 `mobile/AndroidManifest.xml` 中的 `versionName` / `versionCode`，关于页面、安装说明、CHANGELOG 和版本更新说明保持一致。
2. 运行 [测试](TESTING.md)，实机验证登录、同步、选课、考试、本地提醒、后台及覆盖升级。
3. 配置签名。已有维护者的旧工具缓存密钥会继续复用；其他密钥通过环境变量指定：

```powershell
$env:KEQI_KEYSTORE = 'C:\KeqiKeys\release.keystore'
$env:KEQI_KEY_ALIAS = 'keqi'
# 通过终端或 CI 秘密变量注入 KEQI_KEYSTORE_PASSWORD。
# 如密钥密码与 keystore 不同，还需 KEQI_KEY_PASSWORD。
.\.venv\Scripts\python.exe mobile\build.py
.\.venv\Scripts\python.exe mobile\package_release.py --previous-apk 'C:\KeqiReleases\previous.apk'
```

不要把密码写在提交的命令、脚本或日志中。打包会检查 APK 与当前源码/资源、依赖模块、签名及 SDK 元数据是否匹配；提供上一版本 APK 时还会确认签名相同。没有上一版时报告明确标为未核对升级签名。

## 发布

提交版本更新，创建对应标签；通过 GitHub Release 上传经检查的 APK 和安装 ZIP。附件建议使用 ASCII 文件名，例如 `keqi-android-v0.10.0.apk`、`keqi-install-v0.10.0.zip`。记录上传后的字节数和 SHA-256，再更新 README 下载入口。

安装 ZIP 包含版本 APK、安装说明、项目 LICENSE 和 THIRD_PARTY；APK 中保留所有使用的许可声明。打包产物、生成报告及签名不进入 Git。

仓库中的 [0.10.0 原始构建信息](releases/v0.10.0-build.json) 对应标签 `v0.10.0`，不代表后续 `main` 的自编译字节。开源整理没有替换已有 Release 附件，许可证等未发布变化见 [CHANGELOG](../CHANGELOG.md)。后续版本应归档对应构建信息。
