package com.ouroboros.enterprise.directory;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

/** Provider owned directory reader. It returns a complete snapshot for one published connection. */
public interface EnterpriseDirectorySource {
  String provider();

  EnterpriseDirectorySnapshot load(ResolvedEnterpriseConnection connection,
      EnterpriseDeadline deadline) throws Exception;
}
