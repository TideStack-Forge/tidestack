# ouroboros-file-upload 设计文档

## 1. 概述

`ouroboros-file-upload` 是 Ouroboros 能力层中的统一文件上传与存储能力，负责把上传协议、文件生命周期、访问 URL 生成和存储后端适配组织成可复用服务。它解决的是“文件如何被上传、保存、访问和清理”的平台能力问题，而不是文档业务、附件审批或内容管理本身。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由五部分组成：

- `ouroboros-file-upload-core`
- `ouroboros-file-upload-storage`
- `ouroboros-file-upload-controller`
- `ouroboros-file-upload-runtime`
- `ouroboros-file-upload-fields`

其中：

- `core` 定义 `UploadStorageService`、`StorageManagerService`、`CleanUpScheduler` 以及文件路径、名称、访问地址生成协议。
- `controller` 提供统一 Web 上传入口抽象。
- 公共物理 IO 由 `libs/storage` 的 filesystem / s3 后端提供；`storage` 模块中的 `BlobUploadStorageService` 管理上传规则，`TimerCleanUpScheduler` 提供清理实现。core 保留接口、DTO 和既有 Generator 扩展点；依赖 storage-core 复用对象键 URL 编码，不包含上传存储实现。
- `runtime` 负责自动配置、默认控制器装配和下载访问接入。
- `fields` 负责把上传能力接入字段和值类型系统。

`runtime` 还提供配置属性。普通上传 runtime 与开发接收自动配置都是装配入口，可以依赖 `storage` 实现 JAR；控制器只依赖 core 的上传/读取接口。`storage` 不包含 HTTP 控制器或 Spring 自动配置，开发接收复用它时不会带入普通上传入口。

## 3. 核心模型与机制

### 3.1 UploadStorageService

`UploadStorageService` 是文件上传模块的顶层存储协议，统一临时文件、永久文件和分片上传流程。它定义的是“上传能力”，而不是某个具体存储实现。

### 3.2 StorageManagerService 与 CleanUpScheduler

`StorageManagerService` 负责读取侧的文件流访问；`CleanUpScheduler` 负责临时文件和分片文件的生命周期清理。两者共同补齐了“写入之后如何访问、如何回收”的运行时链路。

### 3.3 生成器协议

`FileAccessUrlGenerator`、`StorageFilePathGenerator`、`StorageFileNameGenerator` 负责文件访问地址和存储路径/名称生成策略。它们让存储布局保持可替换，而不被控制器和存储实现写死。

### 3.4 AbstractUploadController 与存储装配

`AbstractUploadController` 是统一 Web 上传入口，上传和下载均注入上传模块的同一个领域服务。`BlobUploadStorageService` 持有独立配置创建的 BlobStore；开发接收创建自己的实例。分包、配置及迁移见[公共存储](../../libs/storage/README.md)。

## 4. 上传到访问的数据流

当前实现中的关键链路是：

- 控制器层接受上传请求并转交 `UploadStorageService`。
- 上传域负责临时/永久、采用回执和过期责任；各后端负责内容 IO 与自身技术分片。
- 生成器协议负责生成路径、文件名和访问 URL。
- `StorageManagerService` 与下载控制器负责后续读取访问。
- `fields` 子模块把上传能力进一步投影到字段和值类型系统。

这意味着维护时要持续守住两层边界：

- 上传业务属于能力层，公共内容 IO 属于 libs Foundation 层。
- 文档、附件、审批等上层业务只消费该能力，不反向定义其内核。

## 5. 维护重点

维护该模块时，应优先关注：

- `UploadStorageService`、生成器协议和控制器之间的职责分离。
- 新存储介质实现公共 BlobStoreFactory SPI；命名与 URL 仍使用原 Generator builder。
- 文件生命周期管理应限制在清理与存储体系内，而不是散落到业务层。
- `fields` 子模块属于外围接入层，不要反向污染上传核心协议。
