package com.ouroboros.resource.runtime.provider;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.util.SPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Composite resource provider that delegates to multiple providers
 * Providers are queried in priority order based on @Priority annotation
 * Higher @Priority value = higher actual priority (reversed order)
 */
public class CompositeResourceProvider implements ResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(CompositeResourceProvider.class);
    private final List<ResourceProvider> providers;

    public CompositeResourceProvider(List<ResourceProvider> providers) {
        // Sort providers by @Priority annotation (reversed - higher value first)
        this.providers = providers.stream()
                .sorted(((Comparator<ResourceProvider>) SPI::sortByPriority).reversed())
                .collect(Collectors.toList());

        logger.info("CompositeResourceProvider initialized with {} providers", this.providers.size());
        this.providers.forEach(p ->
                logger.debug("  - {}", p.getClass().getSimpleName()));
    }

    @Override
    public Optional<Resource> getResource(String path) {
        for (ResourceProvider provider : providers) {
            Optional<Resource> resource = provider.getResource(path);
            if (resource.isPresent()) {
                logger.debug("Resource found by {}: {}", provider.getClass().getSimpleName(), path);
                return resource;
            }
        }
        logger.debug("Resource not found by any provider: {}", path);
        return Optional.empty();
    }

    @Override
    public boolean exists(String path) {
        for (ResourceProvider provider : providers) {
            if (provider.exists(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> listResources(String directory) {
        // Collect resources from all providers and deduplicate
        Set<String> allResources = new LinkedHashSet<>();

        for (ResourceProvider provider : providers) {
            List<String> resources = provider.listResources(directory);
            allResources.addAll(resources);
        }

        return new ArrayList<>(allResources);
    }

    /**
     * Get all registered providers
     *
     * @return list of providers in priority order
     */
    public List<ResourceProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }
}
