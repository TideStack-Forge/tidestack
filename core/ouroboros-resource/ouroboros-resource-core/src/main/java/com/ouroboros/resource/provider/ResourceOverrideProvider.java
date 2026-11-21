package com.ouroboros.resource.provider;

/**
 * Read and write access to the runtime override layer. Reads and writes target the same provider.
 * Resource coordination owns metadata generation, locking and restoration of defaults.
 */
public interface ResourceOverrideProvider extends ResourceProvider {

    /**
     * Store content and its serialized resource metadata at a scoped resource path.
     * The two writes need not be atomic. Implementations must report partial failure.
     */
    void writeOverride(String path, byte[] content, byte[] serializedMetadata, String contentType);

    /** Delete content and residual metadata. Repeated deletion of a missing override is safe. */
    void deleteOverride(String path);
}
