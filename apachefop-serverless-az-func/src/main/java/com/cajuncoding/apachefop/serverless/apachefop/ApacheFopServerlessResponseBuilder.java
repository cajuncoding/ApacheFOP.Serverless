package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessHeaders;
import com.cajuncoding.apachefop.serverless.http.HttpEncodings;
import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.apache.fop.apps.MimeConstants;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;

public class ApacheFopServerlessResponseBuilder {
    private final SimpleDateFormat dateFormatW3C = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

    public ApacheFopServerlessResponseBuilder() {
    }

    public HttpResponseMessage BuildBadXslFoBodyResponse(
            HttpRequestMessage<Optional<String>> request
    ) {
        var response = request
                .createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("A valid XSL-FO body content must be specified.")
                .build();

        return response;
    }

    public HttpResponseMessage BuildPdfResponse(
            HttpRequestMessage<Optional<String>> request,
            ApacheFopRenderResult pdfRenderResult,
            ApacheFopServerlessConfig config
    ) {
        //Get the results of Apache FOP processing...
        byte[] fopPdfBytes = pdfRenderResult.getPdfBytes();
        String eventLogText = config.isDebuggingEnabled()
                ? pdfRenderResult.getEventsLogAsHeaderValue()
                : "Disabled by AzureFunctions 'DebuggingEnabled' configuration Setting.";

        //Lets create a unique filename -- because that's helpful to the client...
        Calendar cal = Calendar.getInstance();
        String fileName = dateFormatW3C.format(cal.getTime()) + "_RenderedPdf.pdf";
        String contentEncoding = config.isGzipEnabled()
                ? HttpEncodings.GZIP_ENCODING
                : HttpEncodings.IDENTITY_ENCODING;

        //Build the Http Response for the Client!
        HttpResponseMessage response = request
                .createResponseBuilder(HttpStatus.OK)
                .body(fopPdfBytes)
                .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PDF)
                .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(fopPdfBytes.length))
                .header(HttpHeaders.CONTENT_DISPOSITION, MessageFormat.format("inline; filename=\"{0}\"", fileName))
                .header(ApacheFopServerlessHeaders.APACHEFOP_SERVERLESS_EVENTLOG, eventLogText)
                //If GZIP is enabled then specify the proper encoding in the HttpResponse!
                .header(HttpHeaders.CONTENT_ENCODING, contentEncoding)
                .build();

        return response;
    }

    public HttpResponseMessage BuildEventLogDumpResponse(
            HttpRequestMessage<Optional<String>> request,
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
}
