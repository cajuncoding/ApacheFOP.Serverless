package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.http.HttpHeaders;
import com.cajuncoding.apachefop.serverless.utils.AzureFunctionUtils;
import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessFunctionExecutor;
import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessResponseBuilder;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.xmlgraphics.util.MimeConstants;

import java.util.Optional;

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
        try {

            var functionExecutor = new ApacheFopServerlessFunctionExecutor();
            return functionExecutor.ExecuteByteArrayRequest(request, context.getLogger());
        }
        catch (Exception ex) {
            var responseBuilder = new ApacheFopServerlessResponseBuilder<byte[]>(request);
            return responseBuilder.buildExceptionResponse(ex);
        }
    }
}
