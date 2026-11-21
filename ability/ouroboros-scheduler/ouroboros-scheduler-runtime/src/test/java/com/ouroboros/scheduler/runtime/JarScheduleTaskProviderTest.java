package com.ouroboros.scheduler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JarScheduleTaskProviderTest {

  @Test
  void shouldLoadTypedMetadataFromJarMetadataDirectory() {
    var provider = new JarScheduleTaskProvider();

    var tasks = provider.getAllScheduleTask();

    assertEquals(1, tasks.size());
    assertEquals("jar-daily-sync", tasks.get(0).getName());
    assertEquals("0 0 2 * * ?", tasks.get(0).getCron());
  }
}
