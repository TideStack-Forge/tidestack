package com.ouroboros.data.dict.provider;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import com.ouroboros.AppContext;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.exception.InternalException;
import com.ouroboros.metadata.resource.FileSystemMetadataSource;
import com.ouroboros.metadata.resource.MetadataDiagnostic;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;

@Order(AppHomeDataDictMetadataProvider.ORDER)
public class AppHomeDataDictMetadataProvider implements DataDictMetadataProvider {
  static final int ORDER = 100;
  private static final Logger logger = LoggerFactory.getLogger(AppHomeDataDictMetadataProvider.class);
  private static final String HOME_PATH = "data-dict";
  private static final String FILE_EXTENSION = ".dict.json";
  private static final String LEGACY_FILE_EXTENSION = ".json";

  private final AppContext appContext;

  public AppHomeDataDictMetadataProvider(AppContext appContext) {
    this.appContext = appContext;
  }

  @Override
  public Map<String, DataDict> getDataDicts() {
    var rootPath = Paths.get(appContext.getHomePath(), HOME_PATH);
    var spec = TypedMetadataSpec.<DataDict>builder(DataDict.class)
        .typedSuffix(FILE_EXTENSION)
        .sourcePlan(TypedMetadataSpec.SourcePlan.LEGACY_DIRECTORY_THEN_TYPED_SUFFIX)
        .legacyFilePattern(Pattern.compile(".*" + Pattern.quote(LEGACY_FILE_EXTENSION)))
        .jsonParser(DataDict.class)
        .keyExtractor(DataDict::getName)
        .build();
    try {
      var result = new TypedMetadataCatalog(new FileSystemMetadataSource(rootPath)).load(spec);
      result.diagnostics().forEach(AppHomeDataDictMetadataProvider::logDiagnostic);
      return Collections.unmodifiableMap(result.values());
    } catch (RuntimeException ex) {
      throw new InternalException("加载字典模型失败", ex);
    }
  }

  private static void logDiagnostic(MetadataDiagnostic diagnostic) {
    if (diagnostic.level() == MetadataDiagnostic.Level.ERROR) {
      logger.error("加载字典模型文件({})失败: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    } else {
      logger.warn("加载字典模型文件({})跳过: {}", diagnostic.resourceName(), diagnostic.message(), diagnostic.cause());
    }
  }
}
