package com.ouroboros.dev.app.collaboration;

import org.springframework.web.bind.annotation.*;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.dev.app.collaboration.CollaborativeDocumentService.*;

@RestController
@RequestMapping("/api/dev/{appName}/collaboration/{type}")
public class CollaborationController {
  private final CollaborativeDocumentService documents;
  private final CollaborationGateway gateway;

  public CollaborationController(CollaborativeDocumentService documents, CollaborationGateway gateway) {
    this.documents = documents;
    this.gateway = gateway;
  }

  @GetMapping
  public WebApiResult<CollaborationGateway.Bootstrap> list(@PathVariable String appName, @PathVariable String type) {
    var user = user();
    gateway.requireView(appName, user);
    return WebApiResult.ok(new CollaborationGateway.Bootstrap("bootstrap", gateway.writable(appName, user), documents.list(appName, type)));
  }

  @PostMapping("/{id}/initialize")
  public WebApiResult<Document> initialize(@PathVariable String appName, @PathVariable String type,
      @PathVariable String id, @RequestBody Initialization input) {
    var user = user();
    gateway.requireEdit(appName, user);
    var document = documents.initialize(appName, type, id, input, user);
    gateway.publish(appName, type, document);
    return WebApiResult.ok(document);
  }

  @PostMapping("/{id}/updates")
  public WebApiResult<Document> apply(@PathVariable String appName, @PathVariable String type,
      @PathVariable String id, @RequestBody Operation operation) {
    var user = user();
    gateway.requireEdit(appName, user);
    var document = documents.apply(appName, type, id, operation, user);
    gateway.publish(appName, type, document);
    return WebApiResult.ok(document);
  }

  private String user() { return CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity(); }
}
