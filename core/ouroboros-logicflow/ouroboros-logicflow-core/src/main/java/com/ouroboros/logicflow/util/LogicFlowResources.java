package com.ouroboros.logicflow.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LogicFlowResources {
  private LogicFlowResources() {
  }

  public static List<String> resourceContents(String resourceName) {
    return resourceContents(Thread.currentThread().getContextClassLoader(), resourceName);
  }

  public static List<String> resourceContents(ClassLoader classLoader, String resourceName) {
    try {
      var resources = classLoader.getResources(resourceName);
      var contents = new ArrayList<String>();
      while (resources.hasMoreElements()) {
        try (var input = resources.nextElement().openStream()) {
          contents.add(readText(input));
        }
      }
      return contents;
    } catch (IOException exception) {
      return Collections.emptyList();
    }
  }

  public static List<NamedResourceContent> resourceContentsFromDirectory(String directory, Pattern filePattern) {
    return resourceContentsFromDirectory(Thread.currentThread().getContextClassLoader(), directory, filePattern);
  }

  public static List<NamedResourceContent> resourceContentsFromDirectory(ClassLoader classLoader, String directory, Pattern filePattern) {
    try {
      var resources = classLoader.getResources(directory);
      var result = new ArrayList<NamedResourceContent>();
      while (resources.hasMoreElements()) {
        var url = resources.nextElement();
        var protocol = url.getProtocol();
        if ("file".equals(protocol)) {
          result.addAll(readDirectoryResources(url.toURI(), filePattern));
        } else if ("jar".equals(protocol)) {
          result.addAll(readJarResources(url.toURI(), directory, filePattern));
        }
      }
      return result;
    } catch (Exception exception) {
      return Collections.emptyList();
    }
  }

  private static List<NamedResourceContent> readDirectoryResources(URI directoryUri, Pattern filePattern) {
    var directory = new File(directoryUri);
    var files = directory.listFiles();
    if (files == null) {
      return Collections.emptyList();
    }
    var result = new ArrayList<NamedResourceContent>();
    for (var file : files) {
      if (!file.isFile() || !filePattern.matcher(file.getName()).matches()) {
        continue;
      }
      try (var input = file.toURI().toURL().openStream()) {
        result.add(new NamedResourceContent(file.getName(), readText(input)));
      } catch (IOException ignored) {
      }
    }
    return result;
  }

  private static List<NamedResourceContent> readJarResources(URI directoryUri, String directory, Pattern filePattern) throws IOException {
    var raw = directoryUri.getRawSchemeSpecificPart();
    var separatorIndex = raw.indexOf("!/");
    if (separatorIndex < 0) {
      return Collections.emptyList();
    }
    var jarUri = URI.create(raw.substring(0, separatorIndex));
    var result = new ArrayList<NamedResourceContent>();
    try (var jar = new JarFile(new File(jarUri))) {
      var entries = jar.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        var name = entry.getName();
        if (entry.isDirectory() || !name.startsWith(directory)) {
          continue;
        }
        var shortName = name.substring(directory.length());
        if (shortName.contains("/") || !filePattern.matcher(shortName).matches()) {
          continue;
        }
        try (var input = jar.getInputStream(entry)) {
          result.add(new NamedResourceContent(shortName, readText(input)));
        }
      }
    }
    return result;
  }

  private static String readText(InputStream input) {
    return new BufferedReader(new InputStreamReader(input))
        .lines()
        .collect(Collectors.joining("\n"));
  }

  public record NamedResourceContent(String name, String content) {
  }
}
