package com.ouroboros.resource.runtime.provider;

import com.ouroboros.resource.model.Resource;
import com.ouroboros.resource.model.ResourceDescriptor;
import com.ouroboros.resource.model.ResourceIndex;
import com.ouroboros.resource.model.ResourceLayer;
import com.ouroboros.resource.model.ResourceMetadata;
import com.ouroboros.resource.model.ResourceScope;
import com.ouroboros.resource.provider.ResourceProvider;
import com.ouroboros.resource.runtime.loader.ResourceIndexLoader;
import com.ouroboros.resource.util.ResourcePathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.Priority;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resource provider that loads resources from classpath
 * Base path: META-INF/ouroboros-resources/
 * Priority: 0 (lowest priority in reversed order)
 */
@Priority(0)
public class ClasspathResourceProvider implements ResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClasspathResourceProvider.class);
    private final ResourceIndexLoader indexLoader = new ResourceIndexLoader();
    private final ClassLoader classLoader;

    public ClasspathResourceProvider() {
        this(ClasspathResourceProvider.class.getClassLoader());
    }

    ClasspathResourceProvider(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? ClasspathResourceProvider.class.getClassLoader() : classLoader;
    }

    @Override
    public Optional<Resource> getResource(String path) {
        String classpathPath = ResourcePathUtils.toClasspathPath(path);

        try {
            ClassPathResource classPathResource = new ClassPathResource(classpathPath, classLoader);
            if (!classPathResource.exists()) {
                logger.debug("Resource not found on classpath: {}", classpathPath);
                return Optional.empty();
            }

            InputStream inputStream = classPathResource.getInputStream();
            ResourceDescriptor descriptor =
                    buildDescriptor(path, loadMetadataFromIndex(path).orElse(null), classPathResource);

            Resource resource = Resource.builder()
                    .descriptor(descriptor)
                    .content(inputStream)
                    .build();

            logger.debug("Loaded resource from classpath: {}", path);
            return Optional.of(resource);

        } catch (Exception e) {
            logger.error("Error loading resource from classpath: {}", path, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String path) {
        String classpathPath = ResourcePathUtils.toClasspathPath(path);
        ClassPathResource resource = new ClassPathResource(classpathPath, classLoader);
        return resource.exists();
    }

    @Override
    public List<String> listResources(String directory) {
        String normalizedDir = ResourcePathUtils.normalize(directory);
        String classpathDir = ResourcePathUtils.toClasspathPath(normalizedDir);
        Set<String> actualFiles = scanClasspathDirectory(classpathDir, normalizedDir);

        logger.debug("Found {} resources in directory {} from classpath scan",
                actualFiles.size(), directory);

        return actualFiles.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Scan classpath directory to list resources (from all jars)
     *
     * @param classpathDir   classpath directory path
     * @param normalizedDir  normalized directory path
     * @return set of resource paths
     */
    private Set<String> scanClasspathDirectory(String classpathDir, String normalizedDir) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*");
            return com.ouroboros.util.ResourceUtil.getResourceNamesFromDirectory(classLoader, classpathDir, pattern)
                    .stream()
                    .map(relativePath -> {
                        // Remove leading slash if present
                        if (relativePath.startsWith("/")) {
                            relativePath = relativePath.substring(1);
                        }
                        return normalizedDir.isEmpty() ? relativePath : normalizedDir + "/" + relativePath;
                    })
                    .filter(path -> !path.endsWith("/index.json"))  // Exclude index.json itself
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            logger.error("Error scanning classpath directory: {}", classpathDir, e);
            return Collections.emptySet();
        }
    }

    /**
     * Load metadata from index.json for a specific resource
     *
     * @param resourcePath resource path
     * @return optional metadata
     */
    private Optional<ResourceMetadata> loadMetadataFromIndex(String resourcePath) {
        String directory = ResourcePathUtils.getDirectory(resourcePath);
        String fileName = ResourcePathUtils.getFileName(resourcePath);
        String indexPath = ResourcePathUtils.getIndexPath(directory);
        String classpathIndexPath = ResourcePathUtils.toClasspathPath(indexPath);

        try {
            ClassPathResource indexResource = new ClassPathResource(classpathIndexPath, classLoader);
            if (!indexResource.exists()) {
                return Optional.empty();
            }

            Optional<ResourceIndex> indexOpt = indexLoader.load(indexResource.getInputStream());
            if (!indexOpt.isPresent()) {
                return Optional.empty();
            }

            ResourceIndex index = indexOpt.get();
            Optional<ResourceMetadata> metadataOpt = index.getResources().stream()
                    .filter(m -> m.getName().equals(fileName))
                    .findFirst();

            if (metadataOpt.isPresent()) {
                ResourceMetadata resourceMetadata = metadataOpt.get();
                resourceMetadata.setScope(extractScope(resourcePath));
                resourceMetadata.setKey(ResourcePathUtils.toResourceKey(resourcePath));
                resourceMetadata.setLayer(ResourceLayer.APP_DEFAULT);
                return Optional.of(resourceMetadata);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.debug("Failed to load metadata for resource: {}", resourcePath, e);
            return Optional.empty();
        }
    }

    private ResourceDescriptor buildDescriptor(String resourcePath,
                                              ResourceMetadata metadata,
                                              ClassPathResource classPathResource) {
        ResourceDescriptor descriptor = metadata == null
                ? new ResourceDescriptor()
                : metadata.toDescriptor();
        descriptor.setScope(extractScope(resourcePath));
        descriptor.setKey(ResourcePathUtils.toResourceKey(resourcePath));
        descriptor.setLayer(ResourceLayer.APP_DEFAULT);
        return ResourceDescriptorMetadataSupport.enrichFromClasspath(descriptor, classPathResource);
    }

    private ResourceScope extractScope(String resourcePath) {
        String normalizedPath = ResourcePathUtils.normalize(resourcePath);
        int slashIndex = normalizedPath.indexOf('/');
        String scopeValue = slashIndex < 0 ? normalizedPath : normalizedPath.substring(0, slashIndex);
        return ResourceScope.fromValue(scopeValue);
    }
}
