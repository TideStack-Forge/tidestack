package com.ouroboros.security.externalauth.config;

/** Effective authentication settings; configuration adapters supply authoritative revisions. */
@FunctionalInterface
public interface ExternalAuthConfiguration {
  ExternalAuthProperties get();

  default String getRevision() { return null; }
}
