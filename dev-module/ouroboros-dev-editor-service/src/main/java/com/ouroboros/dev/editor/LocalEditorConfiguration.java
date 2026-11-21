package com.ouroboros.dev.editor;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import com.ouroboros.dev.codeintelligence.CodeIntelligenceAutoConfiguration;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.dev.DefaultDevHelperCommandService;
import com.ouroboros.devhelper.dev.metadata.provider.HelperMetadataProvider;
import com.ouroboros.devhelper.dev.transport.socket.SocketDevHelperClientTransport;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;

@ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
    WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class, HttpEncodingAutoConfiguration.class,
    org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class,
    org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class})
@Import({CodeIntelligenceAutoConfiguration.class, HelperMetadataProvider.class,
    LocalHelperConnection.class, LocalEditorCatalogController.class})
public class LocalEditorConfiguration {
  @Bean(destroyMethod = "close")
  DevHelperClientTransport localHelperTransport(
      @Value("${ouroboros.dev-helper.socket.host:127.0.0.1}") String host,
      @Value("${ouroboros.dev-helper.socket.port:19761}") int port,
      @Value("${ouroboros.dev-helper.socket.token}") String token) {
    return new SocketDevHelperClientTransport(host, port, token);
  }
  @Bean DevHelperCommandService helperCommands(DevHelperClientTransport transport) { return new DefaultDevHelperCommandService(transport); }
  @Bean LocalEditorAccessFilter editorAccessFilter(@Value("${ouroboros.editor.token}") String token,
      @Value("${ouroboros.editor.app-name}") String appName, LocalHelperConnection connection) {
    return new LocalEditorAccessFilter(token, appName, connection);
  }
  @Bean com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider logicFlowContexts() {
    return new com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider();
  }
  @Bean com.ouroboros.fieldtype.dev.FieldTypeCodeContextProvider fieldTypeContexts() { return new com.ouroboros.fieldtype.dev.FieldTypeCodeContextProvider(); }
  @Bean com.ouroboros.rpc.dev.RpcCodeContextProvider rpcContexts() {
    return new com.ouroboros.rpc.dev.RpcCodeContextProvider(java.util.List.of(new com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowInvocationInputProvider()));
  }
  @Bean com.ouroboros.notification.NotificationCodeContextProvider notificationContexts() { return new com.ouroboros.notification.NotificationCodeContextProvider(); }
  @Bean com.ouroboros.configuration.ConfigurationCodeContextProvider configurationContexts() { return new com.ouroboros.configuration.ConfigurationCodeContextProvider(); }
  @Bean com.ouroboros.businesslog.dev.BusinessLogCodeContextProvider businessLogContexts() { return new com.ouroboros.businesslog.dev.BusinessLogCodeContextProvider(); }
  @Bean com.ouroboros.businessmodel.dev.BusinessModelCodeContextProvider businessContexts() {
    // previewBusinessModel is the existing pure draft projection; the persistence service is never registered or exposed.
    return new com.ouroboros.businessmodel.dev.BusinessModelCodeContextProvider(new com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService());
  }
}
