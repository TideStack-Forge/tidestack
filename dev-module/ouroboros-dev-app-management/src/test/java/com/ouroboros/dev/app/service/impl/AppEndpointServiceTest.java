package com.ouroboros.dev.app.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;

@ExtendWith(MockitoExtension.class)
class AppEndpointServiceTest {

  @Mock
  private ContainerService containerService;

  @Mock
  private ConfigurationService configurationService;

  private AppEndpointService service;

  @BeforeEach
  void setUp() {
    service = new AppEndpointService(containerService, configurationService);
  }

  @Test
  void shouldReuseReleasedPortInsteadOfAppendingAfterMaxPort() {
    givenPortRange("32080-32090");
    givenContainers(appContainer("32080"), appContainer("32081"), appContainer("32082"));

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32083), result.get("web"));
    assertEquals(Integer.valueOf(32084), result.get("apiDebug"));
  }

  @Test
  void shouldReusePortsReleasedInTheMiddleOfAllocatedRange() {
    givenPortRange("32080-32090");
    givenContainers(appContainer("32080"), appContainer("32082"), appContainer("32083"));

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32081), result.get("web"));
    assertEquals(Integer.valueOf(32084), result.get("apiDebug"));
  }

  @Test
  void shouldAllocateTwoSmallestFreePortsWhenTheyAreNotAdjacent() {
    givenPortRange("32080-32090");
    givenContainers(appContainer("32081"));

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32080), result.get("web"));
    assertEquals(Integer.valueOf(32082), result.get("apiDebug"));
  }

  @Test
  void shouldAllocateFromRangeStartWhenNoContainerUsesTheRange() {
    givenPortRange("32080-32090");
    givenContainers();

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32080), result.get("web"));
    assertEquals(Integer.valueOf(32081), result.get("apiDebug"));
  }

  @Test
  void shouldIgnorePortsOutsideConfiguredRange() {
    givenPortRange("32080-32090");
    givenContainers(appContainer("31000"), appContainer("99999"));

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32080), result.get("web"));
    assertEquals(Integer.valueOf(32081), result.get("apiDebug"));
  }

  @Test
  void shouldIgnoreMalformedAndNonPortLabels() {
    givenPortRange("32080-32090");
    Container container = new Container();
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("ouroboros-test-app", "CRM");
    labels.put("WEB_PORT", "not-a-number");
    labels.put("OUROBOROS_APP_NAME", "crm");
    container.setLabels(labels);
    givenContainers(container);

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32080), result.get("web"));
    assertEquals(Integer.valueOf(32081), result.get("apiDebug"));
  }

  @Test
  void shouldIgnoreContainersWithoutLabels() {
    givenPortRange("32080-32090");
    Container container = new Container();
    container.setName("ouroboros-test-app-crm-alice");
    givenContainers(container);

    Map<String, Integer> result = service.generateAvailablePortMap().get();

    assertEquals(Integer.valueOf(32080), result.get("web"));
    assertEquals(Integer.valueOf(32081), result.get("apiDebug"));
  }

  @Test
  void shouldFailWhenFewerThanTwoPortsRemainAvailable() {
    givenPortRange("32080-32081");
    givenContainers(appContainer("32080"));

    Try<Map<String, Integer>> result = service.generateAvailablePortMap();

    assertTrue(result.isFailure());
    assertEquals("端口号已用完", result.getCause().getMessage());
  }

  @Test
  void shouldFailWhenContainerSearchFails() {
    IllegalStateException searchFailure = new IllegalStateException("docker unavailable");
    when(containerService.searchContainerInfo(any())).thenReturn(Either.left(searchFailure));

    Try<Map<String, Integer>> result = service.generateAvailablePortMap();

    assertTrue(result.isFailure());
    assertEquals(searchFailure, result.getCause());
  }

  private void givenPortRange(String portRange) {
    when(configurationService.getRequired(String.class, ConfigurationConstant.TEST_APP_PORT_RANGE))
        .thenReturn(portRange);
  }

  private void givenContainers(Container... containers) {
    when(containerService.searchContainerInfo(any())).thenReturn(Either.right(Arrays.asList(containers)));
  }

  private static Container appContainer(String webPort) {
    Container container = new Container();
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("ouroboros-test-app", "CRM");
    labels.put("OUROBOROS_APP_NAME", "crm");
    labels.put("WEB_PORT", webPort);
    container.setLabels(labels);
    return container;
  }
}
