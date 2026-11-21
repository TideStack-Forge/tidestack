package com.ouroboros.notification.internal.message.controller;

import java.util.*;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.internal.message.constant.InternalMessagePlatformEnum;
import com.ouroboros.notification.internal.message.constant.InternalMessageStateEnum;
import com.ouroboros.notification.internal.message.service.NotificationInternalMessageService;
import com.ouroboros.notification.internal.message.service.model.NotificationInternalMessageModel;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/notification")
public class InternalMessageNotificationController {

  @Autowired
  private NotificationInternalMessageService service;

  @PermitAll
  @Operation(
      summary = "获取未读站内信数量",
      description = "查询当前登录用户的未读站内信总数。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回未读站内信数量")
  })
  @GetMapping("/unread-count")
  public WebApiResult<Map<String, ?>> unreadCount() {
    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);

    if (currentEmployee == null) {
      return WebApiResult.ok(Collections.singletonMap("total", 0));
    }
    try {
      Long total = service.unreadCount(currentEmployee);
      return WebApiResult.ok(Collections.singletonMap("total", total));
    } catch (Exception e) {
      return WebApiResult.fail(e.getMessage());
    }
  }

  @PermitAll
  @Operation(
      summary = "分页查询站内信",
      description = "按平台、类型和状态分页查询当前登录用户的站内信列表。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回站内信分页结果"),
      @ApiResponse(responseCode = "400", description = "平台或消息状态参数不合法")
  })
  @GetMapping
  public PagedWebApiResult<?> search(
      @Parameter(description = "站内信平台标识", required = true) @RequestParam(value = "platform") String platform,
      @Parameter(description = "消息类型") @RequestParam(value = "type", required = false) String type,
      @Parameter(description = "消息状态") @RequestParam(value = "state", required = false) String state,
      @Parameter(description = "页码") @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(value = "perPage", required = false, defaultValue = "10") Integer perPage) {
    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);

    if (currentEmployee == null) {
      return PagedWebApiResult.ok(0L, Collections.emptyList());
    }

    InternalMessagePlatformEnum platformEnum = InternalMessagePlatformEnum.valueOfEnum(platform)
        .orElseThrow(() -> new BusinessException("错误的平台: " + platform));

    Map<String, Object> where = new HashMap<>();
    where.put("employee", currentEmployee);
    where.put("platform", Collections.singletonMap("like", platformEnum.getValue()));

    if (ObjectUtils.isNotEmpty(state)) {
      InternalMessageStateEnum stateEnum = InternalMessageStateEnum.valueOfEnum(state)
          .orElseThrow(() -> new BusinessException("错误的消息状态: " + state));

      where.put("state", stateEnum.getValue());
    }

    if (ObjectUtils.isNotEmpty(type)) {
      where.put("type", type);
    }

    Map<String, Object> statement = new HashMap<>();
    statement.put("where", where);
    statement.put("skip", (page - 1) * perPage);
    statement.put("limit", perPage);
    statement.put("order", Arrays.asList("state desc", "receivingTime desc"));

    // 查询数据
    Long total = service.count(where);
    List<? extends NotificationInternalMessageModel> list = service.searchList(statement);
    return PagedWebApiResult.ok(total, list);
  }

  @PermitAll
  @Operation(
      summary = "标记单条站内信为已读",
      description = "将当前登录用户的一条站内信标记为已读。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "站内信已标记为已读"),
      @ApiResponse(responseCode = "404", description = "站内信不存在、已读或不属于当前用户")
  })
  @PatchMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
      @Parameter(description = "站内信 ID", required = true) @PathVariable("id") String id) {
    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElseThrow(() -> new BusinessException("当前用户不存在"));

    if (!service.markReadById(currentEmployee, id)) {
      throw new BusinessException(404, "站内信不存在、已读或不属于当前用户");
    }
  }

  @PermitAll
  @Operation(
      summary = "批量标记站内信为已读",
      description = "按消息 ID 列表批量标记当前登录用户的站内信为已读。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "站内信已批量标记为已读"),
      @ApiResponse(responseCode = "400", description = "messageIds 参数缺失")
  })
  @PatchMapping("/mark-read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待标记为已读的消息 ID 列表", required = true)
      @RequestBody Map<String, Object> data) {
    if (data == null || !data.containsKey("messageIds")) {
      throw new BusinessException("参数错误，messageIds不能为空");
    }

    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElseThrow(() -> new BusinessException("当前用户不存在"));

    List<String> messageIds = (List<String>) data.get("messageIds");
    if (messageIds.isEmpty()) {
      return;
    }

    service.markReadByIds(currentEmployee, messageIds);
  }

  @PermitAll
  @Operation(
      summary = "全部标记为已读",
      description = "将当前登录用户的所有站内信标记为已读。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "全部站内信已标记为已读")
  })
  @PatchMapping("/me-all/mark-read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markRead() {
    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElseThrow(() -> new BusinessException("当前用户不存在"));

    service.markReadByEmployee(currentEmployee);
  }
}
