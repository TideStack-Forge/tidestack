package com.ouroboros.dev.environment;

import java.util.List;

/**
 * @author lianshuze
 **/
public interface EnvironmentProvider {
  List<Environment> getAll();
}
