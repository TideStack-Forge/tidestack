package com.ouroboros.logicflow.authoring.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.logicflow.authoring.model.NodeDefine;
import com.ouroboros.logicflow.util.LogicFlowJson;

public class JarLogicFlowNodeDefineProvider implements LogicFlowNodeDefineProvider {
  private final static Logger logger = LoggerFactory.getLogger(JarLogicFlowNodeDefineProvider.class);
  private static final String NODE_DEFINE_ROOT = "META-INF/ouroboros/metadata";
  private static final String NODE_DEFINE_SUFFIX = ".flow-node.json";

  private static final Supplier<List<NodeDefineResource>> CLASSPATH_RESOURCES = cacheResources(defaultClassLoader());
  private final Supplier<List<NodeDefineResource>> resources;

  public JarLogicFlowNodeDefineProvider() {
    resources = CLASSPATH_RESOURCES;
  }

  JarLogicFlowNodeDefineProvider(ClassLoader classLoader) {
    resources = cacheResources(classLoader);
  }

  private static Supplier<List<NodeDefineResource>> cacheResources(ClassLoader classLoader) {
    return new Supplier<>() {
      private List<NodeDefineResource> snapshot;

      @Override
      public synchronized List<NodeDefineResource> get() {
        if (snapshot != null) {
          return snapshot;
        }
        var complete = new boolean[]{true};
        var loaded = new ArrayList<NodeDefineResource>();
        for (var resource : findNodeDefineResources(classLoader, complete)) {
          try {
            loaded.add(readResource(resource));
          } catch (Exception e) {
            logger.error("读取逻辑流节点定义 {} 失败，已跳过", resource, e);
            complete[0] = false;
          }
        }
        if (complete[0] && loaded.stream().allMatch(resource -> parseResource(resource).isSuccess())) {
          snapshot = List.copyOf(loaded);
        }
        return loaded;
      }
    };
  }

  @Override
  public List<NodeDefine> getNodeDefines() {
    return resources.get().stream()
        .flatMap(resource -> parseResource(resource)
            .onFailure(e -> logger.warn("解析逻辑流节点定义 {} 失败，已跳过", resource.name(), e))
            .toJavaStream())
        .collect(Collectors.toMap(NodeDefine::getType, nodeDefine -> nodeDefine, (first, ignored) -> first))
        .values()
        .stream()
        .collect(Collectors.toList());
  }

  private static ClassLoader defaultClassLoader() {
    var classLoader = Thread.currentThread().getContextClassLoader();
    return classLoader == null ? JarLogicFlowNodeDefineProvider.class.getClassLoader() : classLoader;
  }

  private static List<URL> findNodeDefineResources(ClassLoader classLoader, boolean[] complete) {
    try {
      Enumeration<URL> roots = classLoader.getResources(NODE_DEFINE_ROOT);
      if (!roots.hasMoreElements()) {
        return Collections.emptyList();
      }
      return Collections.list(roots).stream()
          .flatMap(root -> findNodeDefineResources(root, complete).stream())
          .collect(Collectors.toList());
    } catch (Exception e) {
      logger.error("查找逻辑流节点定义元数据目录 {} 失败，已跳过", NODE_DEFINE_ROOT, e);
      complete[0] = false;
      return Collections.emptyList();
    }
  }

  private static List<URL> findNodeDefineResources(URL root, boolean[] complete) {
    try {
      if ("file".equals(root.getProtocol())) {
        var rootPath = java.nio.file.Path.of(root.toURI());
        try (var paths = java.nio.file.Files.walk(rootPath)) {
          return paths
              .filter(path -> java.nio.file.Files.isRegularFile(path) && path.getFileName().toString().endsWith(NODE_DEFINE_SUFFIX))
              .map(path -> {
                try {
                  return path.toUri().toURL();
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
              .collect(Collectors.toList());
        }
      }
      if ("jar".equals(root.getProtocol())) {
        var connection = (java.net.JarURLConnection) root.openConnection();
        var rootEntryName = connection.getEntryName();
        try (var jarFile = connection.getJarFile()) {
          return jarFile.stream()
              .filter(entry -> !entry.isDirectory())
              .filter(entry -> entry.getName().startsWith(rootEntryName + "/"))
              .filter(entry -> entry.getName().endsWith(NODE_DEFINE_SUFFIX))
              .map(entry -> {
                try {
                  var jarFileUrl = java.nio.file.Path.of(jarFile.getName()).toUri().toURL().toExternalForm();
                  return URI.create("jar:" + jarFileUrl + "!/" + entry.getName()).toURL();
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              })
              .collect(Collectors.toList());
        }
      }
      logger.warn("跳过不支持的逻辑流节点定义资源协议: {}", root);
      complete[0] = false;
    } catch (Exception e) {
      logger.error("读取逻辑流节点定义元数据目录 {} 失败，已跳过", root, e);
      complete[0] = false;
    }
    return Collections.emptyList();
  }

  private static NodeDefineResource readResource(URL resource) {
    try (var input = resource.openStream()) {
      var content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return new NodeDefineResource(resource.toExternalForm(), content);
    } catch (IOException e) {
      throw new IllegalStateException("读取逻辑流节点定义失败: " + resource, e);
    }
  }

  private static Try<NodeDefine> parseResource(NodeDefineResource resource) {
    return LogicFlowJson.tryToBean(NodeDefine.class, resource.content())
        .filter(Objects::nonNull)
        .filter(node -> node.getType() != null && !node.getType().isBlank());
  }

  private record NodeDefineResource(String name, String content) {
  }
}
