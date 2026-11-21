package com.ouroboros.businesslog.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.ouroboros.AppContext;
import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateProvider;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.util.Json;

/**
 * 从应用运行目录加载业务日志模板。
 */
@Service
public class FileBusinessOperationTemplateProvider {

  private static final Logger logger = LoggerFactory.getLogger(FileBusinessOperationTemplateProvider.class);
  private static final String TEMPLATE_DIRECTORY = "business-log-template";
  private static final String TEMPLATE_SUFFIX = ".business-log.json";

  private final AppContext appContext;

  private List<BusinessOperationTemplate> templates = Collections.emptyList();

  public FileBusinessOperationTemplateProvider(AppContext appContext) {
    this.appContext = appContext;
    refresh();
    Proxy.provider = this;
  }

  @EventListener
  public void refresh(OuroborosMetadataRefreshingEvent event) {
    refresh();
  }

  public List<BusinessOperationTemplate> getAllTemplates() {
    return templates;
  }

  void refresh() {
    List<BusinessOperationTemplate> result = new ArrayList<>();
    var templatePath = Paths.get(appContext.getHomePath(), TEMPLATE_DIRECTORY);
    if (!Files.exists(templatePath)) {
      templates = Collections.emptyList();
      return;
    }

    try (var stream = Files.walk(templatePath)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(TEMPLATE_SUFFIX))
          .forEach(path -> readTemplate(path).ifPresent(result::add));
    } catch (IOException e) {
      logger.error("刷新业务日志模板文件时发生错误", e);
    }

    templates = Collections.unmodifiableList(result);
  }

  private Optional<DefaultBusinessOperationTemplate> readTemplate(java.nio.file.Path path) {
    try {
      String content = String.join("\n", Files.readAllLines(path));
      return Json.tryToBean(DefaultBusinessOperationTemplate.class, content).toJavaOptional();
    } catch (Exception e) {
      logger.error("解析业务日志模板文件 {} 失败", path, e);
      return Optional.empty();
    }
  }

  @Priority(-10)
  @LowCodeProvider
  public static class Proxy implements BusinessOperationTemplateProvider {
    private static FileBusinessOperationTemplateProvider provider;

    @Override
    public BusinessOperationTemplateSource getSource() {
      return BusinessOperationTemplateSource.FILE;
    }

    @Override
    public List<BusinessOperationTemplate> getAllTemplates() {
      return Optional.ofNullable(provider)
          .map(FileBusinessOperationTemplateProvider::getAllTemplates)
          .orElse(Collections.emptyList());
    }
  }
}
