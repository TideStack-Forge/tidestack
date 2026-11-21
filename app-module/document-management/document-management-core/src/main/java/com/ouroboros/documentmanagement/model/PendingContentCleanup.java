package com.ouroboros.documentmanagement.model;

import java.time.LocalDateTime;

/** A document deletion's outstanding physical cleanup responsibility. */
public record PendingContentCleanup(String id, String storagePath, int attempts,
                                    LocalDateTime nextAttemptAt, String lastError, LocalDateTime createdAt) { }
