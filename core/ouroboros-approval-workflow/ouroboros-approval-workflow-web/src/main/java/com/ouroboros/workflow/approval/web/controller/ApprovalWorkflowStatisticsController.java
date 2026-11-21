package com.ouroboros.workflow.approval.web.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.model.FlowDurationStatistic;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

@RestController
@RequestMapping("/api/approval-workflow/statistics")
public class ApprovalWorkflowStatisticsController {
  private static final Duration MAX_DURATION_RANGE = Duration.ofDays(366);

  @Autowired
  FlowInstanceService flowInstanceService;

  @Autowired
  UsernameResolverRegistry usernameResolverRegistry;

  @Operation(
      summary = "统计审批流时长",
      description = "按流程和时间范围返回流程、节点、审批人三个维度的时长统计。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回审批流时长统计结果")
  })
  @GetMapping("/duration")
  public WebApiResult<FlowDurationStatistic> duration(
      @Parameter(description = "流程定义 ID", required = true) @RequestParam String flowKey,
      @Parameter(description = "统计开始时间，ISO-8601 格式", required = true) @RequestParam String from,
      @Parameter(description = "统计结束时间，ISO-8601 格式", required = true) @RequestParam String to) {
    return WebApiResult.of(Try.of(() -> {
      var fromDate = parseIso8601(from);
      var toDate = parseIso8601(to);
      validateRange(fromDate, toDate);

      var statistic = flowInstanceService.getDurationStatistic(flowKey, fromDate, toDate);
      resolveApproverNames(statistic);
      return statistic;
    }));
  }

  private Date parseIso8601(String value) {
    if (StringUtils.isBlank(value)) {
      throw invalidRange();
    }
    try {
      return Date.from(Instant.parse(value));
    } catch (DateTimeParseException first) {
      try {
        return Date.from(OffsetDateTime.parse(value).toInstant());
      } catch (DateTimeParseException second) {
        try {
          return Date.from(LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException third) {
          throw invalidRange();
        }
      }
    }
  }

  private void validateRange(Date from, Date to) {
    if (from.after(to)) {
      throw invalidRange();
    }
    if (Duration.between(from.toInstant(), to.toInstant()).compareTo(MAX_DURATION_RANGE) > 0) {
      throw invalidRange();
    }
  }

  private void resolveApproverNames(FlowDurationStatistic statistic) {
    var approvers = statistic == null || statistic.getApprovers() == null
        ? Collections.<FlowDurationStatistic.ApproverDurationStatistic>emptyList()
        : statistic.getApprovers();
    var userIds = approvers.stream()
        .map(FlowDurationStatistic.ApproverDurationStatistic::getUserId)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());
    Map<String, String> userNameMap = usernameResolverRegistry.resolve(userIds);
    approvers.stream()
        .filter(Objects::nonNull)
        .forEach(approver -> approver.setUserName(
            userNameMap.getOrDefault(approver.getUserId(), approver.getUserId())));
  }

  private BusinessException invalidRange() {
    return new BusinessException("invalid_range");
  }
}
