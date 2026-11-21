package com.ouroboros.upload.generator;

import static com.ouroboros.upload.generator.TemplateUtils.processDateTimeParts;
import static com.ouroboros.upload.generator.TemplateUtils.processRandomPart;

import java.nio.file.Paths;
import java.util.Map;

/**
 * 默认的基于模版的文件存储名生成器 <br>
 * 目前可以支持日期、时间、随机数、时间戳等模版 <br>
 * 例如：{rawName}_{yyyy}{MM}{dd}_{HH}{mm}{ss}_{random:6}.{ext} <br>
 * 生成：test_20250901_123456_123456.jpg <br>
 * <br>
 * <i>目前先不支持hash值，因为hash值需要先存储文件，然后再获取hash值，并且分段上传的情况下，hash值也是不确定的，总之计算hash代价较高</i>
 */
public class DefaultStorageFileNameGenerator implements StorageFileNameGenerator {
  @Override
  public String generatorName(String fileName, String path, Map<String, String> options) {
    var fileNameTemplate = options.getOrDefault("fileNameTemplate", "{rawName}.{ext}");
    var pureFileName = Paths.get(fileName).getFileName().toString().replaceAll("\\s", "-");
    return formatName(fileNameTemplate, pureFileName);
  }

  private String formatName(String fileNameTemplate, String fileName) {
    var hasExtName = fileName.contains(".");
    var mainName = hasExtName ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
    var extName = hasExtName ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";

    var name = processDateTimeParts(fileNameTemplate);
    name = processRandomPart(name);
    name = name.replace("{rawName}", mainName);

    if (extName.isEmpty()) {
      return name.replace(".{ext}", "");
    }
    return name.replace("{ext}", extName);
  }

  public static class Builder implements StorageFileNameGeneratorBuilder {
    @Override
    public Boolean support(String strategyName) {
      if ("default".equalsIgnoreCase(strategyName)) {
        return true;
      }
      if ("template".equalsIgnoreCase(strategyName)) {
        return true;
      }
      return false;
    }

    @Override
    public StorageFileNameGenerator build() {
      return new DefaultStorageFileNameGenerator();
    }
  }
}
