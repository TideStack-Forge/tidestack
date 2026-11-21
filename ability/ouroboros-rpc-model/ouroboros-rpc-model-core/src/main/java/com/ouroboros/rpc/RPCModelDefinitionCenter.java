package com.ouroboros.rpc;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.control.Try;

import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.metadata.RPCModelMetadataProvider;
import com.ouroboros.util.SPI;

/**
 * RPC 模型静态定义中心。
 */
public class RPCModelDefinitionCenter {

  private static final Logger logger = LoggerFactory.getLogger(RPCModelDefinitionCenter.class);

  private static Map<String, RPCModelDefinition> definitionMap;

  private RPCModelDefinitionCenter() {
  }

  public static Optional<RPCModelDefinition> getDefinition(String name) {
    return Optional.ofNullable(definitionMap).map(map -> map.get(name));
  }

  public static Map<String, RPCModelDefinition> getAllDefinitions() {
    return Optional.ofNullable(definitionMap)
        .map(Collections::unmodifiableMap)
        .orElse(Collections.emptyMap());
  }

  public static Try<RPCModelDefinition> build(RPCModelMetadata metadata) {
    return builder(metadata)
        .map(builder -> Try.of(() -> builder.build(metadata)))
        .orElseGet(() -> Try.failure(new com.ouroboros.exception.PlatformException("未找到RPC模型构建器")));
  }

  public static boolean supports(RPCModelMetadata metadata) { return builder(metadata).isPresent(); }

  private static Optional<RPCModelDefinitionBuilder> builder(RPCModelMetadata metadata) {
    return SPI.getSortedServiceStream(RPCModelDefinitionBuilder.class)
        .filter(candidate -> candidate.support(metadata)).findFirst();
  }

  public static void refresh() {
    var metadataMap = SPI.getSortedServiceStream(RPCModelMetadataProvider.class)
        .map(RPCModelMetadataProvider::getAllRpcModelMetadata)
        .flatMap(Collection::stream)
        .collect(
            Collectors.toMap(
                RPCModelMetadata::getName,
                metadata -> metadata,
                (oldValue, newValue) -> newValue,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );

    definitionMap = metadataMap.values().stream()
        .filter(RPCModelMetadata::isEnabled)
        .map(metadata -> Tuple.of(metadata, build(metadata)))
        .filter(Objects::nonNull)
        .peek(tuple -> tuple._2.onFailure(e -> logger.error("构建RPC模型定义: {} 失败! ", tuple._1.getName(), e)))
        .filter(tuple -> tuple._2.isSuccess())
        .map(tuple -> tuple._2.get())
        .collect(
            Collectors.toMap(
                RPCModelDefinition::getName,
                definition -> definition,
                (oldValue, newValue) -> newValue,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );
  }
}
