package com.ouroboros.upload;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import io.vavr.control.Either;

import com.ouroboros.exception.InternalException;
import com.ouroboros.upload.model.ChunkResult;
import com.ouroboros.upload.model.StartChunkResult;
import com.ouroboros.upload.model.TempUploadResult;
import com.ouroboros.upload.model.UploadResult;
import com.ouroboros.upload.model.request.ChunkPart;
import com.ouroboros.upload.model.request.FinishChunkRequest;
import com.ouroboros.upload.model.request.StartChunkRequest;
import com.ouroboros.upload.model.request.UploadChunkRequest;

/**
 * 文件上传服务。InputStream 重载由调用者关闭输入流，File 重载自行关闭。
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface UploadStorageService {
  /**
   * 添加临时文件（按默认的删除时间删除）
   *
   * @param fileName    文件名
   * @param fileContent 文件内容
   * @return 临时文件id
   */
  default Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, byte[] fileContent) {
    var byteArrayInputStream = new ByteArrayInputStream(fileContent);
    return addTempFile(fileName, byteArrayInputStream);
  }

  /**
   * 添加临时文件
   *
   * @param fileName           文件名
   * @param fileContent        文件内容
   * @param autoDeleteDuration 自动删除时间
   * @return 临时文件id
   */
  default Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, byte[] fileContent, Duration autoDeleteDuration) {
    var byteArrayInputStream = new ByteArrayInputStream(fileContent);
    return addTempFile(fileName, byteArrayInputStream, autoDeleteDuration);
  }

  /**
   * 添加临时文件（按默认的删除时间删除）
   *
   * @param fileName    文件名
   * @param fileContent 文件内容
   * @return 临时文件id
   */
  Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, InputStream fileContent);

  /**
   * 添加临时文件
   *
   * @param fileName           文件名
   * @param fileContent        文件内容
   * @param autoDeleteDuration 自动删除时间
   * @return 临时文件id
   */
  Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, InputStream fileContent, Duration autoDeleteDuration);

  /**
   * 添加临时文件（按默认的删除时间删除）
   *
   * @param fileName 文件名
   * @param file     文件
   * @return 临时文件id
   */
  default Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, File file) {
    try (var content = Files.newInputStream(file.toPath())) { return addTempFile(fileName, content); }
    catch (IOException e) { return Either.left(new InternalException("读取文件失败", e)); }
  }

  /**
   * 添加临时文件
   *
   * @param fileName           文件名
   * @param file               文件
   * @param autoDeleteDuration 自动删除时间
   * @return 临时文件id
   */
  default Either<? extends Throwable, TempUploadResult> addTempFile(String fileName, File file, Duration autoDeleteDuration) {
    try (var content = Files.newInputStream(file.toPath())) { return addTempFile(fileName, content, autoDeleteDuration); }
    catch (IOException e) { return Either.left(new InternalException("读取文件失败", e)); }
  }

  /**
   * 获取临时文件
   *
   * @param tempFileId 临时文件id
   * @return 文件内容
   */
  Either<? extends Throwable, InputStream> getTempFileContent(String tempFileId);

  /**
   * 删除临时文件
   *
   * @param tempFileId 临时文件id
   * @return 删除结果
   */
  Either<? extends Throwable, Void> deleteTempFile(String tempFileId);

  /**
   * 永久化临时文件
   *
   * @param tempFileId        临时文件id
   * @param permanentFilePath 永久化文件名路径（可以是相对路径，如果是本地存储就是相对于配置中的文件存储路径）
   * @return 永久化结果
   */
  Either<? extends Throwable, Void> permanentlyTempFile(String tempFileId, Path permanentFilePath);

  /**
   * 开始分片上传
   *
   * @param fileName 临时文件名
   * @return 上传id
   */
  default Either<? extends Throwable, StartChunkResult> startTempChunk(String fileName) {
    var startChunkRequest = new StartChunkRequest();
    startChunkRequest.setFileName(fileName);
    startChunkRequest.setName(fileName);
    return startTempChunk(startChunkRequest);
  }

  Either<? extends Throwable, StartChunkResult> startTempChunk(StartChunkRequest startChunkRequest);

  /**
   * 添加分片
   *
   * @param uploadId    上传id
   * @param partNumber  分片序号
   * @param fileContent 文件内容
   * @return 分片eTag
   */
  default Either<? extends Throwable, ChunkResult> addTempChunk(String key, String uploadId, Integer partNumber, byte[] fileContent) {
    var byteArrayInputStream = new ByteArrayInputStream(fileContent);
    var uploadChunkRequest = new UploadChunkRequest();
    uploadChunkRequest.setUploadId(uploadId);
    uploadChunkRequest.setKey(key);
    uploadChunkRequest.setPartNumber(partNumber);
    uploadChunkRequest.setPartSize((long) fileContent.length);
    uploadChunkRequest.setPartContent(byteArrayInputStream);
    return addTempChunk(uploadChunkRequest);
  }

  /**
   * 添加分片
   *
   * @param uploadId    上传id
   * @param partNumber  分片序号
   * @param fileContent 文件内容
   * @return 分片id
   */
  default Either<? extends Throwable, ChunkResult> addTempChunk(String key, String uploadId, Integer partNumber, InputStream fileContent) {
    var uploadChunkRequest = new UploadChunkRequest();
    uploadChunkRequest.setUploadId(uploadId);
    uploadChunkRequest.setKey(key);
    uploadChunkRequest.setPartNumber(partNumber);
    uploadChunkRequest.setPartContent(fileContent);
    return addTempChunk(uploadChunkRequest);
  }

  /**
   * 添加分片
   *
   * @param uploadId   上传id
   * @param partNumber 分片序号
   * @param file       文件
   * @return 分片eTag
   */
  default Either<? extends Throwable, ChunkResult> addTempChunk(String key, String uploadId, Integer partNumber, File file) {
    try (var content = Files.newInputStream(file.toPath())) { return addTempChunk(key, uploadId, partNumber, content); }
    catch (IOException e) { return Either.left(new InternalException("读取文件失败", e)); }
  }

  Either<? extends Throwable, ChunkResult> addTempChunk(UploadChunkRequest uploadChunkRequest);

  /**
   * 完成分片上传
   *
   * @param key           相对文件路径（对象存储的key）
   * @param uploadId      上传id
   * @param chunkPartList 分片列表
   * @return 临时文件id
   */
  default Either<? extends Throwable, TempUploadResult> finishTempChunk(String key, String uploadId, List<ChunkPart> chunkPartList) {
    var finishChunkRequest = new FinishChunkRequest();
    finishChunkRequest.setFileName(key);
    finishChunkRequest.setKey(key);
    finishChunkRequest.setUploadId(uploadId);
    finishChunkRequest.setPartList(chunkPartList);
    return finishTempChunk(finishChunkRequest);
  }

  Either<? extends Throwable, TempUploadResult> finishTempChunk(FinishChunkRequest finishChunkRequest);

  /**
   * 清理分片
   *
   * @param uploadId 上传id
   * @return 清理结果
   */
  Either<? extends Throwable, Void> abortTempChunks(String uploadId);

  /**
   * 开始分片文件上传
   *
   * @param filePath 文件名
   * @return 上传id
   */
  default Either<? extends Throwable, StartChunkResult> startChunk(Path filePath) {
    var startChunkRequest = new StartChunkRequest();
    startChunkRequest.setFileName(filePath.toString());
    startChunkRequest.setName(filePath.toString());
    return startChunk(startChunkRequest);
  }

  Either<? extends Throwable, StartChunkResult> startChunk(StartChunkRequest startChunkRequest);

  /**
   * 添加分片
   *
   * @param uploadId    上传id
   * @param partNumber  分片序号
   * @param fileContent 文件内容
   * @return 分片id
   */
  default Either<? extends Throwable, ChunkResult> addChunk(String key, String uploadId, Integer partNumber, byte[] fileContent) {
    var byteArrayInputStream = new ByteArrayInputStream(fileContent);
    var uploadChunkRequest = new UploadChunkRequest();
    uploadChunkRequest.setUploadId(uploadId);
    uploadChunkRequest.setKey(key);
    uploadChunkRequest.setPartNumber(partNumber);
    uploadChunkRequest.setPartContent(byteArrayInputStream);
    return addChunk(uploadChunkRequest);
  }

  /**
   * 添加分片
   *
   * @param uploadId    上传id
   * @param partNumber  分片序号
   * @param fileContent 文件内容
   * @return 分片id
   */
  default Either<? extends Throwable, ChunkResult> addChunk(String key, String uploadId, Integer partNumber, InputStream fileContent) {
    var uploadChunkRequest = new UploadChunkRequest();
    uploadChunkRequest.setUploadId(uploadId);
    uploadChunkRequest.setKey(key);
    uploadChunkRequest.setPartNumber(partNumber);
    uploadChunkRequest.setPartContent(fileContent);
    return addChunk(uploadChunkRequest);
  }

  /**
   * 添加分片
   *
   * @param uploadId   上传id
   * @param partNumber 分片序号
   * @param file       文件
   * @return 分片id
   */
  default Either<? extends Throwable, ChunkResult> addChunk(String key, String uploadId, Integer partNumber, File file) {
    try (var content = Files.newInputStream(file.toPath())) { return addChunk(key, uploadId, partNumber, content); }
    catch (IOException e) { return Either.left(new InternalException("读取文件失败", e)); }
  }

  Either<? extends Throwable, ChunkResult> addChunk(UploadChunkRequest uploadChunkRequest);

  /**
   * 完成分片上传
   *
   * @param key           相对文件路径（对象存储的key）
   * @param uploadId      上传id
   * @param chunkPartList 分片列表
   * @return 文件id
   */
  default Either<? extends Throwable, UploadResult> finishChunk(String key, String uploadId, List<ChunkPart> chunkPartList) {
    var finishChunkRequest = new FinishChunkRequest();
    finishChunkRequest.setFileName(key);
    finishChunkRequest.setKey(key);
    finishChunkRequest.setUploadId(uploadId);
    finishChunkRequest.setPartList(chunkPartList);
    return finishChunk(finishChunkRequest);
  }

  Either<? extends Throwable, UploadResult> finishChunk(FinishChunkRequest finishChunkRequest);

  /**
   * 清理分片
   *
   * @param uploadId 上传id
   * @return 清理结果
   */
  Either<? extends Throwable, Void> abortChunks(String uploadId);

  /**
   * 删除文件
   *
   * @param filePath 相对文件路径
   * @return 删除结果
   */
  Either<? extends Throwable, Void> deleteFile(Path filePath);

  /**
   * 添加文件
   *
   * @param filePath    相对文件路径
   * @param fileContent 文件内容
   * @return 文件id
   */
  default Either<? extends Throwable, Void> addFile(Path filePath, byte[] fileContent) {
    var byteArrayInputStream = new ByteArrayInputStream(fileContent);
    return addFile(filePath, byteArrayInputStream);
  }

  /**
   * 添加文件
   *
   * @param filePath    相对文件路径
   * @param fileContent 文件内容
   * @return 文件id
   */
  Either<? extends Throwable, Void> addFile(Path filePath, InputStream fileContent);

  /**
   * 添加文件
   *
   * @param filePath 相对文件路径
   * @param file     文件
   * @return 文件id
   */
  default Either<? extends Throwable, Void> addFile(Path filePath, File file) {
    try (var content = Files.newInputStream(file.toPath())) { return addFile(filePath, content); }
    catch (IOException e) { return Either.left(new InternalException("读取文件失败", e)); }
  }

  /**
   * 获取文件
   *
   * @param filePath 相对文件路径
   * @return 文件内容流
   */
  Either<? extends Throwable, InputStream> getFileContent(Path filePath);

}
