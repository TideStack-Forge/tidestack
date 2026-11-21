package com.ouroboros.ability.auth.oidc.registration;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationConverter;
import com.ouroboros.ability.auth.oidc.OidcClientSpec;
import com.ouroboros.ability.auth.oidc.OidcRuntimeSettings;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** SAS adapter. All persistent client writes are qualified by the original configuration policy. */
public final class DcrRegistrationEndpoint implements AuthenticationConverter, AuthenticationProvider {
  public static final String PATH = "/connect/register";
  private final ConfigurationService configuration;
  private final ConfigurationManagementService management;
  private final DcrCredentials credentials;
  private final DcrRateLimiter limiter;
  private final Supplier<String> issuer;
  public DcrRegistrationEndpoint(ConfigurationService configuration, ConfigurationManagementService management,
      DcrCredentials credentials, DcrRateLimiter limiter, Supplier<String> issuer) {
    this.configuration = configuration; this.management = management; this.credentials = credentials; this.limiter = limiter; this.issuer = issuer;
  }
  public ConfigurationSnapshot policy() {
    var snapshot = configuration.getSnapshot(DcrSettings.KEY).orElseThrow(DcrFailure::unavailable);
    if (snapshot.storedRawValue() == null || !DcrSettings.parse(snapshot.value()).enabled()
        || !OidcRuntimeSettings.current(configuration).enabled() || !credentials.ready()) { throw DcrFailure.unavailable(); }
    return snapshot;
  }
  public boolean available() {
    try { policy(); return true; }
    catch (RuntimeException error) { return false; } // Discovery omits an endpoint whose authoritative policy cannot be read.
  }
  public void customizeDiscovery(Map<String,Object> claims) {
    if (available()) { claims.put("registration_endpoint", issuer.get() + PATH); }
    else { claims.remove("registration_endpoint"); }
  }
  @Override
  public Authentication convert(HttpServletRequest request) {
    if (!"POST".equals(request.getMethod()) || !PATH.equals(request.getRequestURI().substring(request.getContextPath().length()))) { return null; }
    request.setAttribute("ouroboros.dcr.requestId", UUID.randomUUID().toString());
    try {
      var snapshot = policy();
      var settings = DcrSettings.parse(snapshot.value());
      limiter.count(issuer.get(), request.getRemoteAddr(), settings.rateLimit());
      String type = request.getContentType();
      if (type == null || !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(org.springframework.http.MediaType.parseMediaType(type))) {
        throw new DcrFailure(400, "invalid_client_metadata");
      }
      byte[] bytes = request.getInputStream().readNBytes(65_537);
      if (bytes.length > 65_536) { throw new DcrFailure(413, "invalid_client_metadata"); }
      var metadata = Json.tryToMap(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
          .filter(java.util.Objects::nonNull).getOrElseThrow(error -> new DcrFailure(400, "invalid_client_metadata"));
      String token = null;
      if (settings.mode() == DcrSettings.Mode.INITIAL_ACCESS_TOKEN) {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
            || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1) { throw new DcrFailure(401, "invalid_token"); }
        token = header.substring(7);
      }
      return new Request(metadata, token, snapshot, (String) request.getAttribute("ouroboros.dcr.requestId"));
    } catch (DcrFailure failure) { throw failure; }
    catch (IOException | IllegalArgumentException error) { throw new DcrFailure(400, "invalid_client_metadata"); }
    catch (RuntimeException error) { throw DcrFailure.unavailable(); }
  }
  @Override
  public Authentication authenticate(Authentication authentication) {
    Request request = (Request) authentication;
    DcrCredentials.Claim claim = null;
    String clientId = null;
    try {
      var settings = DcrSettings.parse(request.policy.value());
      if (settings.mode() == DcrSettings.Mode.INITIAL_ACCESS_TOKEN) { credentials.check(request.token); }
      var candidate = DcrMetadata.accept(request.metadata, settings);
      var client = candidate.client();
      clientId = client.getClientId();
      String tokenId = settings.mode() == DcrSettings.Mode.INITIAL_ACCESS_TOKEN && request.token != null
          ? request.token.split("\\.", 2)[0] : null;
      client.setRegistration(new OidcClientSpec.Registration("DCR_" + settings.mode().name(), java.time.Instant.now().getEpochSecond(), tokenId));
      String persistent = Json.toJsonString(client);
      if (settings.mode() == DcrSettings.Mode.INITIAL_ACCESS_TOKEN) {
        claim = credentials.claim(request.token, request.id, client.getClientId(), client.getRegistrationId(),
            DcrCredentials.digest(persistent), request.policy.revision());
      }
      var command = new DcrRegisterClientMutation(persistent, request.policy.revision(), request.id, claim);
      commit(command);
      credentials.finish(claim, true);
      org.slf4j.LoggerFactory.getLogger(DcrRegistrationEndpoint.class).info("DCR registered requestId={} clientId={}", request.id, clientId);
      return new Result(response(candidate), request.id);
    } catch (RuntimeException error) {
      org.slf4j.LoggerFactory.getLogger(DcrRegistrationEndpoint.class).warn("DCR failed requestId={} clientId={} category={}", request.id, clientId, error.getClass().getSimpleName());
      if (claim != null) {
        try { credentials.finish(claim, false); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
      }
      if (error instanceof DcrFailure failure) { throw failure; }
      throw DcrFailure.unavailable();
    }
  }
  private void commit(DcrRegisterClientMutation command) {
    String key = ConfigurationRegisteredClientRepository.KEY;
    for (int attempt = 0; attempt < 3; attempt++) {
      var snapshot = configuration.getSnapshot(key).orElseThrow(DcrFailure::unavailable);
      try {
        management.mutateManagementConfigurations(Map.of(key, command), Map.of(key, snapshot.revision()));
        return;
      } catch (BusinessException error) {
        // This code is raised inside doUpdate; its transaction has definitely rolled back.
        if (!error.getMessage().startsWith("[CONFIGURATION_REVISION_CONFLICT]") || attempt == 2) { throw error; }
      }
    }
    throw DcrFailure.unavailable();
  }
  private Map<String,Object> response(DcrMetadata.Candidate candidate) {
    var client = candidate.client();
    var response = new LinkedHashMap<String,Object>();
    response.put("client_id", client.getClientId()); response.put("client_name", client.getClientName());
    response.put("client_id_issued_at", client.getRegistration().createdAt());
    response.put("redirect_uris", client.getRedirectUris()); response.put("post_logout_redirect_uris", client.getOidc().getPostLogoutRedirectUris());
    response.put("scope", String.join(" ", client.getOidc().getScopes())); response.put("grant_types", client.getGrantTypes());
    response.put("response_types", List.of("code"));
    response.put("application_type", client.getApplicationType());
    response.put("subject_type", "public");
    response.put("id_token_signed_response_alg", "RS256");
    response.put("token_endpoint_auth_method", candidate.secret() == null ? "none" : "client_secret_basic");
    if (candidate.secret() != null) { response.put("client_secret", candidate.secret()); response.put("client_secret_expires_at", 0); }
    return response;
  }
  public void success(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
    response.setStatus(201); response.setHeader("Cache-Control", "no-store"); response.setContentType("application/json");
    response.setHeader("X-Request-Id", ((Result) authentication).id);
    response.getWriter().write(Json.toJsonString(((Result) authentication).value));
  }
  public void failure(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) throws IOException {
    var failure = error instanceof DcrFailure dcr ? dcr : DcrFailure.unavailable();
    Object requestId = request.getAttribute("ouroboros.dcr.requestId");
    if (requestId instanceof String id) { response.setHeader("X-Request-Id", id); }
    response.setStatus(failure.status()); response.setHeader("Cache-Control", "no-store"); response.setContentType("application/json");
    if (failure.status() == 401) { response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\""); }
    if (failure.retryAfter() > 0) { response.setHeader("Retry-After", String.valueOf(failure.retryAfter())); }
    response.getWriter().write(Json.toJsonString(Map.of("error", failure.getError().getErrorCode())));
  }
  @Override public boolean supports(Class<?> authentication) { return Request.class.equals(authentication); }
  private static final class Request extends AbstractAuthenticationToken {
    private final Map<String,Object> metadata;
    private final String token;
    private final ConfigurationSnapshot policy;
    private final String id;
    Request(Map<String,Object> metadata, String token, ConfigurationSnapshot policy, String id) {
      super(List.of()); this.metadata = metadata; this.token = token; this.policy = policy; this.id = id;
    }
    public Object getPrincipal() { return id; }
    public Object getCredentials() { return null; }
    @Override public String toString() { return "DcrRequest[REDACTED]"; }
  }
  private static final class Result extends AbstractAuthenticationToken {
    private final Map<String,Object> value;
    private final String id;
    Result(Map<String,Object> value, String id) { super(List.of()); this.value = value; this.id = id; }
    public Object getPrincipal() { return id; }
    public Object getCredentials() { return null; }
    @Override public String toString() { return "DcrResult[REDACTED]"; }
  }
}
