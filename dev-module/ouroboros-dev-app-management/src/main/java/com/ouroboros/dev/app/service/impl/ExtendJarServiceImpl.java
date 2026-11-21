package com.ouroboros.dev.app.service.impl;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.dev.app.service.ExtendJarService;
import com.ouroboros.dev.app.service.runtime.VCSArtifactBindingStorage;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.vcs.VCSManagerCenter;

@Service
@SuppressWarnings("unused")
public class ExtendJarServiceImpl implements ExtendJarService {
  private final static String EXTEND_JAR_DIR = "extend";
  @Autowired
  VCSManagerCenter vcsManagerCenter;
  @Autowired
  VCSArtifactBindingStorage vcsArtifactBindingStorage;

  @Override
  public List<ExtendJarVO> listExtendJars(String appName) {
    var vcsManager = vcsManagerCenter.getVcsManager(appName);
    return vcsManager
        .walkDirectory(EXTEND_JAR_DIR, ".jar")
        .map(f -> {
          var vo = new ExtendJarVO();
          vo.setFileName(f.getFileName());
          vo.setIcon("fa-brands fa-java");
          vo.setCreationTime(f.getCreationTime());
          vo.setLastModifiedTime(f.getLastModifiedTime());
          vo.setSize(f.getSize());
          return vo;
        })
        .collect(Collectors.toList());
  }

  @Override
  public Either<OuroborosException, Void> addExtendJar(String appName, String jarName, InputStream jarContent) {
    return toEither(vcsArtifactBindingStorage.addUploadedArtifact(appName, jarName, jarContent));
  }

  @Override
  public Either<OuroborosException, Void> deleteExtendJar(String appName, String jarName) {
    return toEither(vcsArtifactBindingStorage.deleteUploadedArtifact(appName, jarName));
  }

  @Override
  public Either<OuroborosException, Void> updateExtendJar(String appName, String jarName, InputStream jarContent) {
    return toEither(vcsArtifactBindingStorage.updateUploadedArtifact(appName, jarName, jarContent));
  }

  private Either<OuroborosException, Void> toEither(Try<Void> result) {
    return result.toEither()
        .mapLeft(e -> {
          if (e instanceof OuroborosException) {
            return (OuroborosException) e;
          }
          return new InternalException(-1, e.getMessage());
        })
        .map(ignore -> null);
  }
}
