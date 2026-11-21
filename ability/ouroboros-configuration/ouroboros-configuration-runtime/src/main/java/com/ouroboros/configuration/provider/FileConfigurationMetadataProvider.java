package com.ouroboros.configuration.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.configuration.util.TextIOUtils;
import com.ouroboros.util.Json;
import com.ouroboros.util.Paths;

/**
 * @author liansz
 **/
@Component
public class FileConfigurationMetadataProvider {

  /**
   * 读取开发文件夹内元数据路径
   */
  private static final String METADATA_FILE_HOME = "configuration";
  private static final String METADATA_FILE_EXTENSION = ".config.json";

  private final AppContext appContext;

  public FileConfigurationMetadataProvider(AppContext appContext) {
    this.appContext = appContext;
    Proxy.provider = this;
  }

  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    File rootPath = Paths.File.of(appContext.getHomePath(), METADATA_FILE_HOME).toFile();
    Function<File, List<InputStream>> traversalDirToInputStreams = f -> {
      BiFunction<BiFunction, File, List<InputStream>> filesFunction = (function, file1) -> {
        File[] files = file1.listFiles();
        if (files == null) {
          return new ArrayList<>();
        }

        List<InputStream> inputStreams = new ArrayList<>();
        for (File file : files) {
          if (file.isDirectory()) {
            inputStreams.addAll((List<InputStream>) function.apply(function, file));
          } else if (file.isFile() && file.getName().endsWith(METADATA_FILE_EXTENSION)) {
            inputStreams.add(Try.of(() -> new FileInputStream(file)).get());
          }
        }

        return inputStreams;
      };

      return filesFunction.apply(filesFunction, f);
    };

    return traversalDirToInputStreams.apply(rootPath).stream()
        .map(TextIOUtils::readText)
        .filter(ObjectUtils::isNotEmpty)
        .map(text -> Json.toBean(ConfigurationMetadata.class, text))
        .collect(Collectors.toList());
  }

  @Priority(100)
  @LowCodeProvider
  public static class Proxy implements ConfigurationMetadataProvider {
    private static FileConfigurationMetadataProvider provider;

    @Override
    public List<ConfigurationMetadata> getAllConfigurationMetadata() {
      return Optional.ofNullable(provider)
          .map(FileConfigurationMetadataProvider::getAllConfigurationMetadata)
          .orElseGet(Collections::emptyList);
    }
  }
}
