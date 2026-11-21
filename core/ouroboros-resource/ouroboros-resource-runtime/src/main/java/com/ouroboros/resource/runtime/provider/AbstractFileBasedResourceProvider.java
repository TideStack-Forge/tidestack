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
import com.ouroboros.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract file-based resource provider
 * Provides common functionality for file-based resource providers
 */
public abstract class AbstractFileBasedResourceProvider implements ResourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(AbstractFileBasedResourceProvider.class);
    private static final String SIDECAR_SUFFIX = ".resource.json";
    private final ResourceIndexLoader indexLoader = new ResourceIndexLoader();

    /**
     * Get the base directory for resources
     *
     * @return base directory path
     */
    protected abstract Path getBaseDirectory();

    /**
     * Resource layer produced by this provider.
     *
     * @return resource layer
     */
    protected abstract ResourceLayer getLayer();

    @Override
    public Optional<Resource> getResource(String path) {
        Path basePath = getBaseDirectory();
        if (basePath == null || !Files.exists(basePath)) {
            logger.debug("Base directory does not exist: {}", basePath);
            return Optional.empty();
        }

        String normalizedPath = ResourcePathUtils.normalize(path);
        if (normalizedPath.endsWith(SIDECAR_SUFFIX)) {
            return Optional.empty();
        }
        Path filePath = basePath.resolve(normalizedPath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            logger.debug("Resource not found: {}", filePath);
            return Optional.empty();
        }

        try {
            InputStream inputStream = new FileInputStream(filePath.toFile());
            ResourceDescriptor descriptor = buildDescriptor(path, loadMetadata(path).orElse(null), filePath);

            Resource resource = Resource.builder()
                    .descriptor(descriptor)
                    .content(inputStream)
                    .build();

            logger.debug("Loaded resource: {}", path);
            return Optional.of(resource);

        } catch (Exception e) {
            logger.error("Error loading resource: {}", path, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String path) {
        Path basePath = getBaseDirectory();
        if (basePath == null || !Files.exists(basePath)) {
            return false;
        }

        String normalizedPath = ResourcePathUtils.normalize(path);
        if (normalizedPath.endsWith(SIDECAR_SUFFIX)) {
            return false;
        }
        Path filePath = basePath.resolve(normalizedPath);

        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    @Override
    public List<String> listResources(String directory) {
        Path basePath = getBaseDirectory();
        if (basePath == null || !Files.exists(basePath)) {
            return Collections.emptyList();
        }

        String normalizedDir = ResourcePathUtils.normalize(directory);
        Path directoryPath = basePath.resolve(normalizedDir);
        Set<String> actualFiles = scanFileSystemDirectory(directoryPath, normalizedDir);

        logger.debug("Found {} resources in directory {} from filesystem scan",
                actualFiles.size(), directory);

        return actualFiles.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Scan file system directory to list resources
     *
     * @param directoryPath  directory path
     * @param normalizedDir  normalized directory path
     * @return set of resource paths
     */
    private Set<String> scanFileSystemDirectory(Path directoryPath, String normalizedDir) {
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            return Collections.emptySet();
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(directoryPath)) {
            Path normalizedDirectoryPath = directoryPath.normalize();
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("index.json"))
                    .filter(path -> !path.toString().endsWith(SIDECAR_SUFFIX))
                    .map(path -> {
                        String relativePath = normalizedDirectoryPath.relativize(path.normalize())
                                .toString()
                                .replace(File.separatorChar, '/');
                        return normalizedDir.isEmpty() ? relativePath : normalizedDir + "/" + relativePath;
                    })
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            logger.error("Error scanning file system directory: {}", directoryPath, e);
            return Collections.emptySet();
        }
    }

    /**
     * Load metadata from sidecar or index.json for a specific resource
     *
     * @param resourcePath resource path
     * @return optional metadata
     */
    protected Optional<ResourceMetadata> loadMetadata(String resourcePath) {
        Optional<ResourceMetadata> sidecarMetadata = loadMetadataFromSidecar(resourcePath);
        if (sidecarMetadata.isPresent()) {
            return sidecarMetadata;
        }
        return loadMetadataFromIndex(resourcePath);
    }

    private Optional<ResourceMetadata> loadMetadataFromSidecar(String resourcePath) {
        Path basePath = getBaseDirectory();
        if (basePath == null || !Files.exists(basePath)) {
            return Optional.empty();
        }

        String normalizedPath = ResourcePathUtils.normalize(resourcePath);
        ResourceScope scope = extractScope(normalizedPath);
        String key = ResourcePathUtils.toResourceKey(normalizedPath);
        Path sidecarPath = basePath.resolve(ResourcePathUtils.toSidecarPath(scope, key));
        if (!Files.exists(sidecarPath) || !Files.isRegularFile(sidecarPath)) {
            return Optional.empty();
        }

        try (InputStream inputStream = new FileInputStream(sidecarPath.toFile())) {
            ResourceMetadata metadata = Json.toBean(ResourceMetadata.class, inputStream);
            if (metadata == null) {
                return Optional.empty();
            }
            metadata.setScope(scope);
            metadata.setKey(key);
            metadata.setLayer(getLayer());
            return Optional.of(metadata);
        } catch (Exception e) {
            logger.debug("Failed to load sidecar metadata for resource: {}", resourcePath, e);
            return Optional.empty();
        }
    }

    private Optional<ResourceMetadata> loadMetadataFromIndex(String resourcePath) {
        Path basePath = getBaseDirectory();
        if (basePath == null || !Files.exists(basePath)) {
            return Optional.empty();
        }

        String directory = ResourcePathUtils.getDirectory(resourcePath);
        String fileName = ResourcePathUtils.getFileName(resourcePath);
        String indexPath = ResourcePathUtils.getIndexPath(directory);
        Path indexFilePath = basePath.resolve(ResourcePathUtils.normalize(indexPath));

        if (!Files.exists(indexFilePath)) {
            return Optional.empty();
        }

        try (InputStream is = new FileInputStream(indexFilePath.toFile())) {
            Optional<ResourceIndex> indexOpt = indexLoader.load(is);
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
                resourceMetadata.setLayer(getLayer());
                return Optional.of(resourceMetadata);
            }

            return Optional.empty();

        } catch (Exception e) {
            logger.debug("Failed to load metadata for resource: {}", resourcePath, e);
            return Optional.empty();
        }
    }

    private ResourceDescriptor buildDescriptor(String resourcePath, ResourceMetadata resourceMetadata, Path filePath) {
        ResourceDescriptor descriptor = resourceMetadata == null
                ? new ResourceDescriptor()
                : resourceMetadata.toDescriptor();
        descriptor.setScope(extractScope(resourcePath));
        descriptor.setKey(ResourcePathUtils.toResourceKey(resourcePath));
        descriptor.setLayer(getLayer());
        return ResourceDescriptorMetadataSupport.enrichFromFile(descriptor, filePath);
    }

    private ResourceScope extractScope(String resourcePath) {
        String normalizedPath = ResourcePathUtils.normalize(resourcePath);
        int slashIndex = normalizedPath.indexOf('/');
        String scopeValue = slashIndex < 0 ? normalizedPath : normalizedPath.substring(0, slashIndex);
        return ResourceScope.fromValue(scopeValue);
    }
}
