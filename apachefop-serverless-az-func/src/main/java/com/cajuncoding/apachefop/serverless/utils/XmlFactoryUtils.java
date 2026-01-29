package com.cajuncoding.apachefop.serverless.utils;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.text.MessageFormat;

public class XmlFactoryUtils {

    public static DocumentBuilderFactory newXxeSafeDocumentBuilderFactory() {
        return newDocumentBuilderFactory(true);
    }

    // Initialize a DocumentBuilderFactory safely ensuring that it is XXE secured (if specified) to prevent
    //     unexpected vulnerabilities which are an issue for Java as it is vulnerable by default.
    // This method implements key protections outlined in the OWASP XXE Cheat Sheet to ensure protection:
    // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    public static DocumentBuilderFactory newDocumentBuilderFactory(boolean enableXxeSecurity)
    {
        try {
            var xmlDocBuilderFactory = DocumentBuilderFactory.newInstance();

            // Note: FO uses namespaces; ensure the factory is namespace aware!
            xmlDocBuilderFactory.setNamespaceAware(true);

            if(enableXxeSecurity) {
                // Disallow DOCTYPE declarations entirely
                xmlDocBuilderFactory.setFeature(XmlSecurityFeatures.DISALLOW_DOCTYPE_DECLARATIONS, true);
                // Enable secure processing
                xmlDocBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                // Block external entities - these features MUST be used together to ensure XXE protection!
                xmlDocBuilderFactory.setFeature(XmlSecurityFeatures.EXTERNAL_GENERAL_ENTITIES, false);
                xmlDocBuilderFactory.setFeature(XmlSecurityFeatures.EXTERNAL_PARAMETER_ENTITIES, false);
                // Additional items noted (likely redundant with the above but why not)...
                xmlDocBuilderFactory.setFeature(XmlSecurityFeatures.LOAD_EXTERNAL_DTD, false);
                // We explicitly set these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
                xmlDocBuilderFactory.setXIncludeAware(false);
                xmlDocBuilderFactory.setExpandEntityReferences(false);
            }
            // Finally (if enabled) we can return our Factory that constructs XXE agnostic configured parsers...
            return xmlDocBuilderFactory;
        }
        //Treat ParserConfigurationException as internal misconfiguration and convert to an unchecked exception.
        catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static SAXParserFactory newXxeSafeSaxXmlParserFactory() {
        return newSaxXmlParserFactory(true);
    }

    // Initialize a DocumentBuilderFactory safely ensuring that it is XXE secured (if specified) to prevent
    //     unexpected vulnerabilities which are an issue for Java as it is vulnerable by default.
    // This method implements key protections outlined in the OWASP XXE Cheat Sheet to ensure protection:
    // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    public static SAXParserFactory newSaxXmlParserFactory(boolean enableXxeSecurity)
    {
        try {
            // Initialize a SAX parser factory safely ensuring that it is XXE agnostic
            // and matches the protections in newDocumentBuilderFactory(...)
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

            // Note: FO uses namespaces; ensure the factory is namespace aware!
            saxParserFactory.setNamespaceAware(true);

            if(enableXxeSecurity) {
                // Disallow DOCTYPE declarations entirely
                saxParserFactory.setFeature(XmlSecurityFeatures.DISALLOW_DOCTYPE_DECLARATIONS, true);
                // Enable secure processing (defense-in-depth)
                saxParserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                // Block external entities - these features MUST be used together to ensure XXE protection!
                saxParserFactory.setFeature(XmlSecurityFeatures.EXTERNAL_GENERAL_ENTITIES, false);
                saxParserFactory.setFeature(XmlSecurityFeatures.EXTERNAL_PARAMETER_ENTITIES, false);
                // Additional items noted (likely redundant with the above but why not)...
                saxParserFactory.setFeature(XmlSecurityFeatures.LOAD_EXTERNAL_DTD, false);

                // We explicitly set these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
                saxParserFactory.setXIncludeAware(false);
                //saxParserFactory.setExpandEntityReferences(false);
            }
            // Finally (if enabled) we can return our Factory that constructs XXE agnostic configured parsers...
            return saxParserFactory;
        }
        //Treat ParserConfigurationException as internal misconfiguration and convert to an unchecked exception.
        catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static IllegalArgumentException newBadXmlRequestException(SAXParseException saxParseException) {
       // Add useful context (line/column) for callers/logs; rethrow as IllegalArgumentException
        return new IllegalArgumentException(
                MessageFormat.format(
                    "Invalid XSL-FO at line [{0}], column [{1}]{2}",
                    saxParseException.getLineNumber(),
                    saxParseException.getColumnNumber(),
                    (saxParseException.getMessage() != null ? ": " + saxParseException.getMessage() : StringUtils.EMPTY)
                ),
                saxParseException
        );
    }

    public static IllegalArgumentException newBadXmlRequestException(SAXException saxException) {
        // Add useful context (line/column) for callers/logs; rethrow as IllegalArgumentException
        return new IllegalArgumentException(
                MessageFormat.format(
                        "Invalid XSL-FO{0}",
                        (saxException.getMessage() != null ? ": " + saxException.getMessage() : ".")
                ),
                saxException
        );
    }

}

