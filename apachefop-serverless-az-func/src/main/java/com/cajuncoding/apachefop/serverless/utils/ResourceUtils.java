package com.cajuncoding.apachefop.serverless.utils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceUtils {

    public static String loadResourceAsString(String resource) throws IOException {
        return loadResourceAsString(resource, getClassLoader());
    }

    public static String loadResourceAsString(String resource, ClassLoader resourceClassLoader) throws IOException {
        var sanitizedResource = sanitizeResourcePath(resource);

        try (var resourceStream = resourceClassLoader.getResourceAsStream(sanitizedResource);) {
            var resourceText = IOUtils.toString(resourceStream, StandardCharsets.UTF_8);
            return resourceText;
        }
    }

    public static InputStream loadResourceAsStream(String resource) {
        var sanitizedResource = sanitizeResourcePath(resource);
        var resourceStream = getClassLoader().getResourceAsStream(sanitizedResource);
        return resourceStream;
    }

    public static Path getBaseMappedPath() {
        var baseAppPath = Paths.get("").toAbsolutePath();
        return baseAppPath;
    }

    public static Path MapServerPath(Path pathToMap) {
        var basePath = getBaseMappedPath();
        
        //Guard to ensure that the path is a valid path inside-of/within the current Base Path,
        //  otherwise path.relativize() will throw Exceptions.
        //NOTE: the Path.startsWith() will provide a safe/case-insensitive test...
        if(!pathToMap.startsWith(basePath))
            return null;

        var relativePath = basePath.relativize(pathToMap);
        return relativePath;
    }

    public static ClassLoader getClassLoader() {
        return ResourceUtils.class.getClassLoader();
    }

    public static String sanitizeResourcePath(String resource) {
        var trimmed = resource.trim();
        var slashCorrected = trimmed.indexOf('\\') < 0 ? trimmed : trimmed.replace('\\', '/');
        return slashCorrected;
    }
}
