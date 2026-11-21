package com.ouroboros.webapi.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;

@Component
public class WebAPIInspector {
  private static final String MODEL_FILE_DIRECTORY = "webapi";
  private static final String MODEL_FILE_EXTENSION = ".webapi.json";
  private final BaseDevelopService<LowcodeWebAPIModel> webAPIDevService;

  public WebAPIInspector(BaseDevelopService<LowcodeWebAPIModel> webAPIDevService) {
    this.webAPIDevService = webAPIDevService;
  }

  private static BaseDevelopService<LowcodeWebAPIModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(WebAPIInspector.class).webAPIDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setIcon("webapi")
          .setType("webapi")
          .setTitle(modelName);
      var model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (model == null) {
        return;
      }
      var title = StringUtils.isEmpty(model.getSummary()) ? model.getApi() : model.getSummary();
      description.setTitle(StringUtils.isEmpty(title) ? modelName : title);
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (dir.getPath().equals(MODEL_FILE_DIRECTORY)) {
          dir.setTitle("WebAPI模型");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      var path = file.getPath();
      return path.startsWith(MODEL_FILE_DIRECTORY + "/")
          && path.endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
