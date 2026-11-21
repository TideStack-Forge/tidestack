package com.ouroboros.dev.componentlib.webappinfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.WebAppInfoModifier;
import com.ouroboros.dev.componentlib.dto.PlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.service.PlatformComponentLibService;
import com.ouroboros.uimodel.microweb.MicroWebModel;

@Service
public class DevPlatformComponentLibAppInfoModifier implements WebAppInfoModifier {
  private final PlatformComponentLibService platformComponentLibService;

  public DevPlatformComponentLibAppInfoModifier(PlatformComponentLibService platformComponentLibService) {
    this.platformComponentLibService = platformComponentLibService;
    Proxy.delegate = this;
  }

  @Override
  public void modify(Map<String, Object> webAppInfo) {
    var componentLibraries = webAppInfo.computeIfAbsent("componentLibraries", k -> new ArrayList<>());
    if (!(componentLibraries instanceof List<?> list)) {
      return;
    }
    platformComponentLibService.getAll()
        .forEach(componentLib -> {
          ((List<Object>) list).add(new MicroWebOfPlatformComponentLib(componentLib));
        });
  }

  private static final class MicroWebOfPlatformComponentLib implements MicroWebModel {
    @JsonIgnore
    private final PlatformComponentLibDTO componentLib;

    private MicroWebOfPlatformComponentLib(
        PlatformComponentLibDTO componentLib) {
      this.componentLib = componentLib;
    }

      @Override
      public String getName() {
        return componentLib.getName();
      }

    @Override
    public MicroWebType getType() {
      return MicroWebType.LIBRARY;
    }

    @Override
    public String getEntry() {
      return componentLib.getPublicPath();
    }

    @Override
    public String getPublicPath() {
      return componentLib.getPublicPath();
    }

    @Override
    public InputStream getPackageContent() {
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (MicroWebOfPlatformComponentLib) obj;
      return Objects.equals(this.componentLib, that.componentLib);
    }

      @Override
      public String toString() {
        return "MicroWebOfPlatformComponentLib[" +
            "componentLib=" + componentLib + ']';
      }

    @JsonIgnore
    public PlatformComponentLibDTO componentLib() {
      return componentLib;
    }

    @Override
    public int hashCode() {
      return Objects.hash(componentLib);
    }


  }

  public static class Proxy implements WebAppInfoModifier {
    private static DevPlatformComponentLibAppInfoModifier delegate;

    @Override
    public void modify(Map<String, Object> webAppInfo) {
      delegate.modify(webAppInfo);
    }
  }
}
