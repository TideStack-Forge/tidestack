package com.ouroboros.rpc.dev.service.testing;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.rpc.dev.service.impl.RpcMetadataDevelopServiceImpl;
import com.ouroboros.rpc.dev.service.impl.RpcAuthCredentialMetadataDevelopServiceImpl;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.rpc.util.RpcConfigurationTemplates;

@Component
class RpcModelTestSnapshotReader {
  private final RpcMetadataDevelopServiceImpl models;
  private final RpcAuthCredentialMetadataDevelopServiceImpl credentials;

  RpcModelTestSnapshotReader(RpcMetadataDevelopServiceImpl models, RpcAuthCredentialMetadataDevelopServiceImpl credentials) {
    this.models = models; this.credentials = credentials;
  }

  RpcModelTestSnapshot read(String name, Function<String, String> configuration) {
    var first = readOnce(name, configuration);
    var second = readOnce(name, configuration);
    if (!first.revision().equals(second.revision())) {
      throw new RpcModelTestFailure("RESOURCE", "RESOURCE_CHANGED", "资源读取期间发生变化，请重新加载", name, null);
    }
    return second;
  }

  private RpcModelTestSnapshot readOnce(String name, Function<String, String> configuration) {
    var modelContents = new LinkedHashMap<String, String>();
    var credentialContents = new LinkedHashMap<String, String>();
    var resources = new LinkedHashMap<String, String>();
    var dependencies = new LinkedHashMap<String, String>();
    var branch = models.getVcsManager().getCurrentBranch().getOrElseThrow(error -> unavailable(name));
    collectModel(name, modelContents, credentialContents, resources, dependencies, configuration, new LinkedHashSet<>());
    return new RpcModelTestSnapshot(DevAppHolder.getAppName(), branch, name, modelContents, credentialContents, resources, dependencies);
  }

  private void collectModel(String name, Map<String, String> modelContents, Map<String, String> credentialContents,
                            Map<String, String> resources, Map<String, String> dependencies,
                            Function<String, String> configuration, Set<String> visiting) {
    validateName(name);
    if (!visiting.add(name)) {
      throw new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "动态令牌模型存在循环依赖", name, null);
    }
    if (modelContents.containsKey(name)) {
      visiting.remove(name);
      return;
    }
    var file = models.getModelVCSFile(name);
    var content = readContent(file, name);
    RPCModelMetadata model = models.buildModelFromFileContent(content).getOrElseThrow(error -> unavailable(name));
    if (!name.equals(model.getName()) || model.getRequest() == null || StringUtils.isBlank(model.getType())) {
      throw unavailable(name);
    }
    modelContents.put(name, content); resources.put(file.getPath(), content);
    if (StringUtils.isNotBlank(model.getAuthCredential())) {
      String credential = resolve(model.getAuthCredential(), configuration);
      if (credential != null) {
        dependencies.put("rpc-model:" + name + "/authCredential", credential);
        collectCredential(credential, modelContents, credentialContents, resources, dependencies, configuration, visiting);
      }
    }
    visiting.remove(name);
  }

  private void collectCredential(String name, Map<String, String> modelContents, Map<String, String> credentialContents,
                                 Map<String, String> resources, Map<String, String> dependencies,
                                 Function<String, String> configuration, Set<String> visiting) {
    validateName(name);
    var file = credentials.getModelVCSFile(name);
    var content = readContent(file, name);
    RPCCredentialMetadata metadata = credentials.buildModelFromFileContent(content).getOrElseThrow(error -> unavailable(name));
    if (!name.equals(metadata.getName()) || !metadata.isEnabled()) { throw unavailable(name); }
    credentialContents.put(name, content); resources.put(file.getPath(), content);
    if ("AccessToken".equalsIgnoreCase(metadata.getType())) {
      String mode = resolve(metadata.getExtraPropDeep(String.class, "accessToken", "mode").orElse("dynamic"), configuration);
      if (mode != null) {
        dependencies.put("rpc-auth:" + name + "/accessToken/mode", mode);
        if (!"static".equalsIgnoreCase(mode)) {
          String tokenModel = resolve(metadata.getExtraPropDeep(String.class, "accessToken", "request", "modelName").orElse(""), configuration);
          if (tokenModel != null) {
            dependencies.put("rpc-auth:" + name + "/accessToken/request/modelName", tokenModel);
            collectModel(tokenModel, modelContents, credentialContents, resources, dependencies, configuration, visiting);
          }
        }
      }
    }
  }

  private String resolve(String template, Function<String, String> configuration) {
    try {
      var keys = RpcConfigurationTemplates.keys(template);
      // Missing structural input leaves a partial graph which can be completed by reinitialization.
      boolean missing = false;
      for (String key : keys) if (configuration.apply(key) == null) missing = true;
      return missing ? null : RpcConfigurationTemplates.resolve(template, configuration);
    } catch (IllegalArgumentException error) {
      throw new RpcModelTestFailure("INPUT", "INPUT_INVALID", "配置引用无效", null, null);
    }
  }

  private String readContent(VCSFile file, String name) {
    if (!file.exists()) { throw unavailable(name); }
    var content = file.getContent();
    if (StringUtils.isBlank(content)) { throw unavailable(name); }
    return content;
  }

  private void validateName(String name) {
    if (name == null || !name.matches("[\\p{L}_][\\p{L}\\p{N}_.-]{0,199}") || name.contains("..")) {
      throw new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "资源名称无效", null, null);
    }
  }

  private RpcModelTestFailure unavailable(String name) {
    return new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "模型资源不存在、已禁用或内容无效", name, null);
  }
}
