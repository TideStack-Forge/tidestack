package com.ouroboros.workflow.approval.external.runtime.web;

/**
 * 映射管理独立权限点（设计文档 §安全与权限：不复用普通流程发起权限）。
 *
 * <p>Phase 3 最小落地：权限点常量 + 登录用户检查 + 管理员角色兜底（见
 * {@link EngineMappingPermissionGuard} 与 Phase 3 计划决策日志 D-5）；
 * RBAC 权限点正式接入后各端点按本常量标注，调用点零改动。
 */
public final class EngineMappingPermissions {

  public static final String MAPPING_VIEW = "workflow.external.mapping.view";
  public static final String MAPPING_EDIT = "workflow.external.mapping.edit";
  public static final String MAPPING_VALIDATE = "workflow.external.mapping.validate";
  public static final String MAPPING_ACTIVATE = "workflow.external.mapping.activate";
  public static final String MAPPING_PAUSE = "workflow.external.mapping.pause";
  public static final String MAPPING_RETIRE = "workflow.external.mapping.retire";
  public static final String CONNECTION_HEALTH = "workflow.external.connection.health";

  /** 管理员角色兜底（RBAC 权限点体系就绪前，管理员可执行全部映射管理操作）。 */
  public static final String ADMIN_ROLE = "ADMIN";

  private EngineMappingPermissions() {
  }
}
