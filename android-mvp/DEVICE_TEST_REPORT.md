# Android MVP 真机验收报告

- 日期：2026-08-29
- 设备：PTP-AN00
- 系统：Android 16 / API 36
- ABI：arm64-v8a
- 模型：`local/qwen2.5-0.5b-instruct@1`
- 量化：Qwen2.5 0.5B `q4f16_1`
- 后端：MLC Android GPU
- SDK 路径：`Demo → LocalLlmClient → mlc4j → TVM Runtime`

## 结果

| 项目 | 结果 |
|---|---|
| APK 覆盖安装与冷启动 | 通过 |
| 已安装模型复用（不重新下载） | 通过 |
| SDK `initialize()` / `load()` | 通过 |
| SDK 流式生成 | 通过，测试输出 `PASS` |
| Usage/性能事件 | 通过，prefill 8.7 tok/s，decode 1.9 tok/s（短样本） |
| 生成中取消与会话重置 | 通过 |
| 取消后恢复生成 | 通过，prefill 12.6 tok/s，decode 61.6 tok/s（极短样本，不作持续吞吐基准） |
| Java/native 崩溃 | 未发生 |
| 权限 | 仅保留联网及系统合并依赖权限；无全量相册/存储权限 |

## 强化验收

### 飞行模式离线测试

- 测试前网络状态：飞行模式关闭，Wi-Fi 开启，移动数据开启。
- 开启飞行模式后，设备公网探测明确返回 `Network is unreachable`。
- 在该状态下完成 App 冷启动、已安装模型发现、SDK `load()` 和流式生成。
- 离线生成正常返回，usage 为 prefill 9.0 tok/s、decode 29.1 tok/s（短样本）。
- 结论：已安装模型的加载和推理不存在运行时网络依赖。

### 连续 20 轮对话

- 条件：保持飞行模式、同一 App 进程、同一模型和同一会话。
- 轮次：20/20 完成；每轮提示和对应回答 `OK1` 至 `OK20` 均在界面确认。
- 进程 PID 全程保持 `16056`，无重启、Java 崩溃或 native 崩溃。
- 第 20 轮 usage：prefill 13.7 tok/s、decode 51.4 tok/s（极短输出）。
- 20 轮后内存摘要：TOTAL PSS 401,370 KB，TOTAL RSS 549,148 KB，
  Native Heap PSS 134,576 KB，Graphics PSS 35,676 KB。
- 测试后网络已恢复：飞行模式关闭，Wi-Fi 开启，移动数据开启。

强化验收结论：第一期“飞行模式运行”和“连续 20 次流式对话无崩溃”门槛通过。

短输出的 decode token/s 波动很大，本报告仅证明性能事件链路有效；正式性能结论
需使用固定提示、固定输出长度、预热与多轮统计。第一期剩余验收仍包括飞行模式和
20 次连续对话稳定性测试。
