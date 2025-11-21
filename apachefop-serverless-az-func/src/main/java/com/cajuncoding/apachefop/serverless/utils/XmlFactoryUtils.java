package com.cajuncoding.apachefop.serverless.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class XmlFactoryUtils {

    public static DocumentBuilderFactory newXxeSafeDocumentBuilderFactory() {
        return newDocumentBuilderFactory(true);
    }

    /// Initialize a DocumentBuilderFactory safely ensuring that it is XXE agnostic (if specified) to prevent
    /// unexpected vulnerabilities which are an issue for Java as it is vulnerable by default.
    /// This method implements all protections outlined in the OWASP XXE Cheat Sheet to ensure protection:
    /// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    public static DocumentBuilderFactory newDocumentBuilderFactory(boolean enableXxeSecurity)
    {
        try {
            var xmlDocBuilderFactory = DocumentBuilderFactory.newInstance();
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
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static TransformerFactory newXxeSafeTransformerFactory() {
        return newTransformerFactory(true);
    }

    /// Initialize a TransformerFactory safely ensuring that it is XXE agnostic (if specified) to prevent
    /// unexpected vulnerabilities which are an issue for Java as it is vulnerable by default.
    /// This method implements all protections outlined in the OWASP XXE Cheat Sheet to ensure protection:
    /// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#transformerfactory
    public static TransformerFactory newTransformerFactory(boolean enableXxeSecurity) {
        try {
            TransformerFactory newTransformerFactory = TransformerFactory.newInstance();
            if(enableXxeSecurity) {
                // Enable secure processing
                newTransformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                // Block external entities - these features MUST be used together to ensure XXE protection!
                newTransformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, StringUtils.EMPTY);
                newTransformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, StringUtils.EMPTY);
            }
            // Finally (if enabled) we can return our Factory that constructs XXE agnostic configured parsers...
            return newTransformerFactory;
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }
}

