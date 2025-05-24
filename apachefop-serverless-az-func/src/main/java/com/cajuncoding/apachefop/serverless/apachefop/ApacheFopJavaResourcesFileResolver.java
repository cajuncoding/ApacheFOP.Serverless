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
import java.util.concurrent.ConcurrentHashMap;

public class ApacheFopJavaResourcesFileResolver implements ResourceResolver {

    public static final String FILE_SCHEME = "file";

    private static final ResourceResolver defaultFopResolver = ResourceResolverFactory.createDefaultResourceResolver();

    private Map<URI, byte[]> javaResourceFileCache = new ConcurrentHashMap<>();

    @Override
    public Resource getResource(URI uri) throws IOException {
        InputStream resourceStream = null;

        //We MUST ONLY attempt to handle FILE requests... any Http/Https requests need to use original/default behavior!
        if(uri.getScheme().equalsIgnoreCase(FILE_SCHEME)) {
            resourceStream = tryFindJavaResourceWithCaching(uri);
        }

        //If the resource was located then we return it...
        //Otherwise we fallback to default Apache FOP behavior using the original default Fop Resource Resolver...
        return resourceStream != null
            ? new Resource(resourceStream)
            : defaultFopResolver.getResource(uri);
    }

    @Override
    public OutputStream getOutputStream(URI uri) throws IOException {
        //For Output Streams we simply default to original Apache FOP behavior using the original default Fop Resource Resolver...
        return defaultFopResolver.getOutputStream(uri);

        //return ResourceUtils.GetClassLoader()
        //        .getResource(uri.toString())
        //        .openConnection()
        //        .getOutputStream();
    }

    //Find the Resource and utilize the internal cache for performance since Embedded Resources can't change without
    //  deployments to update, we can keep these elements (e.g. Fonts) loaded in Memory for performance!
    protected InputStream tryFindJavaResourceWithCaching(URI uri) {
        var requestPath = Paths.get(uri);

        //Use cached results from our Concurrent HashMap cached data if possible!
        var resourceBytes = javaResourceFileCache.computeIfAbsent(uri, key -> {
            //Map the requested Uri to the base application path to determine its relative path as a Resource!
            try {
                var mappedPath = ResourceUtils.MapServerPath(requestPath);
                
                //If the path can't be safely mapped we return null to be cached so future lookups are not attempted.
                //NOTE: So that we don't black hole this we attempt to log to the system out...
                if(mappedPath == null) {
                    System.out.print("Java Resource Path could not be resolved for: " + uri.toString());
                    return null;
                }

                //Now with the relative path for our resource we can attempt to retrieve it...
                var resultStream = ResourceUtils.loadResourceAsStream(mappedPath.toString());
                
                return resultStream != null 
                    ? IOUtils.toByteArray(resultStream) 
                    : null;                
            } 
            catch (Exception e) {
                //e.printStackTrace();
                //NOTE: So that we don't black hole this we attempt to log to the system out...
                System.out.print("EXCEPTION occurred trying to resolve Java Resource for [" + uri.toString() + "]: " + e.getMessage());
                return null;
            }
        });

        return resourceBytes != null
                ? new ByteArrayInputStream(resourceBytes)
                : null;
    }
}
