package com.ouroboros.security.rbac.profile;

import com.ouroboros.security.rbac.profile.service.ContactChangeService;

import java.util.Map;
import java.util.Set;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/user/me/contacts/{kind}/changes")
public final class ContactChangeController {
  private final ContactChangeService changes;
  private final ProfileSession session;

  public ContactChangeController(ContactChangeService changes, ProfileSession session) {
    this.changes = changes; this.session = session;
  }

  @PermitAll
  @PostMapping
  public WebApiResult<Map<String, Object>> change(@PathVariable("kind") String kind,
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    if (!body.keySet().equals(Set.of("schemaRevision", "expectedProfileRevision", "newValue", "password"))) { throw invalid(); }
    var input = Json.tryToBean(ContactChangeService.Change.class, Json.toJsonString(body)).getOrElseThrow(error -> invalid());
    return WebApiResult.ok(changes.change(session.require(), request.getRemoteAddr(), kind, input));
  }

  @PermitAll
  @GetMapping
  public WebApiResult<Map<String, Object>> status(@PathVariable("kind") String kind) {
    return WebApiResult.ok(changes.status(session.require(), kind));
  }

  @PermitAll
  @PostMapping("/{requestId}/confirm")
  public WebApiResult<Map<String, Object>> confirm(@PathVariable("kind") String kind,
      @PathVariable("requestId") String requestId, @RequestBody Map<String, Object> body) {
    String field = kind.equals("email") ? "ticket" : "code";
    if (!body.keySet().equals(Set.of(field)) || !(body.get(field) instanceof String value)) { throw invalid(); }
    return WebApiResult.ok(changes.confirm(session.require(), kind, requestId, value));
  }

  @PermitAll
  @DeleteMapping("/{requestId}")
  public WebApiResult<Boolean> cancel(@PathVariable("kind") String kind,
      @PathVariable("requestId") String requestId) {
    changes.cancel(session.require(), kind, requestId); return WebApiResult.ok(true);
  }

  private static BusinessException invalid() { return new BusinessException(400, "[PROFILE_INVALID] 不支持的联系方式请求字段"); }
}
