package com.ouroboros.devhelper.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class JarUtil {
  private static final String HELPER_POM_PROPERTIES = "/META-INF/maven/com.ouroboros/ouroboros-dev-helper-runtime/pom.properties";

  public static String getHelperVersion() {
    return getHelperVersion(() -> JarUtil.class.getResourceAsStream(HELPER_POM_PROPERTIES));
  }

  static String getHelperVersion(StreamSupplier streamSupplier) {
    try (var inStream = streamSupplier.get()) {
      return readHelperVersion(inStream);
    } catch (IOException | RuntimeException e) {
      return "unknown";
    }
  }

  static String readHelperVersion(InputStream inStream) throws IOException {
    if (inStream == null) {
      return "unknown";
    }
    var props = new Properties();
    props.load(inStream);
    var version = props.getProperty("version");
    if (version == null || version.trim().isEmpty()) {
      return "unknown";
    }
    return version;
  }

  interface StreamSupplier {
    InputStream get() throws IOException;
  }
}
