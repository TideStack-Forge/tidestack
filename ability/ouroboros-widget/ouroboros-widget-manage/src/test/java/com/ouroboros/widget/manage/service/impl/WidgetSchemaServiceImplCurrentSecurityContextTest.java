package com.ouroboros.widget.manage.service.impl;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.widget.WidgetSchema;
import com.ouroboros.widget.event.WidgetSchemaRefreshed;
import com.ouroboros.widget.manage.model.WidgetSchemaModel;
import com.ouroboros.widget.service.WidgetDefinitionService;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WidgetSchemaServiceImplCurrentSecurityContextTest {
  private WidgetSchemaServiceImpl service;
  private CacheManager cacheManager;
  private Cache cache;
  private ApplicationEventPublisher applicationEventPublisher;
  private AtomicReference<WidgetSchemaModel> insertedRecord = new AtomicReference<>();
  private AtomicReference<WidgetSchemaModel> updatedRecord = new AtomicReference<>();

  @BeforeEach
  void setUp() {
    service = new WidgetSchemaServiceImpl();
    cacheManager = mock(CacheManager.class);
    cache = mock(Cache.class);
    when(cacheManager.getCache("ouroboros:widget-schema")).thenReturn(cache);
    applicationEventPublisher = mock(ApplicationEventPublisher.class);

    TypedDataModel<String, WidgetSchemaModel> dataModel = mock(TypedDataModel.class);
    configureDataModel(dataModel);

    injectField("cacheManager", cacheManager);
    injectField("applicationEventPublisher", applicationEventPublisher);
    injectField("widgetDefinitionService", mock(WidgetDefinitionService.class));
    injectField("dataModel", dataModel);
    injectField("self", service);
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldStampSeamIdentityOnAddAndUpdate() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(buildUser("alice"))));

    WidgetSchema schema = buildWidgetSchema();
    assertTrue(service.addWidgetSchema(schema).isSuccess());

    WidgetSchemaModel createdModel = insertedRecord.get();
    assertNotNull(createdModel);
    assertEquals("alice", createdModel.getCreatedBy());
    assertNotNull(createdModel.getCreatedAt());

    verify(cache).clear();
    ArgumentCaptor<WidgetSchemaRefreshed> refreshCaptor = ArgumentCaptor.forClass(WidgetSchemaRefreshed.class);
    verify(applicationEventPublisher).publishEvent(refreshCaptor.capture());
    assertSame(service, refreshCaptor.getValue().getSource());

    clearInvocations(cache, applicationEventPublisher);
    insertedRecord.set(null);

    WidgetSchema updatePayload = buildWidgetSchema().setName("schema-2");
    assertTrue(service.updateWidgetSchema("schema-id", updatePayload).isSuccess());
    WidgetSchemaModel updatedModel = updatedRecord.get();
    assertNotNull(updatedModel);
    assertEquals("alice", updatedModel.getUpdatedBy());
    assertNotNull(updatedModel.getUpdatedAt());

    verify(cache).clear();
    verify(applicationEventPublisher).publishEvent(any(WidgetSchemaRefreshed.class));
  }

  @Test
  void shouldNotRecordLegacyPrincipalWithoutSeamIdentity() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("legacy-principal", "N/A", Collections.emptyList()));
    CurrentSecurityContextHolder.clearDelegate();

    WidgetSchema schema = buildWidgetSchema();
    assertTrue(service.addWidgetSchema(schema).isSuccess());

    WidgetSchemaModel createdModel = insertedRecord.get();
    assertNotNull(createdModel);
    assertNull(createdModel.getCreatedBy());

    verify(cache).clear();
    verify(applicationEventPublisher).publishEvent(any(WidgetSchemaRefreshed.class));

    clearInvocations(cache, applicationEventPublisher);
    insertedRecord.set(null);

    assertTrue(service.updateWidgetSchema("schema-id", schema).isSuccess());
    WidgetSchemaModel updatedModel = updatedRecord.get();
    assertNotNull(updatedModel);
    assertNull(updatedModel.getUpdatedBy());

    verify(cache).clear();
    verify(applicationEventPublisher).publishEvent(any(WidgetSchemaRefreshed.class));
  }

  private static WidgetSchema buildWidgetSchema() {
    return new WidgetSchema()
        .setName("schema")
        .setTitle("title")
        .setGeneratorName("generator");
  }

  private static User buildUser(String identity) {
    return () -> identity;
  }

  private void configureDataModel(TypedDataModel<String, WidgetSchemaModel> dataModel) {
    when(dataModel.insert(any(WidgetSchemaModel.class))).thenAnswer(invocation -> {
      WidgetSchemaModel payload = invocation.getArgument(0);
      insertedRecord.set(payload);
      return Try.success(payload);
    });
    when(dataModel.update(anyString(), any(WidgetSchemaModel.class))).thenAnswer(invocation -> {
      WidgetSchemaModel payload = invocation.getArgument(1);
      updatedRecord.set(payload);
      return Try.success(1L);
    });
  }

  private void injectField(String name, Object value) {
    try {
      Field field = WidgetSchemaServiceImpl.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(service, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
