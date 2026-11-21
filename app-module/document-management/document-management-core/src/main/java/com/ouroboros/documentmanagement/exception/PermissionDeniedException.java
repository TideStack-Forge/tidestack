package com.ouroboros.documentmanagement.exception;

import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.exception.BusinessException;

/**
 * 权限不足异常
 *
 * <p>当用户尝试执行操作但没有所需权限时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>用户尝试访问无权限的文档库</li>
 *   <li>用户尝试上传文件但没有 CREATE_FILE 权限</li>
 *   <li>用户尝试删除目录但没有 DELETE_DIR 权限</li>
 *   <li>用户尝试管理权限但没有 MANAGE 权限</li>
 * </ul>
 */
public class PermissionDeniedException extends BusinessException {

  public static final int ERROR_CODE = -4001;

  private final String userId;
  private final String resourceId;
  private final ResourceType resourceType;
  private final Permission requiredPermission;

  /**
   * 创建权限不足异常
   *
   * @param userId             用户 ID
   * @param resourceId         资源 ID
   * @param resourceType       资源类型
   * @param requiredPermission 所需权限
   */
  public PermissionDeniedException(
      String userId, String resourceId, ResourceType resourceType, Permission requiredPermission) {
    super(
        ERROR_CODE,
        String.format(
            "权限不足: 用户 %s 对 %s(%s) 缺少 %s 权限",
            userId, resourceType, resourceId, requiredPermission));
    this.userId = userId;
    this.resourceId = resourceId;
    this.resourceType = resourceType;
    this.requiredPermission = requiredPermission;
  }

  /**
   * 创建权限不足异常（简化版）
   *
   * @param userId       用户 ID
   * @param resourceId   资源 ID
   * @param resourceType 资源类型
   */
  public PermissionDeniedException(String userId, String resourceId, ResourceType resourceType) {
    super(ERROR_CODE, String.format("权限不足: 用户 %s 无权访问 %s(%s)", userId, resourceType, resourceId));
    this.userId = userId;
    this.resourceId = resourceId;
    this.resourceType = resourceType;
    this.requiredPermission = null;
  }

  /**
   * 创建权限不足异常（自定义消息）
   *
   * @param message 异常消息
   */
  public PermissionDeniedException(String message) {
    super(ERROR_CODE, message);
    this.userId = null;
    this.resourceId = null;
    this.resourceType = null;
    this.requiredPermission = null;
  }

  /**
   * 获取用户 ID
   */
  public String getUserId() {
    return userId;
  }

  /**
   * 获取资源 ID
   */
  public String getResourceId() {
    return resourceId;
  }

  /**
   * 获取资源类型
   */
  public ResourceType getResourceType() {
    return resourceType;
  }

  /**
   * 获取所需权限
   */
  public Permission getRequiredPermission() {
    return requiredPermission;
  }
}
