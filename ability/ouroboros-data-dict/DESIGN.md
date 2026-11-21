# ouroboros-data-dict 设计文档

## 1. 概述

`ouroboros-data-dict` 是 Ouroboros 能力层中的统一数据字典能力，负责沉淀字典定义、字典项结构、运行时查询与开发时管理入口。它更接近“平台级基础数据语义服务”，为字段选项、状态值和低代码配置提供统一字典来源，而不是承载某个业务域自己的主数据。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由三部分组成：

- `ouroboros-data-dict-core`
- `ouroboros-data-dict-runtime`
- `ouroboros-data-dict-dev`

其中：

- `core` 定义 `DataDict`、`DataDictItem`、`DataDictService`、`DataDictStateTransition` 等核心抽象。
- `runtime` 提供运行时服务、控制器、结构转换工具和值类型接入。
- `dev` 通过 `@DevelopModule("data-dict")` 与开发平台模型管理脚手架接入。

`core` 还包括字典与字典项的抽象实现和序列化器；`dev` 侧还包括字典文件检查器与开发服务。

## 3. 核心模型与机制

### 3.1 DataDict 与 DataDictItem

`DataDict` 是字典对象的统一抽象，定义名称、类型、结构模式和字典项集合；`DataDictItem` 表达字典项的 code、label、排序和扩展属性。两者共同构成平台字典语义的基础模型。

### 3.2 DataDictService

`DataDictService` 是统一字典服务接口，封装字典与字典项的查询和管理能力。它把使用方与底层存储、结构转换细节隔离开来。

### 3.3 DataDictStateTransition

`DataDictStateTransition` 说明该模块不仅支持静态代码表，也承担状态流相关的字典语义。它体现了本模块在“值集合”之外，还服务于状态型配置场景。

### 3.4 Runtime 值类型与开发态接入

运行时中的 `DataDictItemValue`、`DataDictItemsValue` 把数据字典能力接入值类型系统；开发态则通过 `DataDictDevelopServiceImpl` 和开发平台统一管理入口，形成“运行时消费 + 开发态维护”的闭环。

## 4. 运行时链路

当前实现中的关键链路是：

- 字典定义与字典项在 `core` 中保持统一模型。
- `runtime` 负责字典结构转换、查询与值类型消费接入。
- `dev` 负责开发平台中的字典维护与版本治理接入。

这意味着维护时要持续守住三层边界：

- 字典抽象属于核心模型层。
- 查询、转换和值类型接入属于运行时层。
- 编辑、版本控制和平台管理属于开发态层。

## 5. 维护重点

维护该模块时，应优先关注：

- `DataDict`、`DataDictItem`、`DataDictService` 之间的职责分离。
- 字典能力不要反向演化成业务主数据管理模块。
- 状态流字典语义优先通过 `DataDictStateTransition` 等抽象延伸，而不是在运行时服务里堆特例。
- 值类型接入和开发平台接入应保持为外围适配，不要污染核心字典模型。
