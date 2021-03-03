package com.cajuncoding.apachefop.serverless.helpers;

import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.zip.GZIPOutputStream;

public class ApacheFOPHelper {
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private final FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());

    public ApacheFOPHelper() {
    }

    public byte[] renderPdfBytes(String xslFOSource, boolean gzipEnabled) throws IOException, TransformerException, FOPException {
        //We want to manage the output in memory to eliminate any overhead or risks of using the file-system
        //  (e.g. file cleanup, I/O issues, etc.).
        try(
            ByteArrayOutputStream pdfBaseOutputStream = new ByteArrayOutputStream();
            //If GZIP is enabled then wrap the core ByteArrayOutputStream as a GZIP compression output stream...
            OutputStream fopOutputStream = (gzipEnabled) ? new GZIPOutputStream(pdfBaseOutputStream) : pdfBaseOutputStream;
        )
        {
            //In order to transform the input source into the Binary Pdf output we must initialize a new
            //  Fop (i.e. Formatting Object Processor), and a new Xsl Transformer that executes the transform.
            //  NOTE: Apache FOP uses the event based XSLT processing engine from SAX for optimized processing;
            //          this means that the transformer crawls the Xml tree of the XSL-FO source xml, while
            //          Fop is just processing events as they are raised by the transformer.  This is efficient
            //          because the Xml tree is only processed 1 time which aids in optimizing both performance
            //          and memory utilization.
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, fopOutputStream);
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
            return pdfBytes;
        }
    }

}
