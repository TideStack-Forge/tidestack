import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.template.NotificationTemplateDefinitionValidator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Composes installed production suppliers without starting vendor clients or sending messages. */
public class ChannelManagementScenario {
  public static void main(String[] args) throws Exception {
    var suppliers = NotificationAdapterCenter.getAllSuppliers();
    assertEquals(Set.of("email", "ding-talk", "wecom", "feishu", "internal-message", "j-push", "wechat", "aliyun-sms", "aliyun-call"), suppliers.keySet());
    var validator = new NotificationTemplateDefinitionValidator(mock(ConfigurationService.class));
    int checks = 0;
    for (var entry : suppliers.entrySet()) {
      // Managed enterprise descriptors expose pure types; legacy suppliers still use their original binding.
      if (entry.getValue().managedSupplierType().isEmpty()) {
        var constructor = entry.getValue().getClass().getEnclosingClass().getConstructors()[0];
        var adapter = constructor.getParameterTypes()[0].getConstructor().newInstance();
        constructor.newInstance(adapter);
      }
      var content = Set.of("wechat", "aliyun-sms", "aliyun-call").contains(entry.getKey())
          ? Map.<String, Object>of("id", "deployed-vendor-template")
          : Map.<String, Object>of("titleTemplate", "标题 ${name}", "contentTemplate", "正文 ${name}");
      var valid = template(entry.getKey(), content, Map.of());
      var problems = validator.validate(valid, valid);
      assertTrue(problems.isEmpty(), entry.getKey() + ": " + problems); checks++;
      var empty = template(entry.getKey(), Map.of(), Map.of());
      assertFalse(validator.validate(empty, valid).isEmpty(), "missing required content: " + entry.getKey()); checks++;
      var unknown = new LinkedHashMap<>(content); unknown.put("unsupported", "value");
      assertTrue(validator.validate(template(entry.getKey(), unknown, Map.of()), valid).stream()
          .anyMatch(problem -> problem.code().equals("CHANNEL_BINDING_INVALID")), entry.getKey()); checks++;
      var blank = new LinkedHashMap<String, Object>(); content.keySet().forEach(key -> blank.put(key, "  "));
      assertFalse(validator.validate(template(entry.getKey(), blank, Map.of()), valid).isEmpty(), entry.getKey()); checks++;
      if (entry.getKey().equals("aliyun-call")) {
        assertTrue(validator.validate(template(entry.getKey(), content, Map.of("playTimes", 3, "volume", 100, "speed", -500)), valid).isEmpty()); checks++;
        for (var options : java.util.List.of(Map.of("playTimes", 4), Map.of("volume", -1), Map.of("speed", 501))) {
          assertFalse(validator.validate(template(entry.getKey(), content, options), valid).isEmpty(), options.toString()); checks++;
        }
      }
      System.out.println("PASS installed supplier: " + entry.getKey());
    }
    System.out.println("PASS channel management behavior checks: " + checks);
    verifyRegisteredSecretPolicies();
  }

  private static void verifyRegisteredSecretPolicies() throws Exception {
    var expected = Set.of("notification.email.authorizationCode", "notification.dingTalk.appSecret",
        "notification.wechat.secret", "notification.jpush.masterSecret", "notification.aliyun.sms.accessKeySecret",
        "notification.aliyun.call.accessKeySecret");
    var protectedKeys = new java.util.HashSet<String>();
    var resources = ChannelManagementScenario.class.getClassLoader().getResources(
        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    while (resources.hasMoreElements()) {
      try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(resources.nextElement().openStream(), java.nio.charset.StandardCharsets.UTF_8))) {
        for (String name : lines.lines().filter(line -> line.startsWith("com.ouroboros.notification.")).toList()) {
          var type = Class.forName(name);
          for (var method : type.getDeclaredMethods()) {
            if (method.getAnnotation(org.springframework.context.annotation.Bean.class) == null
                || !com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy.class.isAssignableFrom(method.getReturnType())) continue;
            if (method.getParameterCount() != 0) continue;
            method.setAccessible(true);
            var policy = (com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy)
                method.invoke(type.getConstructor().newInstance());
            var keys = expected.stream().filter(policy::supports).toList();
            assertEquals(1, keys.size(), name);
            var metadata = new com.ouroboros.configuration.metadata.ConfigurationMetadata(); metadata.setKey(keys.getFirst());
            var snapshot = new com.ouroboros.configuration.ConfigurationSnapshot(keys.getFirst(), "test-credential", "test-credential", "1", metadata);
            assertTrue(policy.projectRead(new com.ouroboros.configuration.management.ConfigurationManagementContext(null,
                com.ouroboros.configuration.management.ConfigurationManagementContext.Purpose.MANAGEMENT), snapshot).isEmpty());
            protectedKeys.addAll(keys);
          }
        }
      }
    }
    assertEquals(expected, protectedKeys);
    System.out.println("PASS registered secret policies reject anonymous reads: " + protectedKeys.size());
  }

  private static Map<String, Object> template(String channel, Map<String, Object> schema, Map<String, ?> options) {
    return Map.of("name", "channel-scenario", "title", "标题", "isEnabled", true, "payloadSchema", Map.of(),
        "channel", Map.of(channel, Map.of("schema", schema, "options", options)));
  }
}
