package com.ouroboros.statistics.reporting.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.statistics.reporting.dev.model.LowcodeChartDefinition;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;

@Component
public class ChartInspector {
  private static final String MODEL_FILE_DIRECTORY = "statistics-reporting/charts";
  private static final String MODEL_FILE_EXTENSION = ".chart.json";
  private final BaseDevelopService<LowcodeChartDefinition> chartDevService;

  public ChartInspector(BaseDevelopService<LowcodeChartDefinition> chartDevService) {
    this.chartDevService = chartDevService;
  }

  private static BaseDevelopService<LowcodeChartDefinition> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(ChartInspector.class).chartDevService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      String modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setTitle(modelName)
          .setIcon("fa fa-chart-line")
          .setType("chart");

      Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .ifPresent(model -> description.setTitle(model.getTitle()));
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {
      dirs.forEach(dir -> {
        if (MODEL_FILE_DIRECTORY.equals(dir.getPath())) {
          dir.setTitle("图表");
        }
      });
    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY + "/")
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
