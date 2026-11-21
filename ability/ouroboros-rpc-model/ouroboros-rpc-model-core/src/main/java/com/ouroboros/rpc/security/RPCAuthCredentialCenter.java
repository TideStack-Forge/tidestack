package com.ouroboros.rpc.security;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.rpc.security.metadata.RPCAuthCredentialMetadataProvider;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 **/
public class RPCAuthCredentialCenter {

  private static final Logger logger = LoggerFactory.getLogger(RPCAuthCredentialCenter.class);

  private static volatile Map<String, RPCAuthCredential> authenticatorMap;

  private RPCAuthCredentialCenter() {
  }

  /**
   * 获取Rpc认证器
   *
   * @param name
   * @return
   */
  public static Optional<RPCAuthCredential> getRpcCredential(String name) {
    return Optional.ofNullable(authenticatorMap).map(map -> map.get(name));
  }

  /**
   * 获取所有Rpc认证器
   *
   * @return
   */
  public static Map<String, RPCAuthCredential> getAllRpcCredentials() {
    return Optional.ofNullable(authenticatorMap).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  /**
   * 刷新Rpc认证器
   */
  public static Try<RPCAuthCredential<?>> build(RPCCredentialMetadata metadata,
      com.ouroboros.rpc.execution.RpcExecutionResources resources) {
    return Try.of(() -> {
      var builder = builder(metadata)
          .orElseThrow(() -> new PlatformException("未找到RPC认证构建器"));
      return resources == null ? builder.build(metadata) : builder.build(metadata, resources);
    });
  }

  public static boolean supports(RPCCredentialMetadata metadata) { return builder(metadata).isPresent(); }

  public static Try<Void> validate(RPCCredentialMetadata metadata) {
    return builder(metadata).map(builder -> builder.validate(metadata))
        .orElseGet(() -> Try.failure(new PlatformException("未找到RPC认证构建器")));
  }

  public static List<RPCAuthCredentialBuilder.CredentialType> credentialTypes() {
    var types = new TreeMap<String, RPCAuthCredentialBuilder.CredentialType>(String.CASE_INSENSITIVE_ORDER);
    SPI.getSortedServiceStream(RPCAuthCredentialBuilder.class).forEach(builder -> {
      RPCAuthCredentialBuilder<?> typed = builder;
      typed.credentialType().ifPresent(type -> {
        if (types.putIfAbsent(type.name(), type) != null) { throw new PlatformException("RPC 认证类型重复: " + type.name()); }
      });
    });
    return List.copyOf(types.values());
  }

  private static Optional<RPCAuthCredentialBuilder> builder(RPCCredentialMetadata metadata) {
    return SPI.getSortedServiceStream(RPCAuthCredentialBuilder.class)
        .filter(candidate -> candidate.support(metadata)).findFirst();
  }

  public static void refresh() {
    // 加载Rpc认证器元数据
    var metadataMap = SPI.getSortedServiceStream(RPCAuthCredentialMetadataProvider.class)
        .map(RPCAuthCredentialMetadataProvider::getAllRpcAuthCredentialMetadata)
        .flatMap(Collection::stream)
        .collect(
            Collectors.toMap(
                RPCCredentialMetadata::getName,
                metadata -> metadata,
                (o, n) -> n,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );

    // 加载Rpc认证器构建器

    // 构建Rpc认证器
    authenticatorMap = metadataMap.values().stream()
        .filter(RPCCredentialMetadata::isEnabled)
        .map(metadata -> Tuple.of(
            metadata,
            build(metadata, null)
        ))
        .peek(tuple -> tuple._2.onFailure(e -> logger.error("构建RPC认证器: {} 失败! ", tuple._1.getName(), e)))
        .filter(tuple -> tuple._2.isSuccess())
        .map(tuple -> tuple._2.get())
        .collect(
            Collectors.toMap(
                RPCAuthCredential::getName,
                authenticator -> authenticator,
                (o, n) -> n,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );
  }
}
