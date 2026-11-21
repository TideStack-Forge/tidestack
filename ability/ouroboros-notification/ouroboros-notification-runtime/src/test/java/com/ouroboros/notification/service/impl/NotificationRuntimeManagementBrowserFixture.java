package com.ouroboros.notification.service.impl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.controller.NotificationAdapterSchemaController;
import com.ouroboros.notification.controller.NotificationTemplateController;
import com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService;
import com.ouroboros.notification.template.NotificationTemplateDefinitionValidator;
import com.ouroboros.notification.template.provider.DatabaseNotificationTemplateProvider;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Loopback-only browser fixture: real controllers/use cases/QueryDSL/transactions,
 * bridged through MockMvc. Authentication and external suppliers are fixture boundaries.
 * Uses the existing isolated MySQL environment variables, or an isolated H2 database.
 */
public final class NotificationRuntimeManagementBrowserFixture {
  private NotificationRuntimeManagementBrowserFixture() { }

  public static void main(String[] args) throws Exception {
    var fixture = new NotificationTemplatePersistenceIntegrationTest.Fixture(false);
    ReflectionTestUtils.setField(DataModelCenter.class, "dataModelMap", Map.of("NotificationTemplateModel", fixture.model));
    var persistence = new NotificationTemplateServiceImpl();
    persistence.initializeRuntimeManagement();
    new DatabaseNotificationTemplateProvider(persistence);
    SPI.clearCache();
    var management = new NotificationTemplateRuntimeManagementService(persistence,
        new NotificationTemplateDefinitionValidator(mock(ConfigurationService.class)), fixture.manager);
    var templates = new NotificationTemplateController();
    ReflectionTestUtils.setField(templates, "service", persistence);
    ReflectionTestUtils.setField(templates, "management", management);
    var channels = new NotificationAdapterSchemaController();
    ReflectionTestUtils.setField(channels, "management", management);
    var configurationContext = configurationContext(fixture);
    var configuration = new com.ouroboros.configuration.controller.ConfigurationController();
    ReflectionTestUtils.setField(configuration, "service", configurationContext.getBean(com.ouroboros.configuration.service.RuntimeConfigurationService.class));
    var workbench = new com.ouroboros.configuration.controller.ConfigurationWorkbenchController();
    ReflectionTestUtils.setField(workbench, "workbenchService", configurationContext.getBean(com.ouroboros.configuration.service.ConfigurationWorkbenchService.class));
    var mvc = MockMvcBuilders.standaloneSetup(templates, channels, configuration, workbench)
        .setControllerAdvice(new Errors(), new WebApiResultHttpStatusAdvice()).build();
    var grant = mock(com.ouroboros.security.authority.OuroborosGrantedAuthority.class);
    when(grant.getAuthority()).thenReturn("systemSetting.configurationManagement");
    when(grant.getAccessControlParams()).thenReturn(com.ouroboros.security.authority.accesscontrol.AccessControlParams.empty());
    var role = mock(com.ouroboros.security.rbac.Role.class);
    org.mockito.Mockito.doReturn(List.of(grant)).when(role).getAuthorities();
    var user = mock(com.ouroboros.security.rbac.User.class);
    when(user.getRoles()).thenReturn(List.of(role)); when(user.getUserIdentity()).thenReturn("browser-fixture-admin");
    var identity = new com.ouroboros.security.identity.DefaultSecurityIdentity(user);
    var authorityBridge = new com.ouroboros.security.rbac.provider.SpringBeanAuthorityProvider();
    ReflectionTestUtils.setField(authorityBridge, "authorityProviders", List.of(new com.ouroboros.security.rbac.provider.JarAuthorityProvider()));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), 0);
    server.createContext("/api/", exchange -> {
      try {
        byte[] body = exchange.getRequestBody().readAllBytes();
        var result = CurrentSecurityContextHolder.withIdentity(identity, () -> {
          try {
            return mvc.perform(request(HttpMethod.valueOf(exchange.getRequestMethod()),
                URI.create(exchange.getRequestURI().toString())).contentType("application/json").content(body))
                .andReturn().getResponse();
          } catch (Exception error) { throw new IllegalStateException("Browser fixture dispatch failed", error); }
        });
        byte[] response = result.getContentAsByteArray();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(result.getStatus(), response.length);
        exchange.getResponseBody().write(response);
      } catch (RuntimeException error) {
        error.printStackTrace(System.err);
        exchange.sendResponseHeaders(500, -1);
      } finally { exchange.close(); }
    });
    server.setExecutor(Executors.newFixedThreadPool(4));
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); configurationContext.close(); }));
    server.start();
    System.out.println("Notification management browser fixture ready: " + server.getAddress());
  }

  private static org.springframework.context.annotation.AnnotationConfigApplicationContext configurationContext(
      NotificationTemplatePersistenceIntegrationTest.Fixture fixture) {
    var metadata = new java.util.LinkedHashMap<String, com.ouroboros.configuration.metadata.ConfigurationMetadata>();
    for (String suffix : List.of("host", "authorizationCode")) {
      var item = new com.ouroboros.configuration.metadata.ConfigurationMetadata();
      item.setKey("notification.email." + suffix); item.setModifiable(true); item.setNullable(true);
      item.setGroupPath("notification.email"); item.setLabel(suffix.equals("host") ? "邮件通知主机" : "邮件通知授权码");
      item.setValueType(com.ouroboros.configuration.metadata.ConfigValueType.STRING);
      item.setExtraProp("restoreDefaultRemovesOverride", true);
      if (suffix.equals("authorizationCode")) {
        item.setExtraProp("managementAccessPolicyRequired", true);
        item.setEditorSchema(new com.ouroboros.uischema.schema.SchemaNode(Map.of("type", "input-password")));
      }
      metadata.put(item.getKey(), item);
    }
    ReflectionTestUtils.setField(com.ouroboros.configuration.center.ConfigurationMetadataCenter.class, "metadataMap", metadata);
    ReflectionTestUtils.setField(com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter.class, "metadataMap", Map.of());
    var meta = com.ouroboros.data.typed.runtime.TypedModelMetaHelper.scanModelMate("com.ouroboros.configuration.model").get("RuntimeConfigurationValueModel");
    meta.setRawName("t_browser_configuration_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    var source = fixture.manager.getDataSource();
    var station = new com.ouroboros.data.station.SqlDataStation("browser-configuration", source, List.of(meta),
        com.ouroboros.data.model.MigrationStrategy.AUTO, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL") == null ? "H2" : "MySQL");
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
    var model = new com.ouroboros.data.model.BaseTypedDataModel<String, com.ouroboros.configuration.model.RuntimeConfigurationValueModel>(
        station.getDataModelList().getFirst(), com.ouroboros.configuration.model.RuntimeConfigurationValueModel.class);
    var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
    context.registerBean("dataModel", com.ouroboros.data.model.BaseTypedDataModel.class, () -> model);
    context.registerBean("transactionManager", org.springframework.jdbc.datasource.DataSourceTransactionManager.class, () -> fixture.manager);
    context.registerBean("cacheManager", org.springframework.cache.CacheManager.class, () -> new org.springframework.cache.concurrent.ConcurrentMapCacheManager());
    context.register(ConfigurationServices.class); context.refresh();
    return context;
  }

  @org.springframework.cache.annotation.EnableCaching(proxyTargetClass = true)
  @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass = true)
  static class ConfigurationServices {
    @org.springframework.context.annotation.Bean
    com.ouroboros.configuration.service.impl.ConfigurationValueServiceImpl values() { return new com.ouroboros.configuration.service.impl.ConfigurationValueServiceImpl(); }
    @org.springframework.context.annotation.Bean
    com.ouroboros.configuration.service.impl.RuntimeConfigurationServiceImpl runtime() { return new com.ouroboros.configuration.service.impl.RuntimeConfigurationServiceImpl(); }
    @org.springframework.context.annotation.Bean
    com.ouroboros.configuration.service.impl.ConfigurationWorkbenchServiceImpl workbench() { return new com.ouroboros.configuration.service.impl.ConfigurationWorkbenchServiceImpl(); }
    @org.springframework.context.annotation.Bean
    com.ouroboros.configuration.util.ConfigurationEditorSchemaBuilder editor() { return new com.ouroboros.configuration.util.ConfigurationEditorSchemaBuilder(); }
    @org.springframework.context.annotation.Bean
    com.ouroboros.notification.configuration.NotificationChannelSecretManagementPolicy secretPolicy() {
      return new com.ouroboros.notification.configuration.NotificationChannelSecretManagementPolicy("notification.email.authorizationCode");
    }
  }

  @RestControllerAdvice
  static class Errors {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> business(BusinessException error) {
      return WebApiResult.build(error.getErrorCode(), error.getMessage(), null);
    }
  }
}
