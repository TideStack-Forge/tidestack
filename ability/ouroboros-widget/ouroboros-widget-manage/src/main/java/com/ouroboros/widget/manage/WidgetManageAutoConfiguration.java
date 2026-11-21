package com.ouroboros.widget.manage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.widget.WidgetSchemaProvider;
import com.ouroboros.widget.manage.provider.WidgetSchemaProviderImpl;
import com.ouroboros.widget.manage.service.WidgetSchemaService;

@ComponentScan
@DataModelScan
public class WidgetManageAutoConfiguration {

  /**
   * 创建 WidgetSchemaProvider 实现实例
   * Proxy 的 delegate 将在 WidgetSchemaProviderImpl 构造函数中自动设置
   *
   * @param widgetSchemaService Widget Schema 服务
   * @return WidgetSchemaProvider 实现
   */
  @Bean
  public WidgetSchemaProvider widgetSchemaProvider(WidgetSchemaService widgetSchemaService) {
    return new WidgetSchemaProviderImpl(widgetSchemaService);
  }
}
