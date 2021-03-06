package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderer;
import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopServerlessResponseBuilder;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class ApacheFopFunction {
    /**
     * This function listens at endpoint "/api/apache-fop/xslfo". Two ways to invoke it using "curl" command in bash:
     */
    @FunctionName("ApacheFOP")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/xslfo", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        var logger =  context.getLogger();
        logger.info("ApacheFOP.Serverless HTTP trigger processing a request...");

        //Read the Configuration from AzureFunctions (environment variables)
        var config = new ApacheFopServerlessConfig(request);

        //Create the Response Builder to handle the various responses we support.
        var responseBuilder = new ApacheFopServerlessResponseBuilder();

        // Safely parse & validate the body content parameter as the XSL-FO source...
        Optional<String> bodyContent = request.getBody();
        String xslFOBodyContent = bodyContent.isPresent() ? bodyContent.get().trim() : "";
        if (StringUtils.isBlank(xslFOBodyContent))
        {
            logger.info(" - [BAD_REQUEST - 400] No XSL-FO body content was specified");
            return responseBuilder.BuildBadXslFoBodyResponse(request);
        }

        logger.info(MessageFormat.format(" - XSL-FO Payload [Length={0}]", xslFOBodyContent.length()));

        //Now we process the XSL-FO source...
        try {
            context.getLogger().info(" - Executing Transformation with Apache FOP...");

            //Log the Full XSL-FO Payload from the Request if Debugging is enabled...
            if(config.isDebuggingEnabled()) {
                logger.info("[DEBUG] XSL-FO Payload Received:".concat(System.lineSeparator()).concat(xslFOBodyContent));
            }

            //Initialize the ApacheFopRenderer (potentially optimized with less logging.
            //NOTE: If used, the Logger must be the instance injected into the Azure Function!
            ApacheFopRenderer fopHelper = config.isApacheFopLoggingEnabled()
                ? new ApacheFopRenderer(logger)
                : new ApacheFopRenderer();

            //Execute the transformation of the XSL-FO source content to Binary PDF format...
            var pdfRenderResult = fopHelper.renderPdfResult(xslFOBodyContent, config.isGzipEnabled());

            //Render the PDF Response (or EventLog Dump if specified)...
            var response = config.isEventLogDumpModeEnabled()
                    ? responseBuilder.BuildEventLogDumpResponse(request, pdfRenderResult, config)
                    : responseBuilder.BuildPdfResponse(request, pdfRenderResult, config);

            return response;
        }
        catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(ex).build();
        }
    }


}
