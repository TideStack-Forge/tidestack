package com.ouroboros.widget.manage.provider;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ouroboros.widget.WidgetSchema;
import com.ouroboros.widget.WidgetSchemaProvider;
import com.ouroboros.widget.manage.model.WidgetSchemaModel;
import com.ouroboros.widget.manage.service.WidgetSchemaService;

/**
 * Widget Schema Provider 实现类
 * 负责从数据库中读取 WidgetSchemaModel 并转换为 WidgetSchema
 */
@Component
public class WidgetSchemaProviderImpl implements WidgetSchemaProvider {
  private final WidgetSchemaService widgetSchemaService;

  /**
   * 构造函数
   *
   * @param widgetSchemaService Widget Schema 服务
   */
  public WidgetSchemaProviderImpl(WidgetSchemaService widgetSchemaService) {
    this.widgetSchemaService = widgetSchemaService;
    Proxy.delegate = this;
  }

  /**
   * 获取所有 Widget Schema
   * 从数据库读取 WidgetSchemaModel 并转换为 WidgetSchema
   *
   * @return Widget Schema 列表
   */
  @Override
  public List<WidgetSchema> getWidgetSchemas() {
    return widgetSchemaService.getAllWidgetSchemas()
        .stream()
        .map(WidgetSchemaModel::toWidgetSchema)
        .collect(Collectors.toList());
  }

  /**
   * Proxy 类
   * 用于代理 WidgetSchemaProviderImpl，提供 SPI 暴露点
   */
  public static class Proxy implements WidgetSchemaProvider {
    private static WidgetSchemaProvider delegate;

    /**
     * 获取 Widget Schema 列表
     * 通过代理实现延迟初始化
     *
     * @return Widget Schema 列表
     */
    @Override
    public List<WidgetSchema> getWidgetSchemas() {
      if (delegate == null) {
        return Collections.emptyList();
      }
      return delegate.getWidgetSchemas();
    }
  }
}
