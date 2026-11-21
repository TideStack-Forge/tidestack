import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.mothership.OuroborosApplication;
import com.ouroboros.notification.*;
import com.ouroboros.notification.constant.NotificationStateConstant;
import com.ouroboros.notification.service.NotificationService;
import com.ouroboros.util.Json;
import org.springframework.boot.SpringApplication;

/** Test-side driver/QueryDSL observer. It contributes no beans, production endpoints or substitutes. */
public class RuntimeMechanisms {
  static final List<String> PROVIDERS = List.of("dingtalk", "wecom", "feishu");
  static final Path ROOT = Path.of(System.getProperty("eis.root"));
  static JsonNode scenario;
  static JsonNode configuration;
  static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public static void main(String[] args) throws Exception {
    scenario = Json.mapperCopy().readTree(Files.readString(ROOT.resolve("secrets/scenario-state.json")));
    configuration = Json.mapperCopy().readTree(Files.readString(ROOT.resolve("secrets/providers.json")));
    var application = new SpringApplication(OuroborosApplication.class);
    String phase = System.getProperty("eis.phase");
    try (var context = application.run(args)) {
      if (phase.equals("callbacks")) callbacks();
      else if (phase.startsWith("key-")) keys(context.getBean(com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials.class), phase);
      else notifications(context.getBean(EmployeeNotificationDispatcher.class), context.getBean(NotificationService.class), phase.endsWith("recovery"));
    }
    System.out.println("SIM-PASS " + phase);
    var evidence = ROOT.getParent().getParent().resolve("testing-evidence/enterprise-simulation");
    Files.createDirectories(evidence);
    String caseId = phase.startsWith("key-") ? "EIS-KEY-001" : phase.equals("callbacks") ? "EIS-CALLBACK-001" : "EIS-NOTIFICATION-004";
    Files.writeString(evidence.resolve("persistent-" + phase + ".json"),
        Json.toJsonString(Map.of("caseId", caseId, "phase", phase, "result", "pass")) + "\n");
  }

  static void callbacks() {
    var receipts = DataModelCenter.getDataModel("ExternalCallbackReceipt").orElseThrow();
    var inbox = DataModelCenter.getDataModel("ExternalEventInbox").orElseThrow();
    for (String provider : PROVIDERS) {
      var where = Map.<String, Object>of("connectionId", Map.of("=", provider + "-sim"));
      var rows = receipts.query(Map.of("WHERE", where)).get();
      require(rows.size() == 1, "Callback replay created another receipt: " + provider);
      require("COMPLETED".equals(rows.getFirst().get("status")), "Callback was not durably completed: " + provider);
      require(inbox.count(where).get() == 1L, "Callback replay created another inbox fact: " + provider);
      System.out.println("SIM-CALLBACK " + provider + " receipt=1 inbox=1");
    }
  }

  static void keys(com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials credentials, String phase) throws Exception {
    var records = com.ouroboros.enterprise.runtime.credential.EnterpriseManagedCredentials.records(credentials.snapshot());
    require(records.keySet().equals(Set.of("dingtalk-sim", "wecom-sim", "feishu-sim")), "Missing managed enterprise credentials");
    var versions = new TreeMap<String, String>();
    records.forEach((id, record) -> versions.put(id, record.credentialVersion()));
    var baseline = ROOT.resolve("secrets/credential-versions.json");
    if (phase.equals("key-before")) {
      Files.writeString(baseline, Json.toJsonString(versions));
      Files.setPosixFilePermissions(baseline, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    } else {
      require(Json.mapperCopy().readTree(Files.readString(baseline)).equals(Json.mapperCopy().valueToTree(versions)),
          "Master-key re-encryption changed credential generations");
      for (var record : records.values()) {
        require(record.keyId().equals("replacement"), "Encrypted credential retained the old master key");
        var decrypted = Json.mapperCopy().readTree(credentials.decrypt(record));
        String secret = record.provider().equals("wecom") ? "secret" : "appSecret";
        require(decrypted.path(secret).asText().equals(configuration.path("providers").path(record.provider()).path("secret").asText()),
            "Re-encryption lost credential material");
      }
    }
    System.out.println("SIM-KEY " + phase + " records=" + records.size() + " generations-preserved=true");
  }

  static String channel(String provider) { return provider.equals("dingtalk") ? "ding-talk" : provider; }
  static NotificationDispatchRequest request(String provider, boolean unknown) {
    var options = new DispatchOptions(); options.setChannels(List.of(channel(provider)));
    String fact = "simulation-" + provider + (unknown ? "-unknown" : "-public");
    return new NotificationDispatchRequest(fact, "enterprise-simulation.notice", "revision-1",
        List.of(scenario.path("guests").path(provider).asText()), "enterprise-simulation-notice",
        Map.of("marker", fact), options);
  }
  static final NotificationDeliveryPolicy PUBLIC_NOTICE = (employee, channel, addresses) ->
      new NotificationDeliveryPolicy.Decision(NotificationDeliveryPolicy.Disposition.ALLOW, "public-fixture-notice-approved");

  static void notifications(EmployeeNotificationDispatcher dispatcher, NotificationService persistence, boolean recovery) throws Exception {
    for (String provider : PROVIDERS) {
      var request = request(provider, false);
      int before = messageCount(request.sourceFactId());
      var result = dispatcher.sendWithResult(request, PUBLIC_NOTICE);
      require(result.isComplete(), "Public notification failed: " + provider + " " + result);
      require(messageCount(request.sourceFactId()) == (recovery ? before : before + 1), "Public notification external write count mismatch");
      require(dispatcher.sendWithResult(request, PUBLIC_NOTICE).isComplete(), "Duplicate dispatch lost completion");
      require(messageCount(request.sourceFactId()) == 1, "Completed notification was sent again");
      var saved = persistence.findDelivery(request.sourceFactId(), request.ruleIdentity()).orElseThrow();
      require(saved.isComplete() && saved.units().size() == 1, "Notification plan did not persist its recipient result");
      System.out.println("SIM-NOTIFICATION " + provider + " persisted=SUCCESS externalWrites=1 noUser=true");
    }
    var request = request("feishu", true);
    if (!recovery) control("POST", "/faults", Map.of("provider", "feishu", "path", "/open-apis/im/v1/messages", "mode", "accepted-disconnect"));
    var result = dispatcher.sendWithResult(request, PUBLIC_NOTICE);
    require(result.status() == NotificationDispatchResult.Status.UNKNOWN, "Lost response must remain UNKNOWN: " + result);
    require(dispatcher.sendWithResult(request, PUBLIC_NOTICE).status() == NotificationDispatchResult.Status.UNKNOWN, "Unknown result must not authorize a resend");
    require(messageCount(request.sourceFactId()) == 1, "Unknown notification duplicated an external write");
    var saved = persistence.findDelivery(request.sourceFactId(), request.ruleIdentity()).orElseThrow();
    require(saved.units().getFirst().state() == NotificationStateConstant.UNKNOWN && saved.units().getFirst().attempt() == 1,
        "Unknown attempt state did not persist");
    System.out.println("SIM-NOTIFICATION feishu persisted=UNKNOWN attempts=1 externalWrites=1");
  }

  static int messageCount(String marker) throws Exception {
    int count = 0;
    for (var message : control("GET", "/state", null).path("messages")) {
      if (message.path("content").asText().contains(marker)) count++;
    }
    return count;
  }
  static JsonNode control(String method, String route, Map<String, Object> body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://simulation-providers:8080" + route))
        .timeout(Duration.ofSeconds(10)).header("authorization", "Bearer " + configuration.path("controlKey").asText())
        .header("content-type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(Json.toJsonString(body))).build();
    var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    require(response.statusCode() == 200, "Simulator control failed");
    return Json.mapperCopy().readTree(response.body());
  }
  static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
