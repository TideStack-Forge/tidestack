package com.ouroboros.resource.runtime.loader;

import com.ouroboros.exception.InternalException;
import com.ouroboros.resource.model.ResourceIndex;
import com.ouroboros.util.Json;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;

/**
 * Loader for resource index (index.json) files
 */
public class ResourceIndexLoader {

    private static final Logger logger = LoggerFactory.getLogger(ResourceIndexLoader.class);

    /**
     * Load resource index from input stream
     *
     * @param inputStream index.json content
     * @return optional resource index
     */
    public Optional<ResourceIndex> load(InputStream inputStream) {
        Try<ResourceIndex> result = Json.tryToBean(ResourceIndex.class, inputStream);
        if (result.isFailure()) {
            logger.error("Failed to load resource index", result.getCause());
        }
        return result.toJavaOptional();
    }

    /**
     * Save resource index to JSON string
     *
     * @param index resource index
     * @return JSON string
     */
    public String save(ResourceIndex index) {
        Try<String> result = Json.tryToPrettyJsonString(index);
        if (result.isFailure()) {
            logger.error("Failed to save resource index", result.getCause());
            throw new InternalException("Failed to save resource index", result.getCause());
        }
        return result.get();
    }
}
