package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.render.RenderedProjectTemplate;
import com.ouroboros.dev.projectgeneration.zip.DeterministicProjectZipWriter;

class DeterministicProjectZipProcessTest {

  @Test
  void shouldProduceSameDigestAcrossProcessesAndTimeZones() throws Exception {
    String utcDigest = digestFromProbe("UTC");
    String shanghaiDigest = digestFromProbe("Asia/Shanghai");

    assertEquals(utcDigest, shanghaiDigest);
  }

  private static String digestFromProbe(String timezone) throws Exception {
    String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Process process = new ProcessBuilder(
        javaBinary,
        "-Duser.timezone=" + timezone,
        "-cp",
        testClasspath(),
        DeterministicProjectZipProcessProbe.class.getName()
    )
        .redirectErrorStream(true)
        .start();
    boolean exited = process.waitFor(20, TimeUnit.SECONDS);
    if (!exited) {
      process.destroyForcibly();
      throw new IllegalStateException("ZIP digest probe timed out");
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    assertEquals(0, process.exitValue(), output);
    String[] lines = output.split("\\R");
    return lines[lines.length - 1];
  }

  private static String testClasspath() {
    return System.getProperty(
        "surefire.test.class.path",
        System.getProperty("java.class.path")
    );
  }
}

final class DeterministicProjectZipProcessProbe {

  private DeterministicProjectZipProcessProbe() {
  }

  public static void main(String[] args) throws Exception {
    try (GeneratedProject generatedProject = new DeterministicProjectZipWriter()
        .write(renderedTemplate(), "1.0.0", null)) {
      System.out.println(generatedProject.sha256());
    }
  }

  private static RenderedProjectTemplate renderedTemplate() {
    return new RenderedProjectTemplate(
        "customer-portal",
        Map.of(
            "customer-portal/src/index.ts", bytes("export default 'customer';"),
            "customer-portal/README.md", bytes("# Customer Portal"),
            "customer-portal/.ouroboros-template.json", bytes("{\"contractVersion\":\"1\"}")
        )
    );
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
