package com.cajuncoding.apachefop.serverless;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.sun.xml.internal.ws.encoding.ContentType;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private FopFactory fopFactory = FopFactory.newInstance(new File(".").toURI());

    /**
     * This function listens at endpoint "/api/apache-fop/xslfo". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("ApacheFOP")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/xslfo", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {

        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        Optional<String> bodyContent = request.getBody();

        if (!bodyContent.isPresent())
        {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("A valid XSL-FO body content must be specified.").build();
        }

        try {
            String xslFOBodyContent = bodyContent.get().trim();
            ByteArrayOutputStream fopOutputStream = new ByteArrayOutputStream();

            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, fopOutputStream);
            Transformer transformer = transformerFactory.newTransformer();

            Source src = new StreamSource(new StringReader(xslFOBodyContent));
            Result res = new SAXResult(fop.getDefaultHandler());

            transformer.transform(src, res);

            byte[] fopPdfBytes = fopOutputStream.toByteArray();
            fopOutputStream.close();

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss");
            String dateStr = dateFormat.format(cal.getTime());

            HttpResponseMessage resp = request
                    .createResponseBuilder(HttpStatus.OK)
                    .body(fopPdfBytes)
                    .header("Content-Type", MimeConstants.MIME_PDF)
                    .header("Content-Length", Integer.toString(fopPdfBytes.length))
                    .header("Content-Disposition", MessageFormat.format("inline; filename=\"{0}_RenderedPdf.pdf\"", dateStr))
                    .build();

            return resp;
        }
        catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(ex).build();
        }
    }
}
