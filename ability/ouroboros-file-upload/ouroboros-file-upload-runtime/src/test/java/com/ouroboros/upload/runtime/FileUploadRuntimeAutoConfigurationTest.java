package com.ouroboros.upload.runtime;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.upload.runtime.config.UploadProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileUploadRuntimeAutoConfigurationTest {

  @Test
  void anonymousApiProviderShouldNormalizeWildcardSuffix() {
    UploadProperties properties = new UploadProperties()
        .setAnonymousPrefixes(new String[]{"public", "assets/**"});

    List<String> apis = new FileUploadRuntimeAutoConfiguration()
        .getFileAnonymousAccessApiProvider(properties)
        .getAnonymousApis();

    assertEquals(Arrays.asList("GET /api/file/public/**", "GET /api/file/assets/**"), apis);
  }

  @Test
  void anonymousApiProviderShouldReturnEmptyWhenNoPrefixes() {
    UploadProperties properties = new UploadProperties()
        .setAnonymousPrefixes(new String[]{});

    List<String> apis = new FileUploadRuntimeAutoConfiguration()
        .getFileAnonymousAccessApiProvider(properties)
        .getAnonymousApis();

    assertEquals(Collections.emptyList(), apis);
  }
}
