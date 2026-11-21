package com.ouroboros.businesslog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;

class BusinessOperationTemplateCenterTest {

  @Test
  void shouldPreferDatabaseThenFileThenJarWhenMergingTemplates() {
    Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource =
        new EnumMap<>(BusinessOperationTemplateSource.class);
    templatesBySource.put(BusinessOperationTemplateSource.DATABASE,
        Collections.singletonList(template("POST", "/orders", "数据库创建订单")));
    templatesBySource.put(BusinessOperationTemplateSource.FILE, Arrays.asList(
        template("POST", "/orders", "文件创建订单"),
        template("PUT", "/orders/{id}", "文件更新订单")
    ));
    templatesBySource.put(BusinessOperationTemplateSource.JAR, Arrays.asList(
        template("POST", "/orders", "Jar创建订单"),
        template("PUT", "/orders/{id}", "Jar更新订单"),
        template("DELETE", "/orders/{id}", "Jar删除订单")
    ));
    Map<String, BusinessOperationTemplateSourceView> merged = BusinessOperationTemplateCenter.merge(templatesBySource);

    assertEquals(BusinessOperationTemplateSource.DATABASE, merged.get("POST /orders").getSource());
    assertEquals(BusinessOperationTemplateSource.FILE, merged.get("PUT /orders/{id}").getSource());
    assertEquals(BusinessOperationTemplateSource.JAR, merged.get("DELETE /orders/{id}").getSource());

    assertIterableEquals(Arrays.asList(
        BusinessOperationTemplateSource.DATABASE,
        BusinessOperationTemplateSource.FILE,
        BusinessOperationTemplateSource.JAR
    ), merged.get("POST /orders").getAvailableSources());
    assertIterableEquals(Arrays.asList(
        BusinessOperationTemplateSource.FILE,
        BusinessOperationTemplateSource.JAR
    ), merged.get("POST /orders").getOverridesSources());
    assertTrue(merged.get("POST /orders").isControlled());
  }

  @Test
  void shouldUseExplicitSourceForCustomNamedProvider() {
    BusinessOperationTemplateProvider customProvider = provider(
        BusinessOperationTemplateSource.DATABASE,
        template("POST", "/custom", "自定义来源")
    );

    Map<String, BusinessOperationTemplateSourceView> merged =
        BusinessOperationTemplateCenter.mergeProviders(Stream.of(customProvider));

    assertEquals(BusinessOperationTemplateSource.DATABASE, merged.get("POST /custom").getSource());
  }

  @Test
  void shouldKeepTemplatesFromEveryProviderOfTheSameSource() {
    Map<String, BusinessOperationTemplateSourceView> merged = BusinessOperationTemplateCenter.mergeProviders(Stream.of(
        provider(BusinessOperationTemplateSource.FILE, template("POST", "/orders", "创建订单")),
        provider(BusinessOperationTemplateSource.FILE, template("PUT", "/orders/{id}", "更新订单"))
    ));

    assertEquals(2, merged.size());
    assertTrue(merged.containsKey("POST /orders"));
    assertTrue(merged.containsKey("PUT /orders/{id}"));
  }

  @Test
  void shouldLetLaterTemplateOverrideDuplicateKeysWithinTheSameSource() {
    Map<String, BusinessOperationTemplateSourceView> merged = BusinessOperationTemplateCenter.mergeProviders(Stream.of(
        provider(BusinessOperationTemplateSource.FILE, template("POST", "/orders", "第一个")),
        provider(BusinessOperationTemplateSource.FILE, template("post", "/orders", "第二个"))
    ));

    assertEquals(1, merged.size());
    assertEquals("第二个", merged.get("POST /orders").getOperation());
  }

  private BusinessOperationTemplateProvider provider(BusinessOperationTemplateSource source,
                                                      BusinessOperationTemplate... templates) {
    return new BusinessOperationTemplateProvider() {
      @Override
      public BusinessOperationTemplateSource getSource() {
        return source;
      }

      @Override
      public List<BusinessOperationTemplate> getAllTemplates() {
        return Arrays.asList(templates);
      }
    };
  }

  private BusinessOperationTemplate template(String httpMethod, String url, String operation) {
    return new BusinessOperationTemplate() {
      @Override
      public String getHttpMethod() {
        return httpMethod;
      }

      @Override
      public String getUrl() {
        return url;
      }

      @Override
      public String getModuleName() {
        return "订单";
      }

      @Override
      public String getOperation() {
        return operation;
      }

      @Override
      public String getLevel() {
        return "INFO";
      }

      @Override
      public String getBusinessKeyName() {
        return "id";
      }

      @Override
      public String getSummaryTemplate() {
        return operation;
      }

      @Override
      public List<String> getSensitiveFields() {
        return Collections.singletonList("password");
      }

      @Override
      public Boolean isShouldSaveResponseBody() {
        return Boolean.FALSE;
      }
    };
  }
}
