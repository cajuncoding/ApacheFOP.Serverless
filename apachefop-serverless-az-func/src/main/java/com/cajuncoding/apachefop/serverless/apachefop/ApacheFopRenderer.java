package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import com.cajuncoding.apachefop.serverless.utils.ResourceUtils;
import com.cajuncoding.apachefop.serverless.utils.XPathUtils;

import com.cajuncoding.apachefop.serverless.utils.XmlFactoryUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
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
    //NOTE: volatile + static lock provides for safe publication and class-wide locking ---
    private static volatile FopFactory staticFopFactory = null;
    private static final Object FOP_INITIALIZATION_LOCK = new Object();

    //SAXParserFactory may be re-used as a singleton as long as it's never mutated/modified directly by
    //  more than one thread (e.g. configuration changes on the Factory class).
    // Explicitly Opt-in to Safe processing by enabling secure processing & Block external DTDs & stylesheets!
    // NOTE: Even though XXE vulnerabilities were patched inside some of the FOP internal handling after FOP v2.10
    //      this was still an issue for Java in general which is vulnerable by default and therefore our XML parsing
    //      must be explicitly set to securely process the XML itself -- it is NOT safe from XXE by default (ugg)!
    private static final SAXParserFactory saxXmlParserFactory = XmlFactoryUtils.newXxeSafeSaxXmlParserFactory();

    private Logger logger = null;
    private ApacheFopServerlessConfig apacheFopConfig = null;

    public ApacheFopRenderer(ApacheFopServerlessConfig config) {
        this(config, null);
    }

    public ApacheFopRenderer(ApacheFopServerlessConfig config, Logger optionalLogger) {
        if(config == null) throw new IllegalArgumentException("Failed to initialize ApacheFOPRenderer class; the ApacheFOPServerlessConfig is null.");
        this.apacheFopConfig = config;
        this.logger = optionalLogger;
    }

    public ApacheFopRenderResult renderPdfResult(String xslFOSource, boolean gzipEnabled) throws IOException, FOPException {
        //If GZIP is enabled then wrap the core ByteArrayOutputStream as a GZIP compression output stream...
        try (
            //We want to manage the output in memory to eliminate any overhead or risks of using the file-system (e.g. file cleanup, I/O issues, etc.).
            ByteArrayOutputStream pdfBaseOutputStream = new ByteArrayOutputStream();
            OutputStream fopOutputStream = gzipEnabled ? new GZIPOutputStream(pdfBaseOutputStream) : pdfBaseOutputStream
        ) {
            //Ensure we are ALWAYS fully initialized...
            FopFactory fopFactory = initApacheFopFactorySafely();

            //Enable the Event Listener for capturing Logging details (e.g. parsing/processing Events)...
            ApacheFopEventListener eventListener = new ApacheFopEventListener(logger);
            FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
            foUserAgent.getEventBroadcaster().addEventListener(eventListener);

            //In order to transform the input source into the Binary Pdf output we must initialize a new
            //  Fop (i.e. Formatting Object Processor) and pipe our XslFO Xml directly into FOP with SAX for
            //  best performance.
            //  NOTE: This is now using a fully streaming (SAX) pipeline — NO DOM is built and NO identity transform hop.
            //  NOTE: Apache FOP uses the event based XSLT processing engine from SAX for optimized processing;
            //          this means that the transformer crawls the Xml tree of the XSL-FO source xml, while
            //          Fop is just processing events as they are raised by the transformer.  This is efficient
            //          because the Xml tree is only processed 1 time which aids in optimizing both performance
            //          and memory utilization.
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, fopOutputStream);

            try (Reader xslFoInputReader = new StringReader(xslFOSource)) {
                // Fully streaming SAX-only pipeline: parser → FOP handler → fopOutputStream (initialize above!)
                XMLReader saxXmlReader = saxXmlParserFactory.newSAXParser().getXMLReader();
                saxXmlReader.setContentHandler(fop.getDefaultHandler());

                InputSource saxInputSource = new InputSource(xslFoInputReader);
                saxXmlReader.parse(saxInputSource);
            }
            //Treat ParserConfigurationException as internal misconfiguration and convert to an unchecked exception.
            catch (ParserConfigurationException e) {
                logUnexpectedException(e);
                throw XmlFactoryUtils.newXmlParserInitException(e);
            }
            catch (SAXParseException e) {
                throw XmlFactoryUtils.newBadXmlRequestException(e);
            }
            catch (SAXException e) {
                throw XmlFactoryUtils.newBadXmlRequestException(e);
            }

            //FINALLY if GZIP is Enabled we must finish the stream (e.g. flush & close) to ensure the output is finalized...
            if (gzipEnabled) {
                ((GZIPOutputStream) fopOutputStream).finish();
            }

            return new ApacheFopRenderResult(pdfBaseOutputStream.toByteArray(), eventListener);
        }
    }

    //NOTE: For non-static method, we must use locks on a static object with double-checked locking to ensure thread safety!
    private FopFactory initApacheFopFactorySafely() {
        FopFactory localFopFactoryInstance = staticFopFactory;
        if (localFopFactoryInstance == null) {
            synchronized (FOP_INITIALIZATION_LOCK) {
                localFopFactoryInstance = staticFopFactory;
                if (localFopFactoryInstance == null) {
                    staticFopFactory = buildFopFactoryWithConfigAndLoggingUnsafe();
                    localFopFactoryInstance = staticFopFactory;
                }
            }
        }

        if (localFopFactoryInstance == null)
            throw new IllegalStateException("[ApacheFopRenderer.initApacheFopFactorySafely()] FopFactory unavailable after initialization!");

        return localFopFactoryInstance;
    }

    //Provide mechanism to force rebuilding of the FopFactory for edge cases where something gets corrupted
    //  (e.g. Font Cache may have random potential/occasional corruption issues, etc.).
    public void rebuildFopFactorySingleton() {
        synchronized (FOP_INITIALIZATION_LOCK) {
            staticFopFactory = buildFopFactoryWithConfigAndLoggingUnsafe();
        }
    }

    //Dedicated (unsafe) builder method that can use instance logger/config to encapsulate all work related
    //  to constructing a valid FopFactory singleton.
    private FopFactory buildFopFactoryWithConfigAndLoggingUnsafe() {
        var baseUri = new File(".").toURI();
        var configFilePath = ApacheFopServerlessConstants.ConfigXmlResourceName;
        FopFactory newFopFactory = null;

        try {
            String configXmlText = ResourceUtils.loadResourceAsString(configFilePath);
            if (StringUtils.isNotBlank(configXmlText)) {

                //When Debugging log the full Configuration file...
                if (this.apacheFopConfig.isXslFoDebuggingEnabled()) {
                    LogMessage("[DEBUG] ApacheFOP Configuration Xml:".concat(System.lineSeparator()).concat(configXmlText));
                }

                //Attempt to initialize with Configuration loaded from Configuration XML Resource file...
                //NOTE: FOP Factory requires a Stream so we have to initialize a new Stream for it to load from!
                FopFactoryBuilder fopFactoryBuilder = null;
                try (var configXmlStream = IOUtils.toInputStream(configXmlText, StandardCharsets.UTF_8)) {
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
                    logUnexpectedConfigException(configFilePath, e);
                }

                if (fopFactoryBuilder != null) {
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
            logUnexpectedConfigException(configFilePath, e);
        }

        //If not loaded with Configuration we still safely Initialize will All DEFAULTS, as well as our custom Resource Resolver...
        if (newFopFactory == null) {
            var fopFactoryBuilder = new FopFactoryBuilder(baseUri, fopResourcesFileResolver);
            newFopFactory = fopFactoryBuilder.build();
        }

        return newFopFactory;
    }

    private void logUnexpectedConfigException(String configFilePath, Exception exc)
    {
        var message = MessageFormat.format(
            "An Exception occurred loading the Configuration file [{0}]; {1}",
            configFilePath,
            exc.getMessage()
        );

        logUnexpectedException(exc, message);
    }

    private void logUnexpectedException(Exception exc)
    {
        logUnexpectedException(exc, null);
    }

    private void logUnexpectedException(Exception exc, String message)
    {
        String msg = message == null ? exc.getMessage() : message;
        System.out.println(msg);
        if (logger != null) logger.log(Level.SEVERE, msg, exc);
    }

    protected void LogMessage(String message)
    {
        if (logger != null) logger.info(message);
    }
}
