package com.cajuncoding.apachefop.serverless.web;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderResult;
import com.cajuncoding.apachefop.serverless.utils.GzipUtils;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessHeaders;
import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.cajuncoding.apachefop.serverless.utils.TextUtils;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.apache.fop.apps.MimeConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Optional;

public class ApacheFopServerlessResponseBuilder<TRequest> {
    public final String TruncationMarker = " . . . (TRUNCATED!)";

    private HttpRequestMessage<Optional<TRequest>> request;

    public ApacheFopServerlessResponseBuilder(HttpRequestMessage<Optional<TRequest>> azureFunctionRequest) {
        this.request = azureFunctionRequest;
    }

    public HttpResponseMessage BuildBadXslFoBodyResponse() {
        var response = request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("A valid XSL-FO body content must be specified.")
                .build();

        return response;
    }

    public HttpResponseMessage BuildPdfResponse(
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) throws IOException {

        //Get the results of Apache FOP processing...
        byte[] fopPdfBytes = pdfRenderResult.getPdfBytes();
        String eventLogText = config.isDebuggingEnabled()
                ? pdfRenderResult.getEventsLogAsHeaderValue()
                : "Disabled by AzureFunctions 'DebuggingEnabled' configuration Setting.";

        //Lets create a unique filename -- because that's helpful to the client...
        String fileName = TextUtils.getCurrentW3cDateTime().concat("_RenderedPdf.pdf");

        String contentEncoding = config.isGzipResponseEnabled()
                ? HttpEncodings.GZIP_ENCODING
                : HttpEncodings.IDENTITY_ENCODING;

        var eventLogSafeHeaderValue = CreateSafeHeaderValue(eventLogText, config.getMaxHeaderBytesSize());

        //Build the Http Response for the Client!
        HttpResponseMessage response = request
                .createResponseBuilder(HttpStatus.OK)
                .body(fopPdfBytes)
                .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, MessageFormat.format("inline; filename=\"{0}\"", fileName))
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG_ENCODING, eventLogSafeHeaderValue.getEncoding())
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG, eventLogSafeHeaderValue.getValue())
                //If GZIP is enabled then specify the proper encoding in the HttpResponse!
                .header(HttpHeaders.CONTENT_ENCODING, contentEncoding)
                .build();

        return response;
    }

    public HttpResponseMessage BuildEventLogDumpResponse(
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
                .build();

        return response;
    }

    public SafeHeader CreateSafeHeaderValue(String headerValue, int maxHeaderBytesSize) throws IOException {
        String resultEncoding = HttpEncodings.IDENTITY_ENCODING;
        String resultValue = null;

        var headerBytes = headerValue.getBytes(StandardCharsets.UTF_8);
        if(headerBytes.length <= maxHeaderBytesSize) {
            resultValue = headerValue;
        }
        else {
            var compressedValue = GzipUtils.compressToBase64(headerBytes);
            var compressedBytes = compressedValue.getBytes(StandardCharsets.UTF_8);
            if(compressedBytes.length <= maxHeaderBytesSize) {
                resultValue = compressedValue;
                resultEncoding = HttpEncodings.GZIP_ENCODING;
            }
            else {
                //Safely truncate the value to the specified Byte size...
                var truncatedValue = TextUtils.truncateToFitUtf8ByteLength(headerValue, maxHeaderBytesSize);
                //Safely overwrite the last set of characters with the Truncation Marker...
                truncatedValue = truncatedValue
                        .substring(0, truncatedValue.length() - TruncationMarker.length())
                        .concat(TruncationMarker);

                resultValue = truncatedValue;
            }
        }

        return new SafeHeader(resultValue, resultEncoding);
    }
}
