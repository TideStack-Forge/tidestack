package com.ouroboros.workflow.approval.web.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流 HTTP 接口的权限元数据覆盖校验。
 *
 * <p>{@code AuthorizeCheckFilterSupplier} 对不在任何 authority {@code apis} 中的请求路径默认拒绝，
 * 因此"controller 上存在某个 mapping，但没有任何 authority 覆盖它"就等于该接口对普通角色恒 403。
 * 本测试机械枚举两个 controller 包下的全部 mapping，与随包发布的 authority 元数据比对，
 * 防止新增接口时漏登记、或删除接口后残留死条目。
 */
class ApprovalWorkflowApiAuthorityCoverageTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

  private static final List<String> CONTROLLER_PACKAGES = List.of(
      "com.ouroboros.workflow.approval.web",
      "com.ouroboros.workflow.approval.runtime");

  /**
   * 审批流 authority 有意声明、但实现不落在审批流 controller 包内的接口。
   *
   * <p>两类：① 跨模块引用（发起页需要组织架构树/员工查询）；② 由 approval-workflow-runtime
   * 提供、本模块测试类路径不可见的接口。加入此处即显式承认"不由本模块的 mapping 校验覆盖"。
   */
  private static final List<String> CROSS_MODULE_AUTHORITY_APIS = List.of(
      "GET /api/organization-structure/tree",
      "GET /api/organization-structure/{id}/employee",
      "GET /api/approval-workflow/dynamic-form/bindings",
      "GET /api/approval-workflow/dynamic-form/history");

  private static final List<String> AUTHORITY_RESOURCES = List.of(
      "META-INF/ouroboros/metadata/approvalWorkflow/center.authority.json",
      "META-INF/ouroboros/metadata/approvalWorkflow/center/start.authority.json",
      "META-INF/ouroboros/metadata/systemSetting/approvalWorkflow/definition.authority.json",
      "META-INF/ouroboros/metadata/systemSetting/approvalWorkflow/definition/statistics.authority.json");

  @Test
  void everyControllerMappingIsCoveredBySomeAuthority() throws Exception {
    List<String> authorityApis = readAuthorityApis();
    assertFalse(authorityApis.isEmpty(), "authority 元数据应至少声明一条接口");

    Map<String, String> uncovered = new LinkedHashMap<>();
    int mappingCount = 0;
    for (Class<?> controller : findControllers()) {
      for (String mapping : mappingsOf(controller)) {
        mappingCount++;
        if (authorityApis.stream().noneMatch(api -> covers(api, mapping))) {
          uncovered.put(mapping, controller.getSimpleName());
        }
      }
    }

    assertTrue(mappingCount > 0, "应至少扫描到一个 controller mapping");
    assertTrue(uncovered.isEmpty(),
        "以下接口没有任何 authority 覆盖，对普通角色将恒 403（补进 authority apis 或删除端点）: " + uncovered);
  }

  @Test
  void everyAuthorityApiMatchesSomeControllerMapping() throws Exception {
    List<String> mappings = new ArrayList<>();
    for (Class<?> controller : findControllers()) {
      mappings.addAll(mappingsOf(controller));
    }

    List<String> orphans = readAuthorityApis().stream()
        .filter(api -> !CROSS_MODULE_AUTHORITY_APIS.contains(api))
        .filter(api -> mappings.stream().noneMatch(mapping -> apiCoversMapping(api, mapping)))
        .toList();

    assertTrue(orphans.isEmpty(), "以下 authority 条目没有任何 controller mapping 对应，属死元数据: " + orphans);
  }

  /** 单个 authority 模式是否覆盖某个具体 mapping（method 必须一致，path 按 Ant 语义匹配）。 */
  private static boolean covers(String authorityApi, String mapping) {
    return apiCoversMapping(authorityApi, mapping);
  }

  private static boolean apiCoversMapping(String authorityApi, String mapping) {
    int authoritySpace = authorityApi.indexOf(' ');
    int mappingSpace = mapping.indexOf(' ');
    if (authoritySpace < 0 || mappingSpace < 0) {
      return false;
    }
    if (!authorityApi.substring(0, authoritySpace).equalsIgnoreCase(mapping.substring(0, mappingSpace))) {
      return false;
    }
    return ANT_PATH_MATCHER.match(
        authorityApi.substring(authoritySpace + 1), mapping.substring(mappingSpace + 1));
  }

  private static List<String> mappingsOf(Class<?> controller) {
    RequestMapping typeMapping =
        AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    if (typeMapping == null) {
      return List.of();
    }
    List<String> prefixes = pathsOf(typeMapping);
    List<String> mappings = new ArrayList<>();
    for (Method method : controller.getDeclaredMethods()) {
      RequestMapping methodMapping =
          AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
      if (methodMapping == null) {
        continue;
      }
      for (RequestMethod verb : verbsOf(methodMapping)) {
        for (String prefix : prefixes) {
          for (String path : pathsOf(methodMapping)) {
            mappings.add(verb.name() + " " + join(prefix, path));
          }
        }
      }
    }
    return mappings;
  }

  private static List<RequestMethod> verbsOf(RequestMapping mapping) {
    return mapping.method().length == 0 ? List.of(RequestMethod.GET) : List.of(mapping.method());
  }

  private static List<String> pathsOf(RequestMapping mapping) {
    List<String> paths = new ArrayList<>();
    for (String path : mapping.path()) {
      if (!path.isBlank()) {
        paths.add(path);
      }
    }
    for (String value : mapping.value()) {
      if (!value.isBlank()) {
        paths.add(value);
      }
    }
    return paths.isEmpty() ? List.of("") : paths;
  }

  private static String join(String prefix, String path) {
    String normalizedPrefix = prefix.endsWith("/") && prefix.length() > 1
        ? prefix.substring(0, prefix.length() - 1) : prefix;
    String normalizedPath = path.isEmpty() ? "" : (path.startsWith("/") ? path : "/" + path);
    String joined = normalizedPrefix + normalizedPath;
    return joined.isEmpty() ? "/" : joined;
  }

  private static List<Class<?>> findControllers() throws IOException {
    CachingMetadataReaderFactory readerFactory =
        new CachingMetadataReaderFactory(new PathMatchingResourcePatternResolver());
    List<Class<?>> controllers = new ArrayList<>();
    for (String pkg : CONTROLLER_PACKAGES) {
      Resource[] resources = new PathMatchingResourcePatternResolver()
          .getResources("classpath*:" + pkg.replace('.', '/') + "/**/*.class");
      for (Resource resource : resources) {
        MetadataReader reader = readerFactory.getMetadataReader(resource);
        if (!reader.getAnnotationMetadata().hasAnnotation(RestController.class.getName())
            && !reader.getAnnotationMetadata().hasAnnotation(Controller.class.getName())) {
          continue;
        }
        Class<?> controller = load(reader.getClassMetadata().getClassName());
        if (controller != null && !controllers.contains(controller)) {
          controllers.add(controller);
        }
      }
    }
    return controllers;
  }

  private static Class<?> load(String className) {
    try {
      return Class.forName(className, false, ApprovalWorkflowApiAuthorityCoverageTest.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  private static List<String> readAuthorityApis() throws IOException {
    List<String> apis = new ArrayList<>();
    for (String resource : AUTHORITY_RESOURCES) {
      JsonNode root = OBJECT_MAPPER.readTree(read(resource));
      root.path("apis").forEach(api -> apis.add(api.asText()));
    }
    return apis;
  }

  private static String read(String resource) throws IOException {
    try (InputStream in =
        ApprovalWorkflowApiAuthorityCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("authority 元数据不在 classpath: " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
