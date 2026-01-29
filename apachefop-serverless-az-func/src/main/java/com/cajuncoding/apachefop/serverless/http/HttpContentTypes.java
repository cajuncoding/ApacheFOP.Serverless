package com.cajuncoding.apachefop.serverless.http;

public final class HttpContentTypes {
    //Prevent instantiation...
    private HttpContentTypes() { throw new AssertionError("Class should not be instantiated!"); }

    public static final String PLAIN_TEXT_UTF8 = "text/plain; charset=utf-8";
    public static final String JSON = "application/json";
}
