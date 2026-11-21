package com.ouroboros.rpc.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.ouroboros.rpc.RpcFieldSemantics;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

/** Resolves configuration-only leaves before enum/number conversion. Values are never evaluated as code. */
public final class RpcConfigurationTemplates {
  private static final Pattern REFERENCE = Pattern.compile("#\\{([^{}]*)}");

  private RpcConfigurationTemplates() {}

  public static java.util.Set<String> keys(String source) {
    var keys = new java.util.LinkedHashSet<String>();
    var matcher = REFERENCE.matcher(source);
    while (matcher.find()) keys.add(key(matcher));
    return java.util.Collections.unmodifiableSet(keys);
  }

  private static String key(java.util.regex.Matcher matcher) {
    if (matcher.group(1).isBlank()) throw new IllegalArgumentException("RPC配置键不能为空");
    return matcher.group(1);
  }

  public static String resolve(String source, Function<String, String> getter) {
    if (source == null) return null;
    var matcher = REFERENCE.matcher(source);
    var result = new StringBuilder();
    while (matcher.find()) {
      String value = getter.apply(key(matcher));
      if (value == null) throw new IllegalArgumentException("RPC配置项不存在: " + matcher.group(1));
      matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  public static Map<String, Object> resolveFields(Map<String, Object> source, String resource, String type,
      String pointer, Function<String, String> getter) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> result.put(key, resolveValue(value, resource, type,
        pointer + "/" + key.replace("~", "~0").replace("/", "~1"), getter)));
    return result;
  }

  private static Object resolveValue(Object value, String resource, String type, String pointer,
      Function<String, String> getter) {
    if (value instanceof String text) {
      var language = RpcFieldSemantics.language(resource, type, pointer);
      // Static tokens retain their dedicated blank/missing-key diagnostics and lazy lookup.
      return language == RpcFieldSemantics.Language.TEMPLATE || language == RpcFieldSemantics.Language.NONE
          || pointer.equals("/accessToken/staticAccessToken") ? text : resolve(text, getter);
    }
    if (value instanceof Map<?, ?> map) {
      var fields = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> fields.put(String.valueOf(key), item));
      return resolveFields(fields, resource, type, pointer, getter);
    }
    if (value instanceof Iterable<?> items) {
      var result = new ArrayList<Object>();
      for (var item : items) result.add(resolveValue(item, resource, type, pointer + "/" + result.size(), getter));
      return result;
    }
    return value;
  }

  public static RPCCredentialMetadata credential(RPCCredentialMetadata source, Function<String, String> getter) {
    var result = new RPCCredentialMetadata();
    result.setName(source.getName()); result.setTitle(source.getTitle()); result.setType(source.getType());
    result.setEnabled(source.isEnabled()); result.setDescription(source.getDescription());
    result.setInputSchema(source.getInputSchema()); result.setOutputSchema(source.getOutputSchema());
    result.setExtraProps(resolveFields(source.getExtraProps(), "rpc-auth", source.getType(), "", getter));
    return result;
  }
}
