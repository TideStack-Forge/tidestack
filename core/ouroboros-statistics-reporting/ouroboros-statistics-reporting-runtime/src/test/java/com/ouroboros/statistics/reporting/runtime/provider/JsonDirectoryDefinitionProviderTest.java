package com.ouroboros.statistics.reporting.runtime.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.PlatformException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDirectoryDefinitionProviderTest {
  @TempDir
  Path appHome;

  @ParameterizedTest
  @MethodSource("providers")
  void shouldRejectMalformedAndUnknownJsonFields(String fileName,
      Function<Path, AbstractJsonDirectoryDefinitionProvider<?>> factory) throws Exception {
    Path file = appHome.resolve(fileName);
    for (String content : new String[]{"{broken", "{\"name\":\"demo.report\",\"removedField\":true}"}) {
      DefinitionProviderTestSupport.writeText(file, content);

      PlatformException error = assertThrows(PlatformException.class, () -> factory.apply(appHome).loadDefinitions());

      assertTrue(error.getMessage().contains(file.toString()));
      assertNotNull(error.getCause());
    }
  }

  @ParameterizedTest
  @MethodSource("providers")
  void shouldRejectNullAndUnnamedDefinitions(String fileName,
      Function<Path, AbstractJsonDirectoryDefinitionProvider<?>> factory) throws Exception {
    Path file = appHome.resolve(fileName);
    for (String content : new String[]{"null", "{}", "{\"name\":\" \"}"}) {
      DefinitionProviderTestSupport.writeText(file, content);

      PlatformException error = assertThrows(PlatformException.class, () -> factory.apply(appHome).loadDefinitions());

      assertTrue(error.getMessage().contains("name is required"));
      assertTrue(error.getMessage().contains(file.toString()));
    }
  }

  @ParameterizedTest
  @MethodSource("providers")
  void shouldReportInvalidUtf8InsteadOfReturningPartialResults(String fileName,
      Function<Path, AbstractJsonDirectoryDefinitionProvider<?>> factory) throws Exception {
    Path file = appHome.resolve(fileName);
    Files.createDirectories(file.getParent());
    Files.write(file, new byte[]{(byte) 0xc3, 0x28});

    InternalException error = assertThrows(InternalException.class, () -> factory.apply(appHome).loadDefinitions());

    assertTrue(error.getMessage().contains(file.toString()));
    assertInstanceOf(java.nio.charset.CharacterCodingException.class, error.getCause());
  }

  private static Stream<Arguments> providers() {
    return Stream.of(
        Arguments.of("statistics-reporting/datasets/demo.report.dataset.json",
            (Function<Path, AbstractJsonDirectoryDefinitionProvider<?>>) AppDefaultDatasetDefinitionProvider::new),
        Arguments.of("data/statistics-reporting/datasets/demo.report.dataset.json",
            (Function<Path, AbstractJsonDirectoryDefinitionProvider<?>>) RuntimeOverrideDatasetDefinitionProvider::new),
        Arguments.of("statistics-reporting/charts/demo.report.chart.json",
            (Function<Path, AbstractJsonDirectoryDefinitionProvider<?>>) AppDefaultChartDefinitionProvider::new),
        Arguments.of("data/statistics-reporting/charts/demo.report.chart.json",
            (Function<Path, AbstractJsonDirectoryDefinitionProvider<?>>) RuntimeOverrideChartDefinitionProvider::new));
  }
}
