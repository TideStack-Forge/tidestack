package com.ouroboros.businessmodel.action;

/** Internal implementation diagnostics; no business object state is included. */
public record BusinessActionImplementation(String modelRevision, String implementationRef, String implementationVersion) {
}
