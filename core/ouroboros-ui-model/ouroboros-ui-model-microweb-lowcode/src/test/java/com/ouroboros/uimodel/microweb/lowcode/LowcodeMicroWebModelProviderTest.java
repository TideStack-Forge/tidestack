package com.ouroboros.uimodel.microweb.lowcode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.uimodel.microweb.MicroWebModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LowcodeMicroWebModelProviderTest {

  @TempDir
  Path appHome;

  @Test
  void loadsDirectLegacyAndTypedModelsWithTypedTakingPriority() throws Exception {
    write("micro-web/shared.json", modelJson("shared", "/legacy.js"));
    write("micro-web/shared.micro-web.json", modelJson("shared", "/typed.js"));
    write("micro-web/legacy-only.json", modelJson("legacy-only", "/legacy-only.js"));
    write("micro-web/nested/ignored.micro-web.json", modelJson("ignored", "/ignored.js"));
    write("micro-web/package/package-file.micro-web.json", modelJson("package-file", "/package-file.js"));

    var provider = new LowcodeMicroWebModelProvider();
    provider.appContext = () -> appHome.toString();

    Map<String, MicroWebModel> models = provider.getAllMicroWebModel()
        .stream()
        .collect(Collectors.toMap(MicroWebModel::getName, Function.identity()));

    assertEquals(2, models.size());
    assertEquals("/typed.js", models.get("shared").getEntry());
    assertEquals("/legacy-only.js", models.get("legacy-only").getEntry());
    assertFalse(models.containsKey("ignored"));
    assertFalse(models.containsKey("package-file"));
  }

  private void write(String relativePath, String content) throws Exception {
    var target = appHome.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content, StandardCharsets.UTF_8);
  }

  private static String modelJson(String name, String entry) {
    return "{\"name\":\"" + name + "\",\"entry\":\"" + entry + "\",\"publicPath\":\"/" + name + "/\"}";
  }
}
