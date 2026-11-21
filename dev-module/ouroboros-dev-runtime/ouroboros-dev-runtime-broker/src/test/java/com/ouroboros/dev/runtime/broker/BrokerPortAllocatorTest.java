package com.ouroboros.dev.runtime.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

class BrokerPortAllocatorTest {

  @Test
  void shouldAllocatePortsWithinConfiguredRangeAndReuseReleasedPorts() {
    BrokerPortAllocator allocator = new BrokerPortAllocator(43000, 43001);

    Integer first = allocator.allocate().get();
    Integer second = allocator.allocate().get();
    Try<Integer> exhausted = allocator.allocate();
    allocator.release(first.intValue());
    Integer reused = allocator.allocate().get();

    assertEquals(Integer.valueOf(43000), first);
    assertEquals(Integer.valueOf(43001), second);
    assertTrue(exhausted.isFailure());
    assertEquals(first, reused);
  }
}
