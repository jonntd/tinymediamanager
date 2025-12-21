package org.tinymediamanager.core.tvshow;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * Strategy to handle WebDAV specific path normalization including decoding and NFC normalization.
 */
public class WebDavPathStrategy implements PathNormalizationStrategy {

    private static final WebDavPathStrategy INSTANCE = new WebDavPathStrategy();

    private WebDavPathStrategy() {
    }

    public static WebDavPathStrategy getInstance() {
        return INSTANCE;
    }

    @Override
    public String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return path;
        }

        String normalized = path;

        // Delegate to helper if it's a WebDAV path
        if (WebDavDataSourceHelper.isWebDavPath(normalized)) {
            normalized = WebDavDataSourceHelper.decodeWebDavPath(normalized);
            normalized = WebDavDataSourceHelper.normalizeToNFC(normalized);
        }

        // Standard trailing slash handling
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
