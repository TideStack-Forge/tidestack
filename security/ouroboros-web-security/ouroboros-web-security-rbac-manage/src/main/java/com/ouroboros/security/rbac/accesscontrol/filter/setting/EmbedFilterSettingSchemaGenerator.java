package com.ouroboros.security.rbac.accesscontrol.filter.setting;

import java.util.List;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.setting.ACFilterSettingSchemaGenerator;
import com.ouroboros.uischema.schema.SchemaNode;

@Priority(999)
public class EmbedFilterSettingSchemaGenerator implements ACFilterSettingSchemaGenerator {

  @Override
  public boolean supports(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    return ObjectUtils.isNotEmpty(accessControlFilterDefine.getSettingSchema());
  }

  @Override
  public List<SchemaNode> generate(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    return accessControlFilterDefine.getSettingSchema();
  }
}
