package com.ouroboros.develop.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class DevelopActuatorHealthAccessConfigurationTest {

  @Test
  void shouldExposeOnlyGetActuatorHealthAsAnonymousApi() {
    DevelopActuatorHealthAccessConfiguration configuration = new DevelopActuatorHealthAccessConfiguration();

    assertEquals(
        Collections.singletonList("GET /actuator/health"),
        configuration.developActuatorHealthAnonymousApiProvider().getAnonymousApis()
    );
  }
}
