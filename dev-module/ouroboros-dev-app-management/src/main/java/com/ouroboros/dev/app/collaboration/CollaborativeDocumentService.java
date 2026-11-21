package com.ouroboros.dev.app.collaboration;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ouroboros.dev.collaboration.CollaborativeResource;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

/** Persists opaque Yjs updates. Merging stays in Yjs, while VCS remains the durable source. */
@Service
public class CollaborativeDocumentService {
  public static final String FORMAT = "ouroboros-yjs-v1";
  private final VCSManagerCenter managers;
  private final Map<String, CollaborativeResource> resources;

  public record Update(String id, String data) {}
  public record Document(String format, String id, long revision, String seed,
      String checkpoint, List<Update> updates) {}
  public record Initialization(String seed, String checkpoint) {}
  public record Operation(String id, long baseRevision, String data, String checkpoint) {}

  public CollaborativeDocumentService(VCSManagerCenter managers, List<CollaborativeResource> resources) {
    this.managers = managers;
    this.resources = resources.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
        CollaborativeResource::collaborationType, resource -> resource));
  }

  public List<Document> list(String app, String type) {
    var resource = resource(type);
    var manager = managers.getVcsManager(app);
    synchronized (manager) {
      try (var files = manager.walkDirectory(resource.collaborationDirectory(), resource.collaborationSuffix())) {
        return files.map(file -> {
          var name = file.getFileName();
          var id = name.substring(0, name.length() - resource.collaborationSuffix().length());
          validateId(id);
          return read(manager, resource, id);
        }).toList();
      }
    }
  }

  public Document initialize(String app, String type, String id, Initialization input, String user) {
    validateId(id);
    if (input == null) throw new ValidationException("协同初始化数据不能为空");
    var resource = resource(type);
    resource.validateCollaborationSeed(id, input.seed());
    binary(input.checkpoint());
    var manager = managers.getVcsManager(app);
    synchronized (manager) {
      var current = read(manager, resource, id);
      if (current != null && current.checkpoint() != null) return current;
      if (current != null && !Json.toMap(current.seed()).equals(Json.toMap(input.seed()))) {
        throw new BusinessException("画布已变更，请重新加载后加入协同");
      }
      var document = new Document(FORMAT, id, 1, input.seed(), input.checkpoint(), List.of());
      write(manager, resource, document, user);
      return document;
    }
  }

  public Document apply(String app, String type, String id, Operation operation, String user) {
    validateId(id);
    if (operation == null || operation.id() == null || !operation.id().matches("[a-zA-Z0-9_-]{1,80}")) {
      throw new ValidationException("协同操作标识无效");
    }
    binary(operation.data());
    binary(operation.checkpoint());
    var resource = resource(type);
    var manager = managers.getVcsManager(app);
    synchronized (manager) {
      var current = read(manager, resource, id);
      if (current == null || current.checkpoint() == null) throw new BusinessException("协同文档尚未初始化");
      var previous = current.updates().stream().filter(update -> update.id().equals(operation.id())).findFirst();
      if (previous.isPresent()) {
        if (!previous.get().data().equals(operation.data())) throw new ValidationException("协同操作标识已被使用");
        return current;
      }
      if (operation.baseRevision() < 1 || operation.baseRevision() > current.revision()) {
        throw new ValidationException("协同文档版本无效");
      }
      // A checkpoint can replace the log only when it includes the exact persisted prefix.
      boolean compact = operation.baseRevision() == current.revision();
      var updates = compact ? new ArrayList<Update>() : new ArrayList<>(current.updates());
      updates.add(new Update(operation.id(), operation.data()));
      var document = new Document(FORMAT, id, current.revision() + 1, current.seed(),
          compact ? operation.checkpoint() : current.checkpoint(), List.copyOf(updates));
      write(manager, resource, document, user);
      return document;
    }
  }

  public Document get(String app, String type, String id) {
    validateId(id);
    var manager = managers.getVcsManager(app);
    synchronized (manager) {
      return read(manager, resource(type), id);
    }
  }

  private Document read(VCSManager manager, CollaborativeResource resource, String id) {
    var file = manager.getFile(path(resource, id));
    if (!file.exists()) return null;
    var content = file.getContent();
    if (FORMAT.equals(Json.toMap(content).get("format"))) return Json.toBean(Document.class, content);
    return new Document(FORMAT, id, 0, content, null, List.of());
  }

  private void write(VCSManager manager, CollaborativeResource resource, Document document, String user) {
    var path = path(resource, document.id());
    var previous = manager.getFile(path);
    var expected = previous.exists() ? previous.getContent() : null;
    var batch = manager.batchOperation().committer(user).autoLock().unlockWhenCommit();
    batch.beforeWrite(() -> {
      var current = manager.getFile(path);
      if (!Objects.equals(expected, current.exists() ? current.getContent() : null)) {
        throw new BusinessException("协同存储已更新，请重试当前操作");
      }
    });
    if (expected == null) batch.create(path, Json.toPrettyJsonString(document));
    else batch.update(path, Json.toPrettyJsonString(document));
    batch.commit("同步协同文档 " + document.id()).get();
  }

  private CollaborativeResource resource(String type) {
    var resource = resources.get(type);
    if (resource == null) throw new ValidationException("不支持的协同文档类型");
    return resource;
  }

  private String path(CollaborativeResource resource, String id) {
    return resource.collaborationDirectory() + "/" + id + resource.collaborationSuffix();
  }

  private void validateId(String id) {
    if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,191}") || id.contains("..")) {
      throw new ValidationException("协同文档标识无效");
    }
  }

  private void binary(String encoded) {
    try {
      if (encoded == null || Base64.getDecoder().decode(encoded).length < 2) throw new IllegalArgumentException();
    } catch (IllegalArgumentException error) {
      throw new ValidationException("协同数据编码无效");
    }
  }
}
