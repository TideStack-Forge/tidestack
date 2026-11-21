package com.ouroboros.webapi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.util.Json;

@Component
@ConditionalOnBean(AppContext.class)
public class AppWebAPIModelFromFileProvider {
  private static final Logger logger = LoggerFactory.getLogger(AppWebAPIModelFromFileProvider.class);
  private static final String METADATA_FILE_EXTENSION = ".webapi.json";
  final Map<String, WebAPIModel> apiModelMap;

  public AppWebAPIModelFromFileProvider(AppContext context) {
    var homePath = context.getHomePath();
    var file = new File(homePath, "webapi");
    var contents = loadFileContentRecurse(file);
    apiModelMap = contents.stream().map(content ->
        Json.tryToBean(SimpleWebAPIModel.class, content)
    ).filter(Try::isSuccess).map(Try::get).collect(
        HashMap::new, (m, v) -> m.put(v.getApi(), v), HashMap::putAll
    );

    Proxy.provider = this;
  }

  private List<String> loadFileContentRecurse(File file) {
    if (!file.exists() || !file.isDirectory()) {
      return Collections.emptyList();
    }
    try (var stream = Files.walk(file.toPath())) {
      return stream
          .filter(f -> f.toFile().isFile() && f.toString().endsWith(METADATA_FILE_EXTENSION))
          .sorted()
          .flatMap(path -> readFileContent(path).stream())
          .collect(Collectors.toList());
    } catch (IOException e) {
      logger.error("遍历低代码 WebAPI 元数据目录失败: {}", file, e);
    }
    return Collections.emptyList();
  }

  private Optional<String> readFileContent(Path path) {
    try {
      return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
    } catch (IOException e) {
      logger.error("读取低代码 WebAPI 元数据文件失败: {}", path, e);
      return Optional.empty();
    }
  }

  public Map<String, WebAPIModel> getApiModelMap() {
    return Collections.unmodifiableMap(apiModelMap);
  }

  @Priority(-10)
  @LowCodeProvider
  public static class Proxy implements WebAPIModelProvider {
    private static AppWebAPIModelFromFileProvider provider;

    @Override
    public Map<String, WebAPIModel> getWebAPIModelMap() {
      return Optional.ofNullable(provider)
          .map(AppWebAPIModelFromFileProvider::getApiModelMap)
          .orElseGet(Collections::emptyMap);
    }
  }
}
