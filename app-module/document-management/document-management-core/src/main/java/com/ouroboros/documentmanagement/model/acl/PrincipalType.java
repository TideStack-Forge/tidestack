package com.ouroboros.documentmanagement.model.acl;

import com.ouroboros.uischema.schema.SchemaNode;

import java.util.Optional;

/**
 * 权限控制主体类型接口
 *
 * <p>定义权限控制中的主体类型（如用户、用户组、角色、部门等），
 * 每个主体类型都关联一个 {@link PrincipalResolver} 用于解析主体为用户列表。
 *
 * <p>实现示例：
 * <pre>
 * public class UserPrincipalType implements PrincipalType {
 *     {@literal @}Override
 *     public String getName() {
 *         return "USER";
 *     }
 *
 *     {@literal @}Override
 *     public String getDisplayName() {
 *         return "用户";
 *     }
 *
 *     {@literal @}Override
 *     public PrincipalResolver getResolver() {
 *         return new UserPrincipalResolver();
 *     }
 *
 *     {@literal @}Override
 *     public Optional&lt;SchemaNode&gt; getConfigUISchema() {
 *         return Optional.empty(); // 无需配置UI
 *     }
 * }
 * </pre>
 */
public interface PrincipalType {

  /**
   * 获取主体类型名称（唯一标识）
   *
   * @return 主体类型名称，如 "USER"、"GROUP"、"ROLE"
   */
  String getName();

  /**
   * 获取主体类型的显示名称
   *
   * @return 显示名称，如 "用户"、"用户组"、"角色"
   */
  String getDisplayName();

  /**
   * 获取该主体类型对应的解析器
   *
   * @return Principal 解析器实例
   */
  PrincipalResolver getResolver();

  /**
   * 获取该主体类型的配置用户界面架构
   *
   * <p>返回用于前端配置该主体类型的 UI Schema 定义。
   * 如果主体类型不需要额外的配置界面，返回 {@link Optional#empty()}。
   *
   * <p>使用场景示例：
   * <ul>
   *   <li>用户组类型可能需要配置"选择用户组"的下拉框或树形选择器</li>
   *   <li>角色类型可能需要配置"选择角色"的多选框</li>
   *   <li>用户类型通常不需要额外配置，直接输入用户 ID 即可</li>
   * </ul>
   *
   * @return UI Schema 配置节点，如果不需要配置界面则返回 empty
   */
  Optional<SchemaNode> getConfigUISchema();
}
