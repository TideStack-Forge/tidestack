package com.ouroboros.rpc.security.hmac;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.ouroboros.rpc.http.HttpRPCRequest;

public interface HmacSignaturePreset {
  String getName();

  boolean isHeaderSignature();

  Set<String> getRequiredSignedHeaders(HmacSignatureConfig config);

  void validate(HmacSignatureConfig config);

  ResolvedSigningContext resolveContext(
      HttpRPCRequest request,
      HmacSignatureConfig config,
      Map<String, Object> variables,
      Function<String, String> configGetter,
      Clock clock,
      HmacRequestBodySerializer serializer
  );

  CanonicalHttpRequest canonicalize(HttpRPCRequest request, ResolvedSigningContext context, SerializedBody body);

  String buildStringToSign(CanonicalHttpRequest canonicalRequest, ResolvedSigningContext context);

  String sign(String stringToSign, ResolvedSigningContext context);

  void apply(HttpRPCRequest request, ResolvedSigningContext context, String signature);
}
