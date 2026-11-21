package com.ouroboros.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.metadata.RPCModelMetadataProvider;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialBuilder;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.security.metadata.RPCAuthCredentialMetadataProvider;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

public class RPCModelDefinitionCenterTest {

  @Test
  void refresh_buildsEnabledOpenApiDefinitions() {
    RPCModelDefinitionCenter.refresh();

    var definition = RPCModelDefinitionCenter.getDefinition("openApi");
    assertTrue(definition.isPresent());
    assertEquals("openApi", definition.get().getName());
    assertEquals("OpenApi", definition.get().getType());
  }

  @Test
  void refresh_skipsDisabledMetadata() {
    RPCModelDefinitionCenter.refresh();

    assertFalse(RPCModelDefinitionCenter.getDefinition("disabledMock").isPresent());
  }

  @Test
  void refresh_ignoresUnknownMetadataType() {
    RPCModelDefinitionCenter.refresh();

    assertFalse(RPCModelDefinitionCenter.getDefinition("unknownMock").isPresent());
  }

  @Test
  void refresh_keepsLastDefinitionForDuplicateNamesByProviderPriority() {
    RPCModelDefinitionCenter.refresh();

    var definition = RPCModelDefinitionCenter.getDefinition("duplicateMock");
    assertTrue(definition.isPresent());
    assertEquals("duplicateMock", definition.get().getName());
    assertEquals("second duplicate", definition.get().getTitle());
    assertEquals("MockType", definition.get().getType());
    assertEquals("duplicate-credential-2", definition.get().getAuthCredential());
  }

  @Test
  void refresh_keepsLastRpcCredentialForDuplicateNamesByProviderPriority() {
    RPCAuthCredentialCenter.refresh();

    var credential = RPCAuthCredentialCenter.getRpcCredential("duplicateCredential");
    assertTrue(credential.isPresent());
    assertEquals("duplicateCredential", credential.get().getName());
    assertEquals("second credential", credential.get().getTitle());
    assertEquals("MockAuth", credential.get().getType());
  }

  @Priority(-100)
  public static class DuplicateFirstProvider implements RPCModelMetadataProvider {
    @Override
    public List<RPCModelMetadata> getAllRpcModelMetadata() {
      return Collections.singletonList(
          metadata("duplicateMock", "first duplicate", "duplicate-credential-1", "MockType", true));
    }
  }

  @Priority(100)
  public static class DuplicateSecondProvider implements RPCModelMetadataProvider {
    @Override
    public List<RPCModelMetadata> getAllRpcModelMetadata() {
      return Collections.singletonList(
          metadata("duplicateMock", "second duplicate", "duplicate-credential-2", "MockType", true));
    }
  }

  @Priority(0)
  public static class EdgeCaseProvider implements RPCModelMetadataProvider {
    @Override
    public List<RPCModelMetadata> getAllRpcModelMetadata() {
      return Arrays.asList(
          metadata("disabledMock", "disabled mock", "disabled-credential", "MockType", false),
          metadata("unknownMock", "unknown mock", "unknown-credential", "UnknownType", true)
      );
    }
  }

  @Priority(-100)
  public static class MockDefinitionBuilder implements RPCModelDefinitionBuilder {
    @Override
    public boolean support(RPCModelMetadata metadata) {
      return metadata != null && "MockType".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCModelDefinition build(RPCModelMetadata metadata) {
      return new RPCModelDefinition.Simple(
          metadata.getName(),
          metadata.getTitle(),
          metadata.getType(),
          metadata.getAuthCredential(),
          Collections.<String, Object>singletonMap("source", "test")
      );
    }
  }

  @Priority(-100)
  public static class DuplicateCredentialFirstProvider implements RPCAuthCredentialMetadataProvider {
    @Override
    public List<RPCCredentialMetadata> getAllRpcAuthCredentialMetadata() {
      return Collections.singletonList(credential("duplicateCredential", "first credential", "MockAuth", true));
    }
  }

  @Priority(100)
  public static class DuplicateCredentialSecondProvider implements RPCAuthCredentialMetadataProvider {
    @Override
    public List<RPCCredentialMetadata> getAllRpcAuthCredentialMetadata() {
      return Collections.singletonList(credential("duplicateCredential", "second credential", "MockAuth", true));
    }
  }

  @Priority(-100)
  public static class MockAuthCredentialBuilder implements RPCAuthCredentialBuilder<RPCRequest> {
    @Override
    public boolean support(RPCCredentialMetadata metadata) {
      return metadata != null && "MockAuth".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCAuthCredential<RPCRequest> build(RPCCredentialMetadata metadata) {
      return new TestCredential(metadata);
    }
  }

  private static RPCModelMetadata metadata(
      String name,
      String title,
      String authCredential,
      String type,
      boolean enabled
  ) {
    var metadata = new RPCModelMetadata();
    metadata.setName(name);
    metadata.setTitle(title);
    metadata.setAuthCredential(authCredential);
    metadata.setType(type);
    metadata.setEnabled(enabled);
    metadata.setRequest(Collections.emptyMap());
    return metadata;
  }

  private static RPCCredentialMetadata credential(String name, String title, String type, boolean enabled) {
    var metadata = new RPCCredentialMetadata();
    metadata.setName(name);
    metadata.setTitle(title);
    metadata.setType(type);
    metadata.setEnabled(enabled);
    return metadata;
  }

  private static class TestCredential implements RPCAuthCredential<RPCRequest> {
    private final RPCCredentialMetadata metadata;

    TestCredential(RPCCredentialMetadata metadata) {
      this.metadata = metadata;
    }

    @Override
    public String getName() {
      return metadata.getName();
    }

    @Override
    public String getTitle() {
      return metadata.getTitle();
    }

    @Override
    public String getType() {
      return metadata.getType();
    }

    @Override
    public Try<RPCRequest> applyToRequest(RPCRequest request, Map<String, Object> context) {
      return Try.success(request);
    }
  }
}
