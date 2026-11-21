package com.ouroboros.uimodel.microweb;

import java.io.*;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.InternalException;
import com.ouroboros.util.ZipReader;

/**
 * @author liansz
 */
@Service
public class NginxMicroWebDeployServiceImpl implements MicroWebDeployService {
  @Value("${ouroboros.microWeb.deployPath:${ouroboros.web-root:}}")
  private String webRoot;


  private File prepareDeployPath(String publicPath) {
    File targetDir = new File(webRoot, publicPath);

    // 检查目录存在文件则删除
    if (targetDir.exists() && targetDir.isDirectory() && ObjectUtils.isNotEmpty(targetDir.listFiles())) {
      try {
        FileUtils.deleteDirectory(targetDir);
      } catch (IOException e) {
        throw new InternalException("删除资源文件失败！", e);
      }
    }

    // 创建资源文件夹
    targetDir.mkdirs();
    return targetDir;
  }

  @Override
  public void deploy(String publicPath, InputStream inputStream) {
    var targetDir = prepareDeployPath(publicPath);

    // 释放资源文件
    ZipReader reader = new ZipReader();
    var result = reader.extractFile(inputStream, targetDir.getPath());
    if (result.isFailure()) {
      throw new InternalException("解压微前端文件失败！", result.getCause());
    }
  }

  @Override
  public void deploy(String publicPath, File source) {
    if (source.isFile() && source.getName().toLowerCase().endsWith(".zip")) {
      try (InputStream inputStream = new FileInputStream(source)) {
        deploy(publicPath, inputStream);
      } catch (FileNotFoundException e) {
        throw new InternalException("微前端文件不存在！", e);
      } catch (IOException e) {
        throw new InternalException("微前端文件读取失败！", e);
      }
      return;
    }

    if (!source.isDirectory()) {
      throw new InternalException("不支持该类型的微前端部署！");
    }

    File targetDir = prepareDeployPath(publicPath);

    try {
      FileUtils.copyDirectory(source, targetDir);
    } catch (IOException e) {
      throw new InternalException("复制微前端文件失败！", e);
    }
  }

  @Override
  public void remove(String publicPath) {
    File resourceDir = new File(webRoot, publicPath);
    if (resourceDir.exists()) {
      try {
        FileUtils.deleteDirectory(resourceDir);
      } catch (IOException e) {
        throw new InternalException("删除资源文件失败！", e);
      }
    }
  }
}
