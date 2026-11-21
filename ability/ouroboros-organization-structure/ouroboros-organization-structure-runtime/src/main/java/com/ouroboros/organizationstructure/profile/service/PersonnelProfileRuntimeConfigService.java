package com.ouroboros.organizationstructure.profile.service;

import java.util.Map;

import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;

/**
 * 人员档案运行时配置服务
 */
public interface PersonnelProfileRuntimeConfigService {

  PersonnelProfileTemplateDefinition getTemplateDefinition();

  PersonnelProfileRuntimeOverrideConfig getRuntimeOverrideConfig();

  PersonnelProfileRuntimeLayoutConfig getRuntimeLayoutConfig();

  PersonnelProfileRuntimeLayoutConfig getDisplayLayoutConfig();

  PersonnelProfileTemplateDefinition getResolvedTemplateDefinition();

  PersonnelProfileTemplateDefinition getResolvedTemplateDefinition(boolean editMode);

  Map<String, Object> getResolvedEditSchema();

  Map<String, Object> getResolvedDetailSchema();

  void saveTemplateDefinition(PersonnelProfileTemplateDefinition definition, String updateBy);

  void saveRuntimeOverrideConfig(PersonnelProfileRuntimeOverrideConfig definition, String updateBy);

  void saveRuntimeLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy);

  void saveDisplayLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy);
}
