package com.ouroboros.businessmodel.dev.vo;

/** One exact resource in a model development batch. */
public record BusinessModelResourceChange(String path, String operation, String encoding, String before, String after) { }
