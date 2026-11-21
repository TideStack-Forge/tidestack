package com.ouroboros.appmodule.dev;

import java.util.List;

import com.ouroboros.appmodule.AppModule;

public interface DevAppModuleProvider {
  List<AppModule> getAppModules(String appName);
}
