package com.ouroboros.upload;

import java.io.InputStream;

import io.vavr.control.Either;

/**
 * 文件管理服务接口（延后完成）
 */
public interface StorageManagerService {
  Either<? extends Throwable, InputStream> getFileStream(String fullPath);
}
