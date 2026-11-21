package com.ouroboros.workflow.approval.web.user;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestSpiUsernameResolver implements UsernameResolver {

  @Override
  public Optional<String> resolve(String user) {
    return "employee-from-spi".equals(user) ? Optional.of("SPI用户") : Optional.empty();
  }

  @Override
  public Map<String, String> resolve(List<String> users) {
    return users.contains("employee-from-spi")
        ? Collections.singletonMap("employee-from-spi", "SPI用户")
        : Collections.emptyMap();
  }
}
