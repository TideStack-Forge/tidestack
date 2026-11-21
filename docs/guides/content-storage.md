# 配置内容存储

部署模块引入 `ouroboros-storage-filesystem` 和/或 `ouroboros-storage-s3`，然后在消费者自己的配置前缀下选择后端。配置在启动期绑定；改变后端或位置需要重启并按迁移步骤处理已有数据。

## 独立配置

| 消费者 | 新配置前缀 | 默认 / 旧配置转换 |
| --- | --- | --- |
| 普通上传与下载 | `ouroboros.upload.storage` | `storage-type=FILESYSTEM`、`file-system.root-path` |
| 开发接收 | `ouroboros.dev.upload.storage` | DevelopWorkspaceContext.devRootDir，独立 Bean `devAppUploadStorageService` |
| 文档 | `ouroboros.document-management.storage` | `storage-type=filesystem`、`storage.filesystem.base-path` |
| 资源运行时覆盖 | `ouroboros.resource.runtime-override.storage` | `runtime-override-base-directory` |

这些前缀各自创建实例。需要物理内容隔离时也应分别配置目录、bucket 或 key-prefix；独立实例不等于自动划分物理空间。

```yaml
ouroboros:
  upload:
    storage:
      type: s3
      options:
        endpoint: ${UPLOAD_S3_ENDPOINT}
        region: us-east-1
        bucket: uploads
        path-style-access: true
        credentials-mode: static
        access-key: ${UPLOAD_S3_ACCESS_KEY}
        secret-key: ${UPLOAD_S3_SECRET_KEY}
        key-prefix: runtime/
  dev:
    upload:
      storage:
        type: filesystem
        options:
          root-directory: ./workspace/dev
  document-management:
    storage:
      type: filesystem
      options:
        root-directory: ./data/documents
    content:
      path-strategy: HASH
      calculate-checksum: true
  resource:
    runtime-override:
      storage:
        type: filesystem
        options:
          root-directory: ./data/resources
```

S3 bucket 需预先建立。生产凭据通过部署密钥系统注入。完整参数见[存储模块](../../libs/storage/README.md#后端配置)。对象 key 可直接包含目录路径；HTTP 地址按路径段编码，业务持久引用仍使用原键或其原访问 URL。

## 旧配置与 Java 接口迁移

缺少新 type/options 时，将旧配置在所属自动装配处转换到同一 factory。显式提供新 type/options 后，继续设置旧位置/类型会报冲突，必须移除旧设置；属性对象自带的默认值不算显式配置。旧云类型枚举原先没有可用实现，不自动映射。

文档旧 `storage.filesystem.path-strategy/calculate-checksum` 在新内容策略缺省时继续转换；新策略使用 `content.*`。普通正式上传保持原 DTO 与 URL 生成入口。旧实例绑定的临时文件、分片与到期状态不会自动导入新实现，切换前须完成/取消在途上传；开发包接收也需完成旧临时文件消费。

分片会话 ID 与完成响应的 `tempFile` 是不同标识。开发 JAR 接收使用 `tempFile` 读取内容；Logo 仍以 `tempFile + 扩展名` 作为表单值，保存时拆出临时文件 ID。两者继续进入原 VCS 流程，读取方负责关闭内容流。

已移除旧 `ouroboros-file-upload-filesystem`、`FileSystemUploadStorageService`、文档 LocalFileSystemStorageService 和 RuntimeOverrideFileResourceProvider，仓库内消费者已迁移。外部 Java 扩展须重新编译并使用新 factory 或原领域端口。本文不声称完成仓库外二进制兼容验证。

原来直接 `new FileSystemUploadStorageService(rootPath)` 的应用，改为构造器注入 `com.ouroboros.upload.UploadStorageService`，复用自动装配的 `BlobUploadStorageService`。在应用的部署装配中显式加入后端实现，版本由平台 BOM 管理：

```xml
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-storage-filesystem</artifactId>
</dependency>
```

宿主提供运行依赖的扩展工程可按其装配约定使用 `provided`，但最终运行时必须包含该实现。原构造参数 `rootPath` 改由 `ouroboros.upload.storage.type=filesystem` 与 `ouroboros.upload.storage.options.root-directory` 提供；检查所有消费者是否应共享普通上传存储，避免把各自的独立目录静默合并。不要通过恢复旧 artifact 或自行创建第二套上传服务绕过正式 factory。

## 切换物理存储

改变实现但保留原目录/key 时，正式文件布局继续可读。改变物理位置时，采用维护窗口保键迁移：

1. 停止写入和相关清理任务，备份原内容与业务数据库，完成/取消旧在途上传。
2. 枚举正式内容；上传临时技术目录单独处理，资源 sidecar/index 与内容一起纳入清单。
3. 按原 key 复制，逐项核对大小和 SHA-256，检查非法路径、同名冲突、Unicode/空格、旧 URL、文档 storagePath 和资源覆盖。
4. 验证零漏项后切换配置，重启并执行新增、读取、删除、覆盖和恢复默认验证。
5. 回滚前停写，将切换后的变更按清单回迁并核对，再恢复旧配置；不得直接切回导致新写内容不可见。

默认没有多后端逐个试读、在线双写或通用历史路由。不可控外部绝对 URL 需要所属业务迁移引用，或者保留原地址服务。此过程尚未针对生产数据实际演练，不代表已完成部署迁移。

## 清理与故障处理

上传域在后端保存临时内容、到期清单、采用回执和会话期限，每小时扫描；临时内容默认保留一天。失败保留责任，下轮重试。同名规则使用部署中现有 DistributedLockManager；共享物理空间的多进程须配置现有分布式锁实现。

文档新增 `doc_content_cleanup` 模型，沿原 DataModelScan 与迁移机制部署；上线前检查表已迁移并与节点表位于同一事务资源。删除节点成功但物理删除失败时，查看该模型的 attempts、lastError、nextAttemptAt；修复存储权限/网络后由文档调度重试。可恢复库中仍引用的内容不会删除。

看到 multipart COMPLETING 或完成结果不确定时，先核对最终对象与会话回执，保留技术内容，禁止盲目取消或覆盖。资源 sidecar 更新失败可重试原上传，恢复默认可重复执行。S3 原生 multipart abort 失败时，异常会保留原因，运维可结合 bucket 生命周期规则回收失联原生上传；配置规则前确认其作用范围。

文档上传与补偿登记之间硬退出可能产生孤儿对象，需要文档维护核对。资源内容与 sidecar 之间硬退出也可能部分完成。公共存储没有跨数据库/多对象事务保证。

## 验证范围

filesystem 与固定源码 MinIO 已有可复跑场景；NAS 挂载、AWS 云账号及 OSS/COS/OBS 均需实际环境验收。候选厂商使用 s3 type 和其实际 endpoint/region/寻址模式；不满足基础合同则单独实现厂商后端。数据库 BLOB/GridFS、SFTP/WebDAV 暂不实现。
