package com.ouroboros.resource.runtime.provider;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceReadResult;
import com.ouroboros.resource.model.ResourceResolveContext;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.resource.spi.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of ResourceService
 * Provides layered resource resolution.
 */
public class DefaultResourceService implements ResourceService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceService.class);
    private final ResourceProvider runtimeOverrideProvider;
    private final ResourceProvider appDefaultProvider;
    private final ResourceProvider compositeProvider;

    public DefaultResourceService(ResourceProvider runtimeOverrideProvider, ResourceProvider appDefaultProvider) {
        this.runtimeOverrideProvider = runtimeOverrideProvider;
        this.appDefaultProvider = appDefaultProvider;
        this.compositeProvider = new CompositeResourceProvider(Arrays.asList(runtimeOverrideProvider, appDefaultProvider));
        logger.info("DefaultResourceService initialized with runtime override provider: {}, app default provider: {}",
                runtimeOverrideProvider.getClass().getSimpleName(), appDefaultProvider.getClass().getSimpleName());
    }
    @Override
    public ResourceReadResult read(ResourceResolveContext context) {
        logger.debug("Reading resource with context: {}", context.getScopedPath());

        Optional<Resource> runtimeOverride = runtimeOverrideProvider.getResource(context);
        Optional<Resource> appDefault = appDefaultProvider.getResource(context);

        ResourceReadResult result = new ResourceReadResult()
            .setHasOverride(runtimeOverride.isPresent())
            .setDefaultExists(appDefault.isPresent());

        if (runtimeOverride.isPresent()) {
            Resource resource = runtimeOverride.get();
            if (appDefault.isPresent() && appDefault.get() != resource) {
                try { appDefault.get().getContent().close(); }
                catch (java.io.IOException e) { throw new com.ouroboros.exception.InternalException("Cannot release unused default resource", e); }
            }
            return result
                .setEffectiveResource(resource)
                .setEffectiveDescriptor(resource.getDescriptor())
                .setEffectiveLayer(resource.getLayer())
                .setSource(runtimeOverrideProvider.getClass().getSimpleName());
        }

        if (appDefault.isPresent()) {
            Resource resource = appDefault.get();
            return result
                .setEffectiveResource(resource)
                .setEffectiveDescriptor(resource.getDescriptor())
                .setEffectiveLayer(resource.getLayer())
                .setSource(appDefaultProvider.getClass().getSimpleName());
        }

        return result;
    }

    @Override
    public Optional<Resource> getResource(String path) {
        logger.debug("Getting resource: {}", path);
        return compositeProvider.getResource(path);
    }

    @Override
    public boolean exists(String path) {
        return compositeProvider.exists(path);
    }

    @Override
    public List<String> listResources(String directory) {
        logger.debug("Listing resources in: {}", directory);
        return compositeProvider.listResources(directory);
    }
}
