package com.ouroboros.dev.app.service.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Converter;

@Service
public class AppEndpointService {

  private static final Logger logger = LoggerFactory.getLogger(AppEndpointService.class);
  private static final String PORT_LABEL_SUFFIX = "_PORT";

  private final ContainerService containerService;
  private final ConfigurationService configurationService;

  public AppEndpointService(ContainerService containerService,
                            ConfigurationService configurationService) {
    this.containerService = containerService;
    this.configurationService = configurationService;
  }

  public Try<Map<String, Integer>> generateAvailablePortMap() {
    return containerService.searchContainerInfo(
        Collections.singletonMap("label", Collections.singletonList("ouroboros-test-app"))
    ).fold(
        Try::failure,
        containers -> {
          String portRange = configurationService.getRequired(String.class, ConfigurationConstant.TEST_APP_PORT_RANGE);
          String[] portRanges = portRange.split("-");
          int portRangeMin = Integer.parseInt(portRanges[0]);
          int portRangeMax = Integer.parseInt(portRanges[1]);

          Set<Integer> usedPorts = containers.stream()
              .filter(container -> container.getLabels() != null)
              .flatMap(c -> c.getLabels().entrySet().stream())
              .filter(labelEntry -> labelEntry.getKey().endsWith(PORT_LABEL_SUFFIX))
              .map(Map.Entry::getValue)
              .map(Converter::toInteger)
              .filter(port -> port != null && port >= portRangeMin && port <= portRangeMax)
              .collect(Collectors.toSet());

          List<Integer> availablePorts = new ArrayList<Integer>();
          for (int port = portRangeMin; port <= portRangeMax && availablePorts.size() < 2; port++) {
            if (!usedPorts.contains(Integer.valueOf(port))) {
              availablePorts.add(Integer.valueOf(port));
            }
          }
          if (availablePorts.size() < 2) {
            return Try.failure(new BusinessException(-1, "端口号已用完"));
          }

          Map<String, Integer> portBindings = new LinkedHashMap<String, Integer>();
          portBindings.put("web", availablePorts.get(0));
          portBindings.put("apiDebug", availablePorts.get(1));
          return Try.success(portBindings);
        }
    );
  }

  public String httpEndpoint(com.ouroboros.container.model.Container container, String portLabel, int containerPort) {
    Integer port = containerPort(container, portLabel, containerPort);
    return port == null ? null : "http://127.0.0.1:" + port;
  }

  public Integer containerPort(com.ouroboros.container.model.Container container, String labelKey, int defaultContainerPort) {
    if (container == null) {
      return null;
    }
    if (container.getLabels() != null) {
      String labelValue = container.getLabels().get(labelKey);
      if (StringUtils.isNotBlank(labelValue)) {
        try {
          return Integer.valueOf(labelValue);
        } catch (NumberFormatException error) {
          logger.warn("Invalid port number in container label {}: {}", labelKey, labelValue);
        }
      }
    }
    if (container.getPortBindings() == null) {
      return null;
    }
    Map<Integer, Integer> tcp = container.getPortBindings().get("tcp");
    return tcp == null ? null : tcp.get(Integer.valueOf(defaultContainerPort));
  }

  public void putPort(Map<String, Object> payload, String key, String endpoint) {
    Integer port = extractPort(endpoint);
    if (port != null) {
      payload.put(key, port);
    }
  }

  public Integer extractPort(String endpoint) {
    if (StringUtils.isBlank(endpoint)) {
      return null;
    }
    try {
      URI uri = URI.create(endpoint);
      return uri.getPort() > 0 ? uri.getPort() : null;
    } catch (Exception parseFailure) {
      return null;
    }
  }
}
