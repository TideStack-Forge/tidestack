package com.ouroboros.rpc.security;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.ouroboros.rpc.RPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
public abstract class AbstractRPCAuthCredential<T extends RPCRequest> implements RPCAuthCredential<T> {
  protected final RPCCredentialMetadata metadata;

  public AbstractRPCAuthCredential(RPCCredentialMetadata metadata) {
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

  protected Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(metadata.getExtraProps().get(key));
  }

  protected <T> Optional<T> getExtraProp(Class<T> type, String key) {
    return getExtraProp(key).map(v -> type.isInstance(v) ? type.cast(v) : null);
  }

  protected <T> T getExtraProps(Class<T> type, String key, T defaultValue) {
    return getExtraProp(type, key).orElse(defaultValue);
  }

  protected Optional<Object> getExtraPropDeep(List<String> path) {
    return Maps.Hierarchical.getValue(metadata.getExtraProps(), path);
  }

  protected Optional<Object> getExtraPropDeep(String... pathKey) {
    return getExtraPropDeep(Arrays.asList(pathKey));
  }

  protected <T> Optional<T> getExtraPropDeep(Class<T> clazz, String... pathKey) {
    return getExtraPropDeep(clazz, Arrays.asList(pathKey));
  }

  protected <T> Optional<T> getExtraPropDeep(Class<T> clazz, List<String> path) {
    var value = Maps.Hierarchical.getValue(metadata.getExtraProps(), path);
    return value
        .map(v -> {
          if (clazz.isInstance(v)) {
            return clazz.cast(v);
          }
          return null;
        });
  }
}
