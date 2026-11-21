package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.PlatformException;

/**
 * 资源不存在异常
 *
 * <p>当查询的资源（文档库、目录、文档、权限条目等）不存在时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>根据 ID 查询文档库、目录或文档时，资源不存在</li>
 *   <li>根据名称查询文档库时，资源不存在</li>
 *   <li>查询权限条目时，权限条目不存在</li>
 * </ul>
 */
public class ResourceNotFoundException extends PlatformException {

  public static final int ERROR_CODE = -6001;

  private final String resourceType;
  private final String resourceId;

  /**
   * 创建资源不存在异常
   *
   * @param resourceType 资源类型（如 "DocumentLibrary", "Directory", "Document"）
   * @param resourceId   资源 ID
   */
  public ResourceNotFoundException(String resourceType, String resourceId) {
    super(ERROR_CODE, String.format("%s 不存在: %s", resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  /**
   * 创建资源不存在异常（带原因）
   *
   * @param resourceType 资源类型
   * @param resourceId   资源 ID
   * @param cause        原因
   */
  public ResourceNotFoundException(String resourceType, String resourceId, Throwable cause) {
    super(ERROR_CODE, String.format("%s 不存在: %s", resourceType, resourceId), cause);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  /**
   * 创建资源不存在异常（自定义消息）
   *
   * @param message 异常消息
   */
  public ResourceNotFoundException(String message) {
    super(ERROR_CODE, message);
    this.resourceType = null;
    this.resourceId = null;
  }

  /**
   * 获取资源类型
   */
  public String getResourceType() {
    return resourceType;
  }

  /**
   * 获取资源 ID
   */
  public String getResourceId() {
    return resourceId;
  }
}
