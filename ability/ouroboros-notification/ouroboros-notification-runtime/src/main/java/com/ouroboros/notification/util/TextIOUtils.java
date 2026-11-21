package com.ouroboros.notification.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.exception.InternalException;

/**
 * @author liansz
 */
public class TextIOUtils {

  /**
   * 从流读取数据（文本数据）
   *
   * @param in
   * @return
   */
  public static String readText(InputStream in) {
    try {
      Objects.requireNonNull(in, "InputStream is null");
    } catch (Exception e) {
      throw new InternalException("输入参数异常: ", e);
    }

    try (in) {
      var bufferedReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      return bufferedReader.lines().collect(Collectors.joining("\n"));
    } catch (FileNotFoundException e) {
      throw new InternalException("配置元数据文件不存在", e);
    } catch (IOException e) {
      throw new InternalException("读取配置元数据文件失败", e);
    }
  }

  /**
   * 向流文件数据（文本数据）
   *
   * @param file
   * @param text
   */
  public static void writeText(File file, String text) {
    try {
      Objects.requireNonNull(file, "File is null");
      Objects.requireNonNull(text, "Text is null");
    } catch (Exception e) {
      throw new InternalException("输入参数异常: ", e);
    }

    if (!file.exists() || !file.isFile()) {
      File parentFile = file.getParentFile();
      if (parentFile != null) {
        parentFile.mkdirs();
      }

      try {
        file.createNewFile();
      } catch (IOException e) {
        throw new InternalException("创建配置文件失败: ", e);
      }
    }

    try {
      Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    } catch (FileNotFoundException e) {
      throw new InternalException("文件不存在", e);
    } catch (IOException e) {
      throw new InternalException("写入文件失败", e);
    }
  }
}
