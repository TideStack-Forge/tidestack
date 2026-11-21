package com.ouroboros.businesslog.dev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;

class BusinessOperationTemplateDevServiceTest {

  private final VCSManagerCenter vcsManagerCenter = mock(VCSManagerCenter.class);
  private final VCSManager vcsManager = mock(VCSManager.class);

  private BusinessOperationTemplateDevService service;

  @BeforeEach
  void setUp() {
    service = new BusinessOperationTemplateDevService();
    ReflectionTestUtils.setField(service, "vcsManagerCenter", vcsManagerCenter);
    ReflectionTestUtils.setField(service, "publisher", mock(ApplicationEventPublisher.class));
    when(vcsManagerCenter.getVcsManager("demo-app")).thenReturn(vcsManager);
    DevAppHolder.setAppName("demo-app");
  }

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
  }

  @Test
  void shouldRejectDuplicateHttpMethodAndUrl() {
    mockTemplates(template("create-order", "POST", "/orders", "创建订单"));

    Try<DefaultBusinessOperationTemplate> result = service.create(
        template("create-order-v2", "POST", "/orders", "创建订单V2"),
        "duplicate"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("HTTP"));
    verify(vcsManager, never()).getFile(anyString());
  }

  @Test
  void shouldRejectDuplicateName() {
    mockTemplates(template("create-order", "POST", "/orders", "创建订单"));

    Try<DefaultBusinessOperationTemplate> result = service.create(
        template("create-order", "PUT", "/orders/{id}", "更新订单"),
        "duplicate-name"
    );

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("名称已存在"));
  }

  @Test
  void shouldSearchWorkspaceTemplatesByKeyword() {
    mockTemplates(
        template("create-order", "POST", "/orders", "创建订单"),
        template("update-order", "PUT", "/orders/{id}", "更新订单")
    );

    List<?> results = service.search(Collections.<String, Object>singletonMap("keyword", "更新"));

    assertEquals(1, results.size());
    DefaultBusinessOperationTemplate template = (DefaultBusinessOperationTemplate) results.get(0);
    assertEquals("update-order", template.getName());
  }

  private void mockTemplates(DefaultBusinessOperationTemplate... templates) {
    when(vcsManager.walkDirectory("business-log-template", ".business-log.json")).thenReturn(
        Stream.of(templates).map(this::toFile)
    );
  }

  private VCSFile toFile(DefaultBusinessOperationTemplate template) {
    VCSFile file = mock(VCSFile.class);
    when(file.getPath()).thenReturn("business-log-template/" + template.getName() + ".business-log.json");
    when(file.getContent()).thenReturn(json(template));
    return file;
  }

  private String json(DefaultBusinessOperationTemplate template) {
    return "{"
        + "\"name\":\"" + template.getName() + "\","
        + "\"httpMethod\":\"" + template.getHttpMethod() + "\","
        + "\"url\":\"" + template.getUrl() + "\","
        + "\"moduleName\":\"" + template.getModuleName() + "\","
        + "\"operation\":\"" + template.getOperation() + "\","
        + "\"level\":\"" + template.getLevel() + "\""
        + "}";
  }

  private DefaultBusinessOperationTemplate template(String name, String httpMethod, String url, String operation) {
    DefaultBusinessOperationTemplate template = new DefaultBusinessOperationTemplate();
    template.setName(name);
    template.setHttpMethod(httpMethod);
    template.setUrl(url);
    template.setModuleName("订单");
    template.setOperation(operation);
    template.setLevel("INFO");
    return template;
  }
}
