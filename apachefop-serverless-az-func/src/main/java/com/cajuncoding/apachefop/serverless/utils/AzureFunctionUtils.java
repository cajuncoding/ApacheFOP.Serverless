package com.cajuncoding.apachefop.serverless.utils;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.microsoft.azure.functions.HttpRequestMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AzureFunctionUtils {

    public static String getBodyContentSafely(HttpRequestMessage<Optional<byte[]>> request, ApacheFopServerlessConfig config) throws IOException {
        //If GZIP Support is not Enabled but is used then the Request is invalid and body content
        //  could not be parsed/retrieved so we safely return null.
        if(config.isGzipRequestEnabled() && !config.isGzipRequestSupported()) {
            //throw new UnsupportedEncodingException("Gzip Encoded Requests are disabled.");
            return null;
        }

        String bodyContent = null;
        var body = request.getBody();
        if(body.isPresent()) {
            var bodyBytes = body.get();

            //NOTE: We Support Base64+GZIP or RAW GZIP; because GZIP alone is more optimized,
            //      but Base64 is easier to test via Postman!
            if(config.isBase64RequestEnabled() && config.isGzipRequestEnabled())
            {
                var bodyText = new String(bodyBytes, StandardCharsets.UTF_8);
                bodyContent = GzipUtils.decompressBase64ToString(bodyText);
            }
            else if (config.isGzipRequestEnabled()) {
                bodyContent = GzipUtils.decompressToString(bodyBytes);
            }
            else {
                bodyContent = new String(bodyBytes, StandardCharsets.UTF_8);
            }
        }
        return bodyContent;
    }
}
