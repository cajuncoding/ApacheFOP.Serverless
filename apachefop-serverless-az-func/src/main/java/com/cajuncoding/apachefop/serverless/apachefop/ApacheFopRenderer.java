package com.cajuncoding.apachefop.serverless.apachefop;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConstants;
import org.apache.fop.apps.*;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.configuration.DefaultConfigurationBuilder;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.text.MessageFormat;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class ApacheFopRenderer {
    //FOPFactory is expected to be re-used as noted in Apache 'overview' section here:
    //  https://xmlgraphics.apache.org/fop/1.1/embedding.html
    private static final FopFactory fopFactory = createApacheFopFactory();
    //TransformerFactory may be re-used as a singleton as long as it's never mutated/modified directly by
    //  more than one thread (e.g. configuration changes on the Factory class).
    private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private Logger logger = null;

    public ApacheFopRenderer() {
        this.logger = null;
    }

    public ApacheFopRenderer(Logger optionalLogger) {
        this.logger = optionalLogger;
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
            FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
            foUserAgent.getEventBroadcaster().addEventListener(eventListener);

            //In order to transform the input source into the Binary Pdf output we must initialize a new
            //  Fop (i.e. Formatting Object Processor), and a new Xsl Transformer that executes the transform.
            //  NOTE: Apache FOP uses the event based XSLT processing engine from SAX for optimized processing;
            //          this means that the transformer crawls the Xml tree of the XSL-FO source xml, while
            //          Fop is just processing events as they are raised by the transformer.  This is efficient
            //          because the Xml tree is only processed 1 time which aids in optimizing both performance
            //          and memory utilization.
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, fopOutputStream);
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

    public static FopFactory createApacheFopFactory() {
        var classLoader = ApacheFopRenderer.class.getClassLoader();
        var baseUri = new File(".").toURI();
        var configFilePath = ApacheFopServerlessConstants.ConfigXmlResourceName;
        FopFactory fopFactory = null;

        try(var configStream = classLoader.getResourceAsStream(configFilePath);) {
            if(configStream != null) {
                //Attempt to initialize with Configuration loaded from Configuration XML Resource file...
                var cfgBuilder = new DefaultConfigurationBuilder();
                var cfg = cfgBuilder.build(configStream);
                var fopFactoryBuilder = new FopFactoryBuilder(baseUri).setConfiguration(cfg);

                fopFactory = fopFactoryBuilder.build();
            }
        }
        catch (IOException | ConfigurationException e) {
            //DO NOTHING if Configuration is Invalid; log info. for troubleshooting.
            System.out.println(MessageFormat.format(
         "An Exception occurred loading the Configuration file [{0}]; {1}",
                configFilePath,
                e.getMessage()
            ));
        }

        //Safely Initialize will All DEFAULTS if not loaded with Configuration...
        if(fopFactory == null) {
            fopFactory = FopFactory.newInstance(baseUri);
        }

        return fopFactory;
    }

//    public static String loadApacheFopConfigXmlText() {
//        ClassLoader classLoader = ApacheFOPHelper.class.getClassLoader();
//        try(var resourceStream = classLoader.getResourceAsStream("apache-fop-config.xml");) {
//
//            return (resourceStream != null)
//                ? IOUtils.toString(resourceStream, StandardCharsets.UTF_8)
//                : null;
//
//        } catch (IOException e) {
//            return null;
//        }
//    }

}
