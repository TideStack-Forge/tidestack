package com.ouroboros.workflow.approval.web.user;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UsernameResolver {

  Optional<String> resolve(String user);

  Map<String, String> resolve(List<String> users);
}
