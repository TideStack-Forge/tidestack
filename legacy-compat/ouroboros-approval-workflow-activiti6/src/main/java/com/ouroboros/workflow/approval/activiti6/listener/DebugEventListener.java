package com.ouroboros.workflow.approval.activiti6.listener;

import java.util.stream.Collectors;

import org.activiti.engine.delegate.event.ActivitiEvent;
import org.activiti.engine.delegate.event.ActivitiEventListener;
import org.activiti.engine.delegate.event.impl.ActivitiEntityEventImpl;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.util.Json;

public class DebugEventListener implements ActivitiEventListener {
  final static Logger logger = LoggerFactory.getLogger(DebugEventListener.class);

  @Override
  public void onEvent(ActivitiEvent event) {
    String logPrefix = String.format(
        "Flow Event: %s, Instance Id: %s", event.getType(), event.getProcessInstanceId()
    );

    logger.trace("{} - Data: {}", logPrefix, Json.toJsonString(event));

    if (event instanceof ActivitiEntityEventImpl) {
      ActivitiEntityEventImpl eventImpl = (ActivitiEntityEventImpl) event;
      if (eventImpl.getEntity() instanceof TaskEntity) {
        TaskEntity task = (TaskEntity) eventImpl.getEntity();
        logger.debug(
            "{} - Task ID: {}, Name: {}, Candidates: {}, Assignee: {}",
            logPrefix,
            task.getId(),
            task.getName(),
            task.getCandidates().stream().map(candidate ->
                candidate.getUserId() != null ? candidate.getUserId() : candidate.getGroupId()
            ).collect(Collectors.joining(", ")),
            task.getAssignee()
        );
      }
    }
  }

  @Override
  public boolean isFailOnException() {
    return false;
  }
}
