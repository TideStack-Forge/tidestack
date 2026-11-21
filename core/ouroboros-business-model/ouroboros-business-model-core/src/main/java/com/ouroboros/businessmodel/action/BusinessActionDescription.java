package com.ouroboros.businessmodel.action;

/** Authorized public description; implementation bindings and protected object fields are omitted. */
public record BusinessActionDescription(String modelFullName, String modelRevision, String name,
                                        String title, String description,
                                        boolean enabled, boolean readOnly) {
}
