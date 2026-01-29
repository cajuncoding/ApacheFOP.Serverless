package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessFunctionExecutor;
import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessResponseBuilder;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;
import java.util.logging.Level;

/**
 * Azure Functions with HTTP Trigger.
 */
public class ApacheFopGzipFunction {
    /**
     * This function listens at endpoint "/api/apache-fop/gzip".
     */
    @FunctionName("ApacheFOPGzip")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/gzip", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION, dataType = "binary")
            HttpRequestMessage<Optional<byte[]>> request,
            final ExecutionContext context
    ) {
        var logger = context.getLogger();

        try {
            var functionExecutor = new ApacheFopServerlessFunctionExecutor();
            return functionExecutor.executeByteArrayRequest(request, logger);
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "[ApacheFopGzipFunction] Request Failed due to Error: " + ex.getMessage(), ex);
            var responseBuilder = new ApacheFopServerlessResponseBuilder<byte[]>(request);
            var config = ApacheFopServerlessFunctionExecutor.createConfigFromRequest(request);
            return responseBuilder.buildExceptionResponse(ex, config.isDetailedExceptionResponsesEnabled());
        }
    }
}
