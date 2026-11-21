# API Runners

这里放 API 回归 runner 和适配层，例如对 metadata 生成接口回归、高代码数据 API 抽查器的统一封装。

## Responsibility

`testing/common/runners/api` 负责“怎么调用”，不负责“这个场景到底测什么”。

因此：

- 场景语义、fixtures、supported rings 以 `testing/integration/**/scenario.yaml` 为准
- 通过标准、排除项、资产真相规则以对应场景 `contract.md` 为准
- runner 只做桥接、参数转换、统一入口和兼容包装

## Bridge Rule

在过渡阶段，允许 runner 继续桥接旧实现，例如：

- `testing/common/runners/api/data_api_regression/run_regression.py`

但桥接 runner 不应自己重新发明一套场景真相。像以下内容都应该优先写回场景契约，而不是只藏在代码里：

- supported execution rings
- 资产真相规则
- preflight 标准
- pending / execution-ready 的统计口径
