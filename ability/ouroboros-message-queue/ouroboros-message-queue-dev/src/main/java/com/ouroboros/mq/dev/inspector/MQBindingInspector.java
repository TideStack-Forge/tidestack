package com.ouroboros.mq.dev.inspector;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.mq.dev.model.MQBindingModel;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.DirDescription;
import com.ouroboros.vcs.file.inspector.FileDescription;


@Component
public class MQBindingInspector {
  // 声明一个BaseDevelopService类型的静态成员变量，用于处理MQ绑定模型
  private final BaseDevelopService<MQBindingModel> mqBindingDevelopService;
  // 定义模型文件存储目录的常量
  private static final String MODEL_FILE_DIRECTORY = "mq-binding";
  // 定义模型文件扩展名的常量
  private static final String MODEL_FILE_EXTENSION = ".mq.json";
  /**
   * 构造函数，用于初始化MQBindingInspector实例
   * @param mqBindingDevelopService 用于处理MQ绑定模型的服务类实例
   */
  public MQBindingInspector(BaseDevelopService<MQBindingModel> mqBindingDevelopService) {
    // 将传入的服务类实例赋值给静态成员变量
    this.mqBindingDevelopService = mqBindingDevelopService;
  }

  private static BaseDevelopService<MQBindingModel> currentService() {
    return com.ouroboros.common.app.util.SpringContextUtil.getBean(MQBindingInspector.class).mqBindingDevelopService;
  }

  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      var modelName = file.getFileName().substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());
      description.setName(modelName)
          .setTitle(modelName)
          .setType("mq-binding")
          .setIcon("fa fa-link");
      var metadata = Optional.ofNullable(currentService())
          .flatMap(service -> service.getOne(modelName))
          .orElse(null);
      if (metadata == null) {
        return;
      }
      // TODO: 考虑分离 title 和 description
      var affix = "consumer".equalsIgnoreCase(metadata.getBindingType()) ? "消费者" : "生产者";
      description.setTitle(metadata.getDescription() + " - " + affix)
          .setDescription(metadata.getDescription());
    }

    @Override
    protected void postProcessDirs(VCSFile file, Set<DirDescription> dirs) {

    }

    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
          && file.getPath().endsWith(MODEL_FILE_EXTENSION);
    }
  }
}
