# 开发态版本控制系统

`ouroboros-dev-version-control-system` 提供应用开发文件的访问、变更动作和版本查询抽象。开发服务通过这些入口维护工作区文件；Git 适配由模块内 JGit 实现承接。

## 入口

- [VCSManager](src/main/java/com/ouroboros/vcs/VCSManager.java)：访问文件、创建/更新/移动/删除动作、批量操作、锁及历史版本读取。
- [JGitClient](src/main/java/com/ouroboros/vcs/versioning/git/JGitClient.java)：Git 适配实现。
- [VCSFileInspector](src/main/java/com/ouroboros/vcs/file/inspector/VCSFileInspector.java)：文件描述扩展接口；开发模块中的接入方式见[开发模块指南](../../docs/guides/development-module-guide.md)。

具体参数、返回值与变更动作的生命周期以源接口为准。使用历史读取和文件锁时，应区分工作区文件、分支/标签/提交版本以及调用方的异常处理。
