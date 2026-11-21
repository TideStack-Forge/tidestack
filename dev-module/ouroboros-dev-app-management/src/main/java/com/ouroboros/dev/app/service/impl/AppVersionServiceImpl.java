package com.ouroboros.dev.app.service.impl;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.dev.app.service.AppVersionService;
import com.ouroboros.dev.app.service.vcs.AppVcsBranchModel;
import com.ouroboros.dev.app.service.vcs.AppVcsFacade;
import com.ouroboros.dev.app.service.vcs.AppVersionTagMetadata;
import com.ouroboros.dev.app.util.AutoDeletingFileInputStream;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.util.ZipWriter;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.versioning.Tag;

@Service
public class AppVersionServiceImpl implements AppVersionService {
  private static final Logger logger = LoggerFactory.getLogger(AppVersionServiceImpl.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final VCSManagerCenter vcsManagerCenter;
  private final AppVcsFacade appVcsFacade;

  public AppVersionServiceImpl(VCSManagerCenter vcsManagerCenter,
                               AppVcsFacade appVcsFacade) {
    this.vcsManagerCenter = vcsManagerCenter;
    this.appVcsFacade = appVcsFacade;
  }

  @Override
  public List<AppVersionDTO> getAll(String appName) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      return vcsManager.getTagList()
          .map(tagList -> tagList.stream()
              .sorted(Comparator.comparing(Tag::getTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
              .map(tagName -> convertToAppVersionDTO(appName, tagName))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toList()))
          .getOrElse(Collections.emptyList());
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public Optional<AppVersionDTO> getOne(String appName, String version) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      return vcsManager.getTagList()
          .map(tagList -> tagList.stream()
              .filter(tagName -> tagName.getName().equals(version))
              .findFirst()
          )
          .filter(Optional::isPresent)
          .map(Optional::get)
              .map(tag -> convertToAppVersionDTO(appName, tag))
          .getOrElse(Optional.empty());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public Try<Void> create(String appName, AppVersionDTO version) {
    return appVcsFacade.ensureWorkspaceClean(appName, "发版")
        .flatMap(ignored -> Try.of(() -> {
          AppBranchDTO currentBranch = appVcsFacade.getCurrentBranch(appName).get();
          if (!currentBranch.isManaged()) {
            throw new BusinessException("当前分支不符合受管分支模型，无法发版: " + currentBranch.getBranchName());
          }

          String logicalVersion = AppVcsBranchModel.normalizeLogicalVersion(version == null ? null : version.getVersion());
          String gitTag = buildGitTag(appName, logicalVersion, currentBranch);
          AppVersionTagMetadata metadata = new AppVersionTagMetadata();
          metadata.setLogicalVersion(logicalVersion);
          metadata.setGitTag(gitTag);
          metadata.setReleaseTrack(currentBranch.getReleaseTrack());
          metadata.setSourceBranch(currentBranch.getBranchName());
          metadata.setEditionCode(currentBranch.getEditionCode());
          metadata.setDescription(trimToNull(version == null ? null : version.getDescription()));

          VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
          String tagMessage = Json.toPrettyJsonString(metadata);
          vcsManager.createTag(gitTag, tagMessage).get();
          return null;
        }));
  }

  @Override
  public List<VCSFile> getAllFiles(String appName) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      return vcsManager.walkDirectory("./", null)
          .collect(Collectors.toList());
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public List<VCSFile> getAllFiles(String appName, String version) {
    try {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);
      return vcsManager.getFileListFromTag("", version);
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public Try<InputStream> exportVersion(String appName, String version) {
    return Try.of(() -> {
      VCSManager vcsManager = vcsManagerCenter.getVcsManager(appName);

      // 获取版本下的所有文件
      List<VCSFile> files = vcsManager.getFileListFromTag("", version);

      // 创建临时文件
      java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("version_export_" + appName + "_" + version + "_", ".zip");

      try (ZipWriter zipWriter = new ZipWriter(tempFile.toFile())) {
        zipWriter.setComment("应用版本导出: " + appName + " v" + version);

        for (VCSFile file : files) {
          if (!file.getPath().endsWith("/")) { // 跳过目录
            try {
              VCSFile tagFile = file.atTag(version);
              byte[] content = tagFile.getBytesArray();
              if (content != null) {
                zipWriter.addEntry(file.getPath(), content);
              }
            } catch (Exception e) {
              logger.warn("导出应用版本时跳过无法读取的文件: {}", file.getPath(), e);
            }
          }
        }

        zipWriter.finish();
      }

      // 返回自动删除的文件输入流
      return new AutoDeletingFileInputStream(tempFile.toFile());
    });
  }

  /**
   * 将标签名转换为AppVersionDTO
   */
  private Optional<AppVersionDTO> convertToAppVersionDTO(String appName, Tag tag) {
    try {
      AppVersionTagMetadata metadata = parseTagMetadata(tag.getMessage()).orElse(null);
      AppVcsBranchModel.ReleaseInfo releaseInfo = AppVcsBranchModel.resolveTag(tag.getName(), appVcsFacade.getTrunkBranch(appName));
      AppVersionDTO dto = new AppVersionDTO()
          .setAppName(appName)
          .setVersion(tag.getName())
          .setLogicalVersion(firstNonBlank(metadata == null ? null : metadata.getLogicalVersion(), releaseInfo.getLogicalVersion()))
          .setGitTag(tag.getName())
          .setReleaseTrack(resolveReleaseTrack(metadata, releaseInfo))
          .setSourceBranch(firstNonBlank(metadata == null ? null : metadata.getSourceBranch(), releaseInfo.getSourceBranch()))
          .setEditionCode(firstNonBlank(metadata == null ? null : metadata.getEditionCode(), releaseInfo.getEditionCode()))
          .setDescription(resolveDescription(tag.getMessage(), metadata))
          .setCreatedTime(formatDateTime(tag.getTime()))
          .setAuthor(tag.getAuthorName());
      return Optional.of(dto);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  /**
   * 格式化时间
   */
  private String formatDateTime(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.format(DATE_TIME_FORMATTER);
  }

  private String buildGitTag(String appName, String logicalVersion, AppBranchDTO currentBranch) {
    AppVcsBranchModel.BranchInfo branchInfo = AppVcsBranchModel.resolveBranch(currentBranch.getBranchName(), appVcsFacade.getTrunkBranch(appName));
    if (!branchInfo.isManaged()) {
      throw new BusinessException("当前分支不符合受管分支模型，无法发版: " + currentBranch.getBranchName());
    }
    return AppVcsBranchModel.buildGitTag(logicalVersion, branchInfo);
  }

  private Optional<AppVersionTagMetadata> parseTagMetadata(String message) {
    String normalized = trimToNull(message);
    if (normalized == null || !normalized.startsWith("{")) {
      return Optional.empty();
    }
    return Optional.ofNullable(Json.toBean(AppVersionTagMetadata.class, normalized))
        .filter(metadata -> StringUtils.isNotBlank(metadata.getGitTag()) || StringUtils.isNotBlank(metadata.getReleaseTrack()));
  }

  private String resolveDescription(String rawMessage, AppVersionTagMetadata metadata) {
    if (metadata != null) {
      return trimToNull(metadata.getDescription());
    }
    String normalized = trimToNull(rawMessage);
    if (StringUtils.equals(normalized, "<空>")) {
      return null;
    }
    return normalized;
  }

  private String firstNonBlank(String first, String second) {
    return StringUtils.defaultIfBlank(trimToNull(first), trimToNull(second));
  }

  private String resolveReleaseTrack(AppVersionTagMetadata metadata, AppVcsBranchModel.ReleaseInfo releaseInfo) {
    String metadataTrack = trimToNull(metadata == null ? null : metadata.getReleaseTrack());
    if (metadataTrack != null) {
      return AppVcsBranchModel.normalizeReleaseTrack(metadataTrack);
    }
    return releaseInfo.getReleaseTrack();
  }

  private String trimToNull(String value) {
    return StringUtils.trimToNull(value);
  }

}
