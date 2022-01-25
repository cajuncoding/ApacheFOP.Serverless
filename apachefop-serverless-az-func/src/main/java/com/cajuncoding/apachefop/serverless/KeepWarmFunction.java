package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopRenderer;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import com.cajuncoding.apachefop.serverless.utils.ResourceUtils;
import com.cajuncoding.apachefop.serverless.utils.TextUtils;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import org.apache.fop.apps.FOPException;

import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.text.MessageFormat;

/**
 * Azure Functions with HTTP Trigger.
 */
public class KeepWarmFunction {
    /**
     * This function executes at the specified timer interval to keep ApacheFOP.Serverless warm to minimize
     *  cold start performance impacts.
     */
    @FunctionName("KeepWarm")
    public void run(
            //Run Every 5 Minutes...
            @TimerTrigger(name = "KeepWarmTrigger", schedule = "%KeepWarmCronSchedule%") String timerInfo,
            final ExecutionContext context
    ) throws IOException, TransformerException, FOPException {

        var logger =  context.getLogger();
        logger.info("ApacheFOP.Serverless Keep Warm Timer Trigger: ".concat(timerInfo));

        logger.info(MessageFormat.format(" - Executing at [{0}]", TextUtils.getCurrentW3cDateTime()));

        var xslFoSource = ResourceUtils.loadResourceAsString(ApacheFopServerlessConstants.KeepinItWarmXslFo);
        logger.info(MessageFormat.format(" - XSL-FO Keep Warm XslFo Script Loaded [Length={0}]", xslFoSource.length()));

        //Now we process the XSL-FO source...
        logger.info("Executing Transformation with Apache FOP...");

        //Read the Configuration from AzureFunctions (request, environment variables)
        var config = new ApacheFopServerlessConfig();

        //Initialize the ApacheFopRenderer (potentially optimized with less logging.
        //NOTE: If used, the Logger must be the instance injected into the Azure Function!
        ApacheFopRenderer fopHelper = new ApacheFopRenderer(config, logger);

        //Execute the transformation of the XSL-FO source content to Binary PDF format...
        var pdfRenderResult = fopHelper.renderPdfResult(xslFoSource, false);
        logger.info(MessageFormat.format("Successfully rendered the PDF [Bytes={0}]", pdfRenderResult.getPdfBytes().length));
    }
}
