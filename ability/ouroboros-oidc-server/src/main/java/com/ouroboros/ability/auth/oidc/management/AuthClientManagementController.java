package com.ouroboros.ability.auth.oidc.management;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/auth-clients")
public class AuthClientManagementController {
  private final ConfigurationManagementService configuration;

  private final com.ouroboros.ability.auth.oidc.registration.DcrCredentials registrationCredentials;
  public AuthClientManagementController(ConfigurationManagementService configuration,
      com.ouroboros.ability.auth.oidc.registration.DcrCredentials registrationCredentials) {
    this.configuration = configuration; this.registrationCredentials = registrationCredentials;
  }

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @GetMapping
  public WebApiResult<Map<String, Object>> list(@RequestParam(required = false) String source) {
    var snapshot = read();
    var clients = Json.toList(Json.toJsonString(snapshot.value())).stream().map(value -> Json.toMap(Json.toJsonString(value)))
        .filter(client -> source == null || source.isBlank() || source.equals(client.get("registration") instanceof Map<?, ?> provenance
            ? provenance.get("source") : "MANUAL")).toList();
    return WebApiResult.ok(Map.of("clients", clients, "revision", snapshot.revision(),
        "canCreate", new ClientManagementAccess().security()));
  }

  @GetMapping("/settings")
  public WebApiResult<Map<String, Object>> settings() {
    new ClientManagementAccess().requireSettings();
    var snapshot = configuration.readManagementSnapshot(com.ouroboros.ability.auth.oidc.OidcRuntimeSettings.KEY)
        .orElseThrow(ClientManagementAccess::denied);
    return WebApiResult.ok(Map.of("settings", snapshot.value(), "expectedRevision", snapshot.revision()));
  }

  @PutMapping("/settings")
  public WebApiResult<Map<String, Object>> saveSettings(@RequestBody RegistrationRequest body) {
    new ClientManagementAccess().requireSettings();
    if (body.expectedRevision() == null || body.expectedRevision().isBlank()) { throw invalid(); }
    String key = com.ouroboros.ability.auth.oidc.OidcRuntimeSettings.KEY;
    var snapshot = configuration.mutateManagementConfigurations(Map.of(key,
        new com.ouroboros.configuration.management.ConfigurationMutation.Replace(body.settings())),
        Map.of(key, body.expectedRevision())).get(key);
    return WebApiResult.ok(Map.of("settings", snapshot.value(), "expectedRevision", snapshot.revision()));
  }

  @GetMapping("/registration")
  public WebApiResult<Map<String, Object>> registration() {
    new ClientManagementAccess().requireSecurity();
    var snapshot = configuration.readManagementSnapshot(com.ouroboros.ability.auth.oidc.registration.DcrSettings.KEY)
        .orElseThrow(ClientManagementAccess::denied);
    return WebApiResult.ok(Map.of("settings", snapshot.value(), "revision", snapshot.revision()));
  }

  @PutMapping("/registration")
  public WebApiResult<Map<String, Object>> saveRegistration(@RequestBody RegistrationRequest body) {
    new ClientManagementAccess().requireSecurity();
    String key = com.ouroboros.ability.auth.oidc.registration.DcrSettings.KEY;
    if (body.expectedRevision() == null || body.expectedRevision().isBlank()) { throw invalid(); }
    var snapshot = configuration.mutateManagementConfigurations(Map.of(key, new com.ouroboros.configuration.management.ConfigurationMutation.Replace(body.settings())),
        Map.of(key, body.expectedRevision())).get(key);
    return WebApiResult.ok(Map.of("settings", snapshot.value(), "revision", snapshot.revision()));
  }

  @PostMapping("/registration/credentials")
  public WebApiResult<com.ouroboros.ability.auth.oidc.registration.DcrCredentials.Issued> issueRegistrationCredential() {
    new ClientManagementAccess().requireSecurity();
    var snapshot = configuration.readManagementSnapshot(com.ouroboros.ability.auth.oidc.registration.DcrSettings.KEY)
        .orElseThrow(ClientManagementAccess::denied);
    var policy = com.ouroboros.ability.auth.oidc.registration.DcrSettings.parse(snapshot.value());
    if (snapshot.storedRawValue() == null || !policy.enabled()
        || policy.mode() != com.ouroboros.ability.auth.oidc.registration.DcrSettings.Mode.INITIAL_ACCESS_TOKEN) {
      throw new com.ouroboros.exception.BusinessException(409, "[DCR_NOT_ENABLED] 请先保存并启用凭证注册");
    }
    return WebApiResult.ok(registrationCredentials.issue(com.ouroboros.security.context.CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity(),
        policy.initialAccessTokenTimeToLive()));
  }

  @DeleteMapping("/registration/credentials/{id}")
  public WebApiResult<Map<String, Boolean>> revokeRegistrationCredential(@PathVariable String id) {
    new ClientManagementAccess().requireSecurity();
    if (!id.matches("[a-f0-9-]{36}")) { throw invalid(); }
    return WebApiResult.ok(Map.of("revoked", registrationCredentials.revoke(id)));
  }

  @GetMapping("/permissions")
  public WebApiResult<Map<String, Object>> permissions(@RequestParam(required = false) String clientId) {
    var access = new ClientManagementAccess();
    if (!access.security() && (clientId == null || !access.visible(clientId))) { throw ClientManagementAccess.denied(); }
    var options = com.ouroboros.security.authority.AuthorityCenter.getAllAuthority().values().stream()
        .filter(authority -> authority.isDelegable())
        .filter(authority -> access.security() || access.mayChange(clientId, "apiAuthorization.allowedBoundary.permissions", List.of(authority.getFullName())))
        .map(authority -> Map.of("value", authority.getFullName(), "label", authority.getTitle() == null ? authority.getFullName() : authority.getTitle()))
        .toList();
    return WebApiResult.ok(Map.of("options", options));
  }

  @GetMapping("/{clientId}")
  public WebApiResult<Map<String, Object>> detail(@PathVariable String clientId) { return WebApiResult.ok(detail(read(), clientId)); }

  @PostMapping
  public WebApiResult<Map<String, Object>> create(@RequestBody CreateRequest body) {
    if (body.client() == null || !(body.client().get("clientId") instanceof String id)) { throw invalid(); }
    return save(id, body.expectedRevision(), ClientConfigurationMutation.Action.CREATE, body.client(), body.newClientSecret());
  }

  @PatchMapping("/{clientId}")
  public WebApiResult<Map<String, Object>> update(@PathVariable String clientId, @RequestBody UpdateRequest body) {
    if (body.changes() == null) { throw invalid(); }
    return save(clientId, body.expectedRevision(), ClientConfigurationMutation.Action.UPDATE, body.changes(), body.newClientSecret());
  }

  @DeleteMapping("/{clientId}")
  public WebApiResult<Map<String, Object>> delete(@PathVariable String clientId, @RequestBody RevisionRequest body) {
    var saved = commit(clientId, body.expectedRevision(), ClientConfigurationMutation.Action.DELETE, Map.of(), null);
    return WebApiResult.ok(Map.of("clientId", clientId, "revision", saved.revision()));
  }

  @PostMapping("/{clientId}/restore-defaults")
  public WebApiResult<Map<String, Object>> restore(@PathVariable String clientId, @RequestBody RevisionRequest body) {
    return save(clientId, body.expectedRevision(), ClientConfigurationMutation.Action.RESTORE_DEFAULTS, Map.of(), null);
  }

  @PostMapping("/{clientId}/secret/generate")
  public WebApiResult<Map<String, String>> generateSecret(@PathVariable String clientId) {
    new ClientManagementAccess().requireSecurity();
    return WebApiResult.ok(Map.of("clientSecret", ClientSecrets.generate()));
  }

  private WebApiResult<Map<String, Object>> save(String id, String revision, ClientConfigurationMutation.Action action,
      Map<String, Object> changes, String secret) {
    return WebApiResult.ok(detail(commit(id, revision, action, changes, secret), id));
  }

  private ConfigurationSnapshot commit(String id, String revision, ClientConfigurationMutation.Action action,
      Map<String, Object> changes, String secret) {
    if (revision == null || revision.isBlank()) { throw new ValidationException("[CONFIGURATION_REVISION_REQUIRED] 请提供配置版本"); }
    String key = ConfigurationRegisteredClientRepository.KEY;
    return configuration.mutateManagementConfigurations(Map.of(key, new ClientConfigurationMutation(action, id, changes, secret)),
        Map.of(key, revision)).get(key);
  }

  private ConfigurationSnapshot read() {
    return configuration.readManagementSnapshot(ConfigurationRegisteredClientRepository.KEY)
        .orElseThrow(ClientManagementAccess::denied);
  }

  private Map<String, Object> detail(ConfigurationSnapshot snapshot, String clientId) {
    List<Object> clients = Json.toList(Json.toJsonString(snapshot.value()));
    Map<String, Object> client = clients.stream().map(value -> Json.toMap(Json.toJsonString(value)))
        .filter(value -> clientId.equals(value.get("clientId"))).findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    var access = new ClientManagementAccess();
    var fields = ClientManagementAccess.MANAGEABLE_FIELDS.stream().filter(field -> access.mayChange(clientId, field, List.of())).toList();
    var result = new LinkedHashMap<String, Object>();
    result.put("canManageSecurity", access.security());
    result.put("client", client); result.put("revision", snapshot.revision()); result.put("writableFields", fields);
    result.put("allowedActions", access.security() ? List.of("update", "delete", "generateSecret", "restoreDefaults")
        : fields.isEmpty() ? List.of() : List.of("update", "restoreDefaults"));
    return result;
  }

  private static ValidationException invalid() { return new ValidationException("[AUTH_CLIENT_INVALID] 请求格式无效"); }

  public record CreateRequest(String expectedRevision, Map<String, Object> client, String newClientSecret) {
    @Override public String toString() { return "CreateRequest[redacted]"; }
  }
  public record UpdateRequest(String expectedRevision, Map<String, Object> changes, String newClientSecret) {
    @Override public String toString() { return "UpdateRequest[redacted]"; }
  }
  public record RegistrationRequest(String expectedRevision, Object settings) { }
  public record RevisionRequest(String expectedRevision) { }
}
