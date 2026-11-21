package com.ouroboros.dev.app.service.impl;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevAppTemplateConfig;
import com.ouroboros.dev.app.model.AppTemplateDTO;
import com.ouroboros.dev.app.service.AppTemplateService;
import com.ouroboros.dev.app.util.AutoDeletingFileInputStream;
import com.ouroboros.dev.event.CreateAppEvent;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.model.AppInfo;
import com.ouroboros.util.Json;
import com.ouroboros.util.ZipReader;
import com.ouroboros.util.ZipWriter;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;

@Service
public class AppTemplateServiceImpl implements AppTemplateService {
  private static final Logger logger = LoggerFactory.getLogger(AppTemplateServiceImpl.class);
  private static final String TEMPLATE_METADATA_FILE = "template.metadata.json";
  private static final String APP_METADATA_FILE = "app.metadata.json";

  private final DevAppTemplateConfig devAppTemplateConfig;

  @Autowired
  private DevelopWorkspaceContext developWorkspaceContext;

  @Autowired
  private VCSManagerCenter vcsManagerCenter;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  public AppTemplateServiceImpl(DevAppTemplateConfig devAppTemplateConfig) {
    this.devAppTemplateConfig = devAppTemplateConfig;
  }

  @Override
  public List<AppTemplateDTO> getAll() {
    return Try.of(() -> {
      Path templateDir = Paths.get(devAppTemplateConfig.getDir());
      if (!Files.exists(templateDir)) {
        return Collections.<AppTemplateDTO>emptyList();
      }

      try (var stream = Files.list(templateDir)) {
        return stream
            .filter(Files::isDirectory)
            .map(this::loadTemplateFromDir)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
      }
    }).getOrElse(Collections.emptyList());
  }

  @Override
  public Optional<AppTemplateDTO> getOne(String name) {
    Path templatePath = Paths.get(devAppTemplateConfig.getDir(), name);
    if (!Files.exists(templatePath) || !Files.isDirectory(templatePath)) {
      return Optional.empty();
    }
    return loadTemplateFromDir(templatePath);
  }

  @Override
  public Try<Void> createAppByTemplate(AppInfo appInfo, String templateName) {
    return Try.of(() -> {
      String appName = appInfo.getName();
      // 检查应用名称是否重复
      Path appDir = Paths.get(developWorkspaceContext.getDevRootDir(), appName);
      if (Files.exists(appDir)) {
        throw new BusinessException("应用名称已存在: " + appName);
      }

      // 检查模板是否存在
      Path templateDir = Paths.get(devAppTemplateConfig.getDir(), templateName);
      if (!Files.exists(templateDir)) {
        throw new BusinessException("模板不存在: " + templateName);
      }

      // 创建应用目录
      Files.createDirectories(appDir);

      // 拷贝模板文件到应用目录
      copyDirectory(templateDir, appDir, path -> !path.endsWith(TEMPLATE_METADATA_FILE));

      // 修改app.metadata.json中的name字段
      Path appMetadataFile = appDir.resolve(APP_METADATA_FILE);
      AppInfo newAppInfo;
      if (Files.exists(appMetadataFile)) {
        String content = new String(Files.readAllBytes(appMetadataFile));
        newAppInfo = Json.tryToBean(AppInfo.class, content).getOrElse(AppInfo::new);
        newAppInfo.setName(appName);
        newAppInfo.setIcon(appInfo.getIcon());
        newAppInfo.setTitle(appInfo.getTitle());
        newAppInfo.setDescription(appInfo.getDescription());
      } else {
        newAppInfo = appInfo;
      }
      Files.write(appMetadataFile, Json.toPrettyJsonString(newAppInfo).getBytes());

      // 初始化Git仓库
      initializeGitRepository(appName);

      // 发布应用创建事件
      eventPublisher.publishEvent(new CreateAppEvent(this, appName));

      return null;
    });
  }

  @Override
  public Try<Void> createTemplateFromApp(AppTemplateDTO template, String appName) {
    return Try.of(() -> {
      // 检查模板名称是否重复
      if (isTemplateExists(template.getName())) {
        throw new BusinessException("模板名称已存在: " + template.getName());
      }

      // 检查应用是否存在
      Path appDir = Paths.get(developWorkspaceContext.getDevRootDir(), appName);
      if (!Files.exists(appDir)) {
        throw new BusinessException("应用不存在: " + appName);
      }

      // 创建模板目录
      Path templateDir = Paths.get(devAppTemplateConfig.getDir(), template.getName());
      Files.createDirectories(templateDir);

      // 拷贝应用文件到模板目录（排除.git目录）
      copyDirectoryExcludeGit(appDir, templateDir);

      // 创建或更新template.metadata.json文件
      createTemplateMetadataFile(templateDir, template);

      return null;
    });
  }

  @Override
  public Try<Void> createTemplateFromApp(AppTemplateDTO template, String appName, String appVersion) {
    return Try.of(() -> {
      // 检查模板名称是否重复
      if (isTemplateExists(template.getName())) {
        throw new BusinessException("模板名称已存在: " + template.getName());
      }

      // 创建模板目录
      Path templateDir = Paths.get(devAppTemplateConfig.getDir(), template.getName());
      Files.createDirectories(templateDir);

      // 通过VCS获取指定版本的文件
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      List<VCSFile> versionFiles = vcsManager.getFileListFromTag("", appVersion);

      // 拷贝版本文件到模板目录
      for (VCSFile vcsFile : versionFiles) {
        if (!vcsFile.getPath().endsWith("/")) { // 排除目录
          try {
            VCSFile tagFile = vcsFile.atTag(appVersion);
            byte[] content = tagFile.getBytesArray();
            if (content != null) {
              Path targetFile = templateDir.resolve(vcsFile.getPath());
              Files.createDirectories(targetFile.getParent());
              Files.write(targetFile, content);
            }
          } catch (Exception e) {
            logger.warn("创建应用模板时跳过无法读取的版本文件: {}", vcsFile.getPath(), e);
          }
        }
      }

      // 创建template.metadata.json文件
      createTemplateMetadataFile(templateDir, template);

      return null;
    });
  }

  @Override
  public Try<Void> importTemplate(String templateName, InputStream templateStream) {
    return Try.of(() -> {
      // 创建临时目录
      Path tempDir = Files.createTempDirectory("template_import_");
      Path tempZipFile = tempDir.resolve("template.zip");

      try {
        // 将输入流写入临时文件（Java 8兼容方式）
        try (FileOutputStream fos = new FileOutputStream(tempZipFile.toFile())) {
          byte[] buffer = new byte[8192];
          int bytesRead;
          while ((bytesRead = templateStream.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
          }
        }

        // 解析template.metadata.json获取模板信息
        ZipReader zipReader = new ZipReader();
        String metadataContent = zipReader.peekFileContentAsString(tempZipFile.toFile(), TEMPLATE_METADATA_FILE);
        if (metadataContent == null || metadataContent.trim().isEmpty()) {
          logger.warn("导入模版时在压缩包中未找到 " + TEMPLATE_METADATA_FILE + " 文件");
        }

        AppTemplateDTO templateInfo = StringUtils.isEmpty(metadataContent)
            ? new AppTemplateDTO()
            : Json.tryToBean(AppTemplateDTO.class, metadataContent).getOrElse(AppTemplateDTO::new);

        String finalTemplateName = Optional.ofNullable(templateInfo.getName()).orElse(templateName);

        if (StringUtils.isEmpty(finalTemplateName)) {
          throw new BusinessException("模板名称不能为空");
        }

        // 检查模板名称是否重复
        if (isTemplateExists(finalTemplateName)) {
          throw new BusinessException("模板名称已存在: " + finalTemplateName);
        }

        // 如果名称发生变化，更新模板信息
        if (!finalTemplateName.equals(templateInfo.getName())) {
          templateInfo.setName(finalTemplateName);
        }

        // 解压到模板目录
        Path templateDir = Paths.get(devAppTemplateConfig.getDir(), finalTemplateName);
        Files.createDirectories(templateDir);

        zipReader.extractFile(tempZipFile.toFile(), templateDir.toString());

        // 更新template.metadata.json文件
        Path metadataFile = templateDir.resolve(TEMPLATE_METADATA_FILE);
        Files.write(metadataFile, Json.toPrettyJsonString(templateInfo).getBytes());

        return null;
      } finally {
        // 清理临时文件
        try {
          Files.walk(tempDir)
              .sorted(Comparator.reverseOrder())
              .map(Path::toFile)
              .forEach(File::delete);
        } catch (IOException e) {
          logger.warn("导入模板后清理临时目录失败: {}", tempDir, e);
        }
      }
    });
  }

  @Override
  public Try<Void> deleteTemplate(String templateName) {
    return Try.of(() -> {
      Path templateDir = Paths.get(devAppTemplateConfig.getDir(), templateName);
      if (!Files.exists(templateDir)) {
        throw new BusinessException("模板不存在: " + templateName);
      }

      // 递归删除模板目录
      Files.walk(templateDir)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);

      return null;
    });
  }

  @Override
  public Try<InputStream> exportTemplate(String templateName) {
    return Try.of(() -> {
      Path templateDir = Paths.get(devAppTemplateConfig.getDir(), templateName);
      if (!Files.exists(templateDir)) {
        throw new BusinessException("模板不存在: " + templateName);
      }

      // 创建临时文件
      Path tempFile = Files.createTempFile("template_export_" + templateName + "_", ".zip");

      try (ZipWriter zipWriter = new ZipWriter(tempFile.toFile())) {
        zipWriter.setComment("模板导出: " + templateName);

        // 遍历模板目录下的所有文件
        Files.walk(templateDir)
            .filter(Files::isRegularFile)
            .forEach(path -> {
              try {
                String relativePath = templateDir.relativize(path).toString().replace("\\", "/");
                byte[] content = Files.readAllBytes(path);
                zipWriter.addEntry(relativePath, content);
              } catch (IOException e) {
                logger.warn("导出模板时跳过无法读取的文件: {}", path, e);
              }
            });

        zipWriter.finish();
      }

      // 返回自动删除的文件输入流
      return new AutoDeletingFileInputStream(tempFile.toFile());
    });
  }

  // ================== 辅助方法 ==================

  /**
   * 从目录加载模板信息
   */
  private Optional<AppTemplateDTO> loadTemplateFromDir(Path templateDir) {
    try {
      Path metadataFile = templateDir.resolve(TEMPLATE_METADATA_FILE);
      if (!Files.exists(metadataFile)) {
        return Optional.empty();
      }

      String content = new String(Files.readAllBytes(metadataFile));
      AppTemplateDTO template = Json.toBean(AppTemplateDTO.class, content);
      return Optional.ofNullable(template);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * 检查模板是否存在
   */
  private boolean isTemplateExists(String templateName) {
    Path templateDir = Paths.get(devAppTemplateConfig.getDir(), templateName);
    return Files.exists(templateDir) && Files.isDirectory(templateDir);
  }

  /**
   * 创建模板元数据文件
   */
  private void createTemplateMetadataFile(Path templateDir, AppTemplateDTO template) throws IOException {
    Path metadataFile = templateDir.resolve(TEMPLATE_METADATA_FILE);

    // 如果文件已存在，检查名称是否一致
    if (Files.exists(metadataFile)) {
      String existingContent = new String(Files.readAllBytes(metadataFile));
      AppTemplateDTO existingTemplate = Json.toBean(AppTemplateDTO.class, existingContent);
      if (existingTemplate != null && !Objects.equals(existingTemplate.getName(), template.getName())) {
        // 名称不一致，更新名称
        existingTemplate.setName(template.getName());
        existingTemplate.setTitle(template.getTitle());
        existingTemplate.setDescription(template.getDescription());
        existingTemplate.setVersion(template.getVersion());
        existingTemplate.setAuthor(template.getAuthor());
        template = existingTemplate;
      }
    }

    // 写入文件
    Files.write(metadataFile, Json.toPrettyJsonString(template).getBytes());
  }

  /**
   * 拷贝目录（包含所有文件）
   */
  private void copyDirectory(Path sourceDir, Path targetDir, Function<Path, Boolean> filter) throws IOException {
    Files.walk(sourceDir)
        .forEach(sourcePath -> {
          try {
            var relativePath = sourceDir.relativize(sourcePath);
            if (filter != null && !filter.apply(relativePath)) {
              return;
            }
            Path targetPath = targetDir.resolve(sourceDir.relativize(sourcePath));
            if (Files.isDirectory(sourcePath)) {
              Files.createDirectories(targetPath);
            } else {
              Files.createDirectories(targetPath.getParent());
              Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
          } catch (IOException e) {
            logger.warn("拷贝模板目录时跳过无法拷贝的文件: {}", sourcePath, e);
          }
        });
  }

  /**
   * 拷贝目录（排除.git目录）
   */
  private void copyDirectoryExcludeGit(Path sourceDir, Path targetDir) throws IOException {
    Files.walk(sourceDir)
        .filter(path -> !path.toString().contains(".git"))
        .forEach(sourcePath -> {
          try {
            Path targetPath = targetDir.resolve(sourceDir.relativize(sourcePath));
            if (Files.isDirectory(sourcePath)) {
              Files.createDirectories(targetPath);
            } else {
              Files.createDirectories(targetPath.getParent());
              Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
          } catch (IOException e) {
            logger.warn("拷贝应用目录时跳过无法拷贝的文件: {}", sourcePath, e);
          }
        });
  }

  /**
   * 初始化Git仓库
   */
  private void initializeGitRepository(String appName) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      // 通过获取文件来触发Git仓库初始化
      var metadataFile = vcsManager.getFile(APP_METADATA_FILE);
      metadataFile.commit("初始化应用");
      var batchOp = vcsManager.batchOperation();
      vcsManager.getUncommittedFiles("")
          .forEach(file -> batchOp.add(file.getPath()));
      batchOp.commit("从模版创建");
    } catch (Exception e) {
      throw new PlatformException("初始化Git仓库失败: " + e.getMessage(), e);
    }
  }
}
