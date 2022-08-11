package com.cajuncoding.apachefop.serverless.config;

import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class ApacheFopServerlessConfig {
    //Request Configuration Parameters...
    private boolean isGzipRequestEnabled = false;
    private boolean isGzipResponseEnabled = false;
    private boolean isBase64RequestEnabled = false;
//    private String apacheFopServerlessContentType = StringUtils.EMPTY;

    //Azure Function Configuration Settings...
    private boolean debuggingEnabled = false;
    private boolean apacheFopLoggingEnabled  = true;
    private boolean isGzipRequestSupported = true;
    private boolean eventLogDumpModeEnabled = false;
    private boolean accessibilityPdfRenderingEnabled = false;
    private int maxHeaderBytesSize = 4096;

    public ApacheFopServerlessConfig() {
        this(null, null);
    }

    public ApacheFopServerlessConfig(Map<String, String> requestHeaders, Map<String, String> requestQueryParams) {
        readEnvironmentConfig();
        readRequestHeadersConfig(requestHeaders);
        readRequestQueryParamsConfig(requestQueryParams);
    }

    protected void readEnvironmentConfig() {
        //Enable Debugging by default which enables the Apache FOP Event Log to be returned via Response Header, etc.
        //NOTE: this can be disabled to save unnecessary logging, returning of Debug Headers, etc.
        this.debuggingEnabled = getConfigAsBooleanOrDefault("DebuggingEnabled", true);

        //Enable ApacheFop logging to AppInsights by default to ensure detailed logs; and AppInsights
        //  provides performance batching of logs to minimize impact.
        this.apacheFopLoggingEnabled = getConfigAsBooleanOrDefault("ApacheFopLoggingEnabled", true);

        //Enable support for Gzip Requests for performance improvements; this option provides a way to disable support
        //  in use-cases or environments where GZIP bombing (e.g. DoS attack) is a risk.
        this.isGzipRequestSupported = getConfigAsBooleanOrDefault("GzipRequestSupportEnabled", true);

        //Despite being noted in the Apache FOP Accessibility specific documentation the <accessibility> xml configuration option
        //  does not work; nor is it not listed in the generalized configuration page.  However it can be set programmatically
        //  therefore we provide this configuration option via ApacheFOP.Serverless configuration in Azure Functions.
        //NOTE: Generally this configuration is set in accordance with the <pdf-ua-mode> configuration element of the PDF Renderer
        //      which is controlled in the Apache FOP Configuration xml.
        //NOTE: If the accessibility configuration is used (e.g. <pdf-ua-mode>PDF/UA-1</pdf-ua-mode>) is used then this
        //      configuration must be enabled or the PDF rendering will return an error stating that accessibility must be enabled!
        this.accessibilityPdfRenderingEnabled = getConfigAsBooleanOrDefault("AccessibilityEnabled", false);
    }

    protected void readRequestHeadersConfig(Map<String, String> headers) {
        if(headers == null || headers.isEmpty())
            return;

        //Determine if the current request Content Encodings specified contain GZIP (as that's all that is currently supported).
        //NOTE: Headers are LowerCased in the returned Map!
        String contentEncodingHeader = headers.getOrDefault(HttpHeaders.CONTENT_ENCODING_LOWERCASE, null);
        this.isGzipRequestEnabled = StringUtils.containsIgnoreCase(contentEncodingHeader, HttpEncodings.GZIP_ENCODING);
        this.isBase64RequestEnabled = StringUtils.containsIgnoreCase(contentEncodingHeader, HttpEncodings.BASE64_ENCODING);

        //Determine if the Acceptable Encodings specified contain GZIP (as that's all that is currently supported).
        //NOTE: Headers are LowerCased in the returned Map!
        String acceptEncodingHeader = headers.getOrDefault(HttpHeaders.ACCEPT_ENCODING_LOWERCASE, null);
        this.isGzipResponseEnabled = StringUtils.containsIgnoreCase(acceptEncodingHeader, HttpEncodings.GZIP_ENCODING);

//        //Get the Custom ContentType specified for reference
//        this.apacheFopServerlessContentType = headers.getOrDefault(
//            ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_CONTENT_TYPE_LOWERCASE,
//            StringUtils.EMPTY
//        );
    }

    protected void readRequestQueryParamsConfig(Map<String, String> queryParams) {
        if(queryParams == null || queryParams.isEmpty())
            return;

        //Determine if Event Log Dump mode is enabled (vs PDF Binary return).
        this.eventLogDumpModeEnabled = BooleanUtils.toBoolean(
            queryParams.getOrDefault(ApacheFopServerlessQueryParams.EventLogDump, null)
        );

    }


    //****************************************************************
    //Azure Function Configuration Settings...
    //****************************************************************
    public boolean isApacheFopLoggingEnabled() {
        return apacheFopLoggingEnabled;
    }

    public boolean isDebuggingEnabled() {
        return debuggingEnabled;
    }

    public boolean isGzipRequestSupported() { return isGzipRequestSupported; }

    public boolean isAccessibilityPdfRenderingEnabled() { return accessibilityPdfRenderingEnabled; }

//    public String getApacheFopServerlessContentType() { return apacheFopServerlessContentType; }

    public int getMaxHeaderBytesSize() { return maxHeaderBytesSize; }

    //****************************************************************
    //Azure Function Configuration Settings...
    //****************************************************************
    public boolean isGzipRequestEnabled() { return isGzipRequestEnabled; }

    public boolean isBase64RequestEnabled() { return isBase64RequestEnabled; }

    public boolean isGzipResponseEnabled() {
        return isGzipResponseEnabled;
    }

    public boolean isEventLogDumpModeEnabled() {
        return eventLogDumpModeEnabled;
    }

    //****************************************************************
    //Helper methods...
    //****************************************************************
    private String getConfigValue(String name) {
        String value = System.getenv(name);
        return value;
    }

    // private String getConfigValueOrDefault(String name, String defaultValue) {
    //     String value = getConfigValue(name);
    //     return StringUtils.isBlank(value)
    //         ? defaultValue
    //         : value;
    // }

    private boolean getConfigAsBooleanOrDefault(String name, boolean defaultValue) {
        String value = getConfigValue(name);
        return StringUtils.isBlank(value)
            ? defaultValue
            : BooleanUtils.toBoolean(value);
    }
}
