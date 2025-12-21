package org.tinymediamanager.core.tvshow;

/**
 * Strategy interface for path normalization and deduplication logic.
 */
public interface PathNormalizationStrategy {

    /**
     * Normalizes a path for comparison/deduplication purposes.
     * 
     * @param path
     *            the original path
     * @return the normalized path used for equality checks
     */
    String normalizePath(String path);
}
