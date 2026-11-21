package com.ouroboros.rpc.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.hmac.SerializedBody;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class HmacSignatureRpcAuthCredentialTest {
  private static final String EMPTY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb924"
          + "27ae41e4649b934ca495991b7852b855";

  @Test
  void build_supportsOnlyHmacSignatureType() {
    RPCCredentialMetadata metadata = metadata("testHmacCredential", "HmacSignature", "aws_sigv4");

    assertTrue(new HmacSignatureRpcAuthCredential.Builder().support(metadata));
    assertFalse(new HmacSignatureRpcAuthCredential.Builder().support(metadata("legacyDigestCredential", "DigestSignature", null)));
    assertFalse(new HmacSignatureRpcAuthCredential.Builder().support(metadata("testBasicCredential", "HttpBasic", null)));
  }

  @Test
  void build_rejectsMissingPreset() {
    AuthenticateException exception = assertBuildFailure(metadata("missingPresetCredential", "HmacSignature", null));

    assertTrue(exception.getMessage().contains("missingPresetCredential"));
    assertTrue(exception.getMessage().contains("preset"));
  }

  @Test
  void build_rejectsMissingAccessKey() {
    RPCCredentialMetadata metadata = metadata("missingAccessKeyCredential", "HmacSignature", "aws_sigv4");
    ((Map<String, Object>) metadata.getExtraProps().get("hmacSignature")).put("accessKey", "   ");

    AuthenticateException exception = assertBuildFailure(metadata);

    assertTrue(exception.getMessage().contains("missingAccessKeyCredential"));
    assertTrue(exception.getMessage().contains("aws_sigv4"));
    assertTrue(exception.getMessage().contains("accessKey"));
  }

  @Test
  void build_rejectsMissingSecretKey() {
    RPCCredentialMetadata metadata = metadata("missingSecretKeyCredential", "HmacSignature", "aws_sigv4");
    ((Map<String, Object>) metadata.getExtraProps().get("hmacSignature")).put("secretKey", "   ");

    AuthenticateException exception = assertBuildFailure(metadata);

    assertTrue(exception.getMessage().contains("missingSecretKeyCredential"));
    assertTrue(exception.getMessage().contains("aws_sigv4"));
    assertTrue(exception.getMessage().contains("secretKey"));
  }

  @Test
  void build_rejectsUnsupportedPresetName() {
    AuthenticateException exception = assertBuildFailure(metadata("unsupportedPresetCredential", "HmacSignature", "legacy_digest"));

    assertTrue(exception.getMessage().contains("unsupportedPresetCredential"));
    assertTrue(exception.getMessage().contains("legacy_digest"));
    assertTrue(exception.getMessage().contains("preset"));
  }

  @Test
  void build_rejectsSignedHeaderIncludeExcludeCollision() {
    RPCCredentialMetadata metadata = metadata("signedHeaderCollisionCredential", "HmacSignature", "aws_sigv4");
    Map<String, Object> hmacSignature = (Map<String, Object>) metadata.getExtraProps().get("hmacSignature");
    Map<String, Object> signedHeaders = new LinkedHashMap<String, Object>();
    signedHeaders.put("include", list("host", "x-trace-id"));
    signedHeaders.put("exclude", list("x-trace-id"));
    hmacSignature.put("signedHeaders", signedHeaders);

    AuthenticateException exception = assertBuildFailure(metadata);

    assertTrue(exception.getMessage().contains("signedHeaderCollisionCredential"));
    assertTrue(exception.getMessage().contains("aws_sigv4"));
    assertTrue(exception.getMessage().contains("signedHeaders.exclude"));
  }

  @Test
  void build_rejectsExcludingRequiredHeaders() {
    RPCCredentialMetadata metadata = metadata("requiredHeaderCredential", "HmacSignature", "aws_sigv4");
    Map<String, Object> hmacSignature = (Map<String, Object>) metadata.getExtraProps().get("hmacSignature");
    Map<String, Object> signedHeaders = new LinkedHashMap<String, Object>();
    signedHeaders.put("include", Collections.emptyList());
    signedHeaders.put("exclude", list("host"));
    hmacSignature.put("signedHeaders", signedHeaders);

    AuthenticateException exception = assertBuildFailure(metadata);

    assertTrue(exception.getMessage().contains("requiredHeaderCredential"));
    assertTrue(exception.getMessage().contains("aws_sigv4"));
    assertTrue(exception.getMessage().contains("signedHeaders.exclude"));
    assertTrue(exception.getMessage().contains("host"));
  }

  @Test
  void build_rejectsMalformedSignedHeadersContainer() {
    RPCCredentialMetadata metadata = metadata("malformedSignedHeadersCredential", "HmacSignature", "aws_sigv4");
    Map<String, Object> hmacSignature = (Map<String, Object>) metadata.getExtraProps().get("hmacSignature");
    hmacSignature.put("signedHeaders", "not-a-map");

    AuthenticateException exception = assertBuildFailure(metadata);

    assertTrue(exception.getMessage().contains("malformedSignedHeadersCredential"));
    assertTrue(exception.getMessage().contains("signedHeaders"));
  }

  @Test
  void applyToRequest_awsSigV4_addsAuthorizationAndCanonicalHeaders() {
    HttpRPCRequest request = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");
    RPCAuthCredential<HttpRPCRequest> credential = credential("aws_sigv4");

    Try<HttpRPCRequest> result = credential.applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertTrue(headerValue(request, "Authorization").startsWith("AWS4-HMAC-SHA256 Credential=test-ak/"));
    assertTrue(headerValue(request, "Authorization").contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"));
    assertNotNull(headerValue(request, "x-amz-date"));
    assertEquals(sha256Hex("{\"a\":true,\"z\":1}"), headerValue(request, "x-amz-content-sha256"));
    assertEquals("{\"a\":true,\"z\":1}", serializedBody(request).getBodyText());
  }

  @Test
  void applyToRequest_awsSigV4_honorsPayloadHashModes() {
    HttpRPCRequest autoRequest = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");
    Try<HttpRPCRequest> autoResult = credential("aws_sigv4").applyToRequest(autoRequest, Collections.emptyMap());
    assertTrue(autoResult.isSuccess(), () -> failureMessage(autoResult));
    assertEquals(sha256Hex("{\"a\":true,\"z\":1}"), headerValue(autoRequest, "x-amz-content-sha256"));

    HttpRPCRequest emptyHashRequest = requestWithoutBody("https://ecs.cn-north-1.amazonaws.com/");
    Try<HttpRPCRequest> emptyHashResult = credential("aws_sigv4", hmac -> hmac.put("payloadHashMode", "empty_sha256"))
        .applyToRequest(emptyHashRequest, Collections.emptyMap());
    assertTrue(emptyHashResult.isSuccess(), () -> failureMessage(emptyHashResult));
    assertEquals(EMPTY_SHA256, headerValue(emptyHashRequest, "x-amz-content-sha256"));

    HttpRPCRequest unsignedRequest = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");
    Try<HttpRPCRequest> unsignedResult = credential("aws_sigv4", hmac -> hmac.put("payloadHashMode", "unsigned_payload"))
        .applyToRequest(unsignedRequest, Collections.emptyMap());
    assertTrue(unsignedResult.isSuccess(), () -> failureMessage(unsignedResult));
    assertEquals("UNSIGNED-PAYLOAD", headerValue(unsignedRequest, "x-amz-content-sha256"));
  }

  @Test
  void applyToRequest_awsSigV4_rejectsEmptySha256WhenBodyPresent() {
    HttpRPCRequest request = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");

    Try<HttpRPCRequest> result = credential("aws_sigv4", hmac -> hmac.put("payloadHashMode", "empty_sha256"))
        .applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isFailure());
    assertTrue(failureMessage(result).contains("payloadHashMode"));
  }

  @Test
  void applyToRequest_awsSigV4_signsSecurityTokenWhenPresent() {
    HttpRPCRequest request = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");
    Try<HttpRPCRequest> result = credential("aws_sigv4", hmac -> hmac.put("securityToken", "session-token"))
        .applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("session-token", headerValue(request, "x-amz-security-token"));
    assertTrue(headerValue(request, "Authorization")
        .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token"));
  }

  @Test
  void applyToRequest_headerPresets_lowercaseAndSortSignedHeaders() {
    HttpRPCRequest request = jsonRequest("https://ecs.cn-north-1.amazonaws.com/");
    request.header("X-Beta", "2");
    request.header("X-Alpha", "1");

    Try<HttpRPCRequest> result = credential("aws_sigv4", hmac -> {
      Map<String, Object> signedHeaders = new LinkedHashMap<String, Object>();
      signedHeaders.put("include", list("X-Beta", "X-Alpha"));
      signedHeaders.put("exclude", Collections.emptyList());
      hmac.put("signedHeaders", signedHeaders);
    }).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertTrue(headerValue(request, "Authorization")
        .contains("SignedHeaders=host;x-alpha;x-amz-content-sha256;x-amz-date;x-beta"));
  }

  @Test
  void applyToRequest_tencentTc3_addsConfiguredActionAndVersionHeaders() {
    HttpRPCRequest request = jsonRequest("https://cvm.tencentcloudapi.com/");
    request.header("X-TC-Action", "FromRequest");
    request.header("X-TC-Version", "2099-01-01");

    Try<HttpRPCRequest> result = credential("tencent_tc3", hmac -> {
      hmac.put("service", "cvm");
      hmac.put("region", "ap-guangzhou");
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2017-03-12");
    }).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("DescribeInstances", headerValue(request, "X-TC-Action"));
    assertEquals("2017-03-12", headerValue(request, "X-TC-Version"));
    assertNotNull(headerValue(request, "X-TC-Timestamp"));
    assertTrue(headerValue(request, "Authorization").startsWith("TC3-HMAC-SHA256 Credential=test-ak/"));
    assertTrue(headerValue(request, "Authorization").contains("SignedHeaders=content-type;host;x-tc-action;x-tc-region"));
  }

  @Test
  void applyToRequest_tencentTc3_signsInferredJsonContentType() {
    HttpRPCRequest request = jsonRequestWithoutContentType("https://cvm.tencentcloudapi.com/");

    Try<HttpRPCRequest> result = credential("tencent_tc3", hmac -> {
      hmac.put("service", "cvm");
      hmac.put("region", "ap-guangzhou");
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2017-03-12");
    }).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("application/json", headerValue(request, "Content-Type"));
    assertTrue(headerValue(request, "Authorization").contains("SignedHeaders=content-type;host;x-tc-action;x-tc-region"));
  }

  @Test
  void applyToRequest_tencentTc3_signsSecurityTokenWhenPresent() {
    HttpRPCRequest request = jsonRequest("https://cvm.tencentcloudapi.com/");

    Try<HttpRPCRequest> result = credential("tencent_tc3", hmac -> {
      hmac.put("service", "cvm");
      hmac.put("region", "ap-guangzhou");
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2017-03-12");
      hmac.put("securityToken", "tc-session-token");
    }).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("tc-session-token", headerValue(request, "X-TC-Token"));
    assertTrue(headerValue(request, "Authorization")
        .contains("SignedHeaders=content-type;host;x-tc-action;x-tc-region;x-tc-token"));
  }

  @Test
  void applyToRequest_tencentTc3_requiresActionFromConfigOrRequest() {
    HttpRPCRequest request = jsonRequest("https://cvm.tencentcloudapi.com/");

    assertApplyFailure("tencent_tc3", request, hmac -> {
      hmac.put("service", "cvm");
      hmac.remove("action");
      hmac.put("version", "2017-03-12");
    }, "action");
  }

  @Test
  void applyToRequest_tencentTc3_requiresVersionFromConfigOrRequest() {
    HttpRPCRequest request = jsonRequest("https://cvm.tencentcloudapi.com/");

    assertApplyFailure("tencent_tc3", request, hmac -> {
      hmac.put("service", "cvm");
      hmac.put("action", "DescribeInstances");
      hmac.remove("version");
    }, "version");
  }

  @Test
  void applyToRequest_huaweiAndVolcengine_addRequiredTimeAndTokenHeaders() {
    HttpRPCRequest huaweiRequest = jsonRequest("https://iam.myhuaweicloud.com/v3/auth/tokens");
    Try<HttpRPCRequest> huaweiResult = credential("huawei_sdk_hmac_sha256")
        .applyToRequest(huaweiRequest, Collections.emptyMap());

    assertTrue(huaweiResult.isSuccess(), () -> failureMessage(huaweiResult));
    assertNotNull(headerValue(huaweiRequest, "X-Sdk-Date"));
    assertTrue(headerValue(huaweiRequest, "Authorization").startsWith("SDK-HMAC-SHA256 Access=test-ak"));

    HttpRPCRequest volcRequest = jsonRequest("https://open.volcengineapi.com/");
    Try<HttpRPCRequest> volcResult = credential("volcengine_hmac_sha256", hmac -> {
      hmac.put("region", "cn-beijing");
      hmac.put("service", "ecs");
      hmac.put("securityToken", "volc-session-token");
    }).applyToRequest(volcRequest, Collections.emptyMap());

    assertTrue(volcResult.isSuccess(), () -> failureMessage(volcResult));
    assertNotNull(headerValue(volcRequest, "X-Date"));
    assertEquals("volc-session-token", headerValue(volcRequest, "X-Security-Token"));
    assertTrue(headerValue(volcRequest, "Authorization").startsWith("HMAC-SHA256 Credential=test-ak/"));
  }

  @Test
  void applyToRequest_huawei_signsInferredJsonContentType() {
    HttpRPCRequest request = jsonRequestWithoutContentType("https://iam.myhuaweicloud.com/v3/auth/tokens");

    Try<HttpRPCRequest> result = credential("huawei_sdk_hmac_sha256")
        .applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("application/json", headerValue(request, "Content-Type"));
    assertTrue(headerValue(request, "Authorization").contains("SignedHeaders=content-type;host;x-sdk-date"));
  }

  @Test
  void applyToRequest_aliyunAcs3_usesConfigNonceThenRequestNonceThenGenerated() {
    HttpRPCRequest configNonceRequest = jsonRequest("https://ecs.aliyuncs.com/");
    configNonceRequest.header("X-Acs-Signature-Nonce", "request-nonce");
    Try<HttpRPCRequest> configNonceResult = credential("aliyun_acs3", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
      hmac.put("nonce", "config-nonce");
    }).applyToRequest(configNonceRequest, Collections.emptyMap());

    assertTrue(configNonceResult.isSuccess(), () -> failureMessage(configNonceResult));
    assertEquals("config-nonce", headerValue(configNonceRequest, "X-Acs-Signature-Nonce"));

    HttpRPCRequest requestNonceRequest = jsonRequest("https://ecs.aliyuncs.com/");
    requestNonceRequest.header("X-Acs-Signature-Nonce", "request-nonce");
    Try<HttpRPCRequest> requestNonceResult = credential("aliyun_acs3", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
      hmac.remove("nonce");
    }).applyToRequest(requestNonceRequest, Collections.emptyMap());

    assertTrue(requestNonceResult.isSuccess(), () -> failureMessage(requestNonceResult));
    assertEquals("request-nonce", headerValue(requestNonceRequest, "X-Acs-Signature-Nonce"));

    HttpRPCRequest generatedNonceRequest = jsonRequest("https://ecs.aliyuncs.com/");
    Try<HttpRPCRequest> generatedNonceResult = credential("aliyun_acs3", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
      hmac.remove("nonce");
    }).applyToRequest(generatedNonceRequest, Collections.emptyMap());

    assertTrue(generatedNonceResult.isSuccess(), () -> failureMessage(generatedNonceResult));
    assertNotNull(headerValue(generatedNonceRequest, "X-Acs-Signature-Nonce"));
    assertTrue(headerValue(generatedNonceRequest, "Authorization").startsWith("ACS3-HMAC-SHA256 Credential=test-ak"));
  }

  @Test
  void applyToRequest_aliyunAcs3_signsVersionHeaderWhenPresent() {
    HttpRPCRequest request = jsonRequest("https://ecs.aliyuncs.com/");

    Try<HttpRPCRequest> result = credential("aliyun_acs3", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
    }).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isSuccess(), () -> failureMessage(result));
    assertEquals("2014-05-26", headerValue(request, "X-Acs-Version"));
    assertTrue(headerValue(request, "Authorization").contains("x-acs-version"));
  }

  @Test
  void applyToRequest_aliyunAcs3_requiresActionFromConfigOrRequest() {
    HttpRPCRequest request = jsonRequest("https://ecs.aliyuncs.com/");

    assertApplyFailure("aliyun_acs3", request, hmac -> {
      hmac.remove("action");
      hmac.put("version", "2014-05-26");
    }, "action");
  }

  @Test
  void applyToRequest_aliyunAcs3_requiresVersionFromConfigOrRequest() {
    HttpRPCRequest request = jsonRequest("https://ecs.aliyuncs.com/");

    assertApplyFailure("aliyun_acs3", request, hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.remove("version");
    }, "version");
  }

  @Test
  void applyToRequest_aliyunRpcV1_signsMergedQueryWithoutSignatureSelfInclusion() {
    RPCAuthCredential<HttpRPCRequest> credential = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
    });

    HttpRPCRequest firstRequest = aliyunRpcV1Request();
    firstRequest.query("SignatureNonce", "fixed-nonce");
    Try<HttpRPCRequest> firstResult = credential.applyToRequest(firstRequest, Collections.emptyMap());

    assertTrue(firstResult.isSuccess(), () -> failureMessage(firstResult));
    assertEquals("HMAC-SHA1", firstRequest.getQuery().get("SignatureMethod"));
    assertEquals("1.0", firstRequest.getQuery().get("SignatureVersion"));
    assertNotNull(firstRequest.getQuery().get("Signature"));

    HttpRPCRequest secondRequest = aliyunRpcV1Request();
    secondRequest.query("SignatureNonce", "fixed-nonce");
    secondRequest.query("Signature", "should-be-ignored");
    Try<HttpRPCRequest> secondResult = credential.applyToRequest(secondRequest, Collections.emptyMap());

    assertTrue(secondResult.isSuccess(), () -> failureMessage(secondResult));
    assertEquals(firstRequest.getQuery().get("Signature"), secondRequest.getQuery().get("Signature"));
  }

  @Test
  void applyToRequest_aliyunRpcV1_usesConfigNonceThenRequestNonceThenGenerated() {
    HttpRPCRequest configNonceRequest = aliyunRpcV1Request();
    configNonceRequest.query("SignatureNonce", "request-nonce");
    Try<HttpRPCRequest> configNonceResult = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
      hmac.put("nonce", "config-nonce");
    }).applyToRequest(configNonceRequest, Collections.emptyMap());

    assertTrue(configNonceResult.isSuccess(), () -> failureMessage(configNonceResult));
    assertEquals("config-nonce", configNonceRequest.getQuery().get("SignatureNonce"));

    HttpRPCRequest requestNonceRequest = aliyunRpcV1Request();
    requestNonceRequest.query("SignatureNonce", "request-nonce");
    Try<HttpRPCRequest> requestNonceResult = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
    }).applyToRequest(requestNonceRequest, Collections.emptyMap());

    assertTrue(requestNonceResult.isSuccess(), () -> failureMessage(requestNonceResult));
    assertEquals("request-nonce", requestNonceRequest.getQuery().get("SignatureNonce"));

    HttpRPCRequest generatedNonceRequest = aliyunRpcV1Request();
    Try<HttpRPCRequest> generatedNonceResult = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
    }).applyToRequest(generatedNonceRequest, Collections.emptyMap());

    assertTrue(generatedNonceResult.isSuccess(), () -> failureMessage(generatedNonceResult));
    assertNotNull(generatedNonceRequest.getQuery().get("SignatureNonce"));
  }

  @Test
  void applyToRequest_aliyunRpcV1_requiresActionFromConfigOrRequest() {
    HttpRPCRequest request = aliyunRpcV1Request();

    assertApplyFailure("aliyun_rpc_v1", request, hmac -> {
      hmac.remove("action");
      hmac.put("version", "2014-05-26");
    }, "action");
  }

  @Test
  void applyToRequest_aliyunRpcV1_requiresVersionFromConfigOrRequest() {
    HttpRPCRequest request = aliyunRpcV1Request();

    assertApplyFailure("aliyun_rpc_v1", request, hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.remove("version");
    }, "version");
  }

  @Test
  void applyToRequest_aliyunRpcV1_reusesTimestampAndNonceFromUrlQuery() {
    RPCAuthCredential<HttpRPCRequest> credential = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
    });

    HttpRPCRequest urlQueryRequest = new HttpRPCRequest();
    urlQueryRequest.setMethod("GET");
    urlQueryRequest.setUrl(
        "https://ecs.aliyuncs.com/?Format=JSON&Timestamp=2026-03-14T00:00:00Z&SignatureNonce=fixed-nonce"
    );

    Try<HttpRPCRequest> urlQueryResult = credential.applyToRequest(urlQueryRequest, Collections.emptyMap());

    assertTrue(urlQueryResult.isSuccess(), () -> failureMessage(urlQueryResult));
    assertNull(urlQueryRequest.getQuery().get("Timestamp"));
    assertNull(urlQueryRequest.getQuery().get("SignatureNonce"));

    HttpRPCRequest explicitQueryRequest = aliyunRpcV1Request();
    explicitQueryRequest.query("SignatureNonce", "fixed-nonce");

    Try<HttpRPCRequest> explicitQueryResult = credential.applyToRequest(explicitQueryRequest, Collections.emptyMap());

    assertTrue(explicitQueryResult.isSuccess(), () -> failureMessage(explicitQueryResult));
    assertEquals(explicitQueryRequest.getQuery().get("Signature"), urlQueryRequest.getQuery().get("Signature"));
  }

  @Test
  void applyToRequest_aliyunRpcV1_prefersConfiguredBusinessQueryOverConflictingUrlQuery() {
    RPCAuthCredential<HttpRPCRequest> credential = credential("aliyun_rpc_v1", hmac -> {
      hmac.put("action", "DescribeInstances");
      hmac.put("version", "2014-05-26");
      hmac.put("nonce", "config-nonce");
    });

    HttpRPCRequest conflictingRequest = new HttpRPCRequest();
    conflictingRequest.setMethod("GET");
    conflictingRequest.setUrl(
        "https://ecs.aliyuncs.com/?Format=JSON&Action=FromUrl&Version=2000-01-01"
            + "&SignatureNonce=url-nonce&Timestamp=2026-03-14T00:00:00Z"
    );

    Try<HttpRPCRequest> conflictingResult = credential.applyToRequest(conflictingRequest, Collections.emptyMap());

    assertTrue(conflictingResult.isSuccess(), () -> failureMessage(conflictingResult));
    assertEquals("DescribeInstances", conflictingRequest.getQuery().get("Action"));
    assertEquals("2014-05-26", conflictingRequest.getQuery().get("Version"));
    assertEquals("config-nonce", conflictingRequest.getQuery().get("SignatureNonce"));

    HttpRPCRequest cleanRequest = aliyunRpcV1Request();
    Try<HttpRPCRequest> cleanResult = credential.applyToRequest(cleanRequest, Collections.emptyMap());

    assertTrue(cleanResult.isSuccess(), () -> failureMessage(cleanResult));
    assertEquals(cleanRequest.getQuery().get("Signature"), conflictingRequest.getQuery().get("Signature"));
  }

  private AuthenticateException assertBuildFailure(RPCCredentialMetadata metadata) {
    return assertThrows(
        AuthenticateException.class,
        () -> new HmacSignatureRpcAuthCredential.Builder().build(metadata)
    );
  }

  private RPCAuthCredential<HttpRPCRequest> credential(String preset) {
    return credential(preset, hmacSignature -> {
    });
  }

  private RPCAuthCredential<HttpRPCRequest> credential(
      String preset,
      java.util.function.Consumer<Map<String, Object>> customizer
  ) {
    RPCCredentialMetadata metadata = authMetadata("credential-" + preset, preset);
    Map<String, Object> hmacSignature = (Map<String, Object>) metadata.getExtraProps().get("hmacSignature");
    customizer.accept(hmacSignature);
    return new HmacSignatureRpcAuthCredential.Builder().build(metadata);
  }

  private RPCCredentialMetadata metadata(String credentialName, String type, String preset) {
    RPCCredentialMetadata metadata = new RPCCredentialMetadata();
    metadata.setName(credentialName);
    metadata.setType(type);
    metadata.setEnabled(true);

    Map<String, Object> hmacSignature = new LinkedHashMap<String, Object>();
    hmacSignature.put("preset", preset);
    hmacSignature.put("accessKey", "#{rpc.ak}");
    hmacSignature.put("secretKey", "#{rpc.sk}");
    hmacSignature.put("region", "cn-north-1");
    hmacSignature.put("service", "ecs");

    Map<String, Object> signedHeaders = new LinkedHashMap<String, Object>();
    signedHeaders.put("include", Collections.emptyList());
    signedHeaders.put("exclude", Collections.emptyList());
    hmacSignature.put("signedHeaders", signedHeaders);

    metadata.setExtraProps(Collections.<String, Object>singletonMap("hmacSignature", hmacSignature));
    return metadata;
  }

  private RPCCredentialMetadata authMetadata(String credentialName, String preset) {
    RPCCredentialMetadata metadata = metadata(credentialName, "HmacSignature", preset);
    Map<String, Object> hmacSignature = (Map<String, Object>) metadata.getExtraProps().get("hmacSignature");
    hmacSignature.put("accessKey", "test-ak");
    hmacSignature.put("secretKey", "test-sk");
    hmacSignature.put("region", "cn-north-1");
    hmacSignature.put("service", "ecs");
    return metadata;
  }

  private HttpRPCRequest jsonRequest(String url) {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setMethod("POST");
    request.setUrl(url);
    request.header("Content-Type", "application/json");
    request.body("z", 1);
    request.body("a", true);
    return request;
  }

  private HttpRPCRequest jsonRequestWithoutContentType(String url) {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setMethod("POST");
    request.setUrl(url);
    request.body("z", 1);
    request.body("a", true);
    return request;
  }

  private HttpRPCRequest requestWithoutBody(String url) {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setMethod("POST");
    request.setUrl(url);
    return request;
  }

  private HttpRPCRequest aliyunRpcV1Request() {
    HttpRPCRequest request = new HttpRPCRequest();
    request.setMethod("GET");
    request.setUrl("https://ecs.aliyuncs.com/");
    request.query("Format", "JSON");
    request.query("Timestamp", "2026-03-14T00:00:00Z");
    return request;
  }

  private String headerValue(HttpRPCRequest request, String name) {
    Map<String, String> headers = new HashMap<String, String>(request.getHeaders());
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private SerializedBody serializedBody(HttpRPCRequest request) {
    return (SerializedBody) request.getAttribute(SerializedBody.REQUEST_ATTRIBUTE);
  }

  private String failureMessage(Try<?> result) {
    return result.getCause() == null ? "unknown failure" : result.getCause().getMessage();
  }

  private void assertApplyFailure(
      String preset,
      HttpRPCRequest request,
      java.util.function.Consumer<Map<String, Object>> customizer,
      String expectedFieldName
  ) {
    Try<HttpRPCRequest> result = credential(preset, customizer).applyToRequest(request, Collections.emptyMap());

    assertTrue(result.isFailure());
    assertTrue(failureMessage(result).contains(expectedFieldName));
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte current : bytes) {
        String item = Integer.toHexString(current & 0xff);
        if (item.length() < 2) {
          builder.append('0');
        }
        builder.append(item);
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 should be available", exception);
    }
  }

  private List<String> list(String... values) {
    List<String> items = new ArrayList<String>();
    Collections.addAll(items, values);
    return items;
  }
}
