package com.ouroboros.dev.projectgeneration.catalog;

import java.util.Arrays;

public record ProjectTemplateCatalogFetchResult(
    byte[] catalogBytes,
    byte[] signatureEnvelopeBytes
) {
  public ProjectTemplateCatalogFetchResult {
    catalogBytes = catalogBytes == null ? new byte[0] : Arrays.copyOf(catalogBytes, catalogBytes.length);
    signatureEnvelopeBytes = signatureEnvelopeBytes == null
        ? new byte[0]
        : Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
  }

  @Override
  public byte[] catalogBytes() {
    return Arrays.copyOf(catalogBytes, catalogBytes.length);
  }

  @Override
  public byte[] signatureEnvelopeBytes() {
    return Arrays.copyOf(signatureEnvelopeBytes, signatureEnvelopeBytes.length);
  }
}
