# ouroboros-scheduler 设计文档

## 1. 概述

`ouroboros-scheduler` 是 Ouroboros 能力层中的任务调度模块，负责把 Cron 和一次性任务调度能力接入平台，并与 LogicFlow 运行时和开发平台任务管理协同工作。它面向的是“如何稳定调度任务”，而不是定义任务所属的具体业务语义。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由四部分组成：

- `ouroboros-scheduler-core`
- `ouroboros-scheduler-quartz`
- `ouroboros-scheduler-runtime`
- `ouroboros-scheduler-dev`

其中：

- `core` 定义 `Scheduler`、`ScheduleTask`、`ScheduleTaskProvider`、`ScheduleTaskIntegrator` 等核心协议。
- `quartz` 提供 `QuartzScheduler` 和 `LogicFlowJob` 等默认调度实现。
- `runtime` 负责自动配置、任务加载和脚本上下文接入。
- `dev` 负责开发平台中的任务管理、文件检查器和维护入口。

## 3. 核心模型与机制

### 3.1 Scheduler

`Scheduler` 是统一调度器接口，承接 Cron 和一次性任务调度、取消与状态判断能力。它是整个模块最关键的抽象边界。

### 3.2 ScheduleTask

`ScheduleTask` 是任务定义抽象，描述任务名称、Cron、目标逻辑流和上下文数据。它让调度内核与具体业务任务解耦。

### 3.3 Provider 与 Integrator

`ScheduleTaskProvider` 与 `ScheduleTaskIntegrator` 负责任务来源发现与聚合。这说明任务定义并不只来自某一个固定配置点，而是允许多来源汇聚后统一装配。

### 3.4 QuartzScheduler 与 LogicFlowJob

`QuartzScheduler` 是当前默认调度实现，`LogicFlowJob` 负责把调度任务桥接到 LogicFlow 执行链路。它们共同构成“调度抽象 + 默认执行桥”的运行时骨架。

## 4. 调度到执行的数据流

当前实现中的关键链路是：

- 任务定义通过 provider 体系被发现并聚合。
- `Scheduler` 把任务调度到具体实现，当前默认实现是 Quartz。
- `LogicFlowJob` 在触发时把任务执行桥接到 LogicFlow。
- `dev` 层对任务定义和版本治理提供维护入口。

这意味着维护时要守住两层边界：

- 调度抽象属于本模块核心。
- 具体任务业务逻辑仍属于 LogicFlow 或上层业务，不应反向塞回调度内核。

## 5. 维护重点

维护该模块时，应优先关注：

- `Scheduler`、`ScheduleTask`、provider/integrator 体系的清晰分工。
- 新任务来源优先通过 `ScheduleTaskProvider` 或 `ScheduleTaskIntegrator` 接入。
- 新调度实现应落在适配层，而不是改写 `Scheduler` 抽象本身。
- Quartz 和 LogicFlow 的桥接逻辑应限制在默认实现边界内，不要让未来演进方案污染当前权威入口。
