package com.ouroboros.app;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;

@Component
public class AppCustomStyleAppInfoContributor {
  private final static String CUSTOM_STYLE_FILE_NAME = "style.css";
  private final static String CUSTOM_STYLE_KEY = "customStyle";
  AppContext appContext;

  public AppCustomStyleAppInfoContributor(AppContext appContext) {
    this.appContext = appContext;
    Proxy.INSTANCE = this;
  }

  public Map<String, Object> getWebAppInfo() {
    StringBuilder styles = new StringBuilder();
    new ClasspathMetadataFiles().legacyThenMetadataContentStreamForOverride(CUSTOM_STYLE_FILE_NAME)
        .filter(style -> style != null && !style.isBlank())
        .forEach(style -> appendStyle(styles, style));

    var appHome = appContext.getHomePath();
    var stylePath = Paths.get(appHome, CUSTOM_STYLE_FILE_NAME);
    if (!Files.exists(stylePath) && styles.length() == 0) {
      return Collections.emptyMap();
    }
    var styleContent = Try.of(() -> {
      var lines = Files.readAllLines(stylePath);
      return String.join("\n", lines);
    });
    styleContent
        .filter(style -> !style.isBlank())
        .forEach(style -> appendStyle(styles, style));
    if (styles.length() == 0) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(CUSTOM_STYLE_KEY, styles.toString());
  }

  private void appendStyle(StringBuilder styles, String style) {
    if (styles.length() > 0) {
      styles.append("\n");
    }
    styles.append(style);
  }

  public static class Proxy implements WebAppInfoContributor {
    static AppCustomStyleAppInfoContributor INSTANCE;

    @Override
    public Map<String, Object> getWebAppInfo() {
      return INSTANCE.getWebAppInfo();
    }
  }
}
