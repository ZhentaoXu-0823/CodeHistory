# XCLogger 广播控制使用说明（AIDL 对照）

本文档用于说明：如何通过广播方式调用 XCLogger，并与 AIDL 接口能力进行对应。

- 目标读者：工程师、自动化 Agent、LLM
- 目标：不读源码也能完成日志工具控制与配置更新

---

## 1. 总览

XCLogger 当前支持两种控制方式：

1. **AIDL 方式**（绑定 `RemoteBindService`）
2. **广播方式**（发送 `Intent`）

你可以把广播理解为“无绑定、命令式调用”；AIDL理解为“有绑定、接口式调用”。

---

## 2. 广播协议（核心）

### 2.1 统一控制入口（推荐）

- **Action**：`com.xcheng.xclogger.CTRL_REQUEST`
- **必填 Extra**：`op_type`

可选的 `op_type`：

- `start`
- `stop`
- `restart`
- `update_config`
- `trigger_compress`
- `query_status`

### 2.2 广播回执

- **Action**：`com.xcheng.xclogger.CTRL_RESULT`
- **回执字段**：
  - `success`（boolean）
  - `message`（String）
  - `op_type`（String）
  - `running_state`（boolean）

> 建议接收方注册 `BroadcastReceiver` 监听 `CTRL_RESULT`，用于验收动作结果。

---

## 3. AIDL 与广播能力对照表

| AIDL 接口 | 广播 `op_type` | 说明 |
|---|---|---|
| `startLogging()` | `start` | 启动日志采集 |
| `stopLogging()` | `stop` | 停止日志采集 |
| `isRunning()` | `query_status` | 查询状态（通过回执 `running_state` 获取） |
| `updateConfigurationPartial(config)` | `update_config` | 部分更新配置 |
| `triggerCompression()` | `trigger_compress` | 触发日志压缩 |
| （无直接接口） | `restart` | 重启采集服务 |

---

## 4. 配置更新（`update_config`）字段说明

当 `op_type=update_config` 时，可附带一个或多个字段：

- `total_size`（int）
- `file_size`（int）
- `buffer_size`（int）
- `log_dir`（String）
- `log_period`（int）
- `filter_tag`（String）
- `filter_level`（String）
- `filter_package`（String）

规则：

- 未传字段保持当前值不变（部分更新）
- 运行中更新时，内部会执行重载流程确保配置生效

---

## 5. ADB 发送广播示例

> 包名：`com.xcheng.xclogger`

### 5.1 启动日志

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type start
```

### 5.2 停止日志

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type stop
```

### 5.3 重启日志

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type restart
```

### 5.4 触发压缩

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type trigger_compress
```

### 5.5 查询状态

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST --es op_type query_status
```

### 5.6 更新一个配置项（仅改日志目录）

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --es log_dir /storage/emulated/0/XcLogger_New
```

### 5.7 一次更新多个配置项

```bash
adb shell am broadcast -a com.xcheng.xclogger.CTRL_REQUEST \
  --es op_type update_config \
  --ei file_size 8 \
  --ei buffer_size 8192 \
  --es filter_tag ActivityManager,MyTag \
  --es filter_level e
```

---

## 6. 旧广播兼容说明

工程仍兼容旧广播：

- `com.xcheng.xclogger.ADB_CMD`
  - `cmd_name=start_xc_log`
  - `cmd_name=stop_xc_log`
  - `cmd_name=file_compress`
- `com.xcheng.xclogger.FILE_COMPRESS`

建议新接入统一使用：`com.xcheng.xclogger.CTRL_REQUEST`。

---

## 7. 结果处理建议

接收 `CTRL_RESULT` 后建议做如下处理：

1. 校验 `op_type` 与请求一致
2. 根据 `success` 判断是否成功
3. 打印 `message` 作为故障信息
4. 使用 `running_state` 更新界面状态

---

## 8. 重要约束（实现层行为）

- 请求来源由 XCLogger 内部反解析，不信任外部伪造 source。
- 控制命令在 XCLogger 内部串行执行（一个完成后再执行下一个）。
- 控制全过程会记录到操作历史文件（History）。
- 配置更新在运行中会触发生效链路，避免“DB已改、运行未生效”。

---

## 9. 常见问题

### Q1：为什么收不到结果广播？
- 检查是否监听了 `com.xcheng.xclogger.CTRL_RESULT`
- 检查 XCLogger 是否安装并可接收广播
- 检查请求参数是否正确（至少有 `op_type`）

### Q2：配置更新后为什么看起来没生效？
- 先确认回执 `success=true`
- 再查看 `running_state` 与应用当前状态
- 检查 `message` 是否提示某一步失败

### Q3：能否一次更新多个配置项？
- 可以。`update_config` 支持在同一个广播里传多个字段。

---

## 10. 最小验收流程（建议）

1. `start`
2. `query_status`（应为 `running_state=true`）
3. `update_config`（修改 1~2 项）
4. `trigger_compress`
5. `stop`
6. `query_status`（应为 `running_state=false`）

以上流程跑通后，说明广播控制链路与核心功能可用。
