# 验证与测试

先按 [编译说明](BUILDING.md) 配置工具链。检查使用虚构课程、账号标识和页面样例，不需要学习通/MOOC 真实登录。

```powershell
.\.venv\Scripts\python.exe scripts\check_repo.py
.\.venv\Scripts\python.exe mobile\run_checks.py
```

完整检查还需要安装 Microsoft Edge。Playwright 使用系统 Edge 的 `msedge` 通道；不要求下载 Chromium。只验证 Java 逻辑时：

```powershell
.\.venv\Scripts\python.exe mobile\run_checks.py --domain-only
```

| 范围 | 入口 | 检查内容 |
| --- | --- | --- |
| 项目结构 | `scripts/check_repo.py` | 文档链接、许可副本、依赖哈希、Git 排除项 |
| 日期、任务与选课 | `mobile/test.py` | 提交/互评、未知日期、提醒身份、移出和恢复 |
| 登录及目录 | `mobile/connection_test.py` | 受保护页面证据、登录失效、课程范围 |
| 续读与考试 | `mobile/chaoxing_read_test.py` | HTTP 202、未就绪、课程归属、部分失败 |
| 存储与提醒 | `mobile/migration_test.py` | 旧数据升级、数百条任务的目录读取次数、关闭/移出课程及完成任务的提醒排除 |
| 界面 | `mobile/*_ui_test.py` | 列表、考试、筛选、设置、长列表与窄屏 |
| 课程选择 | `mobile/course_visibility_test.py`、`mobile/chaoxing_course_selection_test.py`、`mobile/course_name_test.py` | 隐藏作用范围、平台独立编辑、名字更新 |
| 网页续读 | `mobile/chaoxing_connection_test.py`、`mobile/chaoxing_202_browser_test.py` | 页面样例 DOM 到生产 Java 认证/解析 |

统一入口顺序执行 13 个脚本，避免同时编译同一测试目录。存储测试首次运行会下载桌面依赖清单中固定版本并校验哈希的 SQLite JDBC / SLF4J 组件，无需先编译桌面工程。无匿名网页缓存时，连接检查是 103 项；开发者另行采集的匿名公开响应可增加检查，CI 不依赖这些缓存。

`public_login_check.py` 与 `login_navigation_probe.py` 是可选的公开网页诊断，访问真实平台；它们不属于 CI，也不用于证明真实账号登录成功。结果留在忽略的构建目录。

浏览器检查会更新 `mobile/preview/` 中的示例截图；只在需要更新文档时提交截图变化。示例图片不代表真实课程。历史验证记录见 [mobile/验证记录.md](../mobile/验证记录.md)。

仓库提供 [Windows CI 模板](ci.yml)：配置 JDK/SDK、检查项目、运行全部回归并构建 APK。启用后产物使用每次 CI 自己生成的调试签名，不能覆盖官方安装包。

当前模板尚未启用：GitHub 拒绝现有 OAuth 凭据创建 `.github/workflows/ci.yml`，原因是缺少 `workflow` 权限。可由仓库管理员在 GitHub 网页新建该路径并复制模板内容，或为 GitHub CLI 增加权限后提交：

```powershell
gh auth refresh -s workflow
New-Item -ItemType Directory -Force .github/workflows
Copy-Item docs/ci.yml .github/workflows/ci.yml
git add .github/workflows/ci.yml
git commit -m "Enable Windows checks and Android build"
git push
```

授权命令需要管理员本人在 GitHub 确认。工作流启用后每次 PR 或 main 推送运行；当前已验证的是本机干净副本的同一组命令，未声称 GitHub hosted runner 已通过。

这些检查没有 Android SQLite、AlarmManager、JobScheduler、锁屏通知和手机 WebView 的端到端环境。发布前需实机核对覆盖安装、登录、作业/考试范围、课程移出与恢复、返回键、通知权限及后台行为。

## 电脑与手机联动

0.11.0 增加 `shared/test.py` 的共享规则、真实 HTTPS/SQLite 与复审回归，以及 `desktop/tests/` 的实际 Electron 界面和 JVM 恢复检查。桌面编译和测试入口见 [桌面工程](../desktop/README.md)，本机结果与实机边界见 [0.11.0 验证记录](DESKTOP_VERIFICATION.md)。

## Android 启动实机检查

0.11.1 增加可选的 `mobile/device_startup_test.py`，用真实 MainActivity → Native 快照 → WebView 渲染链路检查平台入口和课程。默认的普通 APK 不开放调试；仅在设备所有者同意后，使用 `mobile/build.py --debug --output-dir mobile/build/device-diagnostic` 构建临时诊断包，保持原签名覆盖安装。诊断包不能通过发布打包检查。

```powershell
.\.venv\Scripts\python.exe mobile\device_startup_test.py --adb C:\Android\platform-tools\adb.exe --courses 42
```

脚本只重启课期，临时转发该 App 的 WebView 调试端口，并在退出时移除端口；不清除 App 数据、不写入手机文件、不修改系统设置。需要 Playwright、已授权的 USB 调试及正在安装的诊断版本。`--courses` 填设备实际保留课程数量，也可省略。验证后应以同签名普通 APK 覆盖，关闭 App 调试。此检查能发现页面数据加载阻塞，但不验证平台任务内容或锁屏通知。
