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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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

    public HttpResponseMessage buildExceptionResponse(Exception ex) {
        String timestamp = OffsetDateTime.now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "<no message>"
                : ex.getMessage();

        StringBuilder body = new StringBuilder(512)
                .append("Timestamp: ").append(timestamp).append(StringUtils.LF)
                .append("Exception: ").append(ex.getClass().getName()).append(StringUtils.LF)
                .append("Message: ").append(message).append(StringUtils.LF);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if(ex instanceof IllegalArgumentException)
            httpStatus = HttpStatus.BAD_REQUEST;

        //ONLY provide Stack Trace help if the error is an internal server error (unexpected exception)!
        if(httpStatus == HttpStatus.INTERNAL_SERVER_ERROR) {
            final int maxFrames = 10;
            StackTraceElement[] trace = ex.getStackTrace();
            int stackTracelimit = Math.min(maxFrames, trace.length);

            body.append("StackTrace (top ").append(stackTracelimit).append("):").append(StringUtils.LF);

            for (int i = 0; i < stackTracelimit; i++) {
                body.append("  at ").append(trace[i]).append(StringUtils.LF);
            }

            if (trace.length > stackTracelimit) {
                body.append("  ... ").append(trace.length - stackTracelimit).append(" more").append(StringUtils.LF);
            }
        }

        return request
                .createResponseBuilder(httpStatus)
                .header(HttpHeaders.CONTENT_TYPE, HttpContentTypes.PLAIN_TEXT_UTF8)
                .body(body.toString())
                .build();
    }

    public HttpResponseMessage buildPdfResponse(
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) throws IOException {

        //Get the results of Apache FOP processing...
        byte[] fopPdfBytes = pdfRenderResult.getPdfBytes();
        String eventLogText = config.isDebuggingEnabled()
                ? pdfRenderResult.getEventsLogAsHeaderValue()
                : "Disabled by AzureFunctions 'DebuggingEnabled' configuration Setting.";

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
