package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import com.cajuncoding.apachefop.serverless.utils.ResourceUtils;
import com.cajuncoding.apachefop.serverless.utils.XPathUtils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.*;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class ApacheFopRenderer {
    //Provide Dynamic resolution of Resources (e.g. Fonts) from JAR Resource Files...
    private static final ApacheFopJavaResourcesFileResolver fopResourcesFileResolver = new ApacheFopJavaResourcesFileResolver();

    //FOPFactory is expected to be re-used as noted in Apache 'overview' section here:
    //  https://xmlgraphics.apache.org/fop/1.1/embedding.html
    private static FopFactory staticFopFactory = null;

    //TransformerFactory may be re-used as a singleton as long as it's never mutated/modified directly by
    //  more than one thread (e.g. configuration changes on the Factory class).
    private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private Logger logger = null;
    private ApacheFopServerlessConfig apacheFopConfig = null;

    public ApacheFopRenderer(ApacheFopServerlessConfig config) {
        this(config,null);
    }

    public ApacheFopRenderer(ApacheFopServerlessConfig config, Logger optionalLogger) {
        if(config == null) throw new IllegalArgumentException("Failed to initialize ApacheFOPRenderer class; the ApacheFOPServerlessConfig is null.");
        this.apacheFopConfig = config;
        this.logger = optionalLogger;

        //Safely initialize the Apache FOP Renderer as a lazy-loaded Singleton (with thread-safety)...
        //NOTE: We now lazy-initialize so that we can use Configuration during initialization!
        initApacheFopFactorySafely();
    }

    public ApacheFopRenderResult renderPdfResult(String xslFOSource, boolean gzipEnabled) throws IOException, TransformerException, FOPException {
        //We want to manage the output in memory to eliminate any overhead or risks of using the file-system
        //  (e.g. file cleanup, I/O issues, etc.).
        try(
            ByteArrayOutputStream pdfBaseOutputStream = new ByteArrayOutputStream();
            //If GZIP is enabled then wrap the core ByteArrayOutputStream as a GZIP compression output stream...
            OutputStream fopOutputStream = (gzipEnabled) ? new GZIPOutputStream(pdfBaseOutputStream) : pdfBaseOutputStream;
        ) {
            //Enable the Event Listener for capturing Logging details (e.g. parsing/processing Events)...
            var eventListener = new ApacheFopEventListener(logger);
            FOUserAgent foUserAgent = staticFopFactory.newFOUserAgent();
            foUserAgent.getEventBroadcaster().addEventListener(eventListener);

            //In order to transform the input source into the Binary Pdf output we must initialize a new
            //  Fop (i.e. Formatting Object Processor), and a new Xsl Transformer that executes the transform.
            //  NOTE: Apache FOP uses the event based XSLT processing engine from SAX for optimized processing;
            //          this means that the transformer crawls the Xml tree of the XSL-FO source xml, while
            //          Fop is just processing events as they are raised by the transformer.  This is efficient
            //          because the Xml tree is only processed 1 time which aids in optimizing both performance
            //          and memory utilization.
            Fop fop = staticFopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, fopOutputStream);
            Transformer transformer = transformerFactory.newTransformer();

            try(StringReader stringReader = new StringReader(xslFOSource)) {
                //The Transformer requires both source (input) and result (output) handlers...
                Source src = new StreamSource(stringReader);
                Result res = new SAXResult(fop.getDefaultHandler());

                //Finally we can execute the transformation!
                transformer.transform(src, res);
            }

            //We must flush & close the stream (especially if GZIP is enabled) to ensure the outputs are finalized...
            fopOutputStream.flush();
            fopOutputStream.close();

            //Once complete we now a binary stream that we can most easily return to the client
            //  as a byte array of binary data...
            byte[] pdfBytes = pdfBaseOutputStream.toByteArray();
            return new ApacheFopRenderResult(pdfBytes, eventListener);
        }
    }

    protected synchronized void initApacheFopFactorySafely() {
        if(staticFopFactory == null) {
            var baseUri = new File(".").toURI();
            var configFilePath = ApacheFopServerlessConstants.ConfigXmlResourceName;
            FopFactory newFopFactory = null;

            try {
                String configXmlText = ResourceUtils.loadResourceAsString(configFilePath);
                if (StringUtils.isNotBlank(configXmlText)) {

                    //When Debugging log the full Configuration file...
                    if(this.apacheFopConfig.isDebuggingEnabled()) {
                        LogMessage("[DEBUG] ApacheFOP Configuration Xml:".concat(System.lineSeparator()).concat(configXmlText));
                    }

                    //Attempt to initialize with Configuration loaded from Configuration XML Resource file...
                    //NOTE: FOP Factory requires a Stream so we have to initialize a new Stream for it to load from!
                    FopFactoryBuilder fopFactoryBuilder = null;
                    try(var configXmlStream = IOUtils.toInputStream(configXmlText, StandardCharsets.UTF_8)) {
                        //BUG:
                        //  Changes somewhere since FOP v2.6 (the last version before jumping to now FOP v2.11) caused
                        //      a new bug whereby calling the FopFactoryBuilder.setConfiguration() would correctly load &
                        //      parse the config Xml, but NOT honor the original ResourceResolver, originally passed
                        //      into the constructor for the FopFactoryBuilder, and instead calls the
                        //      ResourceResolverFactory.createDefaultResourceResolver() which loses the resolver context
                        //      for the FontManager!
                        //RESOLUTION:
                        //  However, we can work around this by simply newing up the FopConfParser directly, and then
                        //      retrieving the FopFactoryBuilder from it, this honors the Resource Resolver we pass
                        //      into the constructor of the FopConfParser, and everything works as expected!
                        //  The main impact is that now we are exposed to a possible SAXException that we must now handle.
                        //var cfgBuilder = new DefaultConfigurationBuilder();
                        //var cfg = cfgBuilder.build(configXmlStream);
                        //fopFactoryBuilder = new FopFactoryBuilder(baseUri, fopResourcesFileResolver).setConfiguration(cfg);
                        var fopConfigParser = new FopConfParser(configXmlStream, baseUri, fopResourcesFileResolver);
                        fopFactoryBuilder = fopConfigParser.getFopFactoryBuilder();

                    } catch (SAXException e) {
                        //DO NOTHING if Configuration is Invalid; log info. for troubleshooting.
                        logUnexpectedException(configFilePath, e);
                    }

                    if(fopFactoryBuilder != null) {
                        //Ensure Accessibility is programmatically set (default configuration is false)...
                        //NOTE: There appears to be a bug in ApacheFOP code or documentation whereby it does not load the value from Xml as defined in the Docs!
                        //      to work around this we read the value ourselves and also provide convenience support to simply set it in Azure Functions Configuration
                        //      and if either configuration value is true then it will be enabled.
                        //NOTE: The XPathUtils is null safe so any issues in loading/parsing will simply result in null or default values...
                        var configXml = XPathUtils.fromXml(configXmlText);
                        var isAccessibilityEnabledInXmlConfig = configXml.evalXPathAsBoolean("//fop/accessibility", false);
                        fopFactoryBuilder.setAccessibility(this.apacheFopConfig.isAccessibilityPdfRenderingEnabled() || isAccessibilityEnabledInXmlConfig);

                        newFopFactory = fopFactoryBuilder.build();
                    }
                }
            } catch (IOException e) {
                //DO NOTHING if Configuration is Invalid; log info. for troubleshooting.
                logUnexpectedException(configFilePath, e);
            }

            //If not loaded with Configuration we still safely Initialize will All DEFAULTS, as well as our custom Resource Resolver...
            if (newFopFactory == null) {
                var fopFactoryBuilder = new FopFactoryBuilder(baseUri, fopResourcesFileResolver);
                newFopFactory = fopFactoryBuilder.build();
            }

            staticFopFactory = newFopFactory;
        }
    }

    private void logUnexpectedException(String configFilePath, Exception exc)
    {
        var message = MessageFormat.format(
            "An Exception occurred loading the Configuration file [{0}]; {1}",
            configFilePath,
            exc.getMessage()
        );

        System.out.println(message);
        if(logger != null) logger.log(Level.SEVERE, message, exc);
    }

    protected void LogMessage(String message)
    {
        if(logger != null) logger.info(message);
    }
}
