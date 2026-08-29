# Android 多模型性能测试协议

所有模型使用 q4f16_1、2048 上下文、相同提示词和采样参数。每个测试先预热一轮，
再测三轮；20 轮稳定性用例不预热。测试时记录设备型号、Android/API、可用内存、
温度状态、模型 revision 与 Runtime revision。

## 固定用例

1. 短对话：约 128 输入 token，最多输出 128 token。
2. 局面解释：约 1K 输入 token，最多输出 256 token。
3. 长上下文：接近 2K 输入 token，最多输出 128 token。
4. 连续 20 轮：保持同一人物设定，检查成功率、重复和内存稳定性。
5. 取消：生成开始后立即取消，记录取消终态与耗时。
6. 模型切换：0.5B → 1.5B → 3B → 0.5B，检查释放与重新加载。

## 指标

- 下载大小、磁盘占用、冷加载时间
- 首 token 延迟、总耗时
- prefill/decode token/s
- 成功率、取消耗时、峰值 PSS
- 15 分钟持续运行后的温升与降频

`LocalLlmBenchmarkRunner` 统一采集加载、首 token、总耗时和 MLC usage；PSS、温度、
电量由 ADB 测试脚本补充。报告不能跨不同上下文、量化或 Runtime revision 横向比较。
