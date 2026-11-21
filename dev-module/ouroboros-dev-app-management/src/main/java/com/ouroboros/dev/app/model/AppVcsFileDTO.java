package com.ouroboros.dev.app.model;

import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

public record AppVcsFileDTO(String path, VersionState versionState, LockState lockState,
                            String lockOwner, boolean exists, boolean canCommit,
                            boolean canUnlock, boolean canForceUnlock) {
}
