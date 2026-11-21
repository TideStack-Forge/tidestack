package com.ouroboros.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.*;

/** S3-compatible transport. Ordinary writes stream directly using bounded native parts. */
public final class S3BlobStore implements MultipartBlobStore {
  private final S3Client client;
  private final Optional<DefaultCredentialsProvider> defaultCredentials;
  private final String bucket;
  private final String prefix;
  private final int partSize;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final S3Multipart multipart;

  S3BlobStore(S3Client client, String bucket, String prefix, String technicalPrefix, int partSize, Optional<DefaultCredentialsProvider> defaultCredentials) {
    this.defaultCredentials = defaultCredentials;
    this.client = client; this.bucket = bucket; this.prefix = prefix; this.partSize = partSize;
    multipart = new S3Multipart(this, client, bucket, prefix + technicalPrefix);
  }

  @Override public ObjectInfo put(String key, InputStream content, OptionalLong length, Optional<String> contentType) {
    checkOpen(); ObjectKeys.validate(key);
    var uploaded = writeRaw(prefix + key, content, length, contentType);
    return new ObjectInfo(key, uploaded.size(), contentType, Optional.empty(), Optional.ofNullable(uploaded.eTag()));
  }

  Written writeRaw(String key, InputStream content, OptionalLong length, Optional<String> contentType) {
    ContentTransfer.validateLength(length);
    String uploadId = null;
    try {
      byte[] buffer = new byte[partSize];
      int count = content.readNBytes(buffer, 0, buffer.length);
      long total = count;
      int nextByte = content.read();
      if (nextByte == -1) {
        ContentTransfer.requireLength(total, length);
        var request = PutObjectRequest.builder().bucket(bucket).key(key);
        contentType.ifPresent(request::contentType);
        var response = client.putObject(request.build(), RequestBody.fromBytes(Arrays.copyOf(buffer, count)));
        return new Written(total, response.eTag());
      }
      var create = CreateMultipartUploadRequest.builder().bucket(bucket).key(key);
      contentType.ifPresent(create::contentType);
      uploadId = client.createMultipartUpload(create.build()).uploadId();
      var completed = new ArrayList<CompletedPart>();
      int number = 1;
      while (true) {
        if (length.isPresent() && total > length.getAsLong()) { throw new ValidationException("Content exceeds the declared length"); }
        if (number > 10000) { throw new ValidationException("S3 native multipart part limit exceeded"); }
        var response = client.uploadPart(UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId).partNumber(number).build(),
            RequestBody.fromBytes(Arrays.copyOf(buffer, count)));
        completed.add(CompletedPart.builder().partNumber(number++).eTag(response.eTag()).build());
        if (nextByte == -1) { break; }
        buffer[0] = (byte) nextByte;
        count = 1 + content.readNBytes(buffer, 1, buffer.length - 1);
        total = Math.addExact(total, count);
        nextByte = content.read();
      }
      ContentTransfer.requireLength(total, length);
      var response = client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
          .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build());
      return new Written(total, response.eTag());
    } catch (IOException | RuntimeException e) {
      if (uploadId != null) {
        try { client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build()); }
        catch (SdkException abort) { e.addSuppressed(abort); }
      }
      throw new InternalException("S3 write failed; a completion timeout may have an uncertain outcome", e);
    }
  }

  @Override public Optional<ObjectInfo> stat(String key) {
    checkOpen(); ObjectKeys.validate(key);
    try {
      var response = client.headObject(request -> request.bucket(bucket).key(prefix + key));
      return Optional.of(new ObjectInfo(key, response.contentLength(), Optional.ofNullable(response.contentType()),
          Optional.ofNullable(response.lastModified()), Optional.ofNullable(response.eTag())));
    } catch (S3Exception e) {
      if (e.statusCode() == 404) { return Optional.empty(); }
      throw new InternalException("Cannot inspect S3 content", e);
    } catch (SdkException e) { throw new InternalException("Cannot inspect S3 content", e); }
  }

  @Override public ObjectRead open(String key, Optional<ByteRange> range) {
    checkOpen(); ObjectKeys.validate(key);
    try {
      var request = GetObjectRequest.builder().bucket(bucket).key(prefix + key);
      range.ifPresent(value -> request.range("bytes=" + value.start() + "-" + value.end()));
      var stream = client.getObject(request.build());
      var response = stream.response();
      long length = response.contentLength();
      long size = length;
      if (range.isPresent()) {
        String contentRange = response.contentRange();
        if (contentRange == null || !contentRange.startsWith("bytes " + range.get().start() + "-")) {
          stream.abort();
          throw new InternalException("S3 endpoint did not honor the requested range");
        }
        try { size = Long.parseLong(contentRange.substring(contentRange.lastIndexOf('/') + 1)); }
        catch (RuntimeException e) { stream.abort(); throw new InternalException("Invalid S3 content range", e); }
      }
      var info = new ObjectInfo(key, size, Optional.ofNullable(response.contentType()),
          Optional.ofNullable(response.lastModified()), Optional.ofNullable(response.eTag()));
      return new ObjectRead(info, stream, length);
    } catch (SdkException e) { throw new InternalException("Cannot open S3 content", e); }
  }

  @Override public void delete(String key) {
    checkOpen(); ObjectKeys.validate(key);
    try { client.deleteObject(request -> request.bucket(bucket).key(prefix + key)); }
    catch (SdkException e) { throw new InternalException("Cannot delete S3 content", e); }
  }

  @Override public ObjectPage list(String keyPrefix, int limit, Optional<String> continuationToken) {
    checkOpen(); ObjectKeys.validatePrefix(keyPrefix);
    if (limit <= 0) { throw new ValidationException("Page limit must be positive"); }
    var objects = new ArrayList<ObjectInfo>();
    String token = continuationToken.orElse(null);
    try {
      do {
        var response = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix + keyPrefix)
            .maxKeys(Math.min(limit - objects.size(), 1000)).continuationToken(token).build());
        for (var object : response.contents()) {
          String key = object.key().substring(prefix.length());
          if (!key.startsWith(ObjectKeys.TECHNICAL_PREFIX) && !key.endsWith("/")) {
            objects.add(new ObjectInfo(key, object.size(), Optional.empty(), Optional.ofNullable(object.lastModified()), Optional.ofNullable(object.eTag())));
          }
        }
        token = response.nextContinuationToken();
      } while (token != null && objects.size() < limit);
      return new ObjectPage(objects, Optional.ofNullable(token));
    } catch (SdkException e) { throw new InternalException("Cannot list S3 content", e); }
  }

  void checkOpen() { if (closed.get()) { throw new InternalException("Storage is closed"); } }
  @Override public void close() {
    if (closed.compareAndSet(false, true)) {
      try { client.close(); } finally { defaultCredentials.ifPresent(DefaultCredentialsProvider::close); }
    }
  }
  @Override public MultipartSession inspect(String session) { checkOpen(); return multipart.inspect(session); }
  @Override public String begin(String key, Optional<String> type) { checkOpen(); return multipart.begin(key, type); }
  @Override public String part(String session, int number, InputStream content, OptionalLong length) { checkOpen(); return multipart.part(session, number, content, length); }
  @Override public ObjectInfo complete(String session, List<PartReceipt> receipts) { checkOpen(); return multipart.complete(session, receipts); }
  @Override public void abort(String session) { checkOpen(); multipart.abort(session); }
  record Written(long size, String eTag) { }
}
