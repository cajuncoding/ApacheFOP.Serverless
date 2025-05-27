package com.cajuncoding.apachefop.serverless.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XPathUtils {
    
    public static XPathUtils fromXml(String xmlContent)
    {
		try (var xmlContentStream = IOUtils.toInputStream(xmlContent, StandardCharsets.UTF_8)) {
            return XPathUtils.fromXml(xmlContentStream);
		} catch (IOException e) {
            e.printStackTrace();
        }

        return new XPathUtils(null);
    }

    public static XPathUtils fromXml(InputStream xmlContentStream)
    {
        Document xmlDocument = null;
        try {
            xmlDocument = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xmlContentStream);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            e.printStackTrace();
        }
 
        return new XPathUtils(xmlDocument);
    }

    protected Document _xmlDoc;

    public XPathUtils(Document xmlDoc)
    {
        _xmlDoc = xmlDoc;
    }

    public Document getXmlDocument() {
        return _xmlDoc;
    }

    public <T>T evalXPath(String xpath, Class<T> classType) throws XPathExpressionException
    {
        return evalXPathInternal(xpath, classType);
    }

    public String evalXPathAsString(String xpath, String defaultIfNotFound) throws XPathExpressionException
    {
        try {
            var result = evalXPathInternal(xpath, String.class);
            return result != null ? result : defaultIfNotFound;
        } catch (XPathExpressionException e) {
            return defaultIfNotFound;
        }
    }

    public boolean evalXPathAsBoolean(String xpath, boolean defaultIfNotFound)
    {
        try {
            var result = evalXPathInternal(xpath, Boolean.class);
            return (boolean)(result != null ? result : defaultIfNotFound);
        } catch (XPathExpressionException e) {
            return defaultIfNotFound;
        }
    }

    public <T>T evalXPathInternal(String xpathCommand, Class<T> classType) throws XPathExpressionException
    {
        if(_xmlDoc == null)
            return null;

        XPath xpath = XPathFactory.newInstance().newXPath();
        // //XPathExpression xpathExpression = xpathCompiler.compile(xpath);
        // var result = xpathExpression.evaluate(_xmlDoc, returnType);
        var result = xpath.evaluateExpression(xpathCommand, _xmlDoc, classType);
        return result;
    }
}

