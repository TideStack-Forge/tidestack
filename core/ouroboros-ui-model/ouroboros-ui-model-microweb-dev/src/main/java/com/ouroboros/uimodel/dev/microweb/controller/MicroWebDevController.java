package com.ouroboros.uimodel.dev.microweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.exception.InternalException;
import com.ouroboros.upload.UploadStorageService;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}")
public class MicroWebDevController {
  @Autowired
  @Qualifier("devAppUploadStorageService")
  UploadStorageService uploadStorageService;

  final static private String TEMP_DIR = System.getProperty("java.io.tmpdir");

  @PostMapping({"/micro-web/upload-package", "/micro-web-component/upload-package"})
  public WebApiResult<TempUploadResult> uploadPackage(@RequestParam("file") MultipartFile file) {
    var result = Try.of(file::getInputStream)
        .toEither()
        .<Throwable>mapLeft(InternalException::new)
        .flatMap(inputStream -> (Either<Throwable, TempUploadResult>) uploadStorageService.addTempFile(file.getOriginalFilename(), inputStream))
        .peek(r -> r.setValue(r.getTempFile()));
    return WebApiResult.of(result);
  }
}
