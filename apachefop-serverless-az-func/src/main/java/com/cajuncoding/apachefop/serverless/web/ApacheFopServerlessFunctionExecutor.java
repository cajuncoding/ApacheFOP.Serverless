package com.cajuncoding.apachefop.serverless.web;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderer;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.utils.AzureFunctionUtils;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOPException;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.logging.Logger;

public class ApacheFopServerlessFunctionExecutor {
    public ApacheFopServerlessFunctionExecutor() {
    }

    public HttpResponseMessage ExecuteByteArrayRequest(
        HttpRequestMessage<Optional<byte[]>> request,
        Logger logger
    ) throws IOException, TransformerException, FOPException {
        logger.info("ApacheFOP.Serverless HTTP trigger processing a raw GZip Byte[] request...");

        //Read the Configuration from AzureFunctions (request, environment variables)
        var config = new ApacheFopServerlessConfig(request.getHeaders(), request.getQueryParameters());

        //Create the Response Builder to handle the various responses we support.
        var responseBuilder = new ApacheFopServerlessResponseBuilder<byte[]>(request);

        //Get the XslFO Source from the Request (handling GZip Payloads if specified)...
        var xslFOBodyContent = request.getBody().isPresent()
                ? AzureFunctionUtils.getBodyContentSafely(request.getBody().get(), config)
                : StringUtils.EMPTY;

        //Now that we've initialized the unique elements for Byte[] processing we can
        //  execute the processing of the Render Request...
        return ExecuteRequestInternal(xslFOBodyContent, config, responseBuilder, logger);
    }

    public HttpResponseMessage ExecuteStringRequest(
        HttpRequestMessage<Optional<String>> request,
        Logger logger
    ) throws IOException, TransformerException, FOPException {
        logger.info("ApacheFOP.Serverless HTTP trigger processing a raw GZip Byte[] request...");

        //Read the Configuration from AzureFunctions (request, environment variables)
        var config = new ApacheFopServerlessConfig(request.getHeaders(), request.getQueryParameters());

        //Create the Response Builder to handle the various responses we support.
        var responseBuilder = new ApacheFopServerlessResponseBuilder<String>(request);

        //Get the XslFO Source from the Request (handling GZip Payloads if specified)...
        var xslFOBodyContent = request.getBody().isPresent()
                ? AzureFunctionUtils.getBodyContentSafely(request.getBody().get(), config)
                : StringUtils.EMPTY;

        //Now that we've initialized the unique elements for Byte[] processing we can
        //  execute the processing of the Render Request...
        return ExecuteRequestInternal(xslFOBodyContent, config, responseBuilder, logger);
    }

    protected <TRequest> HttpResponseMessage ExecuteRequestInternal(
        String xslFOBodyContent,
        ApacheFopServerlessConfig config,
        ApacheFopServerlessResponseBuilder<TRequest> responseBuilder,
        Logger logger
    ) throws TransformerException, IOException, FOPException {
        if (StringUtils.isBlank(xslFOBodyContent)) {
            logger.info(" - [BAD_REQUEST - 400] No XSL-FO body content was specified");
            return responseBuilder.BuildBadXslFoBodyResponse();
        }

        logger.info(MessageFormat.format(" - XSL-FO Payload [Length={0}]", xslFOBodyContent.length()));

        //Now we process the XSL-FO source...
        logger.info(" - Executing Transformation with Apache FOP...");

        //Log the Full XSL-FO Payload from the Request if Debugging is enabled...
        if(config.isDebuggingEnabled()) {
            logger.info("[DEBUG] XSL-FO Payload Received:".concat(System.lineSeparator()).concat(xslFOBodyContent));
        }

        //Initialize the ApacheFopRenderer (potentially optimized with less logging.
        //NOTE: If used, the Logger must be the instance injected into the Azure Function!
        ApacheFopRenderer fopHelper = config.isApacheFopLoggingEnabled()
                ? new ApacheFopRenderer(config, logger)
                : new ApacheFopRenderer(config);

        //Execute the transformation of the XSL-FO source content to Binary PDF format...
        var pdfRenderResult = fopHelper.renderPdfResult(xslFOBodyContent, config.isGzipResponseEnabled());

        //Add some contextual Logging so we can know if the PDF bytes were rendered...
        logger.info(MessageFormat.format("[SUCCESS] Successfully Rendered PDF with [{0}] bytes.", pdfRenderResult.getPdfBytes().length));

        //Render the PDF Response (or EventLog Dump if specified)...
        return config.isEventLogDumpModeEnabled()
                ? responseBuilder.BuildEventLogDumpResponse(pdfRenderResult, config)
                : responseBuilder.BuildPdfResponse(pdfRenderResult, config);
    }

}
