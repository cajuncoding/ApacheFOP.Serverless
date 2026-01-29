package com.cajuncoding.apachefop.serverless.web;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderResult;
import com.cajuncoding.apachefop.serverless.http.HttpContentTypes;
import com.cajuncoding.apachefop.serverless.utils.GzipUtils;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessHeaders;
import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.cajuncoding.apachefop.serverless.utils.TextUtils;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.MimeConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ApacheFopServerlessResponseBuilder<TRequest> {
    public static final String TRUNCATION_MARKER = " . . . (TRUNCATED!)";

    private final HttpRequestMessage<Optional<TRequest>> request;

    public ApacheFopServerlessResponseBuilder(HttpRequestMessage<Optional<TRequest>> azureFunctionRequest) {
        this.request = azureFunctionRequest;
    }

    public HttpResponseMessage buildBadXslFoBodyResponse() {
        return request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, HttpContentTypes.PLAIN_TEXT_UTF8)
                .body("A valid XSL-FO body content must be specified.")
                .build();
    }

    public HttpResponseMessage buildExceptionResponse(Exception ex, boolean includeExceptionDetails) {
        String timestampUtc = Instant.now().toString();

        String exceptionType = ex.getClass().getSimpleName();
        if(exceptionType.isBlank())
            exceptionType = "UnknownException";

        String className = ex.getClass().getName();

        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? StringUtils.EMPTY
                : ex.getMessage();

        String detailMessage = MessageFormat.format("[{0}] {1}", exceptionType, message);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if(ex instanceof IllegalArgumentException)
            httpStatus = HttpStatus.BAD_REQUEST;

        //NOTE: WE use LinkedHashMap to preserve the ordering of the properties in the serialized Json output...
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("httpStatusCode", httpStatus.value());
        responseMap.put("httpStatus", httpStatus.name());
        responseMap.put("timestampUtc", timestampUtc);
        responseMap.put("exceptionType", exceptionType);
        if(includeExceptionDetails) {
            responseMap.put("className", className);
        }
        responseMap.put("message", message);
        responseMap.put("detailMessage", detailMessage);

        //ONLY provide Stack Trace help if the error is an internal server error (unexpected exception)!
        if(httpStatus == HttpStatus.INTERNAL_SERVER_ERROR && includeExceptionDetails) {
            final int maxFrames = 10;
            StackTraceElement[] trace = ex.getStackTrace();
            int stackTraceLimit = Math.min(maxFrames, trace.length);

            StringBuilder stackTraceBuilder = new StringBuilder();
            stackTraceBuilder.append("StackTrace (top ").append(stackTraceLimit).append("):").append(StringUtils.LF);

            for (int i = 0; i < stackTraceLimit; i++) {
                stackTraceBuilder.append("  at ").append(trace[i]).append(StringUtils.LF);
            }

            if (trace.length > stackTraceLimit) {
                stackTraceBuilder.append("  ... ").append(trace.length - stackTraceLimit).append(" more").append(StringUtils.LF);
            }

            responseMap.put("stackTrace", stackTraceBuilder.toString());
        }

        return request
                .createResponseBuilder(httpStatus)
                .header(HttpHeaders.CONTENT_TYPE, HttpContentTypes.JSON)
                .body(responseMap)
                .build();
    }

    public HttpResponseMessage buildPdfResponse(
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) throws IOException {

        //Get the results of Apache FOP processing...
        byte[] fopPdfBytes = pdfRenderResult.getPdfBytes();
        String eventLogText = config.isXslFoDebuggingEnabled()
                ? pdfRenderResult.getEventsLogAsHeaderValue()
                : "Disabled by AzureFunctions 'DebuggingEnabled' configuration setting.";

        //Let's create a unique filename -- because that's helpful to the client...
        String fileName = TextUtils.getCurrentW3cDateTime().concat("_RenderedPdf.pdf");

        String contentEncoding = config.isGzipResponseEnabled()
                ? HttpEncodings.GZIP_ENCODING
                : HttpEncodings.IDENTITY_ENCODING;

        var eventLogSafeHeaderValue = createSafeHeaderValue(eventLogText, config.getMaxHeaderBytesSize());

        //Build the Http Response for the Client!
        //If GZIP is enabled then specify the proper encoding in the HttpResponse!
        return request
                .createResponseBuilder(HttpStatus.OK)
                .body(fopPdfBytes)
                .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, MessageFormat.format("inline; filename=\"{0}\"", fileName))
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG_ENCODING, eventLogSafeHeaderValue.getEncoding())
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG, eventLogSafeHeaderValue.getValue())
                //If GZIP is enabled then specify the proper encoding in the HttpResponse!
                .header(HttpHeaders.CONTENT_ENCODING, contentEncoding)
                .build();
    }

    public HttpResponseMessage buildEventLogDumpResponse(ApacheFopRenderResult pdfRenderResult) {
        //Get the results of Apache FOP processing...
        String eventLogText = pdfRenderResult.getEventsLogAsBodyValue();

        //Build the Http Response for the Client!
        return request
                .createResponseBuilder(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, HttpContentTypes.PLAIN_TEXT_UTF8)
                .body(eventLogText)
                .build();
    }

    public SafeHeader createSafeHeaderValue(String headerValue, int maxHeaderBytesSize) throws IOException {
        if(headerValue == null)
            return new SafeHeader(StringUtils.EMPTY, HttpEncodings.IDENTITY_ENCODING);

        String resultEncoding = HttpEncodings.IDENTITY_ENCODING;
        String resultValue = TextUtils.sanitizeForHeader(headerValue);
        var headerBytes = resultValue.getBytes(StandardCharsets.UTF_8);

        //Return the value if no truncation is needed...
        if(headerBytes.length <= maxHeaderBytesSize)
            return new SafeHeader(resultValue, resultEncoding);

        //If it's too long then attempt to compress the full value...
        var compressedValue = GzipUtils.compressToBase64(headerBytes);
        var compressedBytes = compressedValue.getBytes(StandardCharsets.UTF_8);
        if(compressedBytes.length <= maxHeaderBytesSize) {
            return new SafeHeader(compressedValue, HttpEncodings.GZIP_ENCODING);
        }

        //Finally, as a fallback we safely truncate the value to the specified Byte size...
        resultValue = appendMarkerToFitUtf8(resultValue, TRUNCATION_MARKER, maxHeaderBytesSize);
        return new SafeHeader(resultValue, resultEncoding);
    }

    private static String appendMarkerToFitUtf8(String base, String marker, int maxBytes) throws IOException {
        byte[] markerBytes = marker.getBytes(StandardCharsets.UTF_8);
        int bytesBudgetSize = Math.max(0, maxBytes - markerBytes.length);
        String truncatedValue = TextUtils.truncateToFitUtf8ByteLength(base, bytesBudgetSize);

        //If base is already <= bytesBudgetSize, we still want to signal truncation because we’re in that code path...
        String truncatedValueWithMarker = truncatedValue + marker;

        //Defensively ensure final <= maxBytes
        while (truncatedValueWithMarker.getBytes(StandardCharsets.UTF_8).length > maxBytes && !truncatedValue.isEmpty()) {
            truncatedValue = truncatedValue.substring(0, truncatedValue.length() - 1);
            truncatedValueWithMarker = truncatedValue + marker;
        }
        return truncatedValueWithMarker;
    }
}
