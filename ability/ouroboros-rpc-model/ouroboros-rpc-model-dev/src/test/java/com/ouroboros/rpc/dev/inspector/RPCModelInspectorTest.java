package com.ouroboros.rpc.dev.inspector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

class RPCModelInspectorTest {
  private org.springframework.context.support.GenericApplicationContext inspectorContext;

  @org.junit.jupiter.api.BeforeEach
  void openInspectorContext() {
    inspectorContext = new org.springframework.context.support.GenericApplicationContext();
    inspectorContext.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    inspectorContext.refresh();
  }

  @org.junit.jupiter.api.AfterEach
  void closeInspectorContext() {
    inspectorContext.close();
  }


  @Test
  void inspect_populatesModelAndCredentialTitlesFromDevelopServices() {
    inspectorContext.getBeanFactory().registerSingleton("RPCModelInspector", new RPCModelInspector(
        new StubRpcModelService(rpcModel("customer", "客户接口")),
        new StubCredentialService(rpcCredential("token", "令牌认证"))));
    var proxy = new RPCModelInspector.Proxy();

    var modelDescription = proxy.inspect(vcsFile("rpc-model/customer.rpc.json"))
        .orElseThrow(() -> new AssertionError("rpc-model description should exist"));
    assertEquals("customer", modelDescription.getName());
    assertEquals("客户接口", modelDescription.getTitle());
    assertEquals("rpc-model", modelDescription.getType());
    assertEquals("fas fa-exchange-alt", modelDescription.getIcon());

    var authDescription = proxy.inspect(vcsFile("rpc-model/auth/token.rpc.auth.json"))
        .orElseThrow(() -> new AssertionError("rpc-auth description should exist"));
    assertEquals("token", authDescription.getName());
    assertEquals("令牌认证", authDescription.getTitle());
    assertEquals("rpc-auth", authDescription.getType());
    assertEquals("fa fa-key", authDescription.getIcon());

    Set<DirDescription> dirs = proxy.inspectDir(vcsFile("rpc-model/auth/token.rpc.auth.json"));
    var modelDir = dirs.stream()
        .filter(dir -> "rpc-model".equals(dir.getPath()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("rpc-model root dir should exist"));
    assertEquals("外部接口", modelDir.getTitle());

    var authDir = dirs.stream()
        .filter(dir -> "rpc-model/auth".equals(dir.getPath()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("rpc-model/auth dir should exist"));
    assertEquals("认证方式", authDir.getTitle());
  }

  @Test
  void support_isBoundedToRpcModelTree() {
    inspectorContext.getBeanFactory().registerSingleton("RPCModelInspector", new RPCModelInspector(new StubRpcModelService(null), new StubCredentialService(null)));
    var proxy = new RPCModelInspector.Proxy();

    assertTrue(proxy.support(vcsFile("rpc-model/customer.rpc.json")));
    assertTrue(proxy.support(vcsFile("rpc-model/auth/token.rpc.auth.json")));
    assertFalse(proxy.support(vcsFile("models/customer.rpc.json")));
  }

  private static RPCModelMetadata rpcModel(String name, String title) {
    var metadata = new RPCModelMetadata();
    metadata.setName(name);
    metadata.setTitle(title);
    metadata.setType("OpenAPI");
    metadata.setRequest(Collections.emptyMap());
    return metadata;
  }

  private static RPCCredentialMetadata rpcCredential(String name, String title) {
    var metadata = new RPCCredentialMetadata();
    metadata.setName(name);
    metadata.setTitle(title);
    metadata.setType("AccessToken");
    return metadata;
  }

  private static VCSFile vcsFile(String path) {
    return (VCSFile) Proxy.newProxyInstance(
        VCSFile.class.getClassLoader(),
        new Class<?>[]{VCSFile.class},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "exists":
              return true;
            case "getPath":
              return path;
            case "getFileName":
              return path.substring(path.lastIndexOf('/') + 1);
            case "getContent":
              return "";
            case "getInputStream":
              return null;
            case "getBytesArray":
              return new byte[0];
            case "getSize":
              return 1L;
            case "getCreationTime":
            case "getLastCommittedTime":
            case "getLastModifiedTime":
              return LocalDateTime.of(2026, 3, 23, 0, 0);
            case "getVersionState":
              return VersionState.NOTCHANGED;
            case "getLockState":
              return LockState.UNLOCKED;
            case "getLockOwner":
            case "getLockOwnerName":
            case "getRevision":
              return null;
            case "isHistorical":
              return false;
            case "lock":
            case "unlock":
            case "write":
            case "move":
            case "remove":
            case "commit":
            case "rollback":
            case "diffWith":
            case "diffWithWorkingCopy":
              return Try.failure(new UnsupportedOperationException(method.getName()));
            case "getRevisions":
              return Collections.emptyList();
            case "atRevision":
            case "atBranch":
            case "atTag":
              return proxy;
            default:
              throw new UnsupportedOperationException(method.getName());
          }
        });
  }

  private static class StubRpcModelService extends BaseDevelopService<RPCModelMetadata> {
    private final RPCModelMetadata model;

    private StubRpcModelService(RPCModelMetadata model) {
      this.model = model;
    }

    @Override
    protected String getModelFileDirectory() {
      return "rpc-model";
    }

    @Override
    protected String getModelDisplayName() {
      return "外部接口";
    }

    @Override
    protected String getFullName(RPCModelMetadata model) {
      return model.getName();
    }

    @Override
    public Optional<RPCModelMetadata> getOne(String fullName) {
      return Optional.ofNullable(model).filter(current -> fullName.equals(current.getName()));
    }
  }

  private static class StubCredentialService extends BaseDevelopService<RPCCredentialMetadata> {
    private final RPCCredentialMetadata model;

    private StubCredentialService(RPCCredentialMetadata model) {
      this.model = model;
    }

    @Override
    protected String getModelFileDirectory() {
      return "rpc-model/auth";
    }

    @Override
    protected String getModelDisplayName() {
      return "认证方式";
    }

    @Override
    protected String getFullName(RPCCredentialMetadata model) {
      return model.getName();
    }

    @Override
    public Optional<RPCCredentialMetadata> getOne(String fullName) {
      return Optional.ofNullable(model).filter(current -> fullName.equals(current.getName()));
    }
  }
}
