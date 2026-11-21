package com.ouroboros.businesslog.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;

class BusinessOperationTemplateServiceImplTest {

  private TestableBusinessOperationTemplateServiceImpl service;

  private TypedDataModel<String, BusinessOperationTemplateModel> dataModel;

  @BeforeEach
  void setUp() {
    dataModel = mock(TypedDataModel.class);
    service = new TestableBusinessOperationTemplateServiceImpl(dataModel);
    ReflectionTestUtils.setField(service, "self", service);
  }

  @Test
  void shouldFindTemplateByHttpMethodAndUrlIgnoringCase() {
    service.setTemplates(Collections.singletonList(template("1", "post", "/orders")));

    Optional<BusinessOperationTemplateModel> found = service.getByHttpMethodAndUrl("POST", " /orders ");

    assertTrue(found.isPresent());
    assertEquals("1", found.get().getId());
  }

  @Test
  void shouldRejectDuplicateTemplateWhenAdding() {
    service.setTemplates(Collections.singletonList(template("1", "POST", "/orders")));

    BusinessOperationTemplateModel duplicate = template(null, "post", " /orders ");

    BusinessException exception = assertThrows(BusinessException.class, () -> service.add(duplicate));

    assertTrue(exception.getMessage().contains("HTTP"));
    verify(dataModel, never()).insert(any());
  }

  @Test
  void shouldRejectDuplicateTemplateWhenUpdatingAnotherRecord() {
    service.setTemplates(Arrays.asList(
        template("1", "POST", "/orders"),
        template("2", "PUT", "/orders/1")
    ));

    BusinessOperationTemplateModel duplicate = template("2", "POST", "/orders");

    BusinessException exception = assertThrows(BusinessException.class, () -> service.update("2", duplicate));

    assertTrue(exception.getMessage().contains("URL"));
    verify(dataModel, never()).update(anyString(), any(BusinessOperationTemplateModel.class));
  }

  @Test
  void shouldNormalizeHttpMethodBeforePersisting() {
    BusinessOperationTemplateModel model = template(null, " post ", "/orders ");
    when(dataModel.insert(any(BusinessOperationTemplateModel.class))).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    BusinessOperationTemplateModel saved = service.add(model);

    assertEquals("POST", saved.getHttpMethod());
    assertEquals("/orders", saved.getUrl());
    verify(dataModel).insert(eq(saved));
  }

  @Test
  void shouldDefaultOptionalFieldsBeforePersisting() {
    BusinessOperationTemplateModel model = template(null, "POST", "/orders");
    model.setBusinessKeyName(" ");
    model.setSummaryTemplate(" ");
    model.setSensitiveFields(null);
    model.setShouldSaveResponseBody(null);
    when(dataModel.insert(any(BusinessOperationTemplateModel.class))).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    BusinessOperationTemplateModel saved = service.add(model);

    assertNull(saved.getBusinessKeyName());
    assertNull(saved.getSummaryTemplate());
    assertEquals(Collections.emptyList(), saved.getSensitiveFields());
    assertEquals(Boolean.FALSE, saved.getShouldSaveResponseBody());
    verify(dataModel).insert(eq(saved));
  }

  private BusinessOperationTemplateModel template(String id, String httpMethod, String url) {
    BusinessOperationTemplateModel model = new BusinessOperationTemplateModel();
    model.setId(id);
    model.setHttpMethod(httpMethod);
    model.setUrl(url);
    model.setModuleName("订单");
    model.setOperation("创建订单");
    model.setLevel("INFO");
    model.setBusinessKeyName("id");
    model.setSummaryTemplate("创建订单");
    model.setSensitiveFields(Collections.singletonList("password"));
    model.setShouldSaveResponseBody(Boolean.FALSE);
    return model;
  }

  private static class TestableBusinessOperationTemplateServiceImpl extends BusinessOperationTemplateServiceImpl {

    private final TypedDataModel<String, BusinessOperationTemplateModel> dataModel;

    private List<BusinessOperationTemplateModel> templates = Collections.emptyList();

    private TestableBusinessOperationTemplateServiceImpl(TypedDataModel<String, BusinessOperationTemplateModel> dataModel) {
      this.dataModel = dataModel;
    }

    private void setTemplates(List<BusinessOperationTemplateModel> templates) {
      this.templates = templates;
    }

    @Override
    public List<BusinessOperationTemplateModel> searchAll() {
      return templates;
    }

    @Override
    protected TypedDataModel<String, BusinessOperationTemplateModel> getDataModel() {
      return dataModel;
    }
  }
}
