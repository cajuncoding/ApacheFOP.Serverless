package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.utils.GzipUtils;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessHeaders;
import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.cajuncoding.apachefop.serverless.utils.StringUtils;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.apache.fop.apps.MimeConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;

public class ApacheFopServerlessResponseBuilder<TRequest> {
    private final SimpleDateFormat dateFormatW3C = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
    private final String TruncationMarker = " . . . (TRUNCATED!)";

    public ApacheFopServerlessResponseBuilder() {
    }

    public HttpResponseMessage BuildBadXslFoBodyResponse(
            HttpRequestMessage<Optional<TRequest>> request
    ) {
        var response = request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("A valid XSL-FO body content must be specified.")
                .build();

        return response;
    }

    public HttpResponseMessage BuildPdfResponse(
            HttpRequestMessage<Optional<TRequest>> request,
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) throws IOException {

        //Get the results of Apache FOP processing...
        byte[] fopPdfBytes = pdfRenderResult.getPdfBytes();
        String eventLogText = config.isDebuggingEnabled()
                ? pdfRenderResult.getEventsLogAsHeaderValue()
                : "Disabled by AzureFunctions 'DebuggingEnabled' configuration Setting.";

        //Lets create a unique filename -- because that's helpful to the client...
        Calendar cal = Calendar.getInstance();
        String fileName = dateFormatW3C.format(cal.getTime()) + "_RenderedPdf.pdf";
        String contentEncoding = config.isGzipResponseEnabled()
                ? HttpEncodings.GZIP_ENCODING
                : HttpEncodings.IDENTITY_ENCODING;

        String eventLogHeaderValue = config.isGzipResponseEnabled()
                ? GzipUtils.compressToBase64(eventLogText)
                : eventLogText;

        //Build the Http Response for the Client!
        HttpResponseMessage response = request
                .createResponseBuilder(HttpStatus.OK)
                .body(fopPdfBytes)
                .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PDF)
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(fopPdfBytes.length))
                .header(HttpHeaders.CONTENT_DISPOSITION, MessageFormat.format("inline; filename=\"{0}\"", fileName))
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG_ENCODING, contentEncoding)
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG, eventLogHeaderValue)
                //If GZIP is enabled then specify the proper encoding in the HttpResponse!
                .header(HttpHeaders.CONTENT_ENCODING, contentEncoding)
                .build();

        return response;
    }

    public HttpResponseMessage BuildEventLogDumpResponse(
            HttpRequestMessage<Optional<TRequest>> request,
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) {
        //Get the results of Apache FOP processing...
        String eventLogText = pdfRenderResult.getEventsLogAsBodyValue();

        //Build the Http Response for the Client!
        HttpResponseMessage response = request
                .createResponseBuilder(HttpStatus.OK)
                .body(eventLogText)
                .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PLAIN_TEXT)
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(eventLogText.length()))
                .build();

        return response;
    }

    public String CreateSafeHeaderValue(String headerValue, int maxHeaderBytesSize) throws IOException {
        var headerBytes = headerValue.getBytes(StandardCharsets.UTF_8);
        if(headerBytes.length <= maxHeaderBytesSize) {
            return headerValue;
        }
        else {
            var compressedValue = GzipUtils.compressToBase64(headerBytes);
            var compressedBytes = compressedValue.getBytes(StandardCharsets.UTF_8);
            if(compressedBytes.length <= maxHeaderBytesSize) {
                return compressedValue;
            }
            else {
                //Safely truncate the value to the specified Byte size...
                var truncatedValue = StringUtils.truncateToFitUtf8ByteLength(headerValue, maxHeaderBytesSize);
                //Safely overwrite the last set of characters with the Truncation Marker...
                truncatedValue = truncatedValue
                        .substring(0, truncatedValue.length() - TruncationMarker.length())
                        .concat(truncatedValue);

                return truncatedValue;
            }
        }

    }
}
