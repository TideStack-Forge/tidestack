package com.ouroboros.scheduler.quartz;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import io.vavr.control.Try;

import com.ouroboros.logicflow.LogicFlowExecutionFacade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogicFlowJobTest {

  @Test
  void getDataShouldReturnMergedJobDataMapWhenJsonDataIsMissing() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    TestLogicFlowJob job = new TestLogicFlowJob(executionFacade);
    JobDataMap data = new JobDataMap();
    data.put("name", "alice");

    Map<String, Object> actual = job.getData(contextWith(data));

    assertSame(data, actual);
  }

  @Test
  void getDataShouldParseJsonDataWhenPresent() {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    TestLogicFlowJob job = new TestLogicFlowJob(executionFacade);
    JobDataMap data = new JobDataMap();
    data.put(LogicFlowJob.JSON_DATA_KEY, "{\"name\":\"alice\"}");
    data.put("ignored", "value");

    Map<String, Object> actual = job.getData(contextWith(data));

    assertEquals("alice", actual.get("name"));
  }

  @Test
  void executeShouldDelegateToExecutionFacadeWithSchedulerRequest() throws Exception {
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(org.mockito.ArgumentMatchers.anyString(), any())).thenReturn(Try.success("ok"));
    TestLogicFlowJob job = new TestLogicFlowJob(executionFacade);
    job.setFlowName("nightly-sync");
    JobDataMap data = new JobDataMap();
    data.put("tenantId", "acme");

    job.execute(contextWith(data));

    ArgumentCaptor<List> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(executionFacade).execute(eq("nightly-sync"), inputsCaptor.capture());
    List inputs = inputsCaptor.getValue();
    assertEquals(1, inputs.size());
    assertSame(data, inputs.get(0));
  }

  @Test
  void executeShouldTranslateFacadeFailureToJobExecutionException() {
    RuntimeException failure = new RuntimeException("boom");
    LogicFlowExecutionFacade executionFacade = mock(LogicFlowExecutionFacade.class);
    when(executionFacade.execute(org.mockito.ArgumentMatchers.anyString(), any())).thenReturn(Try.failure(failure));
    TestLogicFlowJob job = new TestLogicFlowJob(executionFacade);
    job.setFlowName("nightly-sync");
    JobDataMap data = new JobDataMap();
    data.put("tenantId", "acme");

    JobExecutionException error = assertThrows(JobExecutionException.class, () -> job.execute(contextWith(data)));

    assertSame(failure, error.getCause());
  }

  private JobExecutionContext contextWith(JobDataMap data) {
    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getMergedJobDataMap()).thenReturn(data);
    return context;
  }

  private static class TestLogicFlowJob extends LogicFlowJob {
    private final LogicFlowExecutionFacade executionFacade;

    private TestLogicFlowJob(LogicFlowExecutionFacade executionFacade) {
      this.executionFacade = executionFacade;
    }

    @Override
    protected LogicFlowExecutionFacade getExecutionFacade() {
      return executionFacade;
    }
  }
}
