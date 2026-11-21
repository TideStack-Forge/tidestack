package com.ouroboros.dev.editor;

import java.util.Map;
import org.springframework.boot.SpringApplication;

/** Standalone local assembly; deliberately does not enable the platform's transitive auto-configurations. */
public final class LocalEditorApplication {
  private LocalEditorApplication() { }
  public static void main(String[] args) {
    var application = new SpringApplication(LocalEditorConfiguration.class);
    application.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "19762",
        "ouroboros.dev-helper.transport", "socket"));
    application.run(args);
  }
}
