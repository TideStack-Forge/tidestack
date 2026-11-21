package com.ouroboros.storage.s3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.storage.*;

/** Only the legacy chunk extension stages objects. Its technical state survives application restarts. */
final class S3Multipart {
  private static final Logger LOG = Logger.getLogger(S3Multipart.class.getName());
  private final S3BlobStore store;
  private final S3Client client;
  private final String bucket;
  private final String prefix;

  S3Multipart(S3BlobStore store, S3Client client, String bucket, String prefix) {
    this.store = store; this.client = client; this.bucket = bucket; this.prefix = prefix;
  }

  MultipartSession inspect(String session) {
    var state = load(session);
    return new MultipartSession(state.getProperty("key"), MultipartSession.Status.valueOf(state.getProperty("status").toUpperCase(java.util.Locale.ROOT)));
  }

  String begin(String key, Optional<String> contentType) {
    ObjectKeys.validate(key);
    String session = UUID.randomUUID().toString();
    var state = new Properties();
    state.setProperty("key", key); state.setProperty("type", contentType.orElse(""));
    state.setProperty("status", "open"); state.setProperty("created", Long.toString(System.currentTimeMillis()));
    save(session, state);
    return session;
  }

  String part(String session, int number, InputStream content, OptionalLong length) {
    if (number < 0) { throw new ValidationException("Part number must not be negative"); }
    var state = load(session);
    if (!state.getProperty("status").equals("open")) { throw new ValidationException("Multipart session is no longer open"); }
    String receipt = number + "-" + UUID.randomUUID();
    store.writeRaw(base(session) + "parts/" + receipt, content, length, Optional.empty());
    return receipt;
  }

  ObjectInfo complete(String session, List<PartReceipt> receipts) {
    var seen = new HashSet<Integer>();
    for (var receipt : receipts) {
      if (!seen.add(receipt.partNumber())) { throw new ValidationException("Duplicate multipart part number"); }
    }
    var ordered = receipts.stream().sorted(Comparator.comparingInt(PartReceipt::partNumber)).toList();
    String signature = signature(ordered);
    var state = load(session);
    String status = state.getProperty("status");
    if (!status.equals("open")) {
      if (!signature.equals(state.getProperty("receipts"))) { throw new ValidationException("Multipart receipts differ from the previous completion"); }
      if (status.equals("completed")) { return result(state); }
      throw new InternalException("Multipart completion outcome is uncertain; staged content was retained");
    }
    var keys = ordered.stream().map(receipt -> partKey(session, receipt)).toList();
    try {
      long total = 0;
      for (String key : keys) { total = Math.addExact(total, client.headObject(request -> request.bucket(bucket).key(key)).contentLength()); }
      state.setProperty("receipts", signature); state.setProperty("status", "completing");
      save(session, state);
      var iterator = keys.iterator();
      Enumeration<InputStream> streams = new Enumeration<>() {
        @Override public boolean hasMoreElements() { return iterator.hasNext(); }
        @Override public InputStream nextElement() { return client.getObject(request -> request.bucket(bucket).key(iterator.next())); }
      };
      ObjectInfo info;
      try (var content = new SequenceInputStream(streams)) {
        info = store.put(state.getProperty("key"), content, OptionalLong.of(total), Optional.of(state.getProperty("type")).filter(s -> !s.isEmpty()));
      }
      state.setProperty("size", Long.toString(info.size())); state.setProperty("etag", info.eTag().orElse(""));
      state.setProperty("status", "completed"); save(session, state);
      try { cleanParts(session); }
      catch (SdkException e) { LOG.log(Level.WARNING, "Completed multipart retains technical content for retry: " + session, e); }
      return info;
    } catch (IOException | SdkException e) {
      throw new InternalException("Cannot complete S3 multipart; a transport failure may have an uncertain outcome", e);
    }
  }

  void abort(String session) {
    var state = find(session);
    if (state.isEmpty()) { return; }
    String status = state.get().getProperty("status");
    if (status.equals("completing")) { throw new InternalException("Cannot discard an uncertain multipart completion"); }
    if (!status.equals("completed")) { state.get().setProperty("status", "aborted"); save(session, state.get()); }
    try {
      cleanParts(session);
      client.deleteObject(request -> request.bucket(bucket).key(base(session) + "state"));
    } catch (SdkException e) { throw new InternalException("Cannot clean multipart technical state; retry cancellation", e); }
  }

  private void cleanParts(String session) {
    String token = null;
      do {
        var page = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(base(session) + "parts/").continuationToken(token).build());
        for (var object : page.contents()) { client.deleteObject(request -> request.bucket(bucket).key(object.key())); }
        token = page.nextContinuationToken();
      } while (token != null);
  }

  private Properties load(String session) {
    return find(session).orElseThrow(() -> new ValidationException("Multipart session does not exist"));
  }
  private Optional<Properties> find(String session) {
    try (var input = client.getObject(request -> request.bucket(bucket).key(base(session) + "state"))) {
      byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1);
      if (bytes.length > 4 * 1024 * 1024) { throw new InternalException("Oversized multipart technical state"); }
      var state = new Properties(); state.load(new ByteArrayInputStream(bytes)); return Optional.of(state);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) { return Optional.empty(); }
      throw new InternalException("Cannot read S3 multipart state", e);
    } catch (IOException | SdkException e) { throw new InternalException("Cannot read S3 multipart state", e); }
  }
  private void save(String session, Properties state) {
    try {
      var bytes = new ByteArrayOutputStream(); state.store(bytes, "multipart technical state");
      client.putObject(request -> request.bucket(bucket).key(base(session) + "state"), RequestBody.fromBytes(bytes.toByteArray()));
    } catch (IOException | SdkException e) { throw new InternalException("Cannot persist S3 multipart state", e); }
  }
  private String base(String session) {
    try {
      if (!UUID.fromString(session).toString().equals(session)) { throw new ValidationException("Invalid multipart session"); }
      return prefix + session + "/";
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid multipart session"); }
  }
  private String partKey(String session, PartReceipt receipt) {
    String value = receipt.receipt(); String number = receipt.partNumber() + "-";
    if (!value.startsWith(number)) { throw new ValidationException("Receipt does not match part number"); }
    try {
      if (!UUID.fromString(value.substring(number.length())).toString().equals(value.substring(number.length()))) {
        throw new ValidationException("Invalid multipart receipt");
      }
    } catch (IllegalArgumentException e) { throw new ValidationException("Invalid multipart receipt"); }
    return base(session) + "parts/" + value;
  }
  private String signature(List<PartReceipt> ordered) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ordered.toString().getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new InternalException("SHA-256 is unavailable", e); }
  }
  private ObjectInfo result(Properties state) {
    return new ObjectInfo(state.getProperty("key"), Long.parseLong(state.getProperty("size")),
        Optional.of(state.getProperty("type")).filter(s -> !s.isEmpty()), Optional.empty(), Optional.of(state.getProperty("etag")).filter(s -> !s.isEmpty()));
  }
}
