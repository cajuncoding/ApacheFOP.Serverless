package com.cajuncoding.apachefop.serverless.web;

public class SafeHeader {
    private String value;
    private String encoding;
    public SafeHeader(String value, String encoding) {
        this.value = value;
        this.encoding = encoding;
    }

    public String getEncoding() { return encoding; }
    public String getValue() { return value; }
}