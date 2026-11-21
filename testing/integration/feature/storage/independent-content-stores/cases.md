# 场景用例

| 目标 | 主要测试 |
| --- | --- |
| key、精确长度、流所有权 | CoreStorageContractTest / FileSystemBlobStoreTest |
| SPI、未知配置、新旧冲突、双实例 | StorageBindingTest / S3BlobStoreFactoryTest |
| 真实 S3 IO 与分片、重建客户端、凭据隔离 | S3MinioIT |
| 普通/分片/临时采用、并发同名、失败清理 | BlobUploadStorageServiceTest |
| 定时器反复触发、异常继续、关闭 | TimerCleanUpSchedulerTest |
| URL 编码、HTTP 调用、请求流关闭、模块实例 | DefaultFileAccessUrlGeneratorTest / StorageWiringTest |
| 开发 JAR 完成后的临时文件 ID、Logo 引用、成功/失败流关闭 | AppExtendJarStorageTest / AppLogoStorageTest |
| 文档适配、SQL事务、回滚与重试 | BlobDocumentStorageServiceTest / DocumentContentTransactionTest |
| 缺少基础设施、自定义服务和 Boot 事务管理器装配 | DocumentManagementAutoConfigurationTest |
| 资源部分失败、残留 sidecar、优先级与管理回归 | RuntimeOverrideResourceProviderTest / DefaultResourceServiceTest / RuntimeResourceManageServiceTest / RuntimeResourceControllerTest |
| 核心 Provider 接口替换默认 Bean、同一覆盖实例的读写与恢复 | ResourceProviderWiringTest |
| 缺失、过期、零执行、跳过、失败、重复及计数错误的报告必须阻止通过 | tests/test_verification.py / ReportVerificationTest |
| 编辑、删除、新增文件使旧源码指纹失效；新 Java 测试自动进入清单 | tests/test_verification.py / SourceVerificationTest |
