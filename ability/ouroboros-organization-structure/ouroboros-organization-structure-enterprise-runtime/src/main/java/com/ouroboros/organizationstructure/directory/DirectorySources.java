package com.ouroboros.organizationstructure.directory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ouroboros.enterprise.directory.EnterpriseDirectorySource;
import com.ouroboros.exception.ValidationException;

public final class DirectorySources {
  private final Map<String, EnterpriseDirectorySource> sources;
  public DirectorySources(List<EnterpriseDirectorySource> sources) {
    this.sources = sources.stream().collect(Collectors.toUnmodifiableMap(EnterpriseDirectorySource::provider,
        Function.identity(), (left, right) -> { throw new ValidationException("企业目录来源重复"); }));
  }
  public EnterpriseDirectorySource require(String provider) {
    var source = sources.get(provider);
    if (source == null) { throw new ValidationException("此连接的目录读取模块未安装"); }
    return source;
  }
  public List<String> providers() { return sources.keySet().stream().sorted().toList(); }
}
