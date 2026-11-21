package com.ouroboros.workflow.approval.runtime.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.event.InstanceEventDispatcher;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent.EventType;
import com.ouroboros.workflow.approval.runtime.testutil.TestObjectProviders;

import io.vavr.control.Try;

class ApprovalProcessCallbackOutboxDeliveryTest {

  @Test
  void callbackFailureIsRetriedThroughTheSameSpringOutboxFact() throws Exception {
    var executionFacade = mock(LogicFlowExecutionFacade.class);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(executionFacade.execute(anyString(), anyList()))
        .thenReturn(Try.failure(new IllegalStateException("callback failed")), Try.success("ok"));
    var listener = new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, catalogWithFlow("supplier.processCallback"));

    FlowInstanceEvent event = new FlowInstanceEvent();
    event.setFlowKey("AWF_FLOW_SINGLE_L1");
    event.setInstanceId("instance-1");
    event.setEventType(EventType.APPROVED);
    event.setEventFactId("event-fact-1");
    event.setCallbackFlow("supplier.processCallback");
    FlowEventOutbox outbox = outboxFor(event);
    TestOutboxService outboxService = new TestOutboxService(outbox, event);

    InstanceEventDispatcher dispatcher = new InstanceEventDispatcher();
    setField(dispatcher, "outboxService", outboxService);
    dispatcher.setPublisher(published -> listener.onEvent((FlowInstanceEvent) published));

    dispatcher.deliverOutbox(outbox.getId(), "worker-1", 60);
    assertThat(outbox.getStatus()).isEqualTo(FlowEventOutbox.STATUS_FAILED);

    dispatcher.deliverOutbox(outbox.getId(), "worker-2", 60);

    assertThat(outbox.getStatus()).isEqualTo(FlowEventOutbox.STATUS_SUCCESS);
    verify(executionFacade, times(2)).execute(anyString(), anyList());
    var payloadCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(executionFacade, times(2)).execute(anyString(), payloadCaptor.capture());
    assertThat(payloadCaptor.getAllValues()).allSatisfy(inputs -> {
      var payload = (Map<?, ?>) inputs.get(0);
      assertThat(payload.get("eventFactId")).isEqualTo("event-fact-1");
    });
  }

  private FlowEventOutbox outboxFor(FlowInstanceEvent event) {
    FlowEventOutbox outbox = new FlowEventOutbox();
    outbox.setId(event.getEventFactId());
    outbox.setEventFactId(event.getEventFactId());
    outbox.setEventKind(FlowEventOutbox.EVENT_KIND_INSTANCE);
    outbox.setStatus(FlowEventOutbox.STATUS_PENDING);
    return outbox;
  }

  private ApprovalProcessCallbackBindingCatalog catalogWithFlow(String flowName) {
    var flowModelProvider = mock(FlowModelProvider.class);
    when(flowModelProvider.getFlowModel(flowName))
        .thenReturn(Optional.of(mock(com.ouroboros.logicflow.model.FlowModel.class)));
    return new ApprovalProcessCallbackBindingCatalog(flowModelProvider, TestObjectProviders.empty());
  }

  private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class TestOutboxService extends FlowEventOutboxService {
    private final FlowEventOutbox outbox;
    private final FlowInstanceEvent event;

    private TestOutboxService(FlowEventOutbox outbox, FlowInstanceEvent event) {
      this.outbox = outbox;
      this.event = event;
    }

    @Override
    public Optional<Claim> claim(String id, String owner, int leaseSeconds) {
      if (!outbox.getId().equals(id) || !FlowEventOutbox.STATUS_PENDING.equals(outbox.getStatus())
          && !FlowEventOutbox.STATUS_FAILED.equals(outbox.getStatus())) {
        return Optional.empty();
      }
      outbox.setStatus(FlowEventOutbox.STATUS_PROCESSING);
      outbox.setClaimToken(owner + "-token");
      return Optional.of(new Claim(outbox, outbox.getClaimToken()));
    }

    @Override
    public Optional<FlowEventOutbox> findById(String id) {
      return outbox.getId().equals(id) ? Optional.of(outbox) : Optional.empty();
    }

    @Override
    public Optional<FlowInstanceEvent> fromInstanceJson(FlowEventOutbox ignored) {
      return Optional.of(event);
    }

    @Override
    public boolean markFailure(String id, String claimToken, String error) {
      if (!outbox.getId().equals(id) || !claimToken.equals(outbox.getClaimToken())) {
        return false;
      }
      outbox.setStatus(FlowEventOutbox.STATUS_FAILED);
      outbox.setClaimToken(null);
      outbox.setLastError(error);
      return true;
    }

    @Override
    public boolean markSuccess(String id, String claimToken) {
      if (!outbox.getId().equals(id) || !claimToken.equals(outbox.getClaimToken())) {
        return false;
      }
      outbox.setStatus(FlowEventOutbox.STATUS_SUCCESS);
      outbox.setClaimToken(null);
      return true;
    }
  }
}
