package com.ouroboros.statistics.reporting.dev.service.impl;

import java.util.List;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.base.BaseDevelopService;

/** Resolves statistical source identities consistently on case-sensitive filesystems. */
abstract class AbstractStatisticsDevService<M> extends BaseDevelopService<M> {
  @Override
  protected String buildModelFilePath(String fullName) {
    List<String> matches = getVcsManager().walkDirectory(getModelFileDirectory(), getModelFileExtension())
        .filter(file -> file.getFileName().equalsIgnoreCase(fullName + getModelFileExtension()))
        .map(file -> file.getPath())
        .toList();
    if (matches.size() > 1) {
      throw new BusinessException("Duplicate statistical definition: " + fullName);
    }
    return matches.isEmpty() ? super.buildModelFilePath(fullName) : matches.getFirst();
  }
}
