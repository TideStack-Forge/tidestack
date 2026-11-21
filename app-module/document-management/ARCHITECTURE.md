# 文档管理模块架构

本页说明组件分工与依赖。节点、服务、存储、API 和 UI 的共同设计正文见 [DESIGN.md](DESIGN.md)，使用入口见 [README.md](README.md)。

## 组件与边界

| 子模块 | 职责 |
| --- | --- |
| document-management-core | 领域接口、服务接口、Repository 与 StorageService 抽象 |
| document-management-service | 核心服务实现与业务门面编排 |
| document-management-persistence | 文档库和节点元数据的持久化实现 |
| document-management-storage | 文件内容存储实现 |
| document-management-infra-support | 基础设施接口实现 |
| document-management-webapp | Web API 与 UI Schema |
| document-management-autoconfigure | Spring Boot 装配 |

Web 层调用服务与门面；实现层依赖 core 定义的接口。元数据通过 Repository 保存，文件内容通过 StorageService 保存，节点通过 storagePath 关联内容。目录移动修改元数据父子关系，不要求同步移动文件内容。

详细模型、接口签名、生命周期、失败处理和扩展约束统一维护在 DESIGN.md；本页不再复制接口定义。缓存、对象存储、全文搜索等扩展是否已实现，需要按具体实现与配置核实，不能从旧示例推定启用状态。
