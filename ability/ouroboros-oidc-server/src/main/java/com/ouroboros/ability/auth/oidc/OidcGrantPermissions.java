package com.ouroboros.ability.auth.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.ouroboros.exception.InternalException;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.util.Json;

/** Pure values in the original grant: freezes fields and binds permission execution semantics. */
final class OidcGrantPermissions {
  private OidcGrantPermissions() { }

  static ResolvedAccessControlParams freeze(Authority authority, AccessControlParams params) {
    var fields = params.getFieldConstraint();
    if (fields.mode() == FieldConstraint.Mode.ALL) {
      var definition = authority.getAccessControlDefine();
      var names = new TreeSet<String>();
      if (definition != null) {
        Stream.concat(definition.getCommonFields().stream(), definition.getControlledFields().stream())
            .map(field -> field.getName()).forEach(names::add);
      }
      if (names.isEmpty()) { throw new OAuth2AuthenticationException("invalid_scope"); }
      names.removeAll(fields.excluded());
      fields = FieldConstraint.only(names);
    }
    return new ResolvedAccessControlParams(fields, params.getRowConstraint());
  }

  static boolean current(OidcAuthorizationPolicy.Draft grant, String name) {
    return AuthorityCenter.getAuthority(name).filter(Authority::isDelegable)
        .filter(authority -> Objects.equals(grant.permissionDigests().get(name), digest(authority))).isPresent();
  }

  static String digest(Authority authority) {
    var content = new TreeMap<String, Object>();
    content.put("version", 1);
    content.put("authority", authority.getFullName());
    content.put("apis", authority.getApis().stream().sorted().toList());
    var definition = authority.getAccessControlDefine();
    if (definition != null) {
      content.put("canControlFields", definition.getCanControlFields());
      content.put("fieldSelectionRequired", definition.isFieldSelectionRequired());
      content.put("commonFields", definition.getCommonFields().stream().map(field -> field.getName() + ":" + field.getType()).sorted().toList());
      content.put("controlledFields", definition.getControlledFields().stream().map(field -> field.getName() + ":" + field.getType()).sorted().toList());
      content.put("filters", definition.getFilterDefine().stream().map(filter -> {
        var semantic = new TreeMap<String, Object>();
        semantic.put("name", filter.getName());
        semantic.put("dataType", filter.getDataType());
        semantic.put("originalFieldType", filter.getOriginalFieldType());
        semantic.put("originalFieldName", filter.getOriginalFieldName());
        semantic.put("extraProps", filter.getExtraProps() == null ? new TreeMap<>() : new TreeMap<>(filter.getExtraProps()));
        return Json.toJsonString(semantic);
      }).sorted().toList());
    }
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Json.toJsonString(content).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) { throw new InternalException("无法计算权限定义摘要", error); }
  }
}
