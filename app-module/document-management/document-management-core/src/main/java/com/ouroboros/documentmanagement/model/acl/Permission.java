package com.ouroboros.documentmanagement.model.acl;

/**
 * 权限枚举
 */
public enum Permission {
  // --- 作用于文件和目录 (BOTH) ---
  RENAME_MOVE("rename_move", "重命名/移动", PermissionScope.BOTH),
  DELETE_DIR("delete_dir", "删除目录", PermissionScope.DIRECTORY),
  DELETE_FILE("delete_file", "删除文件", PermissionScope.FILE),
  MANAGE("manage", "管理", PermissionScope.BOTH),
  //  SHARE("share", "共享", PermissionScope.BOTH),
  // 目录上的 download 表示目录中的文件都可以下载
  DOWNLOAD("download", "下载", PermissionScope.BOTH),
  // 查看文件和浏览目录
  VIEW("view", "查看", PermissionScope.BOTH),

  // --- 作用于目录 (DIRECTORY) ---
  CREATE_FILE("create_file", "创建文件", PermissionScope.DIRECTORY),
  CREATE_DIR("create_dir", "创建目录", PermissionScope.DIRECTORY),
  // navigate 表示仅可以进入目录，但不能浏览目录
  // （用于过路，比如某个用户具有深层目录中的文件权限，需要显示深层目录的每个节点的时候要赋予这个虚拟权限）
  NAVIGATE("navigate", "导航", PermissionScope.DIRECTORY);

  private final String code;
  private final String displayName;
  private final PermissionScope scope;

  Permission(String code, String displayName, PermissionScope scope) {
    this.code = code;
    this.displayName = displayName;
    this.scope = scope;
  }

  // 新增的 getter 方法
  public PermissionScope getScope() {
    return scope;
  }
  // ... (保留 getCode(), getDisplayName(), fromCode() 等方法)
  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static Permission fromCode(String code) {
    for (Permission permission : values()) {
      if (permission.code.equalsIgnoreCase(code)) {
        return permission;
      }
    }
    throw new IllegalArgumentException("Invalid permission code: " + code);
  }
  // 辅助枚举，定义权限可以作用于的对象类型
  public enum PermissionScope {
    // 只能用于 DocumentNode
    FILE,
    // 只能用于 DirectoryNode
    DIRECTORY,
    // 既可用于 DocumentNode，也可用于 DirectoryNode
    BOTH;
  }
}
