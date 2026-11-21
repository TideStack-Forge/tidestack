package com.ouroboros.businessmodel.behavior;

import java.util.Objects;

/** Read-only provenance recorded where a definition or binding enters the canonical resolver. */
public record BehaviorMemberSource(String memberType, String memberName, String role, String kind,
                                   BehaviorDefinitionSource source, String description) {
  public BehaviorMemberSource(String memberType, String memberName, String role, String kind, BehaviorDefinitionSource source) {
    this(memberType, memberName, role, kind, source, null);
  }

  public BehaviorMemberSource {
    Objects.requireNonNull(memberType, "memberType");
    Objects.requireNonNull(memberName, "memberName");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(source, "source");
  }
}
