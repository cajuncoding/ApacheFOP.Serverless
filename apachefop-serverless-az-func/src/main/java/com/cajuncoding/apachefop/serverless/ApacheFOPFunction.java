package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.helpers.ApacheFopHelper;
import com.cajuncoding.apachefop.serverless.helpers.HttpEncodings;
import com.cajuncoding.apachefop.serverless.helpers.HttpHeaders;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.MimeConstants;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class ApacheFOPFunction {
    private final SimpleDateFormat dateFormatW3C = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

    /**
     * This function listens at endpoint "/api/apache-fop/xslfo". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("ApacheFOP")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/xslfo", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {

        context.getLogger().info("ApacheFOP Serverless HTTP trigger processing a request...");

        // Safely parse & validate the body content parameter as the XSL-FO source...
        Optional<String> bodyContent = request.getBody();
        String xslFOBodyContent = bodyContent.isPresent() ? bodyContent.get().trim() : "";
        if (StringUtils.isBlank(xslFOBodyContent))
        {
            context.getLogger().info(" - [BAD_REQUEST - 400] No XSL-FO body content was specified");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("A valid XSL-FO body content must be specified.").build();
        }

        String acceptEncodingHeader = request.getHeaders().getOrDefault(HttpHeaders.ACCEPT_ENCODING, "");
        boolean gzipEnabled = StringUtils.containsIgnoreCase(acceptEncodingHeader, HttpEncodings.GZIP_ENCODING);

        //Now we process the XSL-FO source...
        try {
            context.getLogger().info(" - Executing Transformation with Apache FOP...");

            //Execute the transformation of the XSL-FO source content to Binary PDF format...
            ApacheFopHelper fopHelper = new ApacheFopHelper();
            byte[] fopPdfBytes = fopHelper.renderPdfBytes(xslFOBodyContent, gzipEnabled);

            //Lets create a unique filename -- because that's helpful to the client...
            Calendar cal = Calendar.getInstance();
            String fileName = dateFormatW3C.format(cal.getTime()) + "_RenderedPdf.pdf";

            //Build the Http Response for the Client!
            HttpResponseMessage resp = request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(fopPdfBytes)
                    .header(HttpHeaders.CONTENT_TYPE, MimeConstants.MIME_PDF)
                    .header(HttpHeaders.CONTENT_LENGTH, Integer.toString(fopPdfBytes.length))
                    .header(HttpHeaders.CONTENT_DISPOSITION, MessageFormat.format("inline; filename=\"{0}\"", fileName))
                    //If GZIP is enabled then specify the proper encoding in the HttpResponse!
                    .header(HttpHeaders.CONTENT_ENCODING, gzipEnabled ? HttpEncodings.GZIP_ENCODING : HttpEncodings.IDENTITY_ENCODING)
                    .build();

            return resp;
        }
        catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(ex).build();
        }
    }
}
