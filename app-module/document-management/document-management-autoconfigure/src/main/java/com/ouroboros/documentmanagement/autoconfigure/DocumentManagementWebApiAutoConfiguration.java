package com.ouroboros.documentmanagement.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 文档管理 Web API 自动配置
 *
 * <p>自动扫描并注册所有 REST API Controllers
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnProperty(prefix = "ouroboros.document-management", name = "enable-web-api", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.ouroboros.documentmanagement.webapp.controller")
public class DocumentManagementWebApiAutoConfiguration {
  // 通过 @ComponentScan 自动扫描并注册所有 Controllers
}
