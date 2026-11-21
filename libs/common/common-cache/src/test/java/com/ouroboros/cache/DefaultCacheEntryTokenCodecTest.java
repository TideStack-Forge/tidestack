package com.ouroboros.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCacheEntryTokenCodecTest {

  @Test
  void tokenCodec_rejectsTamperedTokenAndCrossCacheReuse() {
    var codec = new DefaultCacheEntryTokenCodec("platform-cache-secret");
    var token = codec.encode("users", "concurrent-map", "u:1");

    assertEquals("u:1", codec.decode("users", "concurrent-map", token).getKeyPayload());
    assertThrows(IllegalArgumentException.class, () -> codec.decode("roles", "concurrent-map", token));
    assertThrows(IllegalArgumentException.class, () -> codec.decode("users", "concurrent-map", token + "tampered"));
  }

  @Test
  void tokenCodec_roundTripsEscapedKeyPayload() {
    var codec = new DefaultCacheEntryTokenCodec("platform-cache-secret");
    var token = codec.encode("users", "concurrent-map", "u:%|1");

    assertEquals("u:%|1", codec.decode("users", "concurrent-map", token).getKeyPayload());
  }
}
