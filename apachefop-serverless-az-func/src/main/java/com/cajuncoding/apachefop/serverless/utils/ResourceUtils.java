package com.cajuncoding.apachefop.serverless.utils;

import com.cajuncoding.apachefop.serverless.KeepWarmFunction;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ResourceUtils {

    public static String LoadResourceAsString(String resource) throws IOException {
        return LoadResourceAsString(resource, ResourceUtils.class.getClassLoader());
    }

    public static String LoadResourceAsString(String resource, ClassLoader resourceClassLoader) throws IOException {
        try (var xslFoStream = resourceClassLoader.getResourceAsStream(resource);) {
            var resourceText = IOUtils.toString(xslFoStream, StandardCharsets.UTF_8);
            return resourceText;
        }
    }
}
