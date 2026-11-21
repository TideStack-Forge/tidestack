package com.ouroboros.security.rbac.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.setting.ACFilterSettingSchemaGenerator;
import com.ouroboros.security.authority.accesscontrol.setting.ACSettingSchemaGenerator;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.SPI;

public class AccessControlDefineUtils {
  private static List<ACSettingSchemaGenerator> acSettingGenerator = loadAccessControlSettingGenerators();

  private static List<ACFilterSettingSchemaGenerator> filterSettingGenerator = loadFilterSettingGenerators();

  private static List<ACSettingSchemaGenerator> loadAccessControlSettingGenerators() {
    return SPI.getCachedReversedServiceStream(ACSettingSchemaGenerator.class)
        .collect(Collectors.toList());
  }

  private static List<ACFilterSettingSchemaGenerator> loadFilterSettingGenerators() {
    return SPI.getCachedReversedServiceStream(ACFilterSettingSchemaGenerator.class)
        .collect(Collectors.toList());
  }

  static void overrideGeneratorsForTest(
      List<ACSettingSchemaGenerator> accessControlSettingGenerators,
      List<ACFilterSettingSchemaGenerator> accessControlFilterSettingGenerators
  ) {
    acSettingGenerator = accessControlSettingGenerators;
    filterSettingGenerator = accessControlFilterSettingGenerators;
  }

  static void resetGeneratorsForTest() {
    acSettingGenerator = loadAccessControlSettingGenerators();
    filterSettingGenerator = loadFilterSettingGenerators();
  }

  public static List<SchemaNode> generateAccessControlSettingSchema(Authority authority) {
    var acDefine = authority.getAccessControlDefine();
    if (acDefine instanceof AccessControlDefine.EmptyAccessControlDefine) {
      return Collections.emptyList();
    }

    var schemaList = new ArrayList<SchemaNode>();

    // 默认生成字段控制
    acSettingGenerator.stream().filter(generator -> generator.supports(acDefine))
        .findFirst()
        .map(generator -> generator.generate(acDefine))
        .ifPresent(schemaList::addAll);

    // 默认生成过滤器控制
    var filterDefines = Optional.ofNullable(acDefine.getFilterDefine()).orElseGet(Collections::emptyList);
    filterDefines.stream()
        .map(filterDefine -> buildFilterSettingSchema(acDefine, filterDefine))
        .filter(ObjectUtils::isNotEmpty)
        .forEach(schemaList::addAll);

    return schemaList;
  }

  private static List<SchemaNode> buildFilterSettingSchema(AccessControlDefine acDefine, AccessControlFilterDefine filterDefine) {
    return filterSettingGenerator.stream()
        .filter(generator -> generator.supports(acDefine, filterDefine))
        .findFirst()
        .map(generator -> generator.generate(acDefine, filterDefine))
        .orElseGet(Collections::emptyList);
  }
}
