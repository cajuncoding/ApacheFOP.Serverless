package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.utils.ResourceUtils;
import org.apache.commons.io.IOUtils;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ApacheFopJavaResourcesFileResolver implements ResourceResolver {

    public static final String FILE_SCHEME = "file";

    private static final ResourceResolver defaultFopResolver = ResourceResolverFactory.createDefaultResourceResolver();

    //Missing or unavailable data should also be cached for maximum performance, so the cache result is Optional<byte[]>
    // to facilitate cache hits even if the result is "not found" or "no data available"...
    //TODO: Consider using Apache Commons LRU Cache here to mitigate potential cache balloon risks...
    private final Map<String, Optional<byte[]>> javaResourceFileCache = new ConcurrentHashMap<>();

    @Override
    public Resource getResource(URI uri) throws IOException {
        InputStream resourceStream = null;

        //We MUST ONLY attempt to handle FILE requests... any Http/Https requests need to use original/default behavior!
        //NOTE: Scheme of uri may be null so we must handle that case...
        var uriScheme = uri.getScheme();
        if (FILE_SCHEME.equalsIgnoreCase(uriScheme)) {
            resourceStream = tryFindJavaResourceWithCaching(uri);
        }

        //If the resource was located then we return it...
        //Otherwise, we fall back to default Apache FOP behavior using the original default Fop Resource Resolver...
        return resourceStream != null
            ? new Resource(resourceStream)
            : defaultFopResolver.getResource(uri);
    }

    @Override
    public OutputStream getOutputStream(URI uri) throws IOException {
        //For Output Streams we simply default to original Apache FOP behavior using the original default Fop Resource Resolver...
        return defaultFopResolver.getOutputStream(uri);
    }

    //Find the Resource and utilize the internal cache for performance since Embedded Resources can't change without
    //  deployments to update, we can keep these elements (e.g. Fonts) loaded in Memory for performance!
    protected InputStream tryFindJavaResourceWithCaching(URI uri) {
        //Normalize the paths to minimize cache duplication due to path navigation (e.g /../, etc.).
        var requestPath = Paths.get(uri).normalize();

        try {
            var mappedPath = ResourceUtils.MapServerPath(requestPath);

            //If the path can't be safely mapped we return null immediately...
            //NOTE: This won't be cached, but it helps minimize cache size (reducing duplication risk) and improves caching of valid paths...
            //NOTE: So that we don't black hole this we attempt to log to the system out...
            if (mappedPath == null) {
                System.out.print("[ApacheFopJavaResourcesFileResolver] Java Resource Path could not be resolved for: " + uri);
                return null;
            }

            //Compute the cache key AFTER mapping, so multiple equivalent URIs that resolve to the same resource will return the same cached result!
            var mappedPathCacheKey = mappedPath.toString();

            //Use cached results from our Concurrent HashMap cached data if possible!
            //NOTE: To ensure missing items are still cached we return
            var cacheResult = javaResourceFileCache.computeIfAbsent(mappedPathCacheKey, key -> {
                //Map the requested Uri to the base application path to determine its relative path as a Resource!
                try {
                    //Now with the relative path for our resource we can attempt to retrieve it...
                    //NOTE: IOUtils.toByteArray() does not automatically close the stream so we must safely handle that!
                    try (var resultStream = ResourceUtils.loadResourceAsStream(mappedPathCacheKey)) {
                        return resultStream != null
                                ? Optional.of(IOUtils.toByteArray(resultStream))
                                : Optional.empty();
                    }
                }
                catch (Exception e) {
                    //e.printStackTrace();
                    //NOTE: So that we don't black hole this we attempt to log to the system out...
                    System.out.print("[ApacheFopJavaResourcesFileResolver] EXCEPTION occurred trying to resolve Java Resource for [" + uri.toString() + "]: " + e.getMessage());
                    return Optional.empty();
                }
            });

            return cacheResult
                    .map(ByteArrayInputStream::new)
                    .orElse(null);
        }
        catch (Exception exc) {
            // Safety net for Paths.get(URI) / mapping anomalies
            System.out.print("[ApacheFopJavaResourcesFileResolver] EXCEPTION mapping Java Resource for [" + uri + "]: " + exc.getMessage());
            return null;
        }
    }
}
