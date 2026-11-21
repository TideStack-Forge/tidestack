package com.ouroboros.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.notification.service.NotificationService;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;
import com.ouroboros.util.SPI;

public class NotificationDispatcherImplTest {
  private org.mockito.MockedStatic<SPI> spiBinding;

  @Before
  @After
  public void clearSpiCache() throws Exception {
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    SPI.clearCache();
    TestNotificationAdapter.clear();
  }

  @Test
  public void employeeDispatchUsesOneTemplateObservationForAddressResolutionAndContent() {
    var dispatcher = new NotificationDispatcherImpl();
    var notifications = mock(NotificationService.class);
    var addresses = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notifications);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", addresses);
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    var template = new DefaultNotificationTemplate(); template.setName("single-observation"); template.setTitle("title");
    template.setEnabled(true); template.setChannel(Map.of("test-channel", Map.of("schema", Map.of(), "options", Map.of())));
    var provider = new NotificationTemplateProvider() {
      public Optional<NotificationTemplate> getTemplate(String name) {
        reads.incrementAndGet();
        if (reads.get() > 1) { template.setEnabled(false); }
        return Optional.of(template);
      }
      public List<NotificationTemplate> getAllTemplate() { return List.of(template); }
    };
    spiBinding = org.mockito.Mockito.mockStatic(SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> SPI.getCachedServiceStream(NotificationTemplateProvider.class))
        .thenAnswer(ignored -> java.util.stream.Stream.of(provider));
    when(addresses.parse(eq(List.of("employee")), anyCollection())).thenReturn(buildAddressMap());
    var result = dispatcher.sendWithResult(List.of("employee"), "single-observation", Map.of(), null);
    assertTrue(result.success()); assertEquals(1, reads.get()); assertEquals(1, TestNotificationAdapter.sendCount);
  }

  @Test
  public void sendShouldReplaceConfigurationPlaceholdersInTemplateChannelOptionsBeforeAdapterInvocation() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    ConfigurationService configurationService = mock(ConfigurationService.class);
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "configurationService", configurationService);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    when(configurationService.get(String.class, "host")).thenReturn(Optional.of("notify.example.com"));
    when(configurationService.get(String.class, "path")).thenReturn(Optional.of("send"));

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.CONFIG_OPTIONS_TEMPLATE_NAME, buildSuccessPayload(), null);

    assertNotNull(TestNotificationAdapter.lastChannelOptions);
    assertEquals("https://notify.example.com/send", TestNotificationAdapter.lastChannelOptions.getEndpoint());
  }

  @Test
  public void sendShouldPersistResolvedTemplateChannelOptionsInNotificationAuditWhenDispatchOptionsIsNull() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    ConfigurationService configurationService = mock(ConfigurationService.class);
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "configurationService", configurationService);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    when(configurationService.get(String.class, "host")).thenReturn(Optional.of("notify.example.com"));
    when(configurationService.get(String.class, "path")).thenReturn(Optional.of("send"));

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.CONFIG_OPTIONS_TEMPLATE_NAME, buildSuccessPayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    verify(notificationService).add(modelCaptor.capture(), org.mockito.ArgumentMatchers.anyCollection());

    Map<String, Object> expectedChannelOptions = new LinkedHashMap<>();
    Map<String, Object> expectedTestChannelOptions = new LinkedHashMap<>();
    expectedTestChannelOptions.put("endpoint", "https://notify.example.com/send");
    expectedChannelOptions.put("test-channel", expectedTestChannelOptions);

    assertNotNull(modelCaptor.getValue().getChannelOptions());
    assertEquals(expectedChannelOptions, modelCaptor.getValue().getChannelOptions().getMap());
  }

  @Test
  public void sendShouldPersistMergedDispatchChannelOptionsOverridesIntoNotificationAudit() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);
    when(employeeAddressParser.parse(anyCollection(), anyCollection())).thenReturn(buildAddressMap());

    DispatchOptions options = new DispatchOptions();
    Map<String, NotificationChannelOptions> channelOptions = new LinkedHashMap<>();
    TestNotificationChannelOptions dispatchChannelOptions = new TestNotificationChannelOptions();
    dispatchChannelOptions.setEndpoint("https://dispatch.example.com/direct");
    channelOptions.put("test-channel", dispatchChannelOptions);
    options.setChannelOptions(channelOptions);

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.MERGED_OPTIONS_TEMPLATE_NAME, buildSuccessPayload(), options);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    verify(notificationService).add(modelCaptor.capture(), org.mockito.ArgumentMatchers.anyCollection());

    Map<String, Object> expectedChannelOptions = new LinkedHashMap<>();
    Map<String, Object> expectedTestChannelOptions = new LinkedHashMap<>();
    expectedTestChannelOptions.put("endpoint", "https://dispatch.example.com/direct");
    expectedTestChannelOptions.put("sender", "template-sender");
    expectedChannelOptions.put("test-channel", expectedTestChannelOptions);

    assertNotNull(TestNotificationAdapter.lastChannelOptions);
    assertEquals("https://dispatch.example.com/direct", TestNotificationAdapter.lastChannelOptions.getEndpoint());
    assertEquals("template-sender", TestNotificationAdapter.lastChannelOptions.getSender());
    assertNotNull(modelCaptor.getValue().getChannelOptions());
    assertEquals(expectedChannelOptions, modelCaptor.getValue().getChannelOptions().getMap());
  }

  @Test
  public void sendShouldPersistChannelOptionsOnlyForChannelsActuallySelectedForDispatch() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    DispatchOptions options = new DispatchOptions();
    options.setChannels(Collections.singletonList("test-channel"));

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.MULTI_CHANNEL_SELECTION_TEMPLATE_NAME, buildSuccessPayload(), options);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    verify(notificationService).add(modelCaptor.capture(), org.mockito.ArgumentMatchers.anyCollection());

    Map<String, Object> expectedChannelOptions = new LinkedHashMap<>();
    Map<String, Object> expectedTestChannelOptions = new LinkedHashMap<>();
    expectedTestChannelOptions.put("endpoint", "https://selected.example.com/send");
    expectedChannelOptions.put("test-channel", expectedTestChannelOptions);

    assertNotNull(modelCaptor.getValue().getChannelOptions());
    assertEquals(expectedChannelOptions, modelCaptor.getValue().getChannelOptions().getMap());
  }

  @Test
  public void sendWithResultPropagatesIdempotencyKeyToAdapterAndAudit() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);
    when(employeeAddressParser.parse(anyCollection(), anyCollection())).thenReturn(buildAddressMap());
    DispatchOptions options = new DispatchOptions();
    options.setIdempotencyKey("alarm:transition-1");

    DispatchResult result = dispatcher.sendWithResult(List.of("employee-1"), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME,
        buildSuccessPayload(), options);

    assertTrue(result.success());
    assertEquals("alarm:transition-1", TestNotificationAdapter.lastPayload.get("__ouroboros_idempotency_key"));
    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    verify(notificationService).add(modelCaptor.capture(), org.mockito.ArgumentMatchers.anyCollection());
    assertEquals("alarm:transition-1", modelCaptor.getValue().getIdempotencyKey());
  }

  @Test
  public void sendRejectsInvalidPayloadWithPayloadErrorCodeAndSchemaDetail() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    ReflectionTestUtils.setField(dispatcher, "notificationPayloadSchemaValidator", new NotificationPayloadSchemaValidator());

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.TEMPLATE_NAME, buildInvalidPayload(), null));

    assertEquals(
        NotificationSendException.ErrorDefine.PAYLOAD_PARAM_NON_CONFORMITY.getCode(),
        exception.getErrorCode());
    assertTrue(exception.getMessage(), exception.getMessage().contains("count"));
    assertTrue(exception.getMessage(), exception.getMessage().contains("type"));
  }

  @Test
  public void sendRejectsEmptyEmployeeIdsBeforeAnyTemplateLookup() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(Collections.emptyList(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), null));

    assertEquals(NotificationSendException.ErrorDefine.EMPLOYEE_ID_EMPTY.getCode(), exception.getErrorCode());
  }

  @Test
  public void sendRejectsEmptyAddressMap() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(Collections.emptyMap(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), null));

    assertEquals(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY.getCode(), exception.getErrorCode());
  }

  @Test
  public void sendRejectsEmptyTemplateNameForAddressDispatch() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(buildAddressMap(), "", buildSuccessPayload(), null));

    assertEquals(NotificationSendException.ErrorDefine.TEMPLATE_NAME_EMPTY.getCode(), exception.getErrorCode());
  }

  @Test
  public void sendRejectsMissingTemplateForAddressDispatch() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(buildAddressMap(), "missing-template", buildSuccessPayload(), null));

    assertEquals(NotificationSendException.ErrorDefine.TEMPLATE_NOT_EXIST.getCode(), exception.getErrorCode());
    assertTrue(exception.getMessage().contains("missing-template"));
  }

  @Test
  public void sendRejectsChannelsNotDefinedInTemplate() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    DispatchOptions options = new DispatchOptions();
    options.setChannels(Collections.singletonList("missing-channel"));

    NotificationSendException exception = assertThrows(
        NotificationSendException.class,
        () -> dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), options));

    assertEquals(NotificationSendException.ErrorDefine.OPTIONS_CHANNEL_NOT_DEFINE.getCode(), exception.getErrorCode());
  }

  @Test
  public void sendShouldPersistFailStatesWhenTemplateIsDisabled() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME, buildSuccessPayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains(TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME));
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("disabled"));
  }

  @Test
  public void sendShouldPersistFailStatesWhenTemplateChannelMapIsEmpty() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME, buildSuccessPayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains(TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME));
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("channel"));
  }

  @Test
  public void sendShouldPersistFailStatesWhenSelectedChannelsFilterOutAllAddresses() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    DispatchOptions options = new DispatchOptions();
    options.setChannels(Collections.singletonList("test-channel"));

    dispatcher.send(buildAddressMapWithoutSelectedChannel(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), options);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals(null, state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("address"));
  }

  @Test
  public void sendShouldPersistMixedSuccessAndFailureStatesWhenOneChannelAdapterIsMissing() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    dispatcher.send(buildAddressMapWithMissingAdapterChannel(), TestNotificationTemplateProvider.MIXED_ADAPTER_TEMPLATE_NAME, buildSuccessPayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.MIXED_ADAPTER_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());
    assertEquals(1, TestNotificationAdapter.sendCount);

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(2, states.size());

    NotificationStateModel successState = findStateByChannel(states, "test-channel");
    assertNotNull(successState);
    assertEquals("receiver", successState.getAddress());
    assertEquals(NotificationStateConstant.SUCCESS.getValue(), successState.getSendState());
    assertEquals(null, successState.getSendFailCause());

    NotificationStateModel failState = findStateByChannel(states, "missing-channel");
    assertNotNull(failState);
    assertEquals("missing-receiver", failState.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), failState.getSendState());
    assertTrue(failState.getSendFailCause(), failState.getSendFailCause().contains("missing-channel"));
  }

  @Test
  public void sendShouldPersistAdapterExceptionMessageIntoFailStates() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildAdapterFailurePayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildAdapterFailurePayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertEquals("adapter exploded", state.getSendFailCause());
  }

  @Test
  public void recursionReplaceConfigurationReplacesAllConfigurationPlaceholdersInTheSameString() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    ConfigurationService configurationService = mock(ConfigurationService.class);
    ReflectionTestUtils.setField(dispatcher, "configurationService", configurationService);

    when(configurationService.get(String.class, "host")).thenReturn(Optional.of("example.com"));
    when(configurationService.get(String.class, "path")).thenReturn(Optional.of("notify"));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("url", "https://#{host}/#{path}");

    ReflectionTestUtils.invokeMethod(dispatcher, "recursionReplaceConfiguration", schema);

    assertEquals("https://example.com/notify", schema.get("url"));
  }

  @Test
  public void sendPersistsSuccessNotificationStatesForAddressDispatch() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    DispatchOptions options = new DispatchOptions();
    options.setSendByEmployee("emp-1");

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), options);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertEquals("emp-1", model.getSendByEmployee());
    assertNotNull(model.getPayload());
    assertEquals(1, TestNotificationAdapter.sendCount);

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.SUCCESS.getValue(), state.getSendState());
    assertEquals(null, state.getSendFailCause());
  }

  @Test
  public void sendPersistsSuccessNotificationStatesForAddressDispatchWhenDispatchOptionsIsNull() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);

    dispatcher.send(buildAddressMap(), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), null);

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertEquals(null, model.getSendByEmployee());
    assertEquals(null, model.getChannelOptions());
    assertNotNull(model.getPayload());
    assertEquals(1, TestNotificationAdapter.sendCount);

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.SUCCESS.getValue(), state.getSendState());
    assertEquals(null, state.getSendFailCause());
  }

  @Test
  public void sendByEmployeeIdsShouldResolveAddressesAndPersistSuccessStates() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);

    when(employeeAddressParser.parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel"))))
        .thenReturn(buildAddressMap());

    DispatchOptions options = new DispatchOptions();
    options.setSendByEmployee("emp-1");

    dispatcher.send(Collections.singletonList("emp-2"), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), options);

    verify(employeeAddressParser).parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel")));

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertEquals("emp-1", model.getSendByEmployee());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.SUCCESS.getValue(), state.getSendState());
    assertEquals(null, state.getSendFailCause());
  }

  @Test
  public void sendByEmployeeIdsShouldResolveAddressesAndPersistSuccessStatesWhenDispatchOptionsIsNull() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);

    when(employeeAddressParser.parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel"))))
        .thenReturn(buildAddressMap());

    dispatcher.send(Collections.singletonList("emp-2"), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), null);

    verify(employeeAddressParser).parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel")));

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertEquals(null, model.getSendByEmployee());
    assertEquals(null, model.getChannelOptions());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals("receiver", state.getAddress());
    assertEquals(NotificationStateConstant.SUCCESS.getValue(), state.getSendState());
    assertEquals(null, state.getSendFailCause());
  }

  @Test
  public void sendByEmployeeIdsShouldPersistFailStatesWhenTemplateIsDisabledAndParserReturnsEmptyMap() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);

    when(employeeAddressParser.parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel"))))
        .thenReturn(buildEmptyAddressMap());

    dispatcher.send(Collections.singletonList("emp-2"), TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME, buildSuccessPayload(), null);

    verify(employeeAddressParser).parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel")));

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals(null, state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains(TestNotificationTemplateProvider.DISABLED_TEMPLATE_NAME));
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("disabled"));
  }

  @Test
  public void sendByEmployeeIdsShouldPersistFailStatesWhenTemplateChannelMapIsEmptyAndParserReturnsEmptyMap() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);

    DispatchOptions options = new DispatchOptions();
    options.setChannels(Collections.singletonList("test-channel"));

    when(employeeAddressParser.parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel"))))
        .thenReturn(buildEmptyAddressMap());

    dispatcher.send(Collections.singletonList("emp-2"), TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME, buildSuccessPayload(), options);

    verify(employeeAddressParser).parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel")));

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals(null, state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains(TestNotificationTemplateProvider.EMPTY_CHANNEL_TEMPLATE_NAME));
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("channel"));
  }

  @Test
  public void sendByEmployeeIdsShouldPersistFailStatesWhenSelectedChannelsResolveNoAddresses() {
    NotificationDispatcherImpl dispatcher = new NotificationDispatcherImpl();
    NotificationService notificationService = mock(NotificationService.class);
    NotificationEmployeeAddressParser employeeAddressParser = mock(NotificationEmployeeAddressParser.class);
    ReflectionTestUtils.setField(dispatcher, "notificationService", notificationService);
    ReflectionTestUtils.setField(dispatcher, "employeeAddressParser", employeeAddressParser);

    DispatchOptions options = new DispatchOptions();
    options.setChannels(Collections.singletonList("test-channel"));

    when(employeeAddressParser.parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel"))))
        .thenReturn(buildEmptyAddressMap());

    dispatcher.send(Collections.singletonList("emp-2"), TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, buildSuccessPayload(), options);

    verify(employeeAddressParser).parse(
        eq(Collections.singletonList("emp-2")),
        argThat(channels -> channels != null && channels.size() == 1 && channels.contains("test-channel")));

    ArgumentCaptor<NotificationModel> modelCaptor = ArgumentCaptor.forClass(NotificationModel.class);
    ArgumentCaptor<Collection> statesCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(notificationService).add(modelCaptor.capture(), statesCaptor.capture());

    NotificationModel model = modelCaptor.getValue();
    assertEquals(TestNotificationTemplateProvider.SUCCESS_TEMPLATE_NAME, model.getTemplate());
    assertNotNull(model.getPayload());
    assertEquals(buildSuccessPayload(), model.getPayload().getMap());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> states = (Collection<NotificationStateModel>) statesCaptor.getValue();
    assertEquals(1, states.size());
    NotificationStateModel state = states.iterator().next();
    assertEquals("test-channel", state.getChannel());
    assertEquals(null, state.getAddress());
    assertEquals(NotificationStateConstant.FAIL.getValue(), state.getSendState());
    assertTrue(state.getSendFailCause(), state.getSendFailCause().contains("address"));
  }

  private Map<String, Collection<String>> buildAddressMap() {
    Map<String, Collection<String>> addressMap = new LinkedHashMap<>();
    addressMap.put("test-channel", Collections.singletonList("receiver"));
    return addressMap;
  }

  private Map<String, Collection<String>> buildEmptyAddressMap() {
    return new LinkedHashMap<>();
  }

  private Map<String, Collection<String>> buildAddressMapWithoutSelectedChannel() {
    Map<String, Collection<String>> addressMap = new LinkedHashMap<>();
    addressMap.put("secondary-channel", Collections.singletonList("receiver"));
    return addressMap;
  }

  private Map<String, Collection<String>> buildAddressMapWithMissingAdapterChannel() {
    Map<String, Collection<String>> addressMap = new LinkedHashMap<>();
    addressMap.put("test-channel", Collections.singletonList("receiver"));
    addressMap.put("missing-channel", Collections.singletonList("missing-receiver"));
    return addressMap;
  }

  private NotificationStateModel findStateByChannel(Collection<NotificationStateModel> states, String channel) {
    for (NotificationStateModel state : states) {
      if (channel.equals(state.getChannel())) {
        return state;
      }
    }

    return null;
  }

  private Map<String, Object> buildInvalidPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("count", "bad");
    return payload;
  }

  private Map<String, Object> buildAdapterFailurePayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("message", "hello");
    payload.put("throwMessage", "adapter exploded");
    return payload;
  }

  private Map<String, Object> buildSuccessPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("message", "hello");
    return payload;
  }

  public static class TestNotificationTemplateProvider implements NotificationTemplateProvider {
    static final String TEMPLATE_NAME = "test-invalid-payload-template";
    static final String SUCCESS_TEMPLATE_NAME = "test-success-template";
    static final String DISABLED_TEMPLATE_NAME = "test-disabled-template";
    static final String EMPTY_CHANNEL_TEMPLATE_NAME = "test-empty-channel-template";
    static final String MIXED_ADAPTER_TEMPLATE_NAME = "test-mixed-adapter-template";
    static final String CONFIG_OPTIONS_TEMPLATE_NAME = "test-config-options-template";
    static final String MERGED_OPTIONS_TEMPLATE_NAME = "test-merged-options-template";
    static final String MULTI_CHANNEL_SELECTION_TEMPLATE_NAME = "test-multi-channel-selection-template";

    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      if (TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildInvalidPayloadTemplate());
      }
      if (SUCCESS_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildSuccessTemplate());
      }
      if (DISABLED_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildDisabledTemplate());
      }
      if (EMPTY_CHANNEL_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildEmptyChannelTemplate());
      }
      if (MIXED_ADAPTER_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildMixedAdapterTemplate());
      }
      if (CONFIG_OPTIONS_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildConfigOptionsTemplate());
      }
      if (MERGED_OPTIONS_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildMergedOptionsTemplate());
      }
      if (MULTI_CHANNEL_SELECTION_TEMPLATE_NAME.equals(name)) {
        return Optional.of(buildMultiChannelSelectionTemplate());
      }
      return Optional.empty();
    }

    @Override
    public java.util.List<NotificationTemplate> getAllTemplate() {
      return java.util.Arrays.asList(buildInvalidPayloadTemplate(), buildSuccessTemplate());
    }

    private NotificationTemplate buildInvalidPayloadTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(TEMPLATE_NAME);
      template.setTitle("test");
      template.setEnabled(true);
      template.setPayloadSchema(buildPayloadSchema());
      template.setChannel(buildChannel());
      return template;
    }

    private NotificationTemplate buildSuccessTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(SUCCESS_TEMPLATE_NAME);
      template.setTitle("success");
      template.setEnabled(true);
      template.setChannel(buildChannel());
      return template;
    }

    private NotificationTemplate buildDisabledTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(DISABLED_TEMPLATE_NAME);
      template.setTitle("disabled");
      template.setEnabled(false);
      template.setChannel(buildChannel());
      return template;
    }

    private NotificationTemplate buildEmptyChannelTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(EMPTY_CHANNEL_TEMPLATE_NAME);
      template.setTitle("empty-channel");
      template.setEnabled(true);
      template.setChannel(new LinkedHashMap<>());
      return template;
    }

    private NotificationTemplate buildMixedAdapterTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(MIXED_ADAPTER_TEMPLATE_NAME);
      template.setTitle("mixed-adapter");
      template.setEnabled(true);
      template.setChannel(buildChannelWithMissingAdapterChannel());
      return template;
    }

    private NotificationTemplate buildConfigOptionsTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(CONFIG_OPTIONS_TEMPLATE_NAME);
      template.setTitle("config-options");
      template.setEnabled(true);
      template.setChannel(buildChannelWithTemplateOptions());
      return template;
    }

    private NotificationTemplate buildMergedOptionsTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(MERGED_OPTIONS_TEMPLATE_NAME);
      template.setTitle("merged-options");
      template.setEnabled(true);
      template.setChannel(buildChannelWithMergedTemplateOptions());
      return template;
    }

    private NotificationTemplate buildMultiChannelSelectionTemplate() {
      DefaultNotificationTemplate template = new DefaultNotificationTemplate();
      template.setName(MULTI_CHANNEL_SELECTION_TEMPLATE_NAME);
      template.setTitle("multi-channel-selection");
      template.setEnabled(true);
      template.setChannel(buildChannelWithSelectedAndUnselectedTemplateOptions());
      return template;
    }

    private Map<String, Object> buildPayloadSchema() {
      Map<String, Object> fieldSchema = new LinkedHashMap<>();
      fieldSchema.put("type", "int");

      Map<String, Object> payloadSchema = new LinkedHashMap<>();
      payloadSchema.put("count", fieldSchema);
      return payloadSchema;
    }

    private Map<String, Map<String, Object>> buildChannel() {
      Map<String, Map<String, Object>> channel = new LinkedHashMap<>();
      channel.put("test-channel", new LinkedHashMap<>());
      return channel;
    }

    private Map<String, Map<String, Object>> buildChannelWithTemplateOptions() {
      Map<String, Object> channelConfig = new LinkedHashMap<>();

      Map<String, Object> options = new LinkedHashMap<>();
      options.put("endpoint", "https://#{host}/#{path}");
      channelConfig.put("options", options);

      Map<String, Map<String, Object>> channel = new LinkedHashMap<>();
      channel.put("test-channel", channelConfig);
      return channel;
    }

    private Map<String, Map<String, Object>> buildChannelWithMergedTemplateOptions() {
      Map<String, Object> channelConfig = new LinkedHashMap<>();

      Map<String, Object> options = new LinkedHashMap<>();
      options.put("endpoint", "https://template.example.com/default");
      options.put("sender", "template-sender");
      channelConfig.put("options", options);

      Map<String, Map<String, Object>> channel = new LinkedHashMap<>();
      channel.put("test-channel", channelConfig);
      return channel;
    }

    private Map<String, Map<String, Object>> buildChannelWithSelectedAndUnselectedTemplateOptions() {
      Map<String, Object> selectedChannelConfig = new LinkedHashMap<>();
      Map<String, Object> selectedOptions = new LinkedHashMap<>();
      selectedOptions.put("endpoint", "https://selected.example.com/send");
      selectedChannelConfig.put("options", selectedOptions);

      Map<String, Object> unselectedChannelConfig = new LinkedHashMap<>();
      Map<String, Object> unselectedOptions = new LinkedHashMap<>();
      unselectedOptions.put("endpoint", "https://unselected.example.com/send");
      unselectedChannelConfig.put("options", unselectedOptions);

      Map<String, Map<String, Object>> channel = new LinkedHashMap<>();
      channel.put("test-channel", selectedChannelConfig);
      channel.put("secondary-channel", unselectedChannelConfig);
      return channel;
    }

    private Map<String, Map<String, Object>> buildChannelWithMissingAdapterChannel() {
      Map<String, Map<String, Object>> channel = new LinkedHashMap<>();
      channel.put("test-channel", new LinkedHashMap<>());
      channel.put("missing-channel", new LinkedHashMap<>());
      return channel;
    }
  }

  public static class TestNotificationAdapterSupplier implements NotificationAdapterSupplier {
    @Override
    public String getName() {
      return "test-channel";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) new TestNotificationAdapter();
    }
  }

  public static class TestNotificationAdapter implements NotificationAdapter<TestNotificationChannelSchema, TestNotificationChannelOptions> {
    private static int sendCount;
    private static TestNotificationChannelOptions lastChannelOptions;
    private static Map<String, Object> lastPayload;

    static void clear() {
      sendCount = 0;
      lastChannelOptions = null;
      lastPayload = null;
    }

    @Override
    public void send(Collection<String> addresses, TestNotificationChannelSchema channelSchema, Map<String, Object> payload,
        TestNotificationChannelOptions channelOptions) {
      sendCount++;
      lastChannelOptions = channelOptions;
      lastPayload = payload == null ? null : new LinkedHashMap<>(payload);
      if (payload != null && payload.get("throwMessage") instanceof String) {
        throw new RuntimeException((String) payload.get("throwMessage"));
      }
    }
  }

  public static class TestNotificationChannelSchema implements NotificationChannelSchema {
  }

  public static class TestNotificationChannelOptions extends TestNotificationChannelOptionsSupport implements NotificationChannelOptions {
  }

  public static class TestNotificationChannelOptionsSupport {
    private String endpoint;
    private String sender;

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getSender() {
      return sender;
    }

    public void setSender(String sender) {
      this.sender = sender;
    }
  }
}
