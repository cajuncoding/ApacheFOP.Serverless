package com.cajuncoding.apachefop.serverless.utils;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AzureFunctionUtils {

    public static String getBodyContentSafely(String body, ApacheFopServerlessConfig config) throws IOException {
        //If GZIP Support is not Enabled but is used then the Request is invalid and body content
        //  could not be parsed/retrieved so we safely return null.
        if(config.isGzipRequestEnabled() && !config.isGzipRequestSupported()) {
            return null;
        }

        String bodyContent = null;
        if(StringUtils.isNotBlank(body)) {
            //NOTE: For String payloads we Support Base64+GZIP as a compressed option...
            if(config.isBase64RequestEnabled() && config.isGzipRequestEnabled())
            {
                bodyContent = GzipUtils.decompressBase64ToString(body);
            }
            else {
                bodyContent = body;
            }
        }
        return bodyContent;
    }

    public static String getBodyContentSafely(byte[] bodyBytes, ApacheFopServerlessConfig config) throws IOException {
        //If GZIP Support is not Enabled but is used then the Request is invalid and body content
        //  could not be parsed/retrieved so we safely return null.
        if(config.isGzipRequestEnabled() && !config.isGzipRequestSupported()) {
            return null;
        }

        String bodyContent = null;
        if(bodyBytes != null && bodyBytes.length > 0) {
            //NOTE: For raw Byte[] payloads we Support Base64+GZIP or RAW GZIP; because GZIP alone is more optimized,
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
