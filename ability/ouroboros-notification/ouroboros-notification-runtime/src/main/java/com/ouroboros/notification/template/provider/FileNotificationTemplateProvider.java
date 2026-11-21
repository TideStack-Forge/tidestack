package com.ouroboros.notification.template.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.notification.template.DefaultNotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateProvider;
import com.ouroboros.notification.util.TextIOUtils;
import com.ouroboros.util.Json;
import com.ouroboros.util.Paths;

/**
 * @author liansz
 */
@Service
public class FileNotificationTemplateProvider {

  /**
   * 读取开发文件夹内元数据路径
   */
  private static final String METADATA_FILE_HOME = "notification-template";
  private static final String METADATA_FILE_SUFFIX = ".notification.json";

  private Map<String, NotificationTemplate> templateMap;

  private Map<String, NotificationTemplate> templateMapByFilePath = Collections.emptyMap();

  private final AppContext appContext;

  public FileNotificationTemplateProvider(AppContext appContext) {
    this.appContext = appContext;
    refresh();
    Proxy.provider = this;
  }

  @EventListener
  public void refresh(OuroborosMetadataRefreshingEvent event) {
    refresh();
  }

  /**
   * 刷新
   */
  private void refresh() {
    Function<File, List<File>> traversalDirToFiles = f -> {
      BiFunction<BiFunction, File, List<File>> filesFunction = (function, file1) -> {
        File[] files = file1.listFiles();
        if (files == null) {
          return new ArrayList<>();
        }

        List<File> templateFiles = new ArrayList<>();
        for (File file : files) {
          if (file.isDirectory()) {
            templateFiles.addAll((List<File>) function.apply(function, file));
          } else if (file.isFile() && file.getName().endsWith(METADATA_FILE_SUFFIX)) {
            templateFiles.add(file);
          }
        }

        return templateFiles;
      };

      return filesFunction.apply(filesFunction, f);
    };

    File rootPath = Paths.File.of(appContext.getHomePath(), METADATA_FILE_HOME).toFile();
    Map<String, NotificationTemplate> refreshedTemplateMapByFilePath = new LinkedHashMap<>();
    for (File file : traversalDirToFiles.apply(rootPath)) {
      String filePath = file.getAbsolutePath();
      Optional<NotificationTemplate> template = readTemplate(file);
      if (template.isPresent()) {
        refreshedTemplateMapByFilePath.put(filePath, template.get());
      } else if (templateMapByFilePath.containsKey(filePath)) {
        refreshedTemplateMapByFilePath.put(filePath, templateMapByFilePath.get(filePath));
      }
    }

    templateMapByFilePath = refreshedTemplateMapByFilePath;
    templateMap = refreshedTemplateMapByFilePath.values().stream()
        .collect(
            Collectors.toMap(
                NotificationTemplate::getName,
                metadata -> metadata,
                (o, n) -> n,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );
  }

  private Optional<NotificationTemplate> readTemplate(File file) {
    try (InputStream inputStream = new FileInputStream(file)) {
      String text = TextIOUtils.readText(inputStream);
      if (ObjectUtils.isEmpty(text)) {
        return Optional.empty();
      }
      return Optional.ofNullable(Json.toBean(DefaultNotificationTemplate.class, text));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  @Priority(-10)
  @LowCodeProvider
  public static class Proxy implements NotificationTemplateProvider {
    private static FileNotificationTemplateProvider provider;

    @Override
    public Optional<NotificationTemplate> getTemplate(String name) {
      return Optional.ofNullable(provider).map(p -> p.templateMap.get(name));
    }

    @Override
    public List<NotificationTemplate> getAllTemplate() {
      return Optional.ofNullable(provider)
          .map(p -> (List<NotificationTemplate>) new ArrayList<>(provider.templateMap.values()))
          .orElse(Collections.emptyList());
    }
  }
}
