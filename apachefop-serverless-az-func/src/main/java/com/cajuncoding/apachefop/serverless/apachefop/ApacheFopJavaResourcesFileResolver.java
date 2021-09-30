package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.utils.ResourceUtils;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;

public class ApacheFopJavaResourcesFileResolver implements ResourceResolver {

    public static final String FILE_SCHEME = "file";

    private static final ResourceResolver defaultFopResolver = ResourceResolverFactory.createDefaultResourceResolver();

    @Override
    public Resource getResource(URI uri) throws IOException {
        InputStream resourceStream = null;

        //We MUST ONLY attempt to handle FILE requests... any Http/Https requests need to use original/default behaviour!
        if(uri.getScheme().equalsIgnoreCase(FILE_SCHEME)) {
            //Map the requested Uri to the base application path to determine it's relative path as a Resource!
            var requestPath = Paths.get(uri);
            var mappedPath = ResourceUtils.MapServerPath(requestPath);

            //Now with the relative path for our resource we can attempt to retrieve it...
            resourceStream = ResourceUtils.LoadResourceAsStream(mappedPath.toString());
        }

        //If the resource was located then we return it...
        //Otherwise we fallback to default Apache FOP behaviour using the original default Fop Resource Resolver...
        return resourceStream != null
            ? new Resource(resourceStream)
            : defaultFopResolver.getResource(uri);
    }

    @Override
    public OutputStream getOutputStream(URI uri) throws IOException {
        //For Output Streams we simply default to original Apache FOP behaviour using the original default Fop Resource Resolver...
        return defaultFopResolver.getOutputStream(uri);

        //return ResourceUtils.GetClassLoader()
        //        .getResource(uri.toString())
        //        .openConnection()
        //        .getOutputStream();
    }
}
