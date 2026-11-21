package com.ouroboros.organizationstructure.profile;

/**
 * 组织架构人员档案运行时配置键
 */
public final class PersonnelProfileConfigurationKeys {

  private PersonnelProfileConfigurationKeys() {
  }

  public static final String TEMPLATE = "organization.structure.personnel-profile.template";
  public static final String RUNTIME_OVERRIDE = "organization.structure.personnel-profile.runtime-override";
  public static final String EDIT_LAYOUT = "organization.structure.personnel-profile.edit-layout";
  public static final String DISPLAY_LAYOUT = "organization.structure.personnel-profile.display-layout";

  /**
   * @deprecated 兼容旧的单布局配置键，等新页面切换完成后移除。
   */
  @Deprecated
  public static final String RUNTIME_LAYOUT = EDIT_LAYOUT;
}
