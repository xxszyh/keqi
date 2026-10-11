# 编译课期

当前构建脚本支持 **Windows x64、Python 3.10+、JDK 17、Android SDK Platform 35、Build Tools 35.0.0**。安装与使用 APK 不需要开发环境。尚未提供 Gradle/Android Studio 工程或 Linux/macOS 构建流程。

## 获取源码

```powershell
git clone https://github.com/xxszyh/keqi.git
cd keqi
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

仓库中的 jsoup 和 JVM 测试 JAR 已固定版本及 SHA-256，编译会校验它们。项目不要求开发者复制维护者的配置、账号或签名文件。

## 方案一：下载便携工具

```powershell
$env:KEQI_TOOL_CACHE = 'C:\KeqiTools'
.\.venv\Scripts\python.exe mobile\bootstrap_tools.py
```

脚本从 Google 官方 Android 仓库及 Microsoft JDK 地址下载工具，写入指定缓存和忽略的 `mobile/tools.local.json`，不修改系统 PATH。缓存路径必须只有 ASCII 字符。SDK 下载按官方清单 SHA-1 校验；Microsoft JDK 下载地址为随补丁版本更新的 JDK 17 链接。若网络不可用或你已安装 SDK，使用方案二。

## 方案二：复用已有环境

先在 Android SDK Manager 安装 `platforms;android-35` 和 `build-tools;35.0.0`，并设置 JDK 17。

```powershell
$env:JAVA_HOME = 'C:\Tools\jdk-17'
$env:ANDROID_HOME = 'C:\Android\sdk'
.\.venv\Scripts\python.exe mobile\configure_tools.py
```

非标准目录可传 `--java-home`、`--android-jar`、`--build-tools` 显式配置，脚本会检查文件是否存在。JDK、SDK 和签名文件路径必须使用 ASCII；项目源码目录可以包含中文。

## 检查和编译

```powershell
.\.venv\Scripts\python.exe scripts\check_repo.py
.\.venv\Scripts\python.exe mobile\run_checks.py --domain-only
.\.venv\Scripts\python.exe mobile\build.py
```

产物是 `mobile/课期-安卓.apk` 和 `mobile/build-info.json`，均不进入 Git。也可避免写入默认位置：

```powershell
.\.venv\Scripts\python.exe mobile\build.py --output-dir mobile\build\local
```

源码先暂存在 SDK 旁的 ASCII 工作目录，再编译 Java、资源与 DEX、对齐并签名。构建会检查 APK 签名、权限、启动入口和包内资源。JVM 测试 JAR 不打包进 App；运行时依赖只有 jsoup 和 Android 系统组件。

## 签名

默认构建在本机工具缓存生成开发者自己的调试密钥，重复使用以便本机开发升级。已有旧版工具缓存密钥会继续使用。默认调试口令为 Android 常用测试口令，不是官方发布密钥。

**自己的签名不能覆盖 GitHub Release APK。** 测试时请使用备用设备/安装环境，避免卸载导致原有数据丢失。维护者正式签名流程见 [发布说明](RELEASING.md)。

要报告构建失败，请只贴脱敏错误、工具版本和复现步骤，不附 `tools.local.json`、签名文件或平台网页原文。完整界面检查见 [测试说明](TESTING.md)。
