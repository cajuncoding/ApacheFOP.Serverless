package com.cajuncoding.apachefop.serverless.web;

import com.cajuncoding.apachefop.serverless.http.HttpEncodings;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.UnsupportedEncodingException;

public class SafeHeader {
    private final String value;
    private final String encoding;

    public SafeHeader(String value, String encoding) throws UnsupportedEncodingException {
        this.value = sanitizeTextForHttpHeader(value, encoding);
        this.encoding = sanitizeTextForHttpHeader(encoding, null);
    }

    public String getEncoding() { return encoding; }
    public String getValue() { return value; }

    protected String sanitizeTextForHttpHeader(String value, String encoding) throws UnsupportedEncodingException {
        if(StringUtils.isBlank(value))
            return value;

        if(StringUtils.isNotBlank(encoding) && !encoding.equalsIgnoreCase(HttpEncodings.IDENTITY_ENCODING))
            return value;

        //BBernard - 09/29/2021
        //FIX bug where ApacheFOP may return Unicode Characters in Event Messages whereby we must escape any
        //  Unicode Characters in the Header Text because onlY ASCII characters are valid.
        return StringEscapeUtils.escapeJava(value);
    }
}