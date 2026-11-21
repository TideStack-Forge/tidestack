package com.ouroboros.i18n.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.i18n.dev.model.I18nLocaleModel;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class I18nLocaleInspector {
  private static final String MODEL_FILE_DIRECTORY = "i18n";
  private static final String MODEL_FILE_EXTENSION = ".i18n.json";
  private final BaseDevelopService<I18nLocaleModel> i18nLocaleDevService;

  public I18nLocaleInspector(BaseDevelopService<I18nLocaleModel> i18nLocaleDevService) {
    this.i18nLocaleDevService = i18nLocaleDevService;
  }

  private static BaseDevelopService<I18nLocaleModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(I18nLocaleInspector.class).i18nLocaleDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      String locale = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(locale)
          .setTitle(locale)
          .setIcon("fa fa-language")
          .setType("i18n-locale");

      I18nLocaleModel model = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(locale))
          .orElse(null);
      if (model == null) {
        return;
      }

      String version = model.getVersion() == null ? "-" : model.getVersion();
      description.setName(model.getLocale())
          .setTitle(model.getName())
          .setDescription(model.getMessageCount() + " 条文案 | v" + version);
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("多语言文件");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
