package com.ouroboros.rpc.security.hmac;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.ouroboros.rpc.http.HttpRPCRequest;

public final class HmacSigningSupport {
  public static final DateTimeFormatter BASIC_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC);
  public static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
      .withZone(ZoneOffset.UTC);
  public static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
      .withZone(ZoneOffset.UTC);
  public static final DateTimeFormatter ACS3_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      .withZone(ZoneOffset.UTC);

  private HmacSigningSupport() {
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return hex(digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 should be available", exception);
    }
  }

  public static byte[] hmacSha256(byte[] key, String value) {
    return hmac("HmacSHA256", key, value);
  }

  public static String hmacSha256Hex(byte[] key, String value) {
    return hex(hmacSha256(key, value));
  }

  public static String hmacSha256Hex(String key, String value) {
    return hmacSha256Hex(StringUtils.defaultString(key).getBytes(StandardCharsets.UTF_8), value);
  }

  public static byte[] hmac(String algorithm, byte[] key, String value) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("HMAC calculation failed", exception);
    }
  }

  public static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (byte current : bytes) {
      String item = Integer.toHexString(current & 0xff);
      if (item.length() < 2) {
        builder.append('0');
      }
      builder.append(item);
    }
    return builder.toString();
  }

  public static URI toUri(HttpRPCRequest request) {
    if (request == null || StringUtils.isBlank(request.getUrl())) {
      return null;
    }
    return URI.create(UriComponentsBuilder.fromUriString(request.getUrl())
        .build(false)
        .expand(UriUtils.encodeUriVariables(request.getPathVariables()))
        .toUriString());
  }

  public static String buildHost(URI uri) {
    String host = uri == null ? null : uri.getHost();
    if (StringUtils.isBlank(host)) {
      return "";
    }
    int port = uri.getPort();
    if (port < 0 || isDefaultPort(uri.getScheme(), port)) {
      return host;
    }
    return host + ":" + port;
  }

  private static boolean isDefaultPort(String scheme, int port) {
    return ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
  }

  public static String canonicalizePath(URI uri) {
    return canonicalizePath(uri == null ? null : uri.getRawPath());
  }

  public static String canonicalizePath(String rawPath) {
    String value = StringUtils.defaultIfBlank(rawPath, "/");
    String[] segments = value.split("/", -1);
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < segments.length; index++) {
      if (index > 0) {
        builder.append('/');
      }
      if (StringUtils.isNotEmpty(segments[index])) {
        appendEncoded(builder, percentDecode(segments[index]), false);
      }
    }
    return builder.toString();
  }

  public static String canonicalizeQuery(HttpRPCRequest request) {
    return canonicalizeQuery(request, Collections.<String>emptySet());
  }

  public static String findQuery(HttpRPCRequest request, String name) {
    if (request == null || StringUtils.isBlank(name)) {
      return null;
    }
    if (request.getQuery().containsKey(name)) {
      return request.getQuery().get(name);
    }

    URI uri = toUri(request);
    String uriQuery = uri == null ? null : uri.getRawQuery();
    if (StringUtils.isBlank(uriQuery)) {
      return null;
    }
    for (String item : uriQuery.split("&", -1)) {
      if (item.isEmpty()) {
        continue;
      }
      int separatorIndex = item.indexOf('=');
      String queryName = separatorIndex < 0 ? percentDecode(item) : percentDecode(item.substring(0, separatorIndex));
      if (!name.equals(queryName)) {
        continue;
      }
      return separatorIndex < 0 ? "" : percentDecode(item.substring(separatorIndex + 1));
    }
    return null;
  }

  public static void removeUrlQuery(HttpRPCRequest request, String name) {
    if (request == null || StringUtils.isBlank(name) || StringUtils.isBlank(request.getUrl())) {
      return;
    }

    URI uri = toUri(request);
    String rawQuery = uri == null ? null : uri.getRawQuery();
    if (StringUtils.isBlank(rawQuery)) {
      return;
    }

    List<String> retainedItems = new ArrayList<String>();
    boolean removed = false;
    for (String item : rawQuery.split("&", -1)) {
      if (item.isEmpty()) {
        continue;
      }
      int separatorIndex = item.indexOf('=');
      String queryName = separatorIndex < 0 ? percentDecode(item) : percentDecode(item.substring(0, separatorIndex));
      if (name.equals(queryName)) {
        removed = true;
        continue;
      }
      retainedItems.add(item);
    }
    if (!removed) {
      return;
    }

    request.setUrl(UriComponentsBuilder.fromUriString(request.getUrl())
        .replaceQuery(retainedItems.isEmpty() ? null : String.join("&", retainedItems))
        .build(false)
        .toUriString());
  }

  public static String canonicalizeQuery(HttpRPCRequest request, Collection<String> excludedNames) {
    return canonicalizeQuery(toUri(request), request == null ? null : request.getQuery(), excludedNames);
  }

  public static String mergeQuery(HttpRPCRequest request) {
    return mergeQuery(toUri(request), request == null ? null : request.getQuery());
  }

  public static String mergeQuery(URI uri, Map<String, String> requestQuery) {
    return mergeQuery(uri, requestQuery, Collections.<String>emptySet());
  }

  public static String mergeQuery(
      URI uri,
      Map<String, String> requestQuery,
      Collection<String> excludedNames
  ) {
    return toQueryText(mergeQueryItems(uri, requestQuery, excludedNames));
  }

  public static String canonicalizeQuery(URI uri, Map<String, String> requestQuery) {
    return canonicalizeQuery(uri, requestQuery, Collections.<String>emptySet());
  }

  public static String canonicalizeQuery(
      URI uri,
      Map<String, String> requestQuery,
      Collection<String> excludedNames
  ) {
    List<QueryItem> items = mergeQueryItems(uri, requestQuery, excludedNames);
    Collections.sort(items, Comparator
        .comparing(QueryItem::getEncodedName)
        .thenComparing(QueryItem::getEncodedValue));
    return toQueryText(items);
  }

  private static boolean shouldIncludeQueryItem(String name, Collection<String> excludedNames) {
    return excludedNames == null || excludedNames.isEmpty() || !excludedNames.contains(name);
  }

  private static List<QueryItem> mergeQueryItems(
      URI uri,
      Map<String, String> requestQuery,
      Collection<String> excludedNames
  ) {
    List<QueryItem> items = new ArrayList<QueryItem>();
    String uriQuery = uri == null ? null : uri.getRawQuery();
    if (StringUtils.isNotBlank(uriQuery)) {
      for (String item : uriQuery.split("&", -1)) {
        if (item.isEmpty()) {
          continue;
        }
        int separatorIndex = item.indexOf('=');
        String name = separatorIndex < 0 ? percentDecode(item) : percentDecode(item.substring(0, separatorIndex));
        if (requestQuery != null && requestQuery.containsKey(name)) {
          continue;
        }
        if (shouldIncludeQueryItem(name, excludedNames)) {
          String value = separatorIndex < 0 ? "" : percentDecode(item.substring(separatorIndex + 1));
          items.add(new QueryItem(name, value));
        }
      }
    }
    if (requestQuery != null) {
      for (Map.Entry<String, String> entry : requestQuery.entrySet()) {
        if (shouldIncludeQueryItem(entry.getKey(), excludedNames)) {
          items.add(new QueryItem(entry.getKey(), entry.getValue()));
        }
      }
    }
    return items;
  }

  private static String toQueryText(List<QueryItem> items) {
    List<String> encoded = new ArrayList<String>();
    for (QueryItem item : items) {
      encoded.add(item.getEncodedName() + "=" + item.getEncodedValue());
    }
    return String.join("&", encoded);
  }

  public static SortedMap<String, String> canonicalizeHeaders(
      HttpRPCRequest request,
      Collection<String> signedHeaders,
      String host
  ) {
    TreeMap<String, String> headers = new TreeMap<String, String>();
    for (String headerName : signedHeaders) {
      if ("host".equals(headerName)) {
        headers.put("host", normalizeHeaderValue(host));
        continue;
      }

      String value = findHeader(request, headerName);
      if (value != null) {
        headers.put(headerName.toLowerCase(), normalizeHeaderValue(value));
      }
    }
    return Collections.unmodifiableSortedMap(headers);
  }

  public static String toCanonicalHeadersText(SortedMap<String, String> headers) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      builder.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
    }
    return builder.toString();
  }

  public static String toSignedHeadersText(SortedMap<String, String> headers) {
    return String.join(";", headers.keySet());
  }

  public static String normalizeHeaderValue(String value) {
    return StringUtils.defaultString(value).trim().replaceAll("\\s+", " ");
  }

  public static String findHeader(HttpRPCRequest request, String name) {
    if (request == null || StringUtils.isBlank(name)) {
      return null;
    }
    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
      if (name.equalsIgnoreCase(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  public static void putHeader(HttpRPCRequest request, String name, String value) {
    if (request == null || StringUtils.isBlank(name)) {
      return;
    }
    removeHeader(request, name);
    request.header(name, value);
  }

  public static void removeHeader(HttpRPCRequest request, String name) {
    if (request == null || StringUtils.isBlank(name)) {
      return;
    }
    List<String> toRemove = new ArrayList<String>();
    for (String headerName : request.getHeaders().keySet()) {
      if (name.equalsIgnoreCase(headerName)) {
        toRemove.add(headerName);
      }
    }
    for (String headerName : toRemove) {
      request.removeHeader(headerName);
    }
  }

  public static String percentEncode(String value) {
    StringBuilder builder = new StringBuilder();
    appendEncoded(builder, value, false);
    return builder.toString();
  }

  private static String percentDecode(String value) {
    if (StringUtils.isEmpty(value)) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (current == '%' && index + 2 < value.length()) {
        int high = hexValue(value.charAt(index + 1));
        int low = hexValue(value.charAt(index + 2));
        if (high >= 0 && low >= 0) {
          buffer.write((high << 4) + low);
          index += 2;
          continue;
        }
      }
      flushDecodedBytes(builder, buffer);
      builder.append(current);
    }
    flushDecodedBytes(builder, buffer);
    return builder.toString();
  }

  private static void flushDecodedBytes(StringBuilder builder, ByteArrayOutputStream buffer) {
    if (buffer.size() <= 0) {
      return;
    }
    builder.append(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    buffer.reset();
  }

  private static int hexValue(char value) {
    if (value >= '0' && value <= '9') {
      return value - '0';
    }
    if (value >= 'a' && value <= 'f') {
      return value - 'a' + 10;
    }
    if (value >= 'A' && value <= 'F') {
      return value - 'A' + 10;
    }
    return -1;
  }

  private static void appendEncoded(StringBuilder builder, String value, boolean preserveSlash) {
    byte[] bytes = StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8);
    for (byte current : bytes) {
      int unsigned = current & 0xff;
      char character = (char) unsigned;
      if (isUnreserved(character) || (preserveSlash && character == '/')) {
        builder.append(character);
      } else {
        builder.append('%');
        char high = Character.toUpperCase(Character.forDigit((unsigned >> 4) & 0xf, 16));
        char low = Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16));
        builder.append(high).append(low);
      }
    }
  }

  private static boolean isUnreserved(char value) {
    return (value >= 'a' && value <= 'z')
        || (value >= 'A' && value <= 'Z')
        || (value >= '0' && value <= '9')
        || value == '-'
        || value == '_'
        || value == '.'
        || value == '~';
  }

  private static final class QueryItem {
    private final String encodedName;
    private final String encodedValue;

    private QueryItem(String name, String value) {
      this.encodedName = percentEncode(name);
      this.encodedValue = percentEncode(value);
    }

    private String getEncodedName() {
      return encodedName;
    }

    private String getEncodedValue() {
      return encodedValue;
    }
  }
}
