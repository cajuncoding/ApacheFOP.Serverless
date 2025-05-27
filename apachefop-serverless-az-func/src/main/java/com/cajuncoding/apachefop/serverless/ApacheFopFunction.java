package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessFunctionExecutor;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class ApacheFopFunction {
    /**
     * This function listens at endpoint "/api/apache-fop/xslfo".
     */
    @FunctionName("ApacheFOP")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/xslfo", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        try {

            var functionExecutor = new ApacheFopServerlessFunctionExecutor();
            return functionExecutor.ExecuteStringRequest(request, context.getLogger());
        }
        catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(ex).build();
        }
    }
}
