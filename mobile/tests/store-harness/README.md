# Android 存储与提醒回归

入口：`mobile/migration_test.py`，也由 `mobile/run_checks.py` 执行。

编译生产 `Store`、`Collaboration`、`Notifications` 及共享领域规则。这里的 Android 类是最小测试替身：SQLite 使用桌面工程清单里固定版本的 JDBC；AlarmManager、通知服务和权限结果使用本地替身。只在新建的临时目录中操作匿名数据库，结束后删除。

- `MigrationRepro`：保存升级前课程、绑定和作业，重新打开生产 Store 后验证快照保留。
- `NotificationScaleRepro`：数百条任务下限制全量工作区读取次数，并检查关闭/移出课程、平台及本地完成和旧任务的提醒排除。

测试对提醒的持久化计算负责，不模拟 Android 数据库锁策略、真实系统闹钟或锁屏通知。启动时页面是否及时显示，另用经用户授权的 `mobile/device_startup_test.py` 在真实设备上检查。
