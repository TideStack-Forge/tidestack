package com.ouroboros.webapi;

import java.beans.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.ouroboros.model.WithExtraProps;
import com.ouroboros.webapi.openapi.*;

/**
 * WebAPI 操作级模型接口
 * 作为 OpenAPI 3.1 文档导出的统一输入，并兼容现有 OpenAPI 3.0 风格字段。
 */
public interface WebAPIModel extends WithExtraProps {
  /**
   * HTTP 方法（GET, POST, PUT, DELETE, PATCH 等）
   */
  String getMethod();

  /**
   * API 路径
   */
  String getPath();

  /**
   * API 标识（method + path）
   */
  @Transient
  String getApi();

  /**
   * API 作用域（PUBLIC, OPEN, INTERNAL）
   */
  WebAPIScope getScope();

  /**
   * 处理器类型（RequestMapping, RouterFunction, LogicFlow, BusinessAction）
   */
  HandlerType getHandlerType();

  /**
   * 简短摘要
   */
  String getSummary();

  // ========== OpenAPI 操作级扩展字段 ==========

  /**
   * 详细描述
   */
  default String getDescription() {
    return null;
  }

  /**
   * 操作 ID（用于唯一标识此操作）
   */
  default String getOperationId() {
    return null;
  }

  /**
   * 标签列表（用于分组和组织 API）
   */
  default List<String> getTags() {
    return Collections.emptyList();
  }

  /**
   * 参数列表（路径参数、查询参数、请求头参数、Cookie 参数）
   */
  default List<ParameterModel> getParameters() {
    return Collections.emptyList();
  }

  /**
   * 请求体定义
   */
  default RequestBodyModel getRequestBody() {
    return null;
  }

  /**
   * 响应定义（按状态码分类）
   * 键为状态码字符串（如 "200", "404", "default"）
   */
  default Map<String, ResponseModel> getResponses() {
    return Collections.emptyMap();
  }

  /**
   * 是否废弃
   */
  default Boolean getDeprecated() {
    return false;
  }

  /**
   * 安全要求列表
   */
  default List<SecurityRequirementModel> getSecurity() {
    return Collections.emptyList();
  }

  /**
   * 服务器列表（可覆盖全局服务器配置）
   */
  default List<ServerModel> getServers() {
    return Collections.emptyList();
  }

  /**
   * 外部文档
   */
  default ExternalDocsModel getExternalDocs() {
    return null;
  }
}
