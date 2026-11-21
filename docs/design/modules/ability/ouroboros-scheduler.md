# ouroboros-scheduler 模块设计摘要

## 模块定位

`ouroboros-scheduler` 是能力层中的任务调度模块，负责把 Cron 和一次性任务调度能力接入平台，并与 LogicFlow 运行时和开发平台任务管理协同工作。它面向的是“如何稳定调度任务”，而不是定义任务所属的具体业务语义。

## 责任边界

该模块负责调度抽象、调度器实现、运行时任务装配和开发平台中的任务管理入口，不负责实现通用消息队列、工作流编排平台或具体业务作业逻辑。任务执行通常会委托给 LogicFlow 或上层模块，本模块应保持在调度协调层，而不是不断吸收业务细节。

## 依赖关系

模块由 `core`、`quartz`、`runtime`、`dev` 构成：`core` 定义 `Scheduler`、`ScheduleTask` 等核心协议，`quartz` 提供默认调度实现，`runtime` 负责自动配置、脚本上下文和低代码任务装配，`dev` 负责开发平台中的任务管理和文件检查器接入。它向上主要服务逻辑流和低代码配置任务，向下依赖 Quartz 与通用运行时能力；新任务来源应优先从 `ScheduleTaskProvider` / `ScheduleTaskIntegrator` 切入。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-scheduler/DESIGN.md](../../../../ability/ouroboros-scheduler/DESIGN.md)

## 维护入口

当前机制在模块 DESIGN.md 持续维护。历史评审与产品分析退出默认阅读链，不作为当前功能或支持承诺。
