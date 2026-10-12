# 课期 Windows 桌面工程

Electron 管理官方登录窗口、隔离会话、托盘与 Windows 通知；Java 17 进程提供平台解析、SQLite 单写入事务和配对 HTTPS。界面复用 `mobile/assets/`；`shared/core-java/` 保持 Android 可编译的 Java 8 语法。

需要 Windows x64、Node.js 24、Python 3.10+ 和 JDK 17。已有 `mobile/tools.local.json` 时可复用便携 JDK；单独构建桌面不需要 Android SDK。

```powershell
# 在仓库根目录；JAVA_HOME 指向 JDK 17，或配置 mobile 工具链
python desktop/build.py --runtime
cd desktop
npm ci
npm start
cd ..
python desktop/package.py
python desktop/verify_package.py
```

依赖锁定在 `package-lock.json` 和 `lib/manifest.json`。JVM 依赖从 Maven Central 按固定 SHA-256 下载；JRE 由 jlink 生成，保留其法律声明。如果 npm 的脚本策略阻止 Electron 下载或 electron-winstaller 的架构选择，请检查对应安装脚本后允许该包；不要全局关闭脚本审查。

`dist/` 是打包输出，`dist/downloads/` 包含安装 EXE 和便携 ZIP；打包目录同时可作便携版。EXE 尚未购买代码签名证书，Windows 可能提示发布者未知。

## 检查

```powershell
python desktop/build.py
python shared/test.py
cd desktop
npm test
```

UI 与平台连接检查使用 Node 版 Playwright。可以安装开发者自己的 Playwright，或用 `KEQI_PLAYWRIGHT` 指向 Python Playwright 的 `driver/package` 目录。测试隔离到临时数据目录，隐藏主窗口，使用明确标记的匿名任务；不会访问真实账号。`tests/platform.test.js` 通过真实 Electron → JVM 验证 MOOC 登录跳转、延迟资源、运行时账号、登录失效与异常跳转，以及课程、作业和考试读取；平台响应由隔离会话内的匿名协议处理器提供。

可以设置 `KEQI_PACKAGED_EXE` 为最终打包目录的 `Keqi.exe` 路径，再运行 `node --test tests/ui.test.js`，验证附带的 JVM、依赖和界面，而不是开发运行时。共享检查同样支持 `JAVA_HOME`，无需 Android SDK。

桌面共享内容在用户数据目录的 SQLite `meta.workspace` 内保存为版本化 JSON。每次命令整体校验并事务提交；来源观察、逐字段因果版本、回执和实体修订在同一事务。首版没有压缩历史或分页拉取，适合个人规模，不能把它当作多人服务部署。

安装和配对见 [使用说明](../docs/DESKTOP_USAGE.md)，方案和实现差异见 [架构](../docs/DESKTOP_ARCHITECTURE.md)，成品与复审结果见 [验证记录](../docs/DESKTOP_VERIFICATION.md)。
