package com.ouroboros.dev.app.model;

import java.util.List;

public record AppVcsFileRequest(List<String> paths, String message, boolean force) {
}
