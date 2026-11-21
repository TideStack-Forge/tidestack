# AppModule Authority Bridge

本文维护模块组织与权限投影的当前代码边界；决定、备选方案与理由仅由[ADR-004](../../adr/ADR-004-appmodule-authority-bridge-band.md)维护。

| 类型 | 当前职责与安全契约 |
| --- | --- |
| AuthorityTreeNode | implements Authority 与 TreeNode，承担权限语义及模块树组织 |
| ArrangedModuleAuthority | implements Authority，按模块组织投影权限及 AccessControlDefine |
| PlacementModuleAuthority | implements Authority，提供占位权限与空访问控制定义 |
| AuthoritiesAppModule | implements AppModule，包装 List<Authority>；不实现 Authority，也不直接暴露 AccessControlDefine |

前三者在[app-module-model-core](../../../core/ouroboros-app-module-model/ouroboros-app-module-model-core/src/main/java/com/ouroboros/appmodule/model/)，AuthoritiesAppModule 在[app-module-model-procode](../../../core/ouroboros-app-module-model/ouroboros-app-module-model-procode/src/main/java/com/ouroboros/appmodule/model/)。同一包族跨模块，不能把物理位置直接当作 P0-3 赋层。

AuthorityTreeNode 的权限与 TreeNode 两侧不变量均须保留；不得为通过层检查把授权语义改为普通 DTO。当前四个类型及其内部类、core/runtime/procode 三个既有投影模块均已归 Bridge；不得用该分类为未来模块自动豁免分层。appmodule.dev、runtime 与 bare root 的具体边界见[P0-3 当前实现](p0-3-family-splitting.md)。

修改这些类型时同时检查权限树、模块布局和架构测试；单次 dry-run、绿态恢复及实施进度不在本页另记一份状态。
