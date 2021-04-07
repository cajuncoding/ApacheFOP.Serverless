package com.cajuncoding.apachefop.serverless.config;

import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.microsoft.azure.functions.HttpRequestMessage;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class ApacheFopServerlessConfig<T> {
    //Request Configuration Parameters...
    private boolean isGzipRequestEnabled = false;
    private boolean isGzipResponseEnabled = false;
    private boolean isBase64RequestEnabled = false;

    //Azure Function Configuration Settings...
    private boolean debuggingEnabled = false;
    private boolean apacheFopLoggingEnabled  = true;
    private boolean isGzipRequestSupported = true;
    private boolean eventLogDumpModeEnabled = false;

    public ApacheFopServerlessConfig(HttpRequestMessage<Optional<T>> request) {
        ReadEnvironmentConfig();
        ReadRequestConfig(request);
    }

    private void ReadEnvironmentConfig() {
        //For performance we default Debugging to being disabled;
        // this saves unnecessary logging, returning of Debug Headers, etc.)
        this.debuggingEnabled = getConfigAsBooleanOrDefault("DebuggingEnabled", false);

        //Enable ApacheFop logging to AppInsights by default to ensure detailed logs; and AppInsights
        //  provides performance batching of logs to minimize impact.
        this.apacheFopLoggingEnabled = getConfigAsBooleanOrDefault("ApacheFopLoggingEnabled", true);

        //Enable support for Gzip Requests for performance improvements; this option provides a way to disable support
        //  in use-cases or environments where GZIP bombing (e.g. DoS attack) is a risk.
        this.isGzipRequestSupported =  getConfigAsBooleanOrDefault("GzipRequestSupportEnabled", true);
    }

    private void ReadRequestConfig(HttpRequestMessage<Optional<T>> request) {
        var headers = request.getHeaders();

        //Determine if the current request Content Encodings specified contain GZIP (as that's all that is currently supported).
        //NOTE: Headers are LowerCased in the returned Map!
        String contentEncodingHeader = headers.getOrDefault(HttpHeaders.CONTENT_ENCODING_LOWERCASE, null);
        this.isGzipRequestEnabled = StringUtils.containsIgnoreCase(contentEncodingHeader, HttpEncodings.GZIP_ENCODING);
        this.isBase64RequestEnabled = StringUtils.containsIgnoreCase(contentEncodingHeader, HttpEncodings.BASE64_ENCODING);

        //Determine if the Acceptable Encodings specified contain GZIP (as that's all that is currently supported).
        //NOTE: Headers are LowerCased in the returned Map!
        String acceptEncodingHeader = headers.getOrDefault(HttpHeaders.ACCEPT_ENCODING_LOWERCASE, null);
        this.isGzipResponseEnabled = StringUtils.containsIgnoreCase(acceptEncodingHeader, HttpEncodings.GZIP_ENCODING);

        //Determine if Event Log Dump mode is enabled (vs PDF Binary return).
        var queryParams = request.getQueryParameters();
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

    private String getConfigValueOrDefault(String name, String defaultValue) {
        String value = getConfigValue(name);
        return StringUtils.isBlank(value)
            ? defaultValue
            : value;
    }

    private boolean getConfigAsBooleanOrDefault(String name, boolean defaultValue) {
        String value = getConfigValue(name);
        return StringUtils.isBlank(value)
            ? defaultValue
            : BooleanUtils.toBoolean(value);
    }
}
