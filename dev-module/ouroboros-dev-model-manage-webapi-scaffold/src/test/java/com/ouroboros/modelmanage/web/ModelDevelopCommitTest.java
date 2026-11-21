package com.ouroboros.modelmanage.web;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.*;

class ModelDevelopCommitTest {
  @Test
  void workspaceSaveKeepsLockAndReadEndpointsRecoverTheCurrentState() {
    var service = new Service();
    var controller = new ModelDevelopController<String>(List.of(service));
    assertEquals(0, controller.create("app", "test", "new", false, "", false).getCode());
    assertEquals("new", controller.get("test", "model", false).getData());
    assertEquals(0, controller.getInitForm("test", "model", false, true, false, false).getCode());
    assertEquals(0, controller.update("app", "test", "model", false, "", true, false, "latest").getCode());
    assertEquals(0, service.unlockCount);
    assertEquals("latest", controller.get("test", "model", false).getData());
    assertEquals(Map.of("versionState", "CHANGED"), controller.getStatus("test", "model").getData());
    assertEquals(List.of("latest"), controller.search("test", Map.of(), false).getData());
    assertEquals(List.of(), controller.getChangeLogs("test", "model", -1, 10).getData());
    assertEquals(List.of(), controller.getChangeLogs("test", "model", 0, 10).getData());
    assertEquals(0, controller.lock("test", "model", false).getCode());
    assertEquals(0, controller.releaseLock("test", "model", false).getCode());
  }

  @Test
  void invalidInputAndForeignLocksPreventCreateUpdateCommitRollbackAndDelete() {
    var service = new Service();
    var controller = new ModelDevelopController<String>(List.of(service));
    service.invalidInput = true;
    assertNotEquals(0, controller.create("app", "test", "bad", false, "", false).getCode());
    assertNotEquals(0, controller.update("app", "test", "model", false, "", true, false, "bad").getCode());
    service.invalidInput = false;
    service.lockResult = Try.failure(new BusinessException("foreign lock"));
    assertNotEquals(0, controller.getInitForm("test", "model", false, true, false, false).getCode());
    assertNotEquals(0, controller.update("app", "test", "model", false, "", true, false, "bad").getCode());
    assertNotEquals(0, controller.commit("test", "model", true, "message", Map.of()).getCode());
    assertNotEquals(0, controller.rollback("app", "test", "model", false, true).getCode());
    assertNotEquals(0, controller.delete("app", "test", "model", false, true, "").getCode());
    assertNull(service.content);
    assertEquals(0, service.unlockCount);
  }

  @Test
  void resourceFailuresStayFailuresAndOptionalInitErrorsCanBeDisplayedAsEmpty() {
    var service = new Service();
    var controller = new ModelDevelopController<String>(List.of(service));
    service.invalidResource = true;
    assertNotEquals(0, controller.create("app", "test", "new", false, "", false).getCode());
    assertNotEquals(0, controller.update("app", "test", "model", false, "", false, false, "new").getCode());
    assertNotEquals(0, controller.getInitForm("test", "model", false, false, false, false).getCode());
    assertEquals(Map.of(), controller.getInitForm("test", "model", true, false, false, false).getData());
    assertNotEquals(0, controller.rollback("app", "test", "model", false, false).getCode());
    service.invalidResource = false;
    assertEquals(0, controller.rollback("app", "test", "model", false, true).getCode());
    assertEquals(0, controller.delete("app", "test", "model", false, true, "").getCode());
    assertThrows(com.ouroboros.exception.PlatformException.class,
        () -> new ModelDevelopController<String>(null).get("unknown", "model", false));
  }
  @Test
  void optionalUpdateCommitFailureIsReturnedEvenAfterSavingSucceeded() {
    var service = new Service();
    service.commitResult = Try.failure(new BusinessException("version rejected"));
    var controller = new ModelDevelopController<String>(List.of(service));
    var result = controller.update("test", "test", "model", true, "message", true, false, "saved");
    assertNotEquals(0, result.getCode());
    assertTrue(result.getMsg().contains("version rejected"));
    assertEquals("saved", service.content);
    assertEquals(0, service.unlockCount);
  }

  @Test
  void explicitCommitValidatesMessageAndDoesNotReportSuccessOnUnlockFailure() {
    var service = new Service();
    var controller = new ModelDevelopController<String>(List.of(service));
    service.commitResult = Try.failure(new BusinessException("提交信息不能为空"));
    var invalid = controller.commit("test", "model", true, "", Map.of("commitMsg", " "));
    assertNotEquals(0, invalid.getCode());
    assertTrue(invalid.getMsg().contains("提交信息不能为空"));
    assertEquals(0, service.unlockCount);
    service.commitResult = Try.success(true);
    service.unlockResult = Try.failure(new BusinessException("unlock failed"));
    var unlockFailed = controller.commit("test", "model", true, "", Map.of("commitMsg", "message"));
    assertNotEquals(0, unlockFailed.getCode());
    assertTrue(unlockFailed.getMsg().contains("unlock failed"));
    assertEquals("message", service.message);
  }

  @Test
  void successfulExplicitCommitPreservesOriginalResponseContract() {
    var service = new Service();
    var controller = new ModelDevelopController<String>(List.of(service));
    var result = controller.commit("test", "model", true, "query message", Map.of());
    assertEquals(0, result.getCode());
    assertTrue(result.getData());
    assertEquals("query message", service.message);
    assertEquals(1, service.unlockCount);
  }

  @DevelopModule("test")
  private static class Service extends BaseDevelopService<String> {
    private Try<Boolean> commitResult = Try.success(true);
    private Try<Void> unlockResult = Try.success(null);
    private String content;
    private String message;
    private int unlockCount;
    private boolean invalidInput;
    private boolean invalidResource;
    private Try<Void> lockResult = Try.success(null);
    protected String getModelFileDirectory() { return "test"; }
    protected String getModelDisplayName() { return "test"; }
    protected String getFullName(String model) { return "model"; }
    public Try<String> buildModelFromRequestBody(String body) { return invalidInput ? Try.failure(new BusinessException("invalid input")) : Try.success(body); }
    public Try<Void> lock(String name, boolean force) { return lockResult; }
    public Try<String> create(String model, String msg) { return update("model", model); }
    public java.util.Optional<String> getOne(String name) { return java.util.Optional.ofNullable(content); }
    public Try<?> getInitFormData(String name) { return invalidResource ? Try.failure(new BusinessException("invalid resource")) : Try.success(content); }
    public Try<?> getStatus(String name) { return Try.success(Map.of("versionState", "CHANGED")); }
    public List<?> search(Map<String, Object> params) { return List.of(content); }
    public List<com.ouroboros.vcs.versioning.ChangeLog> getAllChangeLogs(String name) { return List.of(); }
    public List<com.ouroboros.vcs.versioning.ChangeLog> getChangeLogs(String name, int page, int size) { return List.of(); }
    public Try<Boolean> rollback(String name) { return invalidResource ? Try.failure(new BusinessException("invalid resource")) : Try.success(true); }
    public Try<String> delete(String name, String msg) { return Try.success(content); }
    public Try<String> update(String name, String model) {
      if (invalidResource) return Try.failure(new BusinessException("invalid resource"));
      content = model; return Try.success(model);
    }
    public Try<Boolean> commit(String name, String msg) { message = msg; return commitResult; }
    public Try<Void> unlock(String name, boolean force) { unlockCount++; return unlockResult; }
  }
}
