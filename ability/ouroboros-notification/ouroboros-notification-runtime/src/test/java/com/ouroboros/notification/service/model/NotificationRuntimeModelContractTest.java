package com.ouroboros.notification.service.model;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRuntimeModelContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void notificationTemplateModelShouldUseIsEnabledJsonProperty() throws Exception {
    NotificationTemplateModel model = new NotificationTemplateModel();
    model.setName("welcome");
    model.setEnabled(true);

    String json = objectMapper.writeValueAsString(model);
    NotificationTemplateModel decoded = objectMapper.readValue("{\"isEnabled\":true,\"name\":\"welcome\"}", NotificationTemplateModel.class);

    assertTrue(json.contains("\"isEnabled\":true"));
    assertTrue(decoded.isEnabled());
    assertEquals("welcome", decoded.getName());
  }

  @Test
  void notificationTemplateModelShouldExposeExpectedAnnotations() throws Exception {
    assertModelAnnotation(NotificationTemplateModel.class, "t_notification_template", "通知模板");
    assertPrimaryKey(NotificationTemplateModel.class, "id", "snowflake");
  }

  @Test
  void notificationModelShouldExposeExpectedAnnotations() throws Exception {
    assertModelAnnotation(NotificationModel.class, "t_notification", "消息通知");
    assertPrimaryKey(NotificationModel.class, "id", "snowflake");
  }

  @Test
  void notificationStateModelShouldExposeExpectedAnnotations() throws Exception {
    assertModelAnnotation(NotificationStateModel.class, "t_notification_state", "通知状态");
    assertPrimaryKey(NotificationStateModel.class, "id", "snowflake");
  }

  @Test
  void notificationChannelEmployeeMapModelShouldExposeExpectedAnnotations() throws Exception {
    assertModelAnnotation(NotificationChannelEmployeeMapModel.class, "t_notification_channel_employee_map", "通知渠道员工映射");
    assertPrimaryKey(NotificationChannelEmployeeMapModel.class, "id", "snowflake");
  }

  private void assertModelAnnotation(Class<?> type, String rawName, String label) {
    Model model = type.getAnnotation(Model.class);
    assertNotNull(model);
    assertEquals(rawName, model.rawName());
    assertEquals(label, model.label());
  }

  private void assertPrimaryKey(Class<?> type, String fieldName, String generator) throws Exception {
    Field field = type.getDeclaredField(fieldName);
    PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
    assertNotNull(primaryKey);
    assertEquals(generator, primaryKey.generator());
  }
}
