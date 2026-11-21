package com.ouroboros.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import com.ouroboros.storage.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OSS_TEST_ENDPOINT", matches = ".+")
class S3MinioIT {
  private S3Client admin;
  private String bucket;
  private Map<String, String> options;

  @BeforeEach void createIsolatedBucket() {
    String endpoint = System.getenv("OSS_TEST_ENDPOINT");
    String access = System.getenv("OSS_TEST_ACCESS_KEY");
    String secret = System.getenv("OSS_TEST_SECRET_KEY");
    admin = S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).forcePathStyle(true)
        .httpClientBuilder(UrlConnectionHttpClient.builder()).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret))).build();
    bucket = "oss-test-" + UUID.randomUUID();
    admin.createBucket(request -> request.bucket(bucket));
    options = Map.of("endpoint", endpoint, "region", "us-east-1", "bucket", bucket, "path-style-access", "true",
        "credentials-mode", "static", "access-key", access, "secret-key", secret);
  }
  @AfterEach void removeOnlyTestBucket() {
    if (admin == null) { return; }
    try {
      for (var page : admin.listObjectsV2Paginator(request -> request.bucket(bucket))) {
        for (var object : page.contents()) { admin.deleteObject(request -> request.bucket(bucket).key(object.key())); }
      }
      for (var upload : admin.listMultipartUploads(request -> request.bucket(bucket)).uploads()) {
        admin.abortMultipartUpload(request -> request.bucket(bucket).key(upload.key()).uploadId(upload.uploadId()));
      }
      admin.deleteBucket(request -> request.bucket(bucket));
    } finally { admin.close(); }
  }
  private MultipartBlobStore store() { return (MultipartBlobStore) new S3BlobStoreFactory().create(options); }

  @Test void streamsLargeUnknownLengthAndPreservesExistingContentWhenSourceFails() throws Exception {
    try (var store = store()) {
      long size = 17L * 1024 * 1024 + 19;
      InputStream generated = new InputStream() {
        long remaining = size;
        @Override public int read() { return remaining-- > 0 ? 97 : -1; }
        @Override public int read(byte[] bytes, int offset, int length) {
          if (remaining == 0) { return -1; }
          int count = (int) Math.min(length, remaining); java.util.Arrays.fill(bytes, offset, offset + count, (byte) 97); remaining -= count; return count;
        }
        @Override public void close() { fail("Caller input must remain open"); }
      };
      assertEquals(size, store.put("报告/空 格.txt", generated, OptionalLong.empty(), Optional.of("text/plain")).size());
      assertEquals(size, store.stat("报告/空 格.txt").orElseThrow().size());
      try (var read = store.open("报告/空 格.txt", Optional.of(new ByteRange(size - 3, size + 10)))) {
        assertEquals("aaa", new String(read.content().readAllBytes())); assertEquals(size, read.info().size());
      }
      InputStream broken = new InputStream() {
        int remaining = 9 * 1024 * 1024;
        @Override public int read() throws IOException { if (remaining-- <= 0) { throw new IOException("source failure"); } return 98; }
      };
      assertThrows(RuntimeException.class, () -> store.put("报告/空 格.txt", broken, OptionalLong.empty(), Optional.empty()));
      assertEquals(size, store.stat("报告/空 格.txt").orElseThrow().size());
      assertTrue(admin.listMultipartUploads(request -> request.bucket(bucket)).uploads().isEmpty());
      assertThrows(RuntimeException.class, () -> store.put("报告/空 格.txt", new ByteArrayInputStream(new byte[2]), OptionalLong.of(3), Optional.empty()));
      assertEquals(size, store.stat("报告/空 格.txt").orElseThrow().size());
    }
  }

  @Test void supportsZeroLengthPaginationAndIdempotentDelete() throws Exception {
    try (var store = store()) {
      for (String key : List.of("a/1", "a/2", "other")) { store.put(key, InputStream.nullInputStream(), OptionalLong.of(0), Optional.empty()); }
      var page = store.list("a/", 1, Optional.empty()); assertEquals(1, page.objects().size());
      var next = store.list("a/", 1, page.continuationToken()); assertEquals(1, next.objects().size());
      assertNotEquals(page.objects().getFirst().key(), next.objects().getFirst().key());
      try (var read = store.open("other", Optional.empty())) { assertEquals(-1, read.content().read()); }
      assertThrows(RuntimeException.class, () -> store.open("other", Optional.of(new ByteRange(0, 1))));
      store.delete("other"); store.delete("other"); assertTrue(store.stat("other").isEmpty());
    }
  }

  @Test void legacySmallChunksSurviveClientRestartAndSelectExactReceipts() throws Exception {
    String session; String first; String obsolete; String last;
    try (var store = store()) {
      session = store.begin("finished.txt", Optional.of("text/plain"));
      last = store.part(session, 2, new ByteArrayInputStream("world".getBytes()), OptionalLong.empty());
      obsolete = store.part(session, 0, new ByteArrayInputStream("obsolete".getBytes()), OptionalLong.empty());
      first = store.part(session, 0, new ByteArrayInputStream("hello ".getBytes()), OptionalLong.of(6));
      assertTrue(store.list("", 100, Optional.empty()).objects().isEmpty());
    }
    try (var store = store()) {
      assertThrows(RuntimeException.class, () -> store.complete(session, List.of(new PartReceipt(0, first), new PartReceipt(0, obsolete))));
      assertThrows(RuntimeException.class, () -> store.complete(session, List.of(new PartReceipt(1, first))));
      var receipts = List.of(new PartReceipt(2, last), new PartReceipt(0, first));
      assertEquals(11, store.complete(session, receipts).size());
      assertEquals(11, store.complete(session, receipts.reversed()).size());
      assertThrows(RuntimeException.class, () -> store.complete(session, List.of(new PartReceipt(0, obsolete))));
      store.abort(session);
      try (var read = store.open("finished.txt", Optional.empty())) { assertEquals("hello world", new String(read.content().readAllBytes())); }
      String aborted = store.begin("never.txt", Optional.empty());
      store.part(aborted, 0, new ByteArrayInputStream("temporary".getBytes()), OptionalLong.empty());
      store.abort(aborted); store.abort(aborted);
      assertTrue(store.stat("never.txt").isEmpty());
      assertEquals(List.of("finished.txt"), store.list("", 100, Optional.empty()).objects().stream().map(ObjectInfo::key).toList());
    }
  }

  @Test void forbiddenBucketIsAnErrorInsteadOfAbsence() {
    var invalid = new java.util.HashMap<>(options); invalid.put("secret-key", "incorrect-secret");
    try (var store = new S3BlobStoreFactory().create(invalid)) {
      assertThrows(RuntimeException.class, () -> store.stat("missing"));
      assertThrows(RuntimeException.class, () -> store.list("", 10, Optional.empty()));
      assertThrows(RuntimeException.class, () -> store.delete("missing"));
    }
  }
  @Test void defaultCredentialsAndClientsHaveIndependentLifecycles() {
    String oldAccess = System.getProperty("aws.accessKeyId"); String oldSecret = System.getProperty("aws.secretAccessKey");
    System.setProperty("aws.accessKeyId", System.getenv("OSS_TEST_ACCESS_KEY"));
    System.setProperty("aws.secretAccessKey", System.getenv("OSS_TEST_SECRET_KEY"));
    var configuration = new java.util.HashMap<>(options);
    configuration.remove("access-key"); configuration.remove("secret-key"); configuration.put("credentials-mode", "default");
    try (var first = new S3BlobStoreFactory().create(configuration); var second = new S3BlobStoreFactory().create(configuration)) {
      assertNotSame(first, second); first.close();
      second.put("still-alive", InputStream.nullInputStream(), OptionalLong.of(0), Optional.empty());
      assertTrue(second.stat("still-alive").isPresent());
    } finally {
      if (oldAccess == null) { System.clearProperty("aws.accessKeyId"); } else { System.setProperty("aws.accessKeyId", oldAccess); }
      if (oldSecret == null) { System.clearProperty("aws.secretAccessKey"); } else { System.setProperty("aws.secretAccessKey", oldSecret); }
    }
  }

}
