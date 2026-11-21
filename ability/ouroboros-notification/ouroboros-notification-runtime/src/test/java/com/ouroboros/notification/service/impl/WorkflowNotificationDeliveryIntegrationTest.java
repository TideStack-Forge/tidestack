package com.ouroboros.notification.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.event.BusinessEventDisclosure;
import com.ouroboros.businessmodel.event.BusinessEventListener;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.businessmodel.event.BusinessEventProducer;
import com.ouroboros.businessmodel.event.BusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.event.DefaultBusinessEventPort;
import com.ouroboros.businessmodel.event.DefaultBusinessEventSubscriptionRuntime;
import com.ouroboros.businessmodel.event.store.BusinessEventDeliveryProperties;
import com.ouroboros.businessmodel.event.store.BusinessEventOutbox;
import com.ouroboros.businessmodel.event.store.DataModelBusinessEventOccurrenceRecorder;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.workflow.WorkflowBusinessFactProjectionAutoConfiguration;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.behavior.BusinessEventNotificationListener;
import com.ouroboros.notification.behavior.NotificationBusinessEventAutoConfiguration;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.SPI;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.service.FlowInstanceFactStateService;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real source and receiver persistence, official Spring/SPI ownership and both recoverable delivery boundaries. */
class WorkflowNotificationDeliveryIntegrationTest {
  private static final String MODEL = "cases.Document";

  @Test
  void committedApprovalTransfersToOneGeneratedSubscriptionAndRecreatedWorkerRetriesOnlyItsUnfinishedSend() {
    var fixture = new ReliableNotificationDispatchIntegrationTest.Fixture();
    fixture.expectedRecipientCount = 1;
    addWorkflowModels(fixture);
    var model = new AtomicReference<DefaultBusinessModel>();
    var behavior = new AtomicReference<BusinessModelBehavior>();
    var listener = new AtomicReference<BusinessEventListener>();
    var provider = new BusinessModelBehaviorProvider() {
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) {
        return MODEL.equals(name) ? Optional.ofNullable(behavior.get()) : Optional.empty();
      }
      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() { return Map.of(MODEL, behavior.get()); }
    };
    var source = new FlowEventOutboxService(); var states = new FlowInstanceFactStateService(); var cases = new ApprovalCaseService();
    var users = mock(UserCenter.class); var employees = mock(EmployeeAggregator.class);
    var consumer = mock(User.class); when(consumer.getUserIdentity()).thenReturn("notification-user"); when(consumer.isEnabled()).thenReturn(true);
    when(users.getUser(anyString())).thenReturn(Optional.of(consumer));
    var employee = mock(Employee.class); when(employee.getUser()).thenReturn("notification-user");
    when(employees.getById("alice")).thenReturn(Optional.of(employee));
    BusinessEventDisclosure disclosure = occurrence -> true;
    var runtime = new DefaultBusinessEventSubscriptionRuntime(provider, mock(BusinessActionRuntime.class),
        () -> List.of(listener.get()), identities(users), fixture.store.manager, disclosure);
    var producer = new AtomicReference<BusinessEventProducer>();
    var port = new DefaultBusinessEventPort(provider, () -> List.of(producer.get()),
        new DataModelBusinessEventOccurrenceRecorder(provider, ignored -> { }), fixture.store.manager);
    var transaction = new TransactionTemplate(fixture.store.manager);
    try (var center = mockStatic(BusinessModelCenter.class); var spi = mockStatic(SPI.class, CALLS_REAL_METHODS)) {
      center.when(() -> BusinessModelCenter.getBusinessModel(MODEL)).thenAnswer(ignored -> Optional.ofNullable(model.get()));
      center.when(BusinessModelCenter::getBusinessModelMap).thenAnswer(ignored -> Map.of(MODEL, model.get()));
      spi.when(() -> SPI.getSortedServiceStream(BusinessModelBehaviorProvider.class)).thenAnswer(ignored -> java.util.stream.Stream.of(provider));
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WorkflowBusinessFactProjectionAutoConfiguration.class,
          NotificationBusinessEventAutoConfiguration.class))

          .withBean(SpringContextUtil.class)
          .withBean(org.springframework.transaction.PlatformTransactionManager.class, () -> fixture.store.manager)
          .withBean(BusinessEventPort.class, () -> port).withBean(BusinessEventDisclosure.class, () -> disclosure)
          .withBean(BusinessEventSubscriptionRuntime.class, () -> runtime)
          .withBean(FlowEventOutboxService.class, () -> source).withBean(FlowInstanceFactStateService.class, () -> states)
          .withBean(ApprovalCaseService.class, () -> cases).withBean(FlowTaskService.class, () -> mock(FlowTaskService.class))
          .withBean(FlowDefinitionService.class, () -> mock(FlowDefinitionService.class))
          .withBean(EmployeeNotificationDispatcher.class, fixture::dispatcher)
          .withBean(com.ouroboros.configuration.ConfigurationService.class, () -> mock(com.ouroboros.configuration.ConfigurationService.class))
          .withBean(com.ouroboros.notification.service.NotificationService.class, fixture.store::service)
          .withBean(com.ouroboros.notification.NotificationPayloadSchemaValidator.class)
          .withBean(com.ouroboros.notification.service.NotificationCoreService.class, NotificationCoreServiceImpl::new)
          .withBean(com.ouroboros.notification.service.NotificationStateService.class, NotificationStateServiceImpl::new)
          .withBean(NotificationEmployeeAddressParser.class, () -> fixture.parser)
          .withBean(EmployeeAggregator.class, () -> employees).withBean(UserCenter.class, () -> users)
          .run(context -> {
            assertNull(context.getStartupFailure());
            listener.set(context.getBean(BusinessEventNotificationListener.class));
            producer.set(context.getBean(BusinessEventProducer.class));
            var meta = new DefaultBusinessModelMeta(); meta.setNamespace("cases"); meta.setName("Document"); meta.setIsEnabled(true);
            var id = new BusinessModelFieldMeta(); id.setName("id"); id.setType("单行文本"); id.setIsPrimaryKey(true); meta.setFields(List.of(id));
            meta.setExtraProp("workflow", Map.of("isEnabled", true, "bindings", List.of(Map.of("processId", "approval"))));
            model.set(new DefaultBusinessModel(meta, BusinessModelCenter.buildInProvider)); behavior.set(model.get().getBehavior());
            assertEquals(4, behavior.get().getEventSubscriptions().size()); assertTrue(meta.getEventSubscriptions().isEmpty());
            fixture.withDispatch(() -> {
              fixture.store.models.get(MODEL).insert(Map.of("id", "42")).get();
              var event = transaction.execute(tx -> {
                var submission = cases.reserveSubmission(MODEL, "42", "approval");
                var fact = new FlowInstanceEvent(); fact.setEventFactId(UUID.randomUUID().toString());
                fact.setFlowKey("approval"); fact.setInstanceId("instance"); fact.setEventType(FlowInstanceEvent.EventType.APPROVED);
                fact.setOccurredAt(Instant.parse("2026-10-30T00:00:00Z")); fact.setStarter("alice");
                fact.setBusinessContext(new FlowInstanceEvent.BusinessContext(MODEL, "42")); fact.setCallbackFlow(MODEL + ".processCallback");
                fact.setApprovalCaseId(submission.approvalCaseId()); fact.setSubmissionRound(submission.submissionRound());
                var notification = new com.ouroboros.workflow.approval.definition.FlowDefinition.Notification();
                notification.setInstanceCompletedTemplate("notice"); fact.setNotification(notification);
                states.restoreIdentity(fact); cases.lockForSource(fact); states.advance(fact); cases.applyInstanceEvent(fact); source.enqueue(fact);
                return fact;
              });
              assertNotNull(event.getBusinessNotificationOwner());
              var reloaded = source.fromInstanceJson(source.findById(event.getEventFactId()).orElseThrow()).orElseThrow();
              assertEquals(event.getBusinessNotificationOwner(), reloaded.getBusinessNotificationOwner());
              var callbacks = new AtomicInteger(); var projection = context.getBean(ApprovalBusinessFactProjection.class);
              var binding = new CallbackBinding("approval", MODEL + ".processCallback", MODEL, null, Map.of());
              assertTrue(projection.projectInstance(reloaded, binding, callbacks::incrementAndGet));
              var occurrence = fixture.store.models.get(DataModelBusinessEventOccurrenceRecorder.MODEL_FULL_NAME).query(Map.of()).get().getFirst();
              fixture.factId = occurrence.get("id").toString();
              fixture.ruleIdentity = "business-event:" + MODEL + "#notifyWorkflowApproved";
              fixture.response = ignored -> new NotificationChannelResult(NotificationChannelResult.Outcome.RETRYABLE, "not-accepted");
              worker(fixture, runtime).deliverEvent(fixture.factId);
              assertEquals(1, fixture.calls.size()); assertEquals(1, callbacks.get());
              String sendKey = fixture.calls.getFirst().key();
              assertTrue(projection.projectInstance(reloaded, binding, callbacks::incrementAndGet)); assertEquals(1, callbacks.get());
              var delivery = fixture.store.models.get(BusinessEventOutbox.MODEL_FULL_NAME).query(Map.of()).get().getFirst();
              fixture.response = ignored -> new NotificationChannelResult(NotificationChannelResult.Outcome.ACCEPTED, "accepted");
              assertEquals("FAILED", delivery.get("status"));
              fixture.store.clock.advance(java.time.Duration.ofMinutes(2));
              worker(fixture, runtime).sweep();
              assertEquals(2, fixture.calls.size()); assertEquals(sendKey, fixture.calls.getLast().key());
              assertEquals("SUCCESS", fixture.store.models.get(BusinessEventOutbox.MODEL_FULL_NAME).get(delivery.get("id")).get().get("status"));
              assertFalse(worker(fixture, runtime).redrive(delivery.get("id").toString()));
              assertEquals(2, fixture.calls.size()); assertEquals(1, callbacks.get());
              return null;
            });
          });
    }
  }

  private BusinessEventOutbox worker(ReliableNotificationDispatchIntegrationTest.Fixture fixture, BusinessEventSubscriptionRuntime runtime) {
    return new BusinessEventOutbox(fixture.store.manager, () -> runtime, new BusinessEventDeliveryProperties(), fixture.store.clock);
  }

  private void addWorkflowModels(ReliableNotificationDispatchIntegrationTest.Fixture fixture) {
    var metas = new ArrayList<>(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.event.store").values());
    metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.businessmodel.workflow.store").values());
    metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").values().stream()
        .filter(meta -> List.of("FlowEventOutbox", "FlowInstanceFactState").contains(meta.getFullName())).toList());
    metas.addAll(TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.lifecycle").values().stream()
        .filter(meta -> "ApprovalCase".equals(meta.getFullName())).toList());
    var data = new DataModelMeta(); data.setNamespace("cases"); data.setName("Document"); data.setRawName("workflow_notice_document");
    var id = new DataModelFieldMeta(); id.setName("id"); id.setRawName("id"); id.setType("String");
    data.setPrimaryKeys(List.of("id")); data.setFields(List.of(id)); metas.add(data);
    String suffix = "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    metas.forEach(meta -> meta.setRawName(meta.getRawName() + suffix));
    var station = new SqlDataStation("workflow-notification-integration", fixture.store.manager.getDataSource(), metas,
        MigrationStrategy.AUTO, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL") == null ? "H2" : "MySQL");
    station.generateMigrationSql(station.getDataModelList()).get().forEach(fixture.store.jdbc::execute);
    station.getDataModelList().forEach(model -> fixture.store.models.put(model.getFullName(), model));
  }
  private static com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver identities(UserCenter users) {
    var settings = new com.ouroboros.businessmodel.event.BusinessEventConsumerProperties();
    settings.setPrincipals(Map.of(MODEL, "notification-user"));
    return new com.ouroboros.businessmodel.event.BusinessEventConsumerIdentityResolver(settings, users);
  }
}
