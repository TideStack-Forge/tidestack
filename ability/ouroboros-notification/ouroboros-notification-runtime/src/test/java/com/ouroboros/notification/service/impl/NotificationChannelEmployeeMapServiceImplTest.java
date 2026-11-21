package com.ouroboros.notification.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vavr.control.Try;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.data.model.TypedDataModel;

import com.ouroboros.notification.service.model.NotificationChannelEmployeeMapModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationChannelEmployeeMapServiceImplTest {

  @Test
  void getByEmployeeIdShouldBuildWhereStatementAndReturnSearchResult() {
    NotificationChannelEmployeeMapModel expected = new NotificationChannelEmployeeMapModel();
    expected.setId("map-1");

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.searchOneResult = expected;

    NotificationChannelEmployeeMapModel actual = service.getByEmployeeId("email", "emp-1");

    assertSame(expected, actual);
    assertEquals("email", where(service.lastSearchOneStatement).get("channel"));
    assertEquals("emp-1", where(service.lastSearchOneStatement).get("employeeId"));
  }

  @Test
  void searchByEmployeeIdsShouldBuildWhereStatementAndReturnSearchListResult() {
    NotificationChannelEmployeeMapModel map1 = new NotificationChannelEmployeeMapModel();
    map1.setId("map-1");
    NotificationChannelEmployeeMapModel map2 = new NotificationChannelEmployeeMapModel();
    map2.setId("map-2");

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.searchListResult = Arrays.asList(map1, map2);
    List<String> employeeIds = Arrays.asList("emp-1", "emp-2");

    List<? extends NotificationChannelEmployeeMapModel> actual = service.searchByEmployeeIds("email", employeeIds);

    assertSame(service.searchListResult, actual);
    assertEquals("email", where(service.lastSearchListStatement).get("channel"));
    assertSame(employeeIds, where(service.lastSearchListStatement).get("employeeId"));
  }

  @Test
  void bindShouldDeleteExistingMappingsAndAddFreshModel() throws Exception {
    @SuppressWarnings("unchecked")
    TypedDataModel<String, NotificationChannelEmployeeMapModel> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Try.success(1L));

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.dataModel = dataModel;

    service.bind("email", "emp-1", "user@demo.com", "home");

    ArgumentCaptor<Map> whereCaptor = ArgumentCaptor.forClass(Map.class);
    verify(dataModel).delete(whereCaptor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> where = whereCaptor.getValue();
    @SuppressWarnings("unchecked")
    Map<String, Object> or = (Map<String, Object>) where.get("or");
    assertEquals("email", where.get("channel"));
    assertEquals("emp-1", or.get("employeeId"));
    assertEquals("user@demo.com", or.get("address"));

    NotificationChannelEmployeeMapModel added = service.lastAddedModel;
    assertNotNull(added);
    assertEquals("email", added.getChannel());
    assertEquals("emp-1", added.getEmployeeId());
    assertEquals("user@demo.com", added.getAddress());
    assertEquals("home", added.getRemarks());
    assertNotNull(added.getOperateTime());
  }

  @Test
  void unbindShouldDeleteByChannelAndEmployee() throws Exception {
    @SuppressWarnings("unchecked")
    TypedDataModel<String, NotificationChannelEmployeeMapModel> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Try.success(1L));

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.dataModel = dataModel;

    service.unbind("email", "emp-1");

    ArgumentCaptor<Map> whereCaptor = ArgumentCaptor.forClass(Map.class);
    verify(dataModel).delete(whereCaptor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> where = whereCaptor.getValue();
    assertEquals("email", where.get("channel"));
    assertEquals("emp-1", where.get("employeeId"));
  }

  @Test
  void bindShouldMapDeleteFailureToOuroborosException() {
    @SuppressWarnings("unchecked")
    TypedDataModel<String, NotificationChannelEmployeeMapModel> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Try.failure(new IllegalStateException("boom")));

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.dataModel = dataModel;

    assertThrows(OuroborosException.class, () -> service.bind("email", "emp-1", "user@demo.com", "home"));
  }

  @Test
  void unbindShouldMapDeleteFailureToOuroborosException() {
    @SuppressWarnings("unchecked")
    TypedDataModel<String, NotificationChannelEmployeeMapModel> dataModel = mock(TypedDataModel.class);
    when(dataModel.delete(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Try.failure(new IllegalStateException("boom")));

    TestNotificationChannelEmployeeMapService service = new TestNotificationChannelEmployeeMapService();
    service.dataModel = dataModel;

    assertThrows(OuroborosException.class, () -> service.unbind("email", "emp-1"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> where(Map<String, Object> statement) {
    return (Map<String, Object>) statement.get("where");
  }

  private static class TestNotificationChannelEmployeeMapService extends NotificationChannelEmployeeMapServiceImpl {
    private TypedDataModel<String, NotificationChannelEmployeeMapModel> dataModel;
    private Map<String, Object> lastSearchOneStatement;
    private Map<String, Object> lastSearchListStatement;
    private NotificationChannelEmployeeMapModel searchOneResult;
    private List<? extends NotificationChannelEmployeeMapModel> searchListResult;
    private NotificationChannelEmployeeMapModel lastAddedModel;

    @Override
    protected TypedDataModel<String, NotificationChannelEmployeeMapModel> getDataModel() {
      return dataModel;
    }

    @Override
    public NotificationChannelEmployeeMapModel add(NotificationChannelEmployeeMapModel model) {
      this.lastAddedModel = model;
      return model;
    }

    @Override
    public NotificationChannelEmployeeMapModel searchOne(Map<String, Object> statement) {
      this.lastSearchOneStatement = statement;
      return searchOneResult;
    }

    @Override
    public List<? extends NotificationChannelEmployeeMapModel> searchList(Map<String, Object> statement) {
      this.lastSearchListStatement = statement;
      return searchListResult;
    }

    @Override
    public void bind(String channel, String employeeId, String address, String nikeName) throws OuroborosException {
      super.bind(channel, employeeId, address, nikeName);
    }

    @Override
    public void unbind(String channel, String employeeId) throws OuroborosException {
      super.unbind(channel, employeeId);
    }
  }
}
