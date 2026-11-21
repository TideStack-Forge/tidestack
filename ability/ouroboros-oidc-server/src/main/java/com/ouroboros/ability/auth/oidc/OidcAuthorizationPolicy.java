package com.ouroboros.ability.auth.oidc;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.lock.DistributedLockManager;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.Json;

/** Adds application decisions through SAS converters/providers; SAS still validates and issues every code. */
public final class OidcAuthorizationPolicy implements AuthenticationProvider, AuthenticationConverter {
  public static final String DRAFT = "ouroboros_grant_draft";
  public static final String BINDING = "ouroboros.browserBinding";
  public static final String GRANT = "ouroboros.grant";
  public static final String STAGE = "ouroboros.stage";

  static final class LocalFailure extends OAuth2AuthenticationException {
    final String result;
    final String contextId;
    LocalFailure(String result, String contextId) {
      super("invalid_request"); this.result = result; this.contextId = contextId;
    }
  }
  public record Draft(String browser, String user, String session, String registration, String policyDigest,
      Set<String> requested, Set<String> approved, Map<String, ResolvedAccessControlParams> permissions, Map<String, String> permissionDigests,
      boolean refreshAllowed, boolean confirmation, String viewRevision, Instant expiresAt) implements java.io.Serializable {
    public Draft {
      requested = Set.copyOf(requested); approved = Set.copyOf(approved); permissions = Map.copyOf(permissions); permissionDigests = Map.copyOf(permissionDigests);
    }
  }
  private final ConfigurationRegisteredClientRepository clients;
  private final OAuth2AuthorizationService authorizations;
  private final UserCenter users;
  private final ConfigurationService configuration;
  private OAuth2AuthorizationCodeRequestAuthenticationProvider requestProvider;
  private OAuth2AuthorizationConsentAuthenticationProvider consentProvider;
  private final AuthenticationConverter getConverter = new org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter();

  public OidcAuthorizationPolicy(ConfigurationRegisteredClientRepository clients, OAuth2AuthorizationService authorizations,
      UserCenter users, ConfigurationService configuration) {
    this.clients = clients; this.authorizations = authorizations; this.users = users; this.configuration = configuration;
  }

  public void configureProviders(List<AuthenticationProvider> providers) {
    requestProvider = providers.stream().filter(OAuth2AuthorizationCodeRequestAuthenticationProvider.class::isInstance)
        .map(OAuth2AuthorizationCodeRequestAuthenticationProvider.class::cast).findFirst().orElseThrow();
    consentProvider = providers.stream().filter(OAuth2AuthorizationConsentAuthenticationProvider.class::isInstance)
        .map(OAuth2AuthorizationConsentAuthenticationProvider.class::cast).findFirst().orElseThrow();
    requestProvider.setAuthenticationValidator(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
        .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR).andThen(context -> {
          var token = context.<OAuth2AuthorizationCodeRequestAuthenticationToken>getAuthentication();
          validateCapabilities(token, clients.findSpec(context.getRegisteredClient().getId()));
        }));
    requestProvider.setAuthorizationConsentRequired(context -> {
      Draft draft = (Draft) context.<OAuth2AuthorizationCodeRequestAuthenticationToken>getAuthentication().getAdditionalParameters().get(DRAFT);
      if (draft == null) { throw new OAuth2AuthenticationException("invalid_request"); }
      if (!draft.confirmation() && draft.approved().isEmpty()) {
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error("access_denied"), context.getAuthentication());
      }
      return draft.confirmation();
    });
    providers.removeIf(provider -> provider == requestProvider || provider == consentProvider);
    providers.add(0, this);
  }

  public Authentication convert(HttpServletRequest request) {
    if (request.getMethod().equals("GET")) {
      if (request.getParameterMap().keySet().stream().anyMatch(key -> key.startsWith("ouroboros") || key.equals("context_id"))) {
        throw new OAuth2AuthenticationException("invalid_request");
      }
      var parsed = (OAuth2AuthorizationCodeRequestAuthenticationToken) getConverter.convert(request);
      if (parsed == null) { return null; }
      var extra = new LinkedHashMap<>(parsed.getAdditionalParameters());
      extra.put(BINDING, browser(request));
      return new OAuth2AuthorizationCodeRequestAuthenticationToken(parsed.getAuthorizationUri(), parsed.getClientId(),
          (Authentication) parsed.getPrincipal(), parsed.getRedirectUri(), parsed.getState(), parsed.getScopes(), extra);
    }
    if (!request.getMethod().equals("POST")) { throw new OAuth2AuthenticationException("invalid_request"); }
    var allowed = Set.of("context_id", "view_revision", "decision", "identity_approved", "business_approved", "offline_approved", "_csrf");
    if (!allowed.containsAll(request.getParameterMap().keySet())
        || request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)
        || request.getParameter("decision") == null
        || !Set.of("continue", "cancel").contains(request.getParameter("decision"))) {
      throw new OAuth2AuthenticationException("invalid_request");
    }
    var decisions = new LinkedHashMap<String, String>();
    for (String name : allowed) {
      String value = request.getParameter(name);
      if (value != null) { decisions.put(name, value); }
    }
    if (!decisions.containsKey("context_id") || !decisions.containsKey("view_revision")) {
      throw new OAuth2AuthenticationException("invalid_request");
    }
    return new Decision(SecurityContextHolder.getContext().getAuthentication(), browser(request), decisions);
  }

  @Override
  public Authentication authenticate(Authentication token) {
    if (token instanceof Decision decision) {
      try { return decide(decision); }
      catch (ResponseStatusException error) {
        throw new LocalFailure(switch (error.getStatusCode().value()) {
          case 409 -> "processed"; case 410, 401 -> "expired"; case 503 -> "unavailable"; default -> "invalid";
        }, null);
      }
    }
    var request = (OAuth2AuthorizationCodeRequestAuthenticationToken) token;
    Authentication principal = (Authentication) request.getPrincipal();
    if (!authenticated(principal)) { return requestProvider.authenticate(request); }
    if (!(principal.getDetails() instanceof String session)) { throw new OAuth2AuthenticationException("invalid_request"); }
    var registered = clients.findByClientId(request.getClientId());
    if (registered == null) { return requestProvider.authenticate(request); }
    var validation = OAuth2AuthorizationCodeRequestAuthenticationContext.with(request).registeredClient(registered).build();
    OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(validation);
    OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(validation);
    var spec = clients.findSpec(registered.getId());
    validateCapabilities(request, spec);
    User user = users.getUser(principal.getName()).filter(User::isEnabled)
        .orElseThrow(() -> new OAuth2AuthenticationException("invalid_grant"));
    String prompt = String.valueOf(request.getAdditionalParameters().getOrDefault("prompt", ""));
    boolean confirmation = spec.getConsentPolicy().getMode() == OidcClientSpec.ConsentMode.EXPLICIT
        || Arrays.asList(prompt.split(" ")).contains("consent") || request.getScopes().contains("offline_access");
    Draft draft = plan(spec, user, session, (String) request.getAdditionalParameters().get(BINDING), request.getScopes(),
        confirmation, true, true, false);
    var extra = new LinkedHashMap<>(request.getAdditionalParameters());
    extra.remove(BINDING); extra.put(DRAFT, draft);
    return requestProvider.authenticate(new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(),
        request.getClientId(), principal, request.getRedirectUri(), request.getState(),
        confirmation ? request.getScopes() : draft.approved(), extra));
  }

  private Authentication decide(Decision decision) {
    var lock = DistributedLockManager.getLockManager().getLock("oidc:consent:" + digest(decision.form.get("context_id")));
    lock.lock();
    try {
      var authorization = pending(decision.form.get("context_id"), decision.principal, decision.browser);
      Draft old = authorization.getAttribute(GRANT);
      if (!"PENDING".equals(authorization.getAttribute(STAGE))) {
        if ("PROCESSING".equals(authorization.getAttribute(STAGE))) { authorizations.remove(authorization); }
        throw new LocalFailure("processed", null);
      }
      var spec = clients.findSpec(authorization.getRegisteredClientId());
      var user = users.getUser(authorization.getPrincipalName()).filter(User::isEnabled).orElseThrow(() -> local(HttpStatus.UNAUTHORIZED, "会话已失效"));
      Draft fresh = plan(spec, user, old.session(), old.browser(), old.requested(), true, true, true, false);
      if (!fresh.viewRevision().equals(decision.form.get("view_revision"))) { throw new LocalFailure("changed", authorization.getId()); }
      boolean identity = old.requested().contains("openid");
      boolean business = old.requested().stream().anyMatch(s -> s.startsWith("authority:"));
      requireChoice(decision.form, "identity_approved", identity);
      requireChoice(decision.form, "business_approved", business);
      if (decision.form.containsKey("offline_approved")) {
        requireChoice(decision.form, "offline_approved", old.requested().contains("offline_access"));
      }
      boolean cancelled = decision.form.get("decision").equals("cancel")
          || (identity && !Boolean.parseBoolean(decision.form.get("identity_approved")));
      Draft approved = plan(spec, user, old.session(), old.browser(), old.requested(), false, !cancelled,
          !cancelled && Boolean.parseBoolean(decision.form.get("business_approved")),
          !cancelled && Boolean.parseBoolean(decision.form.get("offline_approved")));
      approved = new Draft(approved.browser(), approved.user(), approved.session(), approved.registration(),
          approved.policyDigest(), approved.requested(), approved.approved(), approved.permissions(), approved.permissionDigests(), approved.refreshAllowed(),
          false, approved.viewRevision(), old.expiresAt());
      if (cancelled) { approved = new Draft(approved.browser(), approved.user(), approved.session(), approved.registration(),
          approved.policyDigest(), approved.requested(), Set.of(), Map.of(), Map.of(), false, false, approved.viewRevision(), old.expiresAt()); }
      authorizations.save(OAuth2Authorization.from(authorization).attribute(GRANT, approved).attribute(STAGE, "PROCESSING").build());
      var original = authorization.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName());
      try {
        return consentProvider.authenticate(new OAuth2AuthorizationConsentAuthenticationToken(original.getAuthorizationUri(),
            spec.getClientId(), decision.principal, authorization.getAttribute("state"), approved.approved(), Map.of()));
      } catch (RuntimeException error) {
        authorizations.remove(authorization);
        throw error;
      }
    } finally { lock.unlock(); }
  }

  private void requireChoice(Map<String, String> form, String key, boolean present) {
    String value = form.get(key);
    if ((!present && value != null) || (present && !Set.of("true", "false").contains(value == null ? "" : value))) {
      throw new OAuth2AuthenticationException("invalid_request");
    }
  }

  public Map<String, Object> view(HttpServletRequest request, String state, String contextId) {
    if ((state == null) == (contextId == null)) { throw local(HttpStatus.BAD_REQUEST, "必须提供一个授权上下文"); }
    var authorization = contextId == null ? authorizations.findByToken(state, new OAuth2TokenType("state")) : authorizations.findById(contextId);
    if (authorization == null) { throw local(HttpStatus.GONE, "授权请求已过期"); }
    pending(authorization.getId(), SecurityContextHolder.getContext().getAuthentication(), browser(request));
    if (!"PENDING".equals(authorization.getAttribute(STAGE))) { throw local(HttpStatus.CONFLICT, "授权请求已处理"); }
    Draft old = authorization.getAttribute(GRANT);
    var spec = clients.findSpec(authorization.getRegisteredClientId());
    User user = users.getUser(authorization.getPrincipalName()).orElseThrow();
    Draft fresh = plan(spec, user, old.session(), old.browser(), old.requested(), true, true, true, false);
    var original = authorization.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName());
    var result = new LinkedHashMap<String, Object>();
    result.put("contextId", authorization.getId()); result.put("viewRevision", fresh.viewRevision()); result.put("expiresAt", old.expiresAt().toString());
    result.put("client", Map.of("name", spec.getClientName() == null ? spec.getClientId() : spec.getClientName(),
        "verifiedRedirectOrigin", URI.create(original.getRedirectUri()).getScheme() + "://" + URI.create(original.getRedirectUri()).getAuthority()));
    result.put("account", Map.of("displayName", user.getDisplayName(), "accountLabel", user.getUserIdentity()));
    if (old.requested().contains("openid")) { result.put("identity", Map.of("scopes", old.requested().stream()
        .filter(s -> ConfigurationRegisteredClientRepository.IDENTITY_SCOPES.contains(s) && !s.equals("offline_access")).sorted().toList(),
        "claims", OidcIdentityClaims.project(user, old.requested()))); }
    if (old.requested().stream().anyMatch(s -> s.startsWith("authority:"))) {
      result.put("business", Map.of("permissions", fresh.permissions().keySet().stream().sorted().map(name -> Map.of("scope",
          ConfigurationRegisteredClientRepository.businessScope(name), "name", AuthorityCenter.getAuthority(name).orElseThrow().getTitle())).toList()));
    }
    if (old.requested().contains("offline_access")) { result.put("offlineAccess", Map.of("requested", true,
        "explanation", "允许离开当前页面后继续访问；平台退出、会话到期或撤销后停止。")); }
    CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    result.put("csrf", Map.of("parameterName", csrf.getParameterName(), "headerName", csrf.getHeaderName(), "token", csrf.getToken()));
    return result;
  }

  private OAuth2Authorization pending(String id, Authentication principal, String browser) {
    if (!OidcRuntimeSettings.current(configuration).enabled()) { throw local(HttpStatus.SERVICE_UNAVAILABLE, "授权服务已暂停"); }
    var authorization = authorizations.findById(id);
    if (authorization == null) { throw local(HttpStatus.GONE, "授权请求已失效"); }
    Draft draft = authorization.getAttribute(GRANT);
    if (!authenticated(principal) || draft == null || !draft.browser().equals(browser)
        || !draft.user().equals(principal.getName()) || !draft.session().equals(principal.getDetails())) {
      throw local(HttpStatus.FORBIDDEN, "授权请求不属于当前会话");
    }
    if (!draft.expiresAt().isAfter(Instant.now())) { authorizations.remove(authorization); throw local(HttpStatus.GONE, "授权请求已过期"); }
    var spec = clients.findSpec(authorization.getRegisteredClientId());
    var original = authorization.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName());
    var registered = clients.findById(authorization.getRegisteredClientId());
    if (spec == null || registered == null || !OidcRedirectUris.matches(registered.getRedirectUris(), original.getRedirectUri())
        || !registered.getScopes().containsAll(draft.requested())) {
      authorizations.remove(authorization); throw local(HttpStatus.GONE, "客户端授权配置已失效");
    }
    return authorization;
  }

  private Draft plan(OidcClientSpec spec, User user, String session, String browser, Set<String> requested,
      boolean confirmation, boolean identity, boolean business, boolean offline) {
    var approved = new HashSet<String>();
    var grants = new LinkedHashMap<String, ResolvedAccessControlParams>();
    var definitions = new LinkedHashMap<String, String>();
    if (identity && requested.contains("openid")) {
      requested.stream().filter(s -> Set.of("openid", "profile", "email", "phone").contains(s)).forEach(approved::add);
    }
    if (business && spec.getApiAuthorization().isEnabled()) {
      for (String authority : spec.getApiAuthorization().getAllowedBoundary().getPermissions()) {
        String scope = ConfigurationRegisteredClientRepository.businessScope(authority);
        if (requested.contains(scope) && AuthorizationEvaluator.standard().evaluate(new DefaultSecurityIdentity(user),
            AuthorizationEvaluator.PLATFORM_API, authority) instanceof AuthorizationDecision.Permit permit) {
          var definition = com.ouroboros.security.authority.AuthorityCenter.getAuthority(authority).orElseThrow();
          approved.add(scope); grants.put(authority, OidcGrantPermissions.freeze(definition, permit.params()));
          definitions.put(authority, OidcGrantPermissions.digest(definition));
        }
      }
    }
    if (offline && identity && requested.contains("offline_access")) { approved.add("offline_access"); }
    boolean refresh = spec.getClientType() == OidcClientSpec.ClientType.CONFIDENTIAL
        && (requested.contains("openid") ? approved.contains("offline_access") : !grants.isEmpty());
    String policy = digest(Json.toJsonString(spec));
    String view = digest(policy + Json.toJsonString(OidcIdentityClaims.project(user,
        requested.contains("openid") ? requested : Set.of())) + Json.toJsonString(grants) + Json.toJsonString(definitions));
    return new Draft(browser, user.getUserIdentity(), session, spec.getRegistrationId(), policy, requested, approved,
        grants, definitions, refresh, confirmation, view, Instant.now().plusSeconds(300));
  }

  private void validateCapabilities(OAuth2AuthorizationCodeRequestAuthenticationToken token, OidcClientSpec spec) {
    boolean identity = token.getScopes().contains("openid");
    var prompts = Arrays.asList(String.valueOf(token.getAdditionalParameters().getOrDefault("prompt", "")).split(" "));
    if (spec == null || token.getScopes().isEmpty()
        || (prompts.contains("none") && prompts.size() > 1)
        || (!identity && token.getScopes().stream().anyMatch(ConfigurationRegisteredClientRepository.IDENTITY_SCOPES::contains))
        || (identity && !spec.getOidc().isEnabled())
        || token.getAdditionalParameters().containsKey("max_age")
        || token.getAdditionalParameters().containsKey("claims")
        || token.getAdditionalParameters().containsKey("request")
        || token.getAdditionalParameters().containsKey("request_uri")
        || token.getAdditionalParameters().containsKey("response_mode")
        || (identity && Arrays.stream(String.valueOf(token.getAdditionalParameters().getOrDefault("prompt", "")).split(" "))
            .anyMatch(value -> !Set.of("", "none", "consent").contains(value)))) {
      throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error("invalid_request"), token);
    }
  }

  private static String browser(HttpServletRequest request) {
    var session = request.getSession();
    synchronized (session) {
      Object value = session.getAttribute(BINDING);
      if (value instanceof String text) { return text; }
      String created = UUID.randomUUID().toString(); session.setAttribute(BINDING, created); return created;
    }
  }
  public static String digest(String value) {
    return com.ouroboros.ability.auth.oidc.service.CacheOAuth2AuthorizationService.tokenKey(value);
  }
  private static boolean authenticated(Authentication principal) { return principal != null && principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken); }
  private static ResponseStatusException local(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
  public boolean supports(Class<?> type) { return Decision.class.isAssignableFrom(type) || OAuth2AuthorizationCodeRequestAuthenticationToken.class.isAssignableFrom(type); }

  private static final class Decision extends AbstractAuthenticationToken {
    private final Authentication principal;
    private final String browser;
    private final Map<String, String> form;
    private Decision(Authentication principal, String browser, Map<String, String> form) { super(List.of()); this.principal = principal; this.browser = browser; this.form = Map.copyOf(form); }
    public Object getPrincipal() { return principal; }
    public Object getCredentials() { return null; }
  }
}
