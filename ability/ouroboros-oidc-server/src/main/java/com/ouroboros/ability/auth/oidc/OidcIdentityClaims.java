package com.ouroboros.ability.auth.oidc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.User;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

/** The same allowlisted, scope-bound projection serves consent, ID Token and UserInfo. */
public final class OidcIdentityClaims {
  private OidcIdentityClaims() { }

  public static String subject(User user) {
    String subject = user.getClaims().getString(StandardClaims.PUBLIC_SUBJECT_ID).orElse(null);
    if (subject == null || !subject.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
      throw new OAuth2AuthenticationException("invalid_grant");
    }
    return subject;
  }

  public static Map<String, Object> project(User user, Set<String> scopes) {
    var claims = new LinkedHashMap<String, Object>();
    claims.put("sub", subject(user));
    var source = user.getClaims().asMap();
    if (scopes.contains("profile")) {
      claims.put("preferred_username", user.getUserIdentity());
      for (String key : Set.of("name", "nickname", "picture", "gender", "birthdate")) {
        Object value = source.get(key);
        if (value instanceof String text && !text.isBlank()) { claims.put(key, text); }
      }
    }
    if (scopes.contains("email") && user.getEmail() != null
        && user.getEmail().matches("[^\\s@,;<>]+@[^\\s@,;<>]+")) {
      claims.put("email", user.getEmail());
      claims.put("email_verified", Boolean.TRUE.equals(source.get("email_verified")));
    }
    if (scopes.contains("phone") && user.getPhoneNumber() != null
        && user.getPhoneNumber().matches("\\+[1-9][0-9]{1,14}")) {
      claims.put("phone_number", user.getPhoneNumber());
      claims.put("phone_number_verified", Boolean.TRUE.equals(source.get("phone_number_verified")));
    }
    return claims;
  }
}
