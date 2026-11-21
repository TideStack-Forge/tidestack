package com.ouroboros.businesslog.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateCenter;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;

class BusinessLogFilterTest {

  @Test
  void shouldPreferSpecificPathPatternToCatchAll() {
    assertSelectedOperation(
        templates(BusinessOperationTemplateSource.JAR,
            template("GET", "/orders/{id}", "specific"),
            template("GET", "/orders/**", "catch-all")),
        "GET",
        "/orders/42",
        "specific"
    );
  }

  @Test
  void shouldPreferExactHttpMethodToWildcard() {
    assertSelectedOperation(
        templates(BusinessOperationTemplateSource.FILE,
            template("*", "/orders/{id}", "wildcard"),
            template("GET", "/orders/{id}", "exact")),
        "GET",
        "/orders/42",
        "exact"
    );
  }

  @Test
  void shouldPreferSpecificPathBeforeExactHttpMethod() {
    assertSelectedOperation(
        templates(BusinessOperationTemplateSource.FILE,
            template("GET", "/orders/**", "exact-catch-all"),
            template("*", "/orders/{id}", "specific-wildcard")),
        "GET",
        "/orders/42",
        "specific-wildcard"
    );
  }

  @Test
  void shouldPreferHigherPrioritySourceForEquivalentPatterns() {
    Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource =
        new EnumMap<>(BusinessOperationTemplateSource.class);
    templatesBySource.put(BusinessOperationTemplateSource.DATABASE,
        Collections.singletonList(template("GET", "/orders/{databaseId}", "database")));
    templatesBySource.put(BusinessOperationTemplateSource.JAR,
        Collections.singletonList(template("GET", "/orders/{jarId}", "jar")));

    assertSelectedOperation(templatesBySource, "GET", "/orders/42", "database");
  }

  @Test
  void shouldUseTemplateKeyAsStableTieBreaker() {
    assertSelectedOperation(
        templates(BusinessOperationTemplateSource.FILE,
            template("GET", "/orders/{z}", "later-key"),
            template("GET", "/orders/{a}", "earlier-key")),
        "GET",
        "/orders/42",
        "earlier-key"
    );
  }

  private void assertSelectedOperation(
      Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource,
      String method,
      String requestUri,
      String expectedOperation
  ) {
    Map<String, BusinessOperationTemplateSourceView> merged =
        BusinessOperationTemplateCenter.merge(templatesBySource);
    MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);

    BusinessOperationTemplate selected = BusinessLogFilter.findMatchingTemplate(
        BusinessLogFilter.buildLogTemplateMatchers(merged.values()),
        request
    ).orElseThrow()._2();

    assertEquals(expectedOperation, selected.getOperation());
  }

  private Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templates(
      BusinessOperationTemplateSource source,
      BusinessOperationTemplate... templates
  ) {
    Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource =
        new EnumMap<>(BusinessOperationTemplateSource.class);
    templatesBySource.put(source, Arrays.asList(templates));
    return templatesBySource;
  }

  private BusinessOperationTemplate template(String method, String url, String operation) {
    DefaultBusinessOperationTemplate template = new DefaultBusinessOperationTemplate();
    template.setHttpMethod(method);
    template.setUrl(url);
    template.setOperation(operation);
    return template;
  }
}
