# Ouroboros Version Control System 设计文档

## 1. 概述

`ouroboros-dev-version-control-system` 是开发平台中的版本控制集成模块，负责把底层 Git 操作提升为面向平台应用的统一 VCS 抽象。它解决的是开发资产的版本治理问题，而不是整个开发平台的产品逻辑。

## 2. 模块位置与依赖

从 `pom.xml` 可见，该模块是单体 `jar` 模块，主要依赖：

- `common-all`
- `common-cache`
- `common-dev`
- `ouroboros-util`
- `org.eclipse.jgit`

这种依赖结构说明它位于开发基础设施侧，围绕缓存、开发上下文和 Git 实现构建统一的版本控制服务。

## 3. 核心模型与机制

### 3.1 VCSManager

`VCSManager` 是单应用版本控制管理器，负责对具体应用执行文件读取、提交、移动、删除、锁定等操作。

### 3.2 VCSManagerCenter

`VCSManagerCenter` 负责管理多个应用的 VCS 管理器，是开发平台按应用隔离版本空间的关键入口。

### 3.3 VCSAction

`VCSAction` 把创建、更新、删除、移动和批量操作抽象为统一动作模型，使高层调用者不必直接操作底层 Git 细节。

### 3.4 VCSFile

`VCSFile` 是统一文件抽象，用来屏蔽具体后端和底层仓库实现差异。

## 4. 当前实现与版本读取

正式接口见 [VCSManager](src/main/java/com/ouroboros/vcs/VCSManager.java)、[VersioningManager](src/main/java/com/ouroboros/vcs/versioning/VersioningManager.java)和 [VCSFile](src/main/java/com/ouroboros/vcs/file/VCSFile.java)。

调用链为 VCSManagerCenter → LocalGitVCSManager → GitManager → JGitClient。平台侧消费统一的文件与动作接口；底层 Git 实现负责版本库操作，不反向承担平台应用生命周期。

VCSManager 的 getFile(relativePath) 读取工作区；getFile(relativePath, revision) 以及分支、标签方法读取指定版本。DefaultVCSFile 以 revision 是否为空区分工作区与历史文件，历史内容经 VersioningManager 读取，不通过切换当前工作区来模拟。VersioningManager 同时定义分支、标签、远程同步、差异与历史查询接口；旧稿中“只支持 HEAD、尚需增加分支读取”的描述不再适用。

## 5. 扩展与维护约束

新能力优先落在既有 VCSManager、VCSAction、VCSFile、VersioningManager 抽象和正式注册入口。路径校验、锁与异常语义沿用当前实现，不在上层另建 Git 旁路。工作区与历史读取要分别覆盖文件、目录、缺失 revision 和失败行为。

接口存在只证明能力定义；实际后端支持、远程认证及并发行为以相应实现和目标环境验证为准。一次性分析、增强提案和演示结果由任务与 Git 历史保存。
