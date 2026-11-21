package com.ouroboros.enterprise;

/**
 * Maps a resolved secret payload into the typed credential required by one provider.
 */
public interface EnterpriseConnectionCredentialMapper {

  String provider();

  EnterpriseCredential map(String secretPayload);
}
