# 开源整理检查记录

日期：2026-10-11。应用功能基线：`v0.10.0` / `4149cfb`。本次整理不替换既有 Release，也不重写 Git 历史。

## 仓库内容

- 核对全部 4 个原有提交中可达的 139 个文件对象，扫描私钥格式、GitHub 令牌、云密钥、用户提供的账号标识/本机目录、长会话 Cookie 字面值；这些规则未发现匹配项。
- 测试中的 `_uid=123`、`JSESSIONID=fixture`、`fixture-password-value` 等为明确的隔离样例。
- 本机配置、工具缓存、数据库、网页诊断、签名、APK 和 ZIP 不进入 Git。生成的 `mobile/build-info.json` 改为忽略；0.10.0 原始报告另行归档到 `docs/releases/`。
- 公开预览图是示例数据，未将用户的录屏、聊天附件和实际登录截图加入仓库。
- 仓库历史中的提交作者信息和原有版本说明会随公开保留；本次未改写历史。

这是指定格式和已知标识的扫描，不能保证发现所有敏感信息。未来贡献应继续在提交前脱敏。

## 来源与许可

本项目采用根目录 MIT，APK 资源附带相同许可副本。jsoup、MOOC_reminder、Xuexitong-mcp 的完整版权/许可声明保留；JVM JSON 测试库不进入 APK。固定版本和来源见 [THIRD_PARTY](../THIRD_PARTY.md)。

构建依赖 JAR 的 SHA-256 已验证。Windows JDK/SDK 不放入仓库；支持便携下载或开发者自己的环境。CI 模板的 GitHub Actions 使用固定提交及只读仓库权限，不使用真实平台账号或维护者签名。

## 验证入口

干净副本仅从 Git 索引导出源码，不复制本机配置、账号缓存或构建目录；复用已安装 JDK/SDK 和 Python 依赖，通过 `configure_tools.py` 重新生成配置。

- `scripts/check_repo.py`：项目资料、52 个本地文档链接、许可副本、JAR 哈希及 Git 排除检查通过。
- `mobile/run_checks.py`：全部 12 个 JVM/Edge 脚本通过。包含 56 项领域、103 项无缓存连接、27 项续读策略、25 项考试检查，以及所有课程和界面回归。
- 干净副本编译：Java/资源/DEX 编译、v2/v3 签名、ZIP 对齐通过；APK 附带项目与第三方许可。
- 打包：源码/资源指纹、模块、版本、安装 ZIP 四个文件及与既有 0.10.0 APK 的同签名检查通过。本次验证构建保存在本机忽略目录，没有替换官方附件。

GitHub 拒绝包含 `.github/workflows/ci.yml` 的推送：当前 OAuth 凭据缺少 `workflow` 权限；已连接的 GitHub 工具也因连接错误不能读取仓库。模板因此以 `docs/ci.yml` 交付，尚未启用或在 GitHub 执行。本地 YAML 解析、动作固定提交、只读权限及干净副本验证通过；启用方式见 [测试说明](TESTING.md)。上述检查不等于真实账号兼容性或 Android 系统通知/后台行为已经实机验证。

2026-10-11 经维护者确认，`xxszyh/keqi` 已改为公开；无需登录即可访问仓库、README 和 0.10.0 发布页。安全漏洞应通过 [SECURITY](../SECURITY.md) 中的私密渠道报告。
