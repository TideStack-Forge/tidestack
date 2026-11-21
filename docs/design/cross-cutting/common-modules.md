# common-* 组件族边界

## 组件族定位

`common-*` 不是单一模块，而是一组围绕 Web、异常、缓存、模板、脚本、表达式、模型基类和应用支持构成的公共组件族。它们位于 `ouroboros-util` 之上、`core` 之下，是把通用工具提升为“平台公共部件”的关键一层。

## 责任边界

这一组件族负责沉淀平台级公共能力，例如异常层次、统一 Web API 响应、缓存接口、模板与脚本能力、表达式求值、模型基类、服务基类和开发态基础抽象。它不负责具体业务模块、具体平台入口或某个单独引擎的领域规则；这些能力应继续留在 `core`、`ability` 或更上层。

## 依赖关系

`common-*` 通常建立在 `ouroboros-util` 之上，再被 `core`、`security`、`ability` 以及上层交付模块广泛复用。`common-all` 则承担聚合入口的角色：它不定义新的公共语义，而是为需要多项公共能力的模块提供一次性引入方式。

## 关键抽象

- 异常体系：`common-exception` 与 `common-exception-mapper` 统一平台异常模型和第三方异常映射。
- Web 与缓存：`common-web`、`common-cache` 统一接口响应和缓存管理。
- 模板、脚本、表达式：`common-template`、`common-script`、`common-expression` 共同承接可生成、可解释的动态能力。
- 模型与服务基类：`common-model`、`common-service`、`common-app`、`common-dev` 为上层模块提供通用基类和运行支撑。

## 关键子模块

- `common-exception`、`common-exception-mapper`
- `common-web`、`common-cache`
- `common-template`、`common-script`、`common-expression`
- `common-model`、`common-service`、`common-app`、`common-dev`
- `common-lock`：统一锁抽象与默认本地实现
- `common-metadata`：typed 元数据资源计划、解析、合并与来源追踪
- `common-all`：公共组件聚合模块

## 扩展机制

`common-*` 组件族本身包含模板、脚本、表达式和异常映射等扩展入口，但这些入口在治理上仍属于共享机制的一部分。模块摘要只保留族级边界说明；具体机制规则请查看[扩展机制](extension-mechanisms.md)。

## 深入文档索引

- `推荐阅读`：[libs/common/GUIDE.md](../../../libs/common/GUIDE.md)
- `补充深度资料`：[common-web 模块设计摘要](../modules/libs/common-web.md)
- `补充深度资料`：[common-cache 模块设计摘要](../modules/libs/common-cache.md)
- `补充深度资料`：[common-model 模块设计摘要](../modules/libs/common-model.md)
- `补充深度资料`：[common-service 模块设计摘要](../modules/libs/common-service.md)
- `补充深度资料`：[common-app 模块设计摘要](../modules/libs/common-app.md)
- `补充深度资料`：[common-template 模块设计摘要](../modules/libs/common-template.md)
- `补充深度资料`：[common-script 模块设计摘要](../modules/libs/common-script.md)
- `补充深度资料`：[common-expression 模块设计摘要](../modules/libs/common-expression.md)
- `补充深度资料`：[common-dev 模块设计摘要](../modules/libs/common-dev.md)
- `补充深度资料`：[common-exception 模块设计摘要](../modules/libs/common-exception.md)
- `补充深度资料`：[common-exception-mapper 模块设计摘要](../modules/libs/common-exception-mapper.md)
- `补充深度资料`：[common-all 模块设计摘要](../modules/libs/common-all.md)
- `补充深度资料`：[common-lock 模块设计摘要](../modules/libs/common-lock.md)
- `补充深度资料`：[common-metadata 模块设计摘要](../modules/libs/common-metadata.md)
- `历史参考`：[libs/common/common-exception-mapper/README.md](../../../libs/common/common-exception-mapper/README.md)
- `暂无独立深度设计文档`：当前 `common-*` 组件族尚未整理出统一的设计专题，主要依赖 GUIDE 和各子模块说明。

## 演进状态

`common-*` 已经形成相对稳定的公共组件版图，但随着平台能力扩展，新的“公共能力”很容易被不断加入。维护者需要持续判断新增能力究竟应该进入 `common-*`，还是应该留在更接近领域或运行时的位置；`common-all` 也应保持“聚合入口”而不是“隐藏依赖复杂度的黑盒”。
