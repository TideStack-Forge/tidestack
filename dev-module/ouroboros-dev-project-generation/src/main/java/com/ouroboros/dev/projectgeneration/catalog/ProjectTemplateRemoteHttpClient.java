package com.ouroboros.dev.projectgeneration.catalog;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface ProjectTemplateRemoteHttpClient {

  ProjectTemplateRemoteHttpResponse get(
      URI uri,
      ProjectTemplateRemoteFetchPolicy policy,
      long maxBytes,
      Duration connectTimeout,
      Duration readTimeout,
      Map<String, String> requestHeaders
  );
}
