package com.ouroboros.resource.spi;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceDescriptor;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.model.ResourceScope;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resource service interface.
 * Provides unified layered resource access.
 */
public interface ResourceService {

    default ResourceReadResult read(ResourceResolveContext context) {
        Optional<Resource> resource = getResource(context.getScopedPath());
        ResourceReadResult result = new ResourceReadResult()
            .setDefaultExists(resource.isPresent())
            .setHasOverride(false);
        resource.ifPresent(value -> result
            .setEffectiveResource(value)
            .setEffectiveDescriptor(value.getDescriptor())
            .setEffectiveLayer(value.getLayer() == null ? ResourceLayer.APP_DEFAULT : value.getLayer())
            .setSource("legacy-resource-provider"));
        return result;
    }

    /**
     * Get a resource by path.
     *
     * @param path resource path
     * @return optional resource
     */
    Optional<Resource> getResource(String path);

    default Optional<Resource> getResource(ResourceScope scope, String key) {
        String normalized = key == null ? "" : key;
        normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return getResource(normalized.isEmpty() ? scope.getValue() : scope.getValue() + "/" + normalized);
    }

    /**
     * Check if a resource exists
     *
     * @param path resource path
     * @return true if resource exists
     */
    boolean exists(String path);

    default boolean exists(ResourceResolveContext context) {
        return exists(context.getScopedPath());
    }

    default boolean exists(ResourceScope scope, String key) {
        String normalized = key == null ? "" : key;
        normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return exists(normalized.isEmpty() ? scope.getValue() : scope.getValue() + "/" + normalized);
    }

    /**
     * List all resources in a directory.
     *
     * @param directory directory path
     * @return list of resource paths
     */
    List<String> listResources(String directory);

    default List<ResourceDescriptor> listResources(ResourceScope scope, String directory) {
        String normalized = directory == null ? "" : directory;
        normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return listResources(normalized.isEmpty() ? scope.getValue() : scope.getValue() + "/" + normalized).stream()
            .map(path -> {
                String normalizedPath = path == null ? "" : path;
                normalizedPath = normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath;
                normalizedPath = normalizedPath.endsWith("/") ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
                int firstSlash = normalizedPath.indexOf('/');
                String scopeValue = firstSlash < 0 ? normalizedPath : normalizedPath.substring(0, firstSlash);
                String keyValue = firstSlash < 0 || firstSlash == normalizedPath.length() - 1 ? "" : normalizedPath.substring(firstSlash + 1);
                return new ResourceDescriptor()
                    .setScope(ResourceScope.fromValue(scopeValue))
                    .setKey(keyValue);
            })
            .collect(Collectors.toList());
    }
}
