package com.ouroboros.dev.app.controller;

import java.nio.file.Paths;
import java.util.*;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.model.AppInfo;
import com.ouroboros.modelmanage.web.AbstractSingleFileController;
import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/setting")
@SuppressWarnings({"unused"})
public class AppSettingController extends AbstractSingleFileController<AppInfo, AppInfo> {
  private final static Logger logger = LoggerFactory.getLogger(AppSettingController.class);
  private final static String METADATA_FILE_PATH = "app.metadata.json";
  private final static String LOGO_FILE_DIR = "resources";
  @Autowired
  VCSManagerCenter vcsManagerCenter;

  @Autowired
  @Qualifier("devAppUploadStorageService")
  UploadStorageService uploadStorageService;

  @Override
  protected String getSingleFilePath() {
    return METADATA_FILE_PATH;
  }

  @Override
  protected Try<AppInfo> getInitFormData(VCSFile file) {
    return Json.tryToBean(AppInfo.class, file.getContent());
  }

  @Override
  protected Try<AppInfo> getSubmitModelFromJson(String jsonString) {
    var submitData = Json.tryToBean(AppInfo.class, jsonString);
    return submitData
        .flatMap(appInfo -> updateLogoFile(appInfo).map(v -> appInfo));
  }

  @Override
  protected List<String> getCommitAddFiles() {
    var vcsManager = getVcsManager();
    var uncommittedFiles = vcsManager.getUncommittedFiles(LOGO_FILE_DIR);
    var files = new ArrayList<String>();
    files.add(getSingleFilePath());
    uncommittedFiles.forEach(f -> {
      if (!f.exists()) {
        return;
      }
      if (!f.getPath().toLowerCase().startsWith(LOGO_FILE_DIR + "/logo.")) {
        return;
      }
      files.add(f.getPath());
    });
    return files;
  }

  @Override
  protected List<String> getCommitRemoveFiles() {
    var vcsManager = getVcsManager();
    var uncommittedFiles = vcsManager.getUncommittedFiles(LOGO_FILE_DIR);
    var files = new ArrayList<String>();
    uncommittedFiles.forEach(f -> {
      if (f.exists()) {
        return;
      }
      if (!f.getPath().toLowerCase().startsWith(LOGO_FILE_DIR + "/logo.")) {
        return;
      }
      files.add(f.getPath());
    });
    return files;
  }

  @Override
  protected boolean returnDataWhenSaved() {
    return true;
  }

  @PostMapping("/upload-logo")
  public WebApiResult<TempUploadResult> uploadLogo(@PathVariable("appName") String appName, @RequestPart("file") MultipartFile file) {
    var rawFileName = file.getOriginalFilename() == null ? "logo.png" : file.getOriginalFilename();
    var fileExt = rawFileName.substring(rawFileName.lastIndexOf("."));

    var result = Try.withResources(file::getInputStream)
        .of(inputStream -> uploadStorageService.addTempFile(rawFileName, inputStream)).toEither()
        .flatMap(uploaded -> uploaded.mapLeft(error -> error))
        .peek(r -> r.setValue(r.getTempFile() + fileExt));
    return WebApiResult.of(result);
  }

  static Map<String, String> contentTypeMap = new HashMap<>() {{
    put("png", "image/png");
    put("jpg", "image/jpeg");
    put("jpeg", "image/jpeg");
    put("gif", "image/gif");
    put("bmp", "image/bmp");
    put("ico", "image/x-icon");
    put("webp", "image/webp");
  }};

  @GetMapping("/logo")
  @Anonymous
  public void getLogo(@PathVariable("appName") String appName, @RequestParam("logoFile") String logoFile, HttpServletResponse response) {
    var vcsManager = vcsManagerCenter.getVcsManager(appName);
    var logoPath = Paths.get("resources", logoFile).toString();
    var vcsFile = vcsManager.getFile(logoPath);
    Try.run(() -> {
      var ext = vcsFile.getFileName().substring(logoFile.lastIndexOf(".") + 1);
      var contentType = contentTypeMap.get(ext);
      if (contentType != null) {
        response.setContentType(contentType);
      }
      var outputStream = response.getOutputStream();
      outputStream.write(vcsFile.getBytesArray());
      outputStream.flush();
      outputStream.close();
    }).onFailure(e -> {
      logger.error("获取Logo失败", e);
      response.setStatus(404);
      response.setContentType("text/plain");
      Try.run(() -> response.getWriter().print("获取Logo失败"));
    });
  }

  Try<Void> updateLogoFile(AppInfo appInfo) {
    var vcsManager = getVcsManager();
    var newLogoValue = Optional.ofNullable(appInfo.getLogo()).orElse("");
    // 如果没有上传logo，则不更新直接返回
    if (newLogoValue.startsWith("logo.")) {
      return Try.success(null);
    }

    var vcsOperation = vcsManager.batchOperation();

    var oldAppInfo = Json.tryToBean(AppInfo.class, getSingleFile().getContent())
        .getOrElse(AppInfo::new);
    String oldLogoPath = Optional.ofNullable(oldAppInfo.getLogo())
        .filter(ObjectUtils::isNotEmpty)
        .map(l -> Paths.get("resources", l).toString())
        .orElse("");

    // 如果清空了 logo，则删除旧 logo 文件
    if (ObjectUtils.isEmpty(newLogoValue)) {
      if (ObjectUtils.isEmpty(oldLogoPath)) {
        return Try.success(null);
      }
      return vcsOperation.remove(oldLogoPath)
          .write(false)
          .map(ignore -> null);
    }

    // 否则更新 logo 文件
    var newLogoExt = newLogoValue.substring(newLogoValue.lastIndexOf("."));
    var newUploadId = newLogoValue.substring(0, newLogoValue.lastIndexOf("."));
    var newLogoPath = Paths.get("resources", "logo" + newLogoExt).toString();

    // 如果上传了logo，但是获取不到logo的内容，不更新
    var logoInputStream = uploadStorageService.getTempFileContent(newUploadId);

    if (logoInputStream.isLeft()) {
      var error = logoInputStream.getLeft();
      if (error instanceof OuroborosException oe) {
        return Try.failure(oe);
      }
      return Try.failure(new BusinessException("获取上传的LOGO文件内容失败，请重试"));
    }

    appInfo.setLogo("logo" + newLogoExt);

    return Try.withResources(logoInputStream::get).of(content -> {
      // 如果旧 logo 不为空，且与新 logo 不相同，则删除旧 logo 文件
      if (!oldLogoPath.isEmpty() && !oldLogoPath.equals(newLogoPath)) {
        vcsOperation.remove(oldLogoPath).create(newLogoPath, content);
      } else if (oldLogoPath.isEmpty()) {
        vcsOperation.create(newLogoPath, content);
      } else {
        vcsOperation.update(newLogoPath, content);
      }
      return vcsOperation.write();
    }).flatMap(result -> result).map(ignore -> null);
  }
}
