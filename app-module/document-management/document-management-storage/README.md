# Document Management Storage

文档领域 StorageService 由 BlobDocumentStorageService 适配到公共内容存储，每个文档模块实例持有自己的 BlobStore。部署引入公共 filesystem 或 s3 后端，不再为文档重复实现物理 IO。

`ouroboros.document-management.storage.type/options` 选择后端，`content.path-strategy` 选择 HASH / DATE / FLAT，`content.calculate-checksum` 控制文档领域 SHA-256。HASH 继续按 UUID 的前四位分层，历史 storagePath 布局不变。

读取返回延迟打开的流，由调用者关闭。输入流也由调用者关闭。copy 先完整写目标，move 在目标成功后删除源；跨对象操作非原子，失败传播。权限与网络错误不会返回“文件不存在”。

文档节点事务、上传回滚补偿和已提交删除重试由 LibraryContentService / DocumentContentCleanupService 与文档 Repository 管理。新增 doc_content_cleanup 模型沿原 DataModelScan 迁移，同节点表使用一个事务资源。

完整参数、旧配置转换、迁移步骤及供应商验证边界见[配置指南](../../../docs/guides/content-storage.md)。公共接口与扩展点见[storage 模块](../../../libs/storage/README.md)。自定义文档 StorageService Bean 的官方替换入口保留；新增通用存储介质优先扩展 BlobStoreFactory。
