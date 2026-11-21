package com.ouroboros.logicflow.adapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.function.ServerRequest;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import com.ouroboros.expression.ContextWrappingHelper;
import com.ouroboros.util.Maps;

public class RequestInfoMap implements Map<String, Object> {
  private static final String PARSED_BODY_ATTRIBUTE = RequestInfoMap.class.getName() + ".parsedBody";
  final ServerRequest request;
  private Map<String, Object> params;
  private Object body;
  private boolean bodyRead;
  private RuntimeException bodyFailure;
  private Set<Entry<String, Object>> entrySet;
  static final Set<String> keys = new HashSet<>(Arrays.asList(
      "method",
      "uri",
      "path",
      "queryString",
      "protocol",
      "host",
      "port",
      "remoteAddress",
      "pathVariables",
      "headers",
      "params",
      "body",
      "data"));

  public RequestInfoMap(ServerRequest request) {
    this.request = request;
  }

  /** One parsed request view shared by adapters and synchronous Action Context. */
  public static RequestInfoMap forRequest(ServerRequest request) {
    String key = RequestInfoMap.class.getName();
    var servlet = request.servletRequest();
    Object cached = servlet.getAttribute(key);
    if (cached instanceof RequestInfoMap info) {
      return info;
    }
    RequestInfoMap info = new RequestInfoMap(request);
    servlet.setAttribute(key, info);
    return info;
  }

  /** MVC has already consumed the stream; retain its parsed value for the same request view. */
  public static void cacheParsedBody(jakarta.servlet.http.HttpServletRequest request, Supplier<Object> body) {
    request.setAttribute(PARSED_BODY_ATTRIBUTE, body);
  }

  private Object getMethod() {
    return request.methodName();
  }

  private Object getUri() {
    return request.uri();
  }

  private Object getPath() {
    return request.path();
  }

  private Object getQueryString() {
    return request.uri().getQuery();
  }

  private Object getProtocol() {
    return request.uri().getScheme();
  }

  private Object getHost() {
    return request.uri().getHost();
  }

  private Object getPort() {
    return request.uri().getPort();
  }

  private Object getRemoteAddress() {
    return request.remoteAddress().map(InetSocketAddress::toString).orElse("");
  }

  private Object getPathVariables() {
    return request.pathVariables();
  }

  private Object getHeaders() {
    return request.headers().asHttpHeaders();
  }

  // 返回不可枚举的Headers，仅可通过get获取
  private Map<String, Object> getNonEnumerableHeaders() {
    var header = request.headers().asHttpHeaders();
    return new Map<>() {
      @Override
      public int size() {
        return 0;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public boolean containsKey(Object key) {
        return header.containsKey(key);
      }

      @Override
      public boolean containsValue(Object value) {
        return false;
      }

      @Override
      public Object get(Object key) {
        return header.get(key);
      }

      @Override
      public Object put(String key, Object value) {
        return null;
      }

      @Override
      public Object remove(Object key) {
        return null;
      }

      @Override
      public void putAll(Map<? extends String, ?> m) {

      }

      @Override
      public void clear() {

      }

      @Override

      public Set<String> keySet() {
        return Collections.emptySet();
      }

      @Override

      public Collection<Object> values() {
        return Collections.emptyList();
      }

      @Override

      public Set<Entry<String, Object>> entrySet() {
        return Collections.emptySet();
      }
    };
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getParams() {
    if (params == null) {
      Map<String, Object> params = buildMapFromMultiValueMap(request.params());
      this.params = new ContextWrappingHelper(
          new ContextWrappingHelper(getNonEnumerableHeaders(),
              (Map<String, Object>) (Map<?, ?>) request.pathVariables()), Maps.Hierarchical.build(params));
    }
    return params;
  }

  private Object getBody() {
    if (bodyFailure != null) {
      throw bodyFailure;
    }
    if (!bodyRead) {
      try {
        Object cached = request.servletRequest().getAttribute(PARSED_BODY_ATTRIBUTE);
        var contentType = request.headers().contentType();
        if (cached instanceof Supplier<?> parsedBody) {
          body = parsedBody.get();
        } else if (contentType.map(MediaType.APPLICATION_FORM_URLENCODED::isCompatibleWith).orElse(false)) {
          body = buildFormDataMap(request);
        } else if (contentType.map(MediaType.MULTIPART_FORM_DATA::isCompatibleWith).orElse(false)) {
          body = buildMultipartFormDataMap(request);
        } else if (contentLength(request) == 0
            || (contentLength(request) < 0 && contentType.isEmpty())) {
          body = Collections.emptyMap();
        } else {
          body = request.body(Object.class);
        }
        bodyRead = true;
      } catch (Exception error) {
        bodyFailure = new com.ouroboros.exception.BusinessException("HTTP 请求体解析失败", error);
        throw bodyFailure;
      }
    }
    return body;
  }

  private static long contentLength(ServerRequest request) {
    return request.servletRequest().getContentLengthLong();
  }

  private Map<String, Object> buildMapFromMultiValueMap(MultiValueMap<String, ?> multiValueMap) {
    return multiValueMap.entrySet().stream()
        .map(entry -> {
          return Tuple.of(entry.getKey(), entry.getValue().size() == 1 ? entry.getValue().get(0) : entry.getValue());
        })
        .filter(tuple -> tuple._2() != null && StringUtils.isNotEmpty(tuple._1()))
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
  }

  private Map<String, Object> buildFormDataMap(ServerRequest request) {
    // TODO: 先通过 Params 获取，将来要是遇到问题，再考虑严谨一些
    //       目前 Servlet 就是这么对待 Form 提交的，虽说不严谨，但是够用
    var data = request.servletRequest().getParameterMap()
        .entrySet().stream()
        .map(entry -> {
          return Tuple.of(entry.getKey(), entry.getValue().length == 1 ? entry.getValue()[0] : Arrays.asList(entry.getValue()));
        })
        .filter(tuple -> tuple._2() != null && StringUtils.isNotEmpty(tuple._1()))
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
    return data;
  }

  private Map<String, Object> buildMultipartFormDataMap(ServerRequest request) {
    var data = Try.of(request::multipartData);
    if (data.isFailure()) {
      throw new com.ouroboros.exception.BusinessException("HTTP multipart 请求体解析失败", data.getCause());
    }

    return data.get().entrySet().stream()
        .map(entry -> Tuple.of(entry.getKey(), partsToValue(entry.getValue())))
        .filter(tuple -> tuple._2() != null && StringUtils.isNotEmpty(tuple._1()))
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
  }

  // 把 Part 转换成 MultipartFile
  public static class PartMultipartFileAdapter implements MultipartFile {
    private final Part delegate;

    public PartMultipartFileAdapter(Part delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getName() {
      return delegate.getName();
    }

    @Override
    public String getOriginalFilename() {
      return delegate.getSubmittedFileName();
    }

    @Override
    public String getContentType() {
      return delegate.getContentType() != null
          ? delegate.getContentType()
          : null;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public long getSize() {
      // 无法直接获取，需要写到临时文件再取长度
      return -1;
    }

    @Override
    public byte[] getBytes() throws IOException {
      try (InputStream inputStream = getInputStream()) {
        return IOUtils.toByteArray(inputStream);
      }
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return delegate.getInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
      // 获取输入流
      try (InputStream inputStream = getInputStream(); FileOutputStream outputStream = new FileOutputStream(dest)) {
        IOUtils.copy(inputStream, outputStream);
      }
    }
  }

  private Object partsToValue(List<Part> part) {
    var values = part.stream()
        .map(this::partToValue)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    return values.size() == 1
        ? values.get(0)
        : values;
  }

  private Object partToValue(Part part) {
    if (StringUtils.isEmpty(part.getSubmittedFileName())) {
      try (InputStream inputStream = part.getInputStream()) {
        var bytes = IOUtils.toByteArray(inputStream);
        return new String(bytes, StandardCharsets.UTF_8);
      } catch (IOException e) {
        return null;
      }
    } else {
      return new PartMultipartFileAdapter(part);
    }
  }

  @Override
  public int size() {
    return keys.size();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean containsKey(Object key) {
    if (!(key instanceof CharSequence)) {
      return false;
    }
    return keys.contains(key.toString());
  }

  @Override
  public boolean containsValue(Object value) {
    return false;
  }

  @Override
  public Object get(Object key) {
    if (!(key instanceof CharSequence)) {
      return null;
    }
    switch (key.toString()) {
      case "method":
        return getMethod();
      case "uri":
        return getUri();
      case "path":
        return getPath();
      case "queryString":
        return getQueryString();
      case "protocol":
        return getProtocol();
      case "host":
        return getHost();
      case "port":
        return getPort();
      case "remoteAddress":
        return getRemoteAddress();
      case "pathVariables":
        return getPathVariables();
      case "headers":
        return getHeaders();
      case "params":
        return getParams();
      case "body":
        return getBody();
      case "data":
        return getBody();
      default:
        return null;
    }
  }

  @Override
  public Object put(String key, Object value) {
    return null;
  }

  @Override
  public Object remove(Object key) {
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ?> m) {

  }

  @Override
  public void clear() {

  }

  @Override
  public Set<String> keySet() {
    return Collections.unmodifiableSet(keys);
  }

  @Override

  public Collection<Object> values() {
    return new LazyValues();
  }

  @Override

  public Set<Entry<String, Object>> entrySet() {
    if (entrySet == null) {
      entrySet = new LazyEntrySet();
    }
    return entrySet;
  }

  class LazyValues extends AbstractCollection<Object> {
    @Override

    public Iterator<Object> iterator() {
      return new Iterator<>() {
        int current = 0;
        final List<Supplier<Object>> values = Arrays.asList(
            RequestInfoMap.this::getMethod,
            RequestInfoMap.this::getUri,
            RequestInfoMap.this::getPath,
            RequestInfoMap.this::getQueryString,
            RequestInfoMap.this::getProtocol,
            RequestInfoMap.this::getHost,
            RequestInfoMap.this::getPort,
            RequestInfoMap.this::getRemoteAddress,
            RequestInfoMap.this::getPathVariables,
            RequestInfoMap.this::getHeaders,
            RequestInfoMap.this::getParams,
            RequestInfoMap.this::getBody,
            RequestInfoMap.this::getBody);

        @Override
        public boolean hasNext() {
          return current < keys.size();
        }

        @Override
        public Object next() {
          return values.get(current++).get();
        }
      };
    }

    @Override
    public int size() {
      return keys.size();
    }
  }

  static class LazyEntry implements Entry<String, Object> {
    String key;
    Supplier<Object> value;

    public LazyEntry(String key, Supplier<Object> value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public Object getValue() {
      return value.get();
    }

    @Override
    public Object setValue(Object value) {
      return null;
    }
  }

  class LazyEntrySet extends AbstractSet<Entry<String, Object>> {
    @Override

    public Iterator<Entry<String, Object>> iterator() {
      return new Iterator<>() {
        int current = 0;
        final List<Supplier<Entry<String, Object>>> values = Arrays.asList(
            () -> new LazyEntry("method", RequestInfoMap.this::getMethod),
            () -> new LazyEntry("uri", RequestInfoMap.this::getUri),
            () -> new LazyEntry("path", RequestInfoMap.this::getPath),
            () -> new LazyEntry("queryString", RequestInfoMap.this::getQueryString),
            () -> new LazyEntry("protocol", RequestInfoMap.this::getProtocol),
            () -> new LazyEntry("host", RequestInfoMap.this::getHost),
            () -> new LazyEntry("port", RequestInfoMap.this::getPort),
            () -> new LazyEntry("remoteAddress", RequestInfoMap.this::getRemoteAddress),
            () -> new LazyEntry("pathVariables", RequestInfoMap.this::getPathVariables),
            () -> new LazyEntry("headers", RequestInfoMap.this::getHeaders),
            () -> new LazyEntry("params", RequestInfoMap.this::getParams),
            () -> new LazyEntry("body", RequestInfoMap.this::getBody),
            () -> new LazyEntry("data", RequestInfoMap.this::getBody));

        @Override
        public boolean hasNext() {
          return current < keys.size();
        }

        @Override
        public Entry<String, Object> next() {
          return values.get(current++).get();
        }
      };
    }

    @Override
    public int size() {
      return keys.size();
    }
  }
}
