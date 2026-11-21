package com.ouroboros.security.authority.accesscontrol.setting;

import java.util.List;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.uischema.schema.SchemaNode;

public interface ACFilterSettingSchemaGenerator {
  boolean supports(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine);

  List<SchemaNode> generate(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine);
}
