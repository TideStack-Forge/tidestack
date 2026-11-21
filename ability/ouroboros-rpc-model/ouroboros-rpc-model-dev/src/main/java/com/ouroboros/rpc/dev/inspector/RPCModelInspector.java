package com.ouroboros.rpc.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class RPCModelInspector {
  private static final String MODEL_FILE_DIRECTORY = "rpc-model";
  private static final String MODEL_FILE_EXTENSION = ".rpc.json";
  private static final String AUTH_METADATA_FILE_DIRECTORY = "rpc-model/auth";
  private static final String AUTH_METADATA_FILE_EXTENSION = ".rpc.auth.json";
  private final BaseDevelopService<RPCModelMetadata> rpcModelDevService;
  private final BaseDevelopService<RPCCredentialMetadata> rpcAuthCredentialMetadataDevelopService;

  public RPCModelInspector(BaseDevelopService<RPCModelMetadata> rpcModelDevService, BaseDevelopService<RPCCredentialMetadata> rpcAuthCredentialMetadataDevelopService) {
    this.rpcModelDevService = rpcModelDevService;
    this.rpcAuthCredentialMetadataDevelopService = rpcAuthCredentialMetadataDevelopService;
  }

  private static BaseDevelopService<RPCModelMetadata> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(RPCModelInspector.class).rpcModelDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {

    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      if (file.getPath().startsWith(AUTH_METADATA_FILE_DIRECTORY)) {
        var modelName = file.getFileName().substring(0, file.getFileName().length() - AUTH_METADATA_FILE_EXTENSION.length());
        description.setIcon("fa fa-key")
            .setType("rpc-auth")
            .setDescription("认证方式")
            .setName(modelName)
            .setTitle(modelName);
        var metadata = Optional.ofNullable(com.ouroboros.common.app.util.SpringContextUtil.getBean(RPCModelInspector.class).rpcAuthCredentialMetadataDevelopService)
            .flatMap(service -> service.getOne(modelName))
            .orElse(null);
        if (metadata == null) {
          return;
        }
        description.setTitle(metadata.getTitle());
      } else if (file.getPath().startsWith(MODEL_FILE_DIRECTORY)) {
        var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
        description.setName(modelName)
            .setTitle(modelName)
            .setDescription("外部接口")
            .setIcon("fas fa-exchange-alt")
            .setType("rpc-model");
        var metadata = Optional.ofNullable(currentService())
            .flatMap(service -> service.getOne(modelName))
            .orElse(null);
        if (metadata == null) {
          return;
        }
        description.setTitle(metadata.getTitle());
      }
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      for (DirDescription dir : dirs) {
        if (dir.getPath().equals(AUTH_METADATA_FILE_DIRECTORY)) {
          dir.setTitle("认证方式");
        }
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("外部接口");
        }
      }
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY);
    }
  }
}
