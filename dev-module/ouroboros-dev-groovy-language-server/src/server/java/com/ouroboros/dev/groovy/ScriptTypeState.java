package com.ouroboros.dev.groovy;

import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;

/** Per-analysis return paths; null means that no return has been seen on this path. */
record ScriptTypeState(TypeShape returned, boolean fallsThrough) {
  static ScriptTypeState continuing() { return new ScriptTypeState(null, true); }
  static ScriptTypeState unknown(String reason) { return new ScriptTypeState(TypeShape.unknown(reason), false); }
  ScriptTypeState then(ScriptTypeState next) { return new ScriptTypeState(union(returned, next.returned), next.fallsThrough); }
  TypeShape result() {
    var result = union(returned, fallsThrough ? TypeShape.scalar("null") : null);
    return result == null ? TypeShape.unknown("no-successful-script-return") : result;
  }
  static TypeShape union(TypeShape left, TypeShape right) { return left == null ? right : right == null ? left : left.merge(right); }
  static ScriptTypeState join(Map<String, TypeShape> target, Map<String, TypeShape> left, ScriptTypeState pass,
      Map<String, TypeShape> right, ScriptTypeState fail) {
    if (pass.fallsThrough && fail.fallsThrough) {
      var keys = new java.util.HashSet<>(left.keySet()); keys.addAll(right.keySet());
      target.clear();
      for (var key : keys) target.put(key, left.getOrDefault(key, TypeShape.unknown("branch-local"))
          .merge(right.getOrDefault(key, TypeShape.unknown("branch-local"))));
    } else if (pass.fallsThrough) { target.clear(); target.putAll(left); }
    else if (fail.fallsThrough) { target.clear(); target.putAll(right); }
    return new ScriptTypeState(union(pass.returned, fail.returned), pass.fallsThrough || fail.fallsThrough);
  }
  static TypeShape method(TypeShape receiver, String name, int arity) {
    TypeShape result = null;
    for (var signature : receiver.schema().path("x-javaMethods")) {
      if (name.equals(signature.path("name").asText()) && arity == signature.path("parameterTypeDisplayNames").size()
          && signature.path("shape").path("schema").isObject()) {
        result = union(result, new TypeShape(signature.path("shape").path("schema")));
      }
    }
    return result == null ? TypeShape.unknown("unknown-method-signature:" + name) : result;
  }
}
