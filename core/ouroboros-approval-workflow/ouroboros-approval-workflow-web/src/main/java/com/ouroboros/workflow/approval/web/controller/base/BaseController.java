package com.ouroboros.workflow.approval.web.controller.base;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.common.service.ModelService;
import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Maps;
import com.ouroboros.web.PagedResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
public class BaseController<PK, M, E extends M, S extends ModelService<PK, M>> {
  private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

  @Autowired
  protected S service;

  @Operation(
      summary = "创建审批工作流基础记录",
      description = "创建当前控制器对应的审批工作流基础记录。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "记录创建成功")
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WebApiResult<M> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增记录参数", required = true)
      @RequestBody Map<String, Object> params) {
    return WebApiResult.ok(service.add(mapToEntity(params)));
  }

  @Operation(
      summary = "部分更新审批工作流基础记录",
      description = "根据记录 ID 局部更新当前控制器对应的审批工作流基础记录。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "记录部分更新成功"),
      @ApiResponse(responseCode = "404", description = "记录不存在")
  })
  @PatchMapping("/{id}")
  public WebApiResult<M> patchUpdate(
      @Parameter(description = "记录 ID", required = true) @PathVariable("id") PK id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "局部更新字段", required = true)
      @RequestBody Map<String, Object> params) {
    Boolean status = service.partialUpdate(id, params);
    if (!status) {
      throw new PlatformException("更新失败!");
    }

    return WebApiResult.ok(service.getByPrimaryKey(id));
  }

  @Operation(
      summary = "全量更新审批工作流基础记录",
      description = "根据记录 ID 全量更新当前控制器对应的审批工作流基础记录。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "记录更新成功"),
      @ApiResponse(responseCode = "404", description = "记录不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<M> putUpdate(
      @Parameter(description = "记录 ID", required = true) @PathVariable("id") PK id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "完整记录参数", required = true)
      @RequestBody Map<String, Object> params) {
    Boolean status = service.update(id, mapToEntity(params));
    if (!status) {
      throw new PlatformException("更新失败!");
    }

    return WebApiResult.ok(service.getByPrimaryKey(id));
  }

  @Operation(
      summary = "删除审批工作流基础记录",
      description = "根据记录 ID 删除当前控制器对应的审批工作流基础记录。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "记录删除成功"),
      @ApiResponse(responseCode = "404", description = "记录不存在")
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@Parameter(description = "记录 ID", required = true) @PathVariable("id") PK id) {
    M m = service.getByPrimaryKey(id);
    if (m == null) {
      throw new PlatformException("删除失败: 未找到该记录!");
    }

    Boolean status = service.delete(id);
    if (!status) {
      throw new PlatformException("删除失败!");
    }
  }

  @Operation(
      summary = "获取审批工作流基础记录详情",
      description = "根据记录 ID 获取当前控制器对应的审批工作流基础记录详情。",
      tags = {"approval-workflow"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回记录详情")
  })
  @GetMapping("/{id}")
  public WebApiResult<M> get(@Parameter(description = "记录 ID", required = true) @PathVariable("id") PK id) {
    return WebApiResult.ok(service.getByPrimaryKey(id));
  }

  /**
   * 分页查询
   *
   * @param statement
   * @return
   */
  protected PagedResult<? extends M> pageSearch(Map<String, Object> statement, int page, int perPage) {
    Map<String, Object> where = (Map<String, Object>) statement.getOrDefault(Keyword.WHERE.toString(), new HashMap<>());

    // 查询总数
    Long count = service.count(where);

    // 增加分页
    statement.put("skip", (page - 1) * perPage);
    statement.put("limit", perPage);

    // 查询列表
    List<? extends M> list = count > 0 ? service.searchList(statement) : Collections.emptyList();

    return PagedResult.of(count, list);
  }

  /**
   * map转实体
   *
   * @param map
   * @return
   */
  protected E mapToEntity(Map<String, Object> map) {
    return Maps.tryToBean(getEntityClass(), map)
        .getOrElseThrow(error -> {
          logger.error("Failed to convert map to entity {}: {}", getEntityClass().getSimpleName(), error.getMessage());
          return new ValidationException("Invalid request data: " + error.getMessage());
        });
  }

  /**
   * 获取实体类
   *
   * @return
   */
  protected Class<E> getEntityClass() {
    return getSuperclassActualArgumentType(2);
  }

  /**
   * 获取超类实际参数类型
   *
   * @param index
   * @param <T>
   * @return
   */
  protected <T> T getSuperclassActualArgumentType(int index) {
    return (T) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[index];
  }
}
