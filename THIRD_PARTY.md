# 开源组件与接口参考

| 项目 | 使用范围 | 固定版本 / 提交 | 许可 |
| --- | --- | --- | --- |
| [jsoup](https://jsoup.org/) | HTML 页面解析，打包进 APK | 1.18.3 | MIT |
| [furina061006/MOOC_reminder](https://github.com/furina061006/MOOC_reminder) | MOOC RPC 路径、课程身份和嵌套测验字段处理参考 | 2397452789b60dd99cc2b0aa6aaa75df4efab086 | MIT |
| [DanMo661/Xuexitong-mcp](https://github.com/DanMo661/Xuexitong-mcp) | 学习通课程、作业列表/详情及考试安排列表字段处理参考 | ebce2cc735bc7acc45fac7ccf32bcefe1a6b2a54 | MIT |
| [stleary/JSON-java](https://github.com/stleary/JSON-java/tree/20240303) | 桌面 Java 运行和 JVM 检查；Android 使用系统 org.json | 20240303 | Public Domain（JAR 内 POM 声明） |

本项目代码采用根目录 [MIT 许可证](LICENSE)，不替代以下组件和参考项目原有的版权与许可。前三项的完整通知保存在 `mobile/assets/licenses/`，并随 APK 分发，可从 App「设置 → 关于课期」查看。Android 自带的 org.json 属于系统运行环境。

便携工具链为 Microsoft OpenJDK 17、Android SDK Platform 35 和 Build Tools 35.0.0，仅用于开发，不打包进 App。SDK 下载按官方仓库清单中的 SHA-1 校验。依赖二进制的本地校验值保存在 `mobile/lib/manifest.json`。

适配逻辑包含本项目独立实现的状态保守判定、日期精度、账号/阶段身份、通知去重、部分同步保留和安卓后台调度。平台接口属于网页实现细节，源码不能代替真实账号兼容性验证。表中的固定提交是参考来源；复核时请访问对应上游仓库，项目不依赖仓库外的个人调研目录。

## 0.11.0 新增组件

| 项目 | 用途与版本 | 许可 |
| --- | --- | --- |
| [ZXing](https://github.com/zxing/zxing/tree/zxing-3.5.4) | 安卓二维码扫描，Core 3.5.4 | Apache-2.0 |
| [Electron](https://github.com/electron/electron) | 桌面浏览器、托盘、通知，44.7.0 | MIT；另含 Chromium 第三方声明 |
| [node-qrcode](https://github.com/soldair/node-qrcode/tree/v1.5.4) | 配对二维码，1.5.4 | MIT |
| [SQLite JDBC](https://github.com/xerial/sqlite-jdbc/tree/3.53.4.0) | 桌面 SQLite，3.53.4.0 | Apache-2.0；另含 Zentus 通知 |
| [JmDNS](https://github.com/jmdns/jmdns/tree/v3.6.4) | 桌面局域网发现，3.6.4 | Apache-2.0 |
| [SLF4J](https://www.slf4j.org/license.html) | JmDNS/SQLite 日志接口与空实现，2.0.17 | MIT |
| [electron-squirrel-startup](https://github.com/mongodb-js/electron-squirrel-startup) | Windows 安装和快捷方式事件，1.0.1 | Apache-2.0 |

ZXing 完整通知在 `mobile/assets/licenses/zxing.txt`，桌面 JVM 组件的通知在 `desktop/assets/licenses/`，构建时复制进界面许可页。Electron 发行目录保留 `LICENSE` 与 `LICENSES.chromium.html`；npm 运行依赖保留包内原始 LICENSE，开发依赖由 lockfile 固定且不打入应用。

桌面新增附带的 Microsoft OpenJDK 17 精简运行环境保留 `runtime/legal/` 全部法律声明；遵循 GPLv2 + Classpath Exception，其源码和来源为 [Microsoft OpenJDK](https://github.com/microsoft/openjdk)。安卓 SDK 与构建工具仍不进入安装包。
