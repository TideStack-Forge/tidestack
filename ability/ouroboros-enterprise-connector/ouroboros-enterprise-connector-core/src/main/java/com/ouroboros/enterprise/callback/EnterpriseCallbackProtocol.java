package com.ouroboros.enterprise.callback;

import java.util.List;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** Installed provider owns signature, challenge, decryption and acknowledgement wire formats. */
public interface EnterpriseCallbackProtocol {
  String provider();

  VerifiedCallback verify(ResolvedEnterpriseConnection connection, EnterpriseCallbackRequest request);

  record Response(String contentType, String body) {
    @Override public String toString() { return "EnterpriseCallbackResponse[material=****]"; }
  }

  record VerifiedCallback(List<VerifiedEnterpriseEvent> events, Response response) {
    public VerifiedCallback { events = List.copyOf(events); }
  }
}
