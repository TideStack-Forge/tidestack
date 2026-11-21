package com.ouroboros.enterprise.directory;

import java.util.List;

/** A provider snapshot. {@code complete=false} must never be interpreted as a deletion signal. */
public record EnterpriseDirectorySnapshot(
    List<EnterpriseDirectoryDepartment> departments,
    List<EnterpriseDirectoryMember> members,
    boolean complete) {
  public EnterpriseDirectorySnapshot {
    departments = departments == null ? List.of() : List.copyOf(departments);
    members = members == null ? List.of() : List.copyOf(members);
  }
}
