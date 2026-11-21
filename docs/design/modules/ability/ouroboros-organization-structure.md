# ouroboros-organization-structure 模块设计摘要

## 模块定位

`ouroboros-organization-structure` 是能力层中的组织与人员结构管理模块，负责把公司、部门、岗位、员工和上下文感知能力组织成统一的组织结构模型。它是身份与组织来源的重要能力，但并不等同于安全层本身。

## 责任边界

该模块负责组织树、人员归属、任职关系、组织上下文和组织相关管理接口，不负责认证与 RBAC 规则本身。它为安全层和业务模块提供身份来源与组织上下文，但不应取代 `ouroboros-web-security` 对登录身份和访问控制的统一定义。

## 依赖关系

从聚合结构看，该模块分为 `core` 和 `runtime` 两层：`core` 定义组织节点、员工、任职和上下文抽象，`runtime` 承接管理服务、REST API、自动配置和 UI/字段仓库集成。它既是能力层模块，也与安全、数据模型、UI 模型存在紧密协作关系。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-organization-structure/DESIGN.md](../../../../ability/ouroboros-organization-structure/DESIGN.md)
- `补充深度资料`：[人员档案表单方案（子专题入口；正文见 DESIGN.md）](ouroboros-organization-structure-personnel-profile-form-scheme.md)

## 演进状态

`ouroboros-organization-structure` 的抽象边界已经相对清楚，但它天然容易和安全、业务模型、审批流等模块交叠。维护者需要持续坚持“它提供组织上下文和身份来源，而不是安全真相来源”这一约束。
