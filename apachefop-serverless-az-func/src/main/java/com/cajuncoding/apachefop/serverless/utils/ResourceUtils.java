package com.cajuncoding.apachefop.serverless.utils;

import com.cajuncoding.apachefop.serverless.KeepWarmFunction;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceUtils {

    public static String LoadResourceAsString(String resource) throws IOException {
        return LoadResourceAsString(resource, GetClassLoader());
    }

    public static String LoadResourceAsString(String resource, ClassLoader resourceClassLoader) throws IOException {
        var sanitizedResource = SanitizeResourcePath(resource);

        try (var resourceStream = resourceClassLoader.getResourceAsStream(sanitizedResource);) {
            var resourceText = IOUtils.toString(resourceStream, StandardCharsets.UTF_8);
            return resourceText;
        }
    }

    public static InputStream LoadResourceAsStream(String resource) {
        var sanitizedResource = SanitizeResourcePath(resource);
        var resourceStream = GetClassLoader().getResourceAsStream(sanitizedResource);
        return resourceStream;
    }

    public static Path GetBaseMappedPath() {
        var baseAppPath = Paths.get("").toAbsolutePath();
        return baseAppPath;
    }

    public static Path MapServerPath(Path pathToMap) {
        var basePath = GetBaseMappedPath();
        var relativePath = basePath.relativize(pathToMap);
        return relativePath;
    }

    public static ClassLoader GetClassLoader() {
        return ResourceUtils.class.getClassLoader();
    }

    public static String SanitizeResourcePath(String resource) {
        var trimmed = resource.trim();
        var slashCorrected = trimmed.indexOf('\\') < 0 ? trimmed : trimmed.replace('\\', '/');
        return slashCorrected;
    }
}
