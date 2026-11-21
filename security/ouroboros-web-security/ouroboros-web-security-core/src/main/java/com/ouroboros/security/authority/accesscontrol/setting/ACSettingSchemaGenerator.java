package com.ouroboros.security.authority.accesscontrol.setting;

import java.util.List;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.uischema.schema.SchemaNode;

public interface ACSettingSchemaGenerator {
  boolean supports(AccessControlDefine accessControlDefine);

  List<SchemaNode> generate(AccessControlDefine accessControlDefine);
}
