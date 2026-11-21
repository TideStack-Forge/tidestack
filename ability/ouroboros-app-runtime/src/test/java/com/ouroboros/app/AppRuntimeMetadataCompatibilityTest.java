package com.ouroboros.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.AppContext;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.model.AppInfo;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.SPI;

/** App-runtime compatibility when the debug helper and shared cache provider are installed. */
class AppRuntimeMetadataCompatibilityTest {
  @TempDir Path appHome;

  @Test
  void exportsEditableApplicationDefaultsAndClearsMissingOptionalValues() {
    var previous = ReflectionTestUtils.getField(AppMetadataConfigurationProvider.Proxy.class, "provider");
    var context = mock(AppContext.class);
    var app = new AppInfo().setTitle("订单中心").setDefaultTheme("light")
        .setLogo("brand.svg").setHomePage("/orders");
    when(context.getAppInfo()).thenAnswer(call -> Optional.of(app));
    try {
      new AppMetadataConfigurationProvider(context);
      var proxy = new AppMetadataConfigurationProvider.Proxy();
      var settings = proxy.getAllConfigurationMetadata().stream()
          .collect(Collectors.toMap(ConfigurationMetadata::getKey, Function.identity()));
      assertEquals(4, settings.size());
      assertEquals("订单中心", settings.get("appName").getDefaultValue());
      assertEquals("light", settings.get("defaultTheme").getDefaultValue());
      assertEquals("/api/app-metadata/brand.svg", settings.get("logo").getDefaultValue());
      assertEquals("/orders", settings.get("homePage").getDefaultValue());
      for (var entry : settings.entrySet()) {
        assertEquals(ConfigValueType.STRING, entry.getValue().getValueType());
        assertTrue(entry.getValue().isModifiable());
        assertEquals(List.of("logo", "homePage").contains(entry.getKey()), entry.getValue().isNullable());
      }
      app.setLogo("").setHomePage("");
      var cleared = proxy.getAllConfigurationMetadata();
      assertNull(cleared.stream().filter(c -> c.getKey().equals("logo")).findFirst().orElseThrow().getDefaultValue());
      assertNull(cleared.stream().filter(c -> c.getKey().equals("homePage")).findFirst().orElseThrow().getDefaultValue());
      when(context.getAppInfo()).thenReturn(Optional.empty());
      assertTrue(proxy.getAllConfigurationMetadata().isEmpty());
    } finally {
      ReflectionTestUtils.setField(AppMetadataConfigurationProvider.Proxy.class, "provider", previous);
    }
  }

  @Test
  void refreshesCaseInsensitiveMicroWebEntriesAndRemovesUnresolvedCustomizations() {
    var previous = AppMetadataWebAppInfoContributor.SingletonHolder.INSTANCE;
    var context = mock(AppContext.class);
    var app = new AppInfo();
    app.getCustom().setLoginMicroWeb("LOGIN");
    app.getCustom().setTopBarMicroWeb("Header");
    app.getCustom().setSideBarMicroWeb("sidebar");
    when(context.getAppInfo()).thenReturn(Optional.of(app));
    var models = List.of(microWeb("login", "/login.js", null), microWeb("header", null, "/header/"),
        microWeb("SIDEBAR", "/sidebar.js", null), microWeb(" ", "/ignored.js", null));
    MicroWebModelProvider provider = () -> models;
    try (var spi = mockStatic(SPI.class)) {
      spi.when(() -> SPI.getSortedServiceStream(MicroWebModelProvider.class)).thenAnswer(call -> Stream.of(provider));
      var contributor = new AppMetadataWebAppInfoContributor(context);
      var proxy = new AppMetadataWebAppInfoContributor.SingletonHolder();
      assertTrue(proxy.getWebAppInfo().isEmpty());
      contributor.onOuroborosModelRefreshedEvent(new OuroborosContextRefreshedEvent(this));
      assertEquals(Map.of("loginEntry", "/login.js", "headerEntry", "/header/", "sidebarEntry", "/sidebar.js"), proxy.getWebAppInfo());
      app.getCustom().setLoginMicroWeb("missing");
      app.getCustom().setTopBarMicroWeb(null);
      app.getCustom().setSideBarMicroWeb("");
      contributor.onOuroborosModelRefreshedEvent(new OuroborosContextRefreshedEvent(this));
      assertTrue(proxy.getWebAppInfo().isEmpty());
    } finally {
      AppMetadataWebAppInfoContributor.SingletonHolder.INSTANCE = previous;
    }
  }

  @Test
  void combinesPackagedStylesBeforeApplicationStylesAndHandlesAbsentOrBlankFiles() throws Exception {
    var previous = AppCustomStyleAppInfoContributor.Proxy.INSTANCE;
    var context = mock(AppContext.class);
    when(context.getHomePath()).thenReturn(appHome.toString());
    try (var resources = mockConstruction(ClasspathMetadataFiles.class, (files, construction) ->
        when(files.legacyThenMetadataContentStreamForOverride("style.css"))
            .thenAnswer(call -> Stream.of(".brand {color: red;}", " ", ".brand {color: blue;}")))) {
      Files.writeString(appHome.resolve("style.css"), ".brand {color: green;}");
      new AppCustomStyleAppInfoContributor(context);
      assertEquals(Map.of("customStyle", ".brand {color: red;}\n.brand {color: blue;}\n.brand {color: green;}"),
          new AppCustomStyleAppInfoContributor.Proxy().getWebAppInfo());
    } finally {
      AppCustomStyleAppInfoContributor.Proxy.INSTANCE = previous;
    }
    try (var resources = mockConstruction(ClasspathMetadataFiles.class, (files, construction) ->
        when(files.legacyThenMetadataContentStreamForOverride("style.css")).thenAnswer(call -> Stream.empty()))) {
      var contributor = new AppCustomStyleAppInfoContributor(context);
      Files.writeString(appHome.resolve("style.css"), " \n");
      assertTrue(contributor.getWebAppInfo().isEmpty());
      Files.delete(appHome.resolve("style.css"));
      assertTrue(contributor.getWebAppInfo().isEmpty());
    } finally {
      AppCustomStyleAppInfoContributor.Proxy.INSTANCE = previous;
    }
  }

  private MicroWebModel microWeb(String name, String entry, String publicPath) {
    var model = mock(MicroWebModel.class);
    when(model.getName()).thenReturn(name);
    when(model.getEntry()).thenReturn(entry);
    when(model.getPublicPath()).thenReturn(publicPath);
    return model;
  }
}
