# XCLogger 对外功能测试用例（SWT）

> 文档目标：面向测试团队，聚焦“用户可操作、外部可接入、实际可提供”的能力，给出可直接执行的测试项、测试手法、验收方式与预期结果。

## 1. 范围与输入来源

本用例基于以下内容整理：

- `app/`（实际实现）
- `xclogger-api/`（AIDL 接口定义）
- `ARCHITECTURE.md`（架构说明）
- `INTEGRATION.md`（外部集成指南，含广播协议和 AIDL 对照）

## 2. 功能分层（先给测试边界）

### 2.1 用户可操作（App 内）

1. 启动/停止日志采集（MainActivity）
2. 配置查看与修改（Config 页面）
3. 打印配置、恢复默认（Developer 页面）
4. 手动触发压缩（通过广播或外部入口）

### 2.2 外部可接入（集成方）

1. **广播协议接入**：`CTRL_REQUEST` / `CTRL_RESULT`
2. **AIDL 接入**：`IXcLoggerService` + `IXcLoggerListener`
3. 兼容旧广播启动/停止：`ADB_CMD`（`start_xc_log` / `stop_xc_log`）
4. **包事件监听**：PACKAGE_ADDED / PACKAGE_REMOVED（需 Manifest 声明 `<data android:scheme="package" />`）

### 2.3 实际可提供（系统行为）

1. 日志采集运行态管理（含开机/升级恢复）
2. 过滤写入（Tag/Level/Package，逻辑 AND）
3. 文件切分、容量与时效清理
4. 压缩上传状态机（IDLE/COMPRESSING/WAIT_UPLOAD_RESULT/CANCELLING）
5. 上传结果回传与失败重试（最多 3 次）
6. 关键操作历史落盘（`A_OperationHistory.txt`）

---

## 3. 测试环境与通用前置条件

- 设备：Android 系统机（建议 userdebug/eng，具备日志与文件访问能力）
- 安装：`com.xcheng.xclogger` 已安装
- 外部测试 App（二选一或都测）：
  - 广播方式（ADB + BroadcastReceiver）
  - AIDL 方式（集成 `xclogger-api-release.aar`）
- 建议准备：
  - 可持续产生日志的测试应用（多 Tag、多 Level）
  - 上传模拟器（可控返回成功/失败）

---

## 4. 测试用例矩阵

> 字段说明：
>
> - **测试手法**：执行步骤（可手工/脚本）
> - **验收方式**：看什么证据判定通过
> - **预期结果**：必须满足的结果

### A. 广播控制能力


| 编号    | 测试项                      | 测试手法                                              | 验收方式                      | 预期结果                                                |
| ----- | ------------------------ | ------------------------------------------------- | ------------------------- | --------------------------------------------------- |
| BR-01 | `start` 启动采集             | 发送 `CTRL_REQUEST op_type=start`                   | 监听 `CTRL_RESULT` + 查询运行状态 | `success=true`，`op_type=start`，`running_state=true` |
| BR-01b | `trigger_compress` 带时间范围 | `CTRL_REQUEST op_type=trigger_compress --es start_time "..." --es end_time "..."` | 回执 + zip 内容检查 | 仅范围内文件被压缩，参数不传 = 全量 |
| BR-02 | `stop` 停止采集              | 发送 `op_type=stop`                                 | 回执 + 查询状态                 | `success=true`，`running_state=false`                |
| BR-03 | `restart` 重启采集           | 发送 `op_type=restart`                              | 回执 + 日志文件继续生成             | `success=true`，重启后仍可采集                              |
| BR-04 | `query_status` 状态查询      | 发送 `op_type=query_status`                         | 检查回执字段                    | 回执包含 `running_state` 且与实际一致                         |
| BR-05 | `update_config` 部分更新     | 仅下发 1~2 个配置字段（如 `file_size`）                      | 更新前后读取配置/观察行为             | 指定字段生效，未下发字段保持原值                                    |
| BR-06 | 未传 `op_type`             | 发空请求                                              | 回执 `op_type`              | 按默认 `query_status` 处理，不崩溃                           |
| BR-07 | 非法 `op_type`             | 发送不存在操作                                           | 回执 `success/message`      | 返回失败或错误说明，不影响服务稳定性                                  |
| BR-08 | 旧协议 `ADB_CMD` start/stop | 发 `ADB_CMD` + `cmd_name=start_xc_log/stop_xc_log` | 状态变化 + 回执/日志              | 兼容生效，行为等价 start/stop                                |


### B. AIDL 接入能力


| 编号    | 测试项                                  | 测试手法                                        | 验收方式                       | 预期结果                                                           |
| ----- | ------------------------------------ | ------------------------------------------- | -------------------------- | -------------------------------------------------------------- |
| AI-01 | Service 绑定                           | 显式 Intent：`com.xcheng.xclogger.REMOTE_BIND` | `onServiceConnected` 回调    | 可成功拿到 `IXcLoggerService`                                       |
| AI-02 | `startLogging/stopLogging/isRunning` | 依次调用并查询                                     | 返回值 + 监听 `onStatusChanged` | 状态一致，回调正确（0/1）                                                 |
| AI-03 | `getConfiguration`                   | 读取配置对象                                      | 字段完整性检查                    | 返回包含 8 个核心字段且可读                                                |
| AI-04 | `updateConfigurationPartial`         | 构造部分字段 patch 更新                             | 更新后再次 `getConfiguration`   | 仅 patch 字段变化，其他字段不变                                            |
| AI-05 | 监听注册/反注册                             | 注册后执行操作，再反注册重复操作                            | 回调计数                       | 注册后有回调，反注册后不再回调                                                |
| AI-05b | `triggerCompressionWithRange`       | 传入时间范围调用                                   | 返回值 + 回调                      | `true`，压缩结果仅含范围内文件                                |
| AI-06 | `getCompressStatus`                  | 不同阶段调用（空闲/压缩中/等待上传）                         | 返回字符串解析                    | 格式 `state=...;zip_files=...;retry_count=...;max_retry_count=3` |
| AI-07 | `cancelCompressTask`                 | 在 WAIT 或 COMPRESSING 调用                     | 状态查询 + 文件检查                | 状态回到 IDLE，待上传 zip 被清理                                          |


### B. 过滤规则

| 编号    | 测试项                         | 测试手法                                   | 验收方式                              | 预期结果                                                   |
| ----- | --------------------------- | -------------------------------------- | --------------------------------- | ------------------------------------------------------ |
| FL-01 | Tag 精确匹配（多条 OR）          | 配置 `filter_tag=TagA,TagB`，制造 TagA、TagB、TagC 的日志 | 检查日志文件 | 只记录 TagA 和 TagB 的日志，TagC 被过滤 |
| FL-02 | Level 阈值                    | 配置 `filter_level=e`，制造 e/w/i/d/v 各一条日志   | 检查日志文件 | 只记录 e(Error) 和 f(Fatal) 级别的日志 |
| FL-03 | Package 精确匹配               | 配置 `filter_package=com.example.app`，安装目标 App | 该 App 日志出现 ✅，其他 App 日志不出现 |
| FL-04 | **Package 前缀匹配**          | 配置 `filter_package=com.example.`（以 `.` 结尾） | 安装多个 `com.example.xxx` App | 所有匹配前缀的应用日志均被记录 |
| FL-05 | **Package 设为 all**          | 配置 `filter_package=all`                | 检查日志文件 | 所有应用的日志都被记录（不过滤） |
| FL-06 | **动态 UID 刷新（广播）**      | 日志运行时安装新应用，包名匹配前缀               | 操作历史 + 日志文件 | 安装后约 10 秒内新 App 的日志出现（PACKAGE_ADDED 或轮询发现） |
| FL-07 | **动态 UID 刷新（轮询兜底）**  | 禁用 PACKAGE_ADDED 的 Manifest，安装新应用，等待 10~15 秒 | 操作历史 + 日志文件 | `Prefix auto-refresh added X UID(s)` 出现，新 App 日志被记录 |
| FL-08 | **卸载后 UID 清理**           | 日志运行时卸载已记录的应用                    | 无异常崩溃 | 对应 App 不再产生日志，filterUidSet 无残留 |

### C. 版本推送

| 编号    | 测试项                         | 测试手法                                   | 验收方式                              | 预期结果                                                   |
| ----- | --------------------------- | -------------------------------------- | --------------------------------- | ------------------------------------------------------ |
| VU-01 | 版本升级后配置更新               | 旧版 APK（`buffer_size=4096`）→ 安装新版（`buffer_size=2048`）| 启动后检查操作历史 | `Config upgraded from v1 to v2 (buffer_size: 4096 -> 2048, ...)` |
| VU-02 | 版本相同不改动                  | 连续两次启动同一版本 APK                    | 操作历史 | 无版本升级日志，用户自定义值保留 |
| VU-03 | config_version 与 database_version 隔离 | DatabaseMigration 升版本不影响配置推送      | 无冲突 | 两个版本号独立增长 |

### D. 压缩上传状态机（核心）


| 编号    | 测试项                         | 测试手法                                   | 验收方式                              | 预期结果                                                   |
| ----- | --------------------------- | -------------------------------------- | --------------------------------- | ------------------------------------------------------ |
| CP-01 | 首次触发压缩成功                    | `trigger_compress`（广播或 AIDL）           | `CTRL_RESULT` / `onCompressReady` | `success=true`，状态到 `WAIT_UPLOAD_RESULT`，返回 `zip_files` |
| CP-01b | 按时间范围压缩（双边）             | `trigger_compress` + `start_time`+`end_time` | zip 文件内容检查                     | 仅范围内（含边界）的文件被打包，范围外文件不在 zip 中 |
| CP-01c | 按时间范围压缩（单边 start）        | `trigger_compress` + 仅 `start_time`          | zip 文件内容检查                     | 从 start 边界到最新文件 |
| CP-01d | 按时间范围压缩（单边 end）           | `trigger_compress` + 仅 `end_time`            | zip 文件内容检查                     | 从最旧文件到 end 边界 |
| CP-01e | start 早于所有日志                  | `start_time` 早于最旧日志                       | zip 内容                          | 从第一个文件开始 |
| CP-01f | end 晚于所有日志                    | `end_time` 晚于最新日志                         | zip 内容                          | 到最后一个文件结束 |
| CP-01g | 时间范围内外都超出                  | `start_time` 早 && `end_time` 晚               | zip 内容                          | 等效全量压缩 |
| CP-02 | 压缩失败路径                      | 制造不可用日志目录或 I/O 异常场景                    | 回执字段                              | `success=false`，状态回 `IDLE`，有错误信息                       |
| CP-03 | WAIT 状态下再次触发（强制重开）          | 在已有 pending zip 时再次 `trigger_compress` | 对比新旧 zip 路径                       | 旧 zip 被清理，生成新一轮结果                                      |
| CP-04 | COMPRESSING 状态下再次触发         | 压缩过程中再次触发                              | 观察最终仅一轮有效结果                       | 旧压缩被取消并自动重启，最终只看新一轮结果                                  |
| CP-05 | `upload_result=true`        | 回传上传成功                                 | 查询状态 + 文件检查                       | 状态 `IDLE`，zip 删除                                       |
| CP-06 | `upload_result=false` 第1/2次 | 连续失败 2 次                               | `retry_count` 变化                  | 状态保持 `WAIT_UPLOAD_RESULT`，同一 zip 可重传                   |
| CP-07 | `upload_result=false` 第3次   | 第 3 次失败回传                              | 状态 + 文件检查                         | 状态 `IDLE`，zip 强制删除，`retry_count=3`                     |
| CP-08 | `query_compress_status`     | 各阶段查询                                  | 返回值与真实状态对比                        | 状态/zip/retry 字段一致                                      |
| CP-09 | `cancel_compress`           | 在不同状态执行取消                              | 回执 + 文件检查                         | 任务清理、zip 清理、状态最终 `IDLE`                                |


### D. 文件与清理策略


| 编号    | 测试项      | 测试手法                                  | 验收方式         | 预期结果                                              |
| ----- | -------- | ------------------------------------- | ------------ | ------------------------------------------------- |
| FS-01 | 日志命名规则   | 运行采集生成文件                              | 文件名正则校验      | `mainlog_<6位index>_<yyyyMMdd>_<HHmmss>_<seq>.txt` |
| FS-02 | 单文件大小切分  | 将 `file_size` 设小并持续打日志                | 观察滚动频率与新文件生成 | 达阈值后自动切分新文件                                       |
| FS-03 | 总容量上限清理  | `total_size` 设小并持续写入                  | 目录容量与文件时间序   | 超限时优先删最旧日志                                        |
| FS-04 | 保留时长清理   | `log_period` 设短并跨时段观察                 | 旧文件是否被删      | 超期日志自动清理                                          |
| FS-05 | 压缩目录过期清理 | 在 `/data/xclogger/mobilelog` 放置过期 zip | 触发相关流程后检查    | 过期 zip 被清理，历史规则符合配置                               |
| FS-06 | 压缩命名规则   | 触发压缩，覆盖“当天/非当天日志”                     | zip 名称检查     | `yyyy_MMdd_HHmmss_hash.zip`；非当天 `HHmmss=235959`   |


### E. 过滤与配置生效


| 编号    | 测试项               | 测试手法                              | 验收方式       | 预期结果            |
| ----- | ----------------- | --------------------------------- | ---------- | --------------- |
| FL-01 | Tag 过滤（多值 OR）     | 设置 `filter_tag=A,B`，注入 A/B/C 日志   | 文件内容抽样     | 仅 A/B Tag 保留    |
| FL-02 | Level 过滤（阈值）      | 设 `filter_level=w`，注入 v/d/i/w/e/f | 文件内容统计     | 仅 `w/e/f` 保留    |
| FL-03 | Package 过滤（多值 OR） | 设两个包名，分别打日志                       | UID/包映射后结果 | 仅目标包日志保留        |
| FL-04 | 三类过滤 AND 关系       | 同时设置 Tag+Level+Package            | 构造交叉样本     | 仅同时满足三条件的日志入文件  |
| FL-05 | 配置路径切换            | 修改 `log_dir` 后继续运行                | 新旧目录文件变化   | 新日志写入新路径，历史文件保留 |


### F. 稳定性与恢复


| 编号    | 测试项        | 测试手法                     | 验收方式                        | 预期结果                                   |
| ----- | ---------- | ------------------------ | --------------------------- | -------------------------------------- |
| ST-01 | 开机恢复运行态    | 运行中重启设备                  | 开机后状态与日志写入                  | 若上次为运行态，自动恢复采集                         |
| ST-02 | 应用升级恢复     | 安装覆盖升级                   | 升级后状态与服务                    | 按历史运行态恢复，RemoteBindService 可用          |
| ST-03 | 并发压力（连续触发） | 高频连续发 `trigger_compress` | 状态机稳定性                      | 无崩溃/死锁，最终状态可收敛                         |
| ST-04 | 操作历史审计     | 执行关键操作链路                 | 检查 `A_OperationHistory.txt` | Start/Stop/Compress/Upload/Cancel 等有记录 |


---

## 5. 推荐测试手法（执行层）

### 5.1 广播自动化（ADB）

- 使用 `adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type <op>`
- 用测试 App 监听 `com.xcheng.xclogger.CTRL_RESULT`，统一落表（时间戳、op_type、success、state、zip、retry）

### 5.2 AIDL 自动化

- Demo App 启动后自动绑定 `REMOTE_BIND`
- 用例驱动调用 AIDL，并记录返回值与 Listener 回调事件序列
- 对“异步压缩”场景，必须以 `onCompressReady/onCompressFinished` 为主验收，不仅看 `triggerCompression()` 返回值

### 5.3 文件系统验收

- 日志目录：`/storage/emulated/0/XcLogger`（或配置新路径）
- 压缩目录：`/data/xclogger/mobilelog`
- 审计文件：`A_OperationHistory.txt` 及备份 `A_OperationHistory_<ts>.txt`

---

## 6. 验收判定标准（交付门槛）

### P0（必须通过）

1. 广播协议完整可用：start/stop/query/update/trigger/upload/query_compress/cancel
2. AIDL 接口与回调完整可用
3. 压缩上传状态机正确，3 次失败策略正确
4. 不出现崩溃、卡死、状态无法回收
5. 过滤规则正确（Tag OR、Package 精确/前缀/三者 AND、Level 阈值）
6. 动态 UID 刷新不导致 ANR 或崩溃

### P1（应通过）

1. 新安装应用能在 10 秒内被自动发现并记录日志
2. 卸载应用后对应日志消失，无残留 UID
3. 版本推送：OTA 后新默认值生效，用户自定义值在版本不变时保留
4. 切分/容量/时效清理策略符合配置
5. 开机/升级恢复能力正确

### P2（建议通过）

1. 并发与长稳测试（高频触发、长时间采集）
2. 异常注入场景（存储不可写、路径异常、上传超时）

---

## 7. 缺陷记录建议模板


| 字段   | 说明              |
| ---- | --------------- |
| 用例编号 | 如 `CP-04`       |
| 环境信息 | 设备型号/系统版本/构建版本  |
| 前置条件 | 配置值、初始状态        |
| 复现步骤 | 可复制的最小步骤        |
| 实际结果 | 含回执字段、回调序列、文件证据 |
| 预期结果 | 引用本用例文档对应条目     |
| 附件   | 日志、截图、关键文件路径    |


---

## 8. 备注（对测试团队）

1. `trigger_compress` 是“强制重开”语义，不是简单排队。
2. `triggerCompression()` 返回 `true` 仅表示请求受理，最终以异步结果为准。
3. 广播结果与 AIDL 回调都要校验字段完整性，尤其 `compress_state/zip_files/retry_count/max_retry_count`。
4. 压缩期间日志采集可继续进行；压缩对象是“封口前快照”，不是实时当前文件。

