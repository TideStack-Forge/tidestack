package com.ouroboros.scheduler.quartz;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.util.Json;

public class LogicFlowJob implements Job {
  public final static String JSON_DATA_KEY = "$JSON_DATA$";
  private String flowName;

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    var data = getData(context);
    runFlowTask(data);
  }

  // 优先取 data 值，如果没有则把整个 context.getMergedJobDataMap() 值作为 data
  protected Map<String, Object> getData(JobExecutionContext context) {
    var jsonData = Optional.ofNullable(context.getMergedJobDataMap().get(JSON_DATA_KEY))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse("");
    if (jsonData.isEmpty()) {
      return context.getMergedJobDataMap();
    }
    return Json.toMap(jsonData);
  }

  protected void runFlowTask(Map<String, Object> data) throws JobExecutionException {
    var result = getExecutionFacade().execute(flowName, List.of(data));
    if (result.isFailure()) {
      throw new JobExecutionException("执行逻辑流计划任务时，发生异常", result.getCause());
    }
  }

  protected LogicFlowExecutionFacade getExecutionFacade() {
    return SpringContextUtil.getBean(LogicFlowExecutionFacade.class);
  }
}
