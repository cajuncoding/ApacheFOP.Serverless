package com.cajuncoding.apachefop.serverless.web;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderer;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.utils.AzureFunctionUtils;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.fop.apps.FOPException;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.logging.Logger;

public class ApacheFopServerlessFunctionExecutor {
    public ApacheFopServerlessFunctionExecutor() {
    }

    public HttpResponseMessage executeByteArrayRequest(
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
        return executeRequestWithRetry(xslFOBodyContent, config, responseBuilder, logger);
    }

    public HttpResponseMessage executeStringRequest(
        HttpRequestMessage<Optional<String>> request,
        Logger logger
    ) throws IOException, TransformerException, FOPException {
        logger.info("ApacheFOP.Serverless HTTP trigger processing a String request...");

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
        return executeRequestWithRetry(xslFOBodyContent, config, responseBuilder, logger);
    }

    private <TRequest> HttpResponseMessage executeRequestWithRetry(
        String xslFOBodyContent,
        ApacheFopServerlessConfig config,
        ApacheFopServerlessResponseBuilder<TRequest> responseBuilder,
        Logger logger
    ) throws TransformerException, IOException, FOPException {
        try {

            return executeRequestInternal(xslFOBodyContent, config, responseBuilder, logger);

        }
        catch (FOPException fopExc) {
            //NOTE: We provide a one-time attempt to recover if there are  FOP exceptions that
            //  are likely result of corruption and/or issues in our singleton initialization...
            if(looksLikePotentiallyRecoverableFopInitIssue(fopExc)) {
                logger.info("[RETRY][ApacheFopServerlessFunctionExecutor] FOPException matched recoverable pattern; "
                            + "rebuilding FopFactory and retrying once. Exception: " + fopExc.getMessage());
                var apacheFopRenderer = createApacheFopRenderer(config, logger);
                apacheFopRenderer.rebuildFopFactorySingleton(); // your hook
                return executeRequestInternal(xslFOBodyContent, config, responseBuilder, logger);
            }

            throw fopExc;
        }
    }

    private <TRequest> HttpResponseMessage executeRequestInternal(
        String xslFOBodyContent,
        ApacheFopServerlessConfig config,
        ApacheFopServerlessResponseBuilder<TRequest> responseBuilder,
        Logger logger
    ) throws TransformerException, IOException, FOPException {
        if (StringUtils.isBlank(xslFOBodyContent)) {
            logger.info(" - [BAD_REQUEST - 400] No XSL-FO body content was specified");
            return responseBuilder.buildBadXslFoBodyResponse();
        }

        logger.info(MessageFormat.format(" - XSL-FO Payload [Length={0}]", xslFOBodyContent.length()));

        //Now we process the XSL-FO source...
        logger.info(" - Executing Transformation with Apache FOP...");

        //Log the Full XSL-FO Payload from the Request if Debugging is enabled...
        if(config.isDebuggingEnabled()) {
            logger.info("[DEBUG] XSL-FO Payload Received:".concat(System.lineSeparator()).concat(xslFOBodyContent));
        }

        //Execute the transformation of the XSL-FO source content to Binary PDF format...
        var apacheFopRenderer = createApacheFopRenderer(config, logger);
        var pdfRenderResult = apacheFopRenderer.renderPdfResult(xslFOBodyContent, config.isGzipResponseEnabled());

        //Add some contextual Logging so we can know if the PDF bytes were rendered...
        logger.info(MessageFormat.format("[SUCCESS] Successfully Rendered PDF with [{0}] bytes.", pdfRenderResult.getPdfBytes().length));

        //Render the PDF Response (or EventLog Dump if specified)...
        return config.isEventLogDumpModeEnabled()
                ? responseBuilder.buildEventLogDumpResponse(pdfRenderResult, config)
                : responseBuilder.buildPdfResponse(pdfRenderResult, config);
    }

    private ApacheFopRenderer createApacheFopRenderer(ApacheFopServerlessConfig config, Logger optionalLogger)
    {
        //Initialize the ApacheFopRenderer (potentially optimized with less logging.
        //NOTE: If used, the Logger must be the instance injected into the Azure Function!
        return config.isApacheFopLoggingEnabled()
                ? new ApacheFopRenderer(config, optionalLogger)
                : new ApacheFopRenderer(config);
    }

    private static final String[] RECOVERABLE_HEURISTICS = { "font", "cache", "resource resolver", "fopconfparser", "fopfactorybuilder" };

    private static boolean looksLikePotentiallyRecoverableFopInitIssue(FOPException exc) {
        Throwable t = exc;
        int depth = 0, maxDepth = 6; // small cap

        while (t != null && depth++ < maxDepth) {
            if (Strings.CI.containsAny(t.getMessage(), RECOVERABLE_HEURISTICS))
                return true;

            t = t.getCause();
        }

        return false;
    }

}
