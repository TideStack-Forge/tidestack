package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 名称重复异常
 *
 * <p>当创建或更新资源时，名称与已有资源冲突时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>创建文档库时，名称已存在</li>
 *   <li>更新文档库名称时，新名称与其他文档库冲突</li>
 *   <li>创建目录或文档时，同一父目录下已存在同名资源</li>
 *   <li>重命名或移动目录/文档时，目标位置已存在同名资源</li>
 * </ul>
 */
public class DuplicateNameException extends BusinessException {

  public static final int ERROR_CODE = -4004;

  private final String name;
  private final String parentId;

  /**
   * 创建名称重复异常
   *
   * @param name 重复的名称
   */
  public DuplicateNameException(String name) {
    super(ERROR_CODE, String.format("名称已存在: %s", name));
    this.name = name;
    this.parentId = null;
  }

  /**
   * 创建名称重复异常（指定父目录）
   *
   * @param name     重复的名称
   * @param parentId 父目录 ID
   */
  public DuplicateNameException(String name, String parentId) {
    super(ERROR_CODE, String.format("同一目录下已存在同名资源: %s (parentId=%s)", name, parentId));
    this.name = name;
    this.parentId = parentId;
  }

  /**
   * 创建名称重复异常（自定义消息）
   *
   * @param message 异常消息
   */
  public DuplicateNameException(String message, String name, String parentId) {
    super(ERROR_CODE, message);
    this.name = name;
    this.parentId = parentId;
  }

  /**
   * 获取重复的名称
   */
  public String getName() {
    return name;
  }

  /**
   * 获取父目录 ID
   */
  public String getParentId() {
    return parentId;
  }
}
