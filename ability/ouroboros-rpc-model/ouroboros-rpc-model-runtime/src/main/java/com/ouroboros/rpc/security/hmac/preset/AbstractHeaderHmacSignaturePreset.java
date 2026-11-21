package com.ouroboros.rpc.security.hmac.preset;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Either;

import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.CanonicalHttpRequest;
import com.ouroboros.rpc.security.hmac.HmacRequestBodySerializer;
import com.ouroboros.rpc.security.hmac.HmacSignatureConfig;
import com.ouroboros.rpc.security.hmac.HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.HmacSigningSupport;
import com.ouroboros.rpc.security.hmac.ResolvedSigningContext;
import com.ouroboros.rpc.security.hmac.SerializedBody;
import com.ouroboros.rpc.util.ExpressionUtils;

abstract class AbstractHeaderHmacSignaturePreset implements HmacSignaturePreset {
  @Override
  public boolean isHeaderSignature() {
    return true;
  }

  @Override
  public Set<String> getRequiredSignedHeaders(HmacSignatureConfig config) {
    return defaultSignedHeaders(config);
  }

  protected abstract Set<String> defaultSignedHeaders(HmacSignatureConfig config);

  protected abstract ResolvedSigningContext doResolveContext(
      HttpRPCRequest request,
      HmacSignatureConfig config,
      Map<String, Object> variables,
      Function<String, String> configGetter,
      Clock clock,
      HmacRequestBodySerializer serializer
  );

  protected abstract Set<String> optionalSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context);

  protected abstract String canonicalUri(HttpRPCRequest request);

  protected String canonicalQuery(HttpRPCRequest request) {
    return HmacSigningSupport.canonicalizeQuery(request);
  }

  @Override
  public ResolvedSigningContext resolveContext(
      HttpRPCRequest request,
      HmacSignatureConfig config,
      Map<String, Object> variables,
      Function<String, String> configGetter,
      Clock clock,
      HmacRequestBodySerializer serializer
  ) {
    return doResolveContext(request, config, variables, configGetter, clock, serializer);
  }

  @Override
  public CanonicalHttpRequest canonicalize(HttpRPCRequest request, ResolvedSigningContext context, SerializedBody body) {
    Set<String> headersToSign = resolveSignedHeaders(request, context);
    SortedMap<String, String> canonicalHeaders = HmacSigningSupport.canonicalizeHeaders(request, headersToSign, context.getHost());
    return new CanonicalHttpRequest(
        StringUtils.defaultIfBlank(request.getMethod(), "GET").toUpperCase(),
        canonicalUri(request),
        canonicalQuery(request),
        HmacSigningSupport.toCanonicalHeadersText(canonicalHeaders),
        HmacSigningSupport.toSignedHeadersText(canonicalHeaders),
        context.getPayloadHash()
    );
  }

  protected Set<String> resolveSignedHeaders(HttpRPCRequest request, ResolvedSigningContext context) {
    Set<String> names = new TreeSet<String>();
    names.addAll(defaultSignedHeaders(context.getConfig()));
    names.addAll(optionalSignedHeaders(request, context));
    names.addAll(context.getConfig().getSignedHeadersInclude());
    names.removeAll(context.getConfig().getSignedHeadersExclude());

    Set<String> result = new LinkedHashSet<String>();
    for (String name : names) {
      if ("host".equals(name) || HmacSigningSupport.findHeader(request, name) != null) {
        result.add(name.toLowerCase());
      }
    }
    return Collections.unmodifiableSet(result);
  }

  protected String resolveRequiredValue(
      HmacSignatureConfig config,
      String fieldName,
      String expression,
      Map<String, Object> variables,
      Function<String, String> configGetter
  ) {
    String value = resolveOptionalValue(config, fieldName, expression, variables, configGetter);
    if (StringUtils.isBlank(value)) {
      throw config.invalid(fieldName, "求值结果为空");
    }
    return value;
  }

  protected String resolveOptionalValue(
      HmacSignatureConfig config,
      String fieldName,
      String expression,
      Map<String, Object> variables,
      Function<String, String> configGetter
  ) {
    if (StringUtils.isBlank(expression)) {
      return null;
    }
    Either<Throwable, Object> evaluated = ExpressionUtils.eval(
        expression,
        variables == null ? Collections.<String, Object>emptyMap() : variables,
        configGetter == null ? key -> null : configGetter
    );
    if (evaluated.isLeft()) {
      throw config.invalid(fieldName, "求值失败", evaluated.getLeft());
    }
    Object value = evaluated.get();
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return StringUtils.isBlank(text) ? null : text;
  }

  protected String resolvePayloadHash(HmacSignatureConfig config, SerializedBody body) {
    return HmacSigningSupport.sha256Hex(body == null ? "" : body.getBodyText());
  }

  protected String requireConfiguredOrRequestValue(HmacSignatureConfig config, String fieldName, String value) {
    if (StringUtils.isBlank(value)) {
      throw config.invalid(fieldName, "配置不能为空，且请求中未提供");
    }
    return value;
  }

  protected String header(HttpRPCRequest request, String name) {
    return HmacSigningSupport.findHeader(request, name);
  }

  protected void putHeader(HttpRPCRequest request, String name, String value) {
    HmacSigningSupport.putHeader(request, name, value);
  }

  protected String host(HttpRPCRequest request) {
    return HmacSigningSupport.buildHost(HmacSigningSupport.toUri(request));
  }

  protected String basicCanonicalUri(HttpRPCRequest request) {
    return HmacSigningSupport.canonicalizePath(HmacSigningSupport.toUri(request));
  }

  protected Instant resolveInstant(String rawValue, java.time.format.DateTimeFormatter formatter, HmacSignatureConfig config, String fieldName) {
    try {
      return formatter.parse(rawValue, Instant::from);
    } catch (DateTimeParseException exception) {
      throw config.invalid(fieldName, "时间格式无效", exception);
    }
  }
}
