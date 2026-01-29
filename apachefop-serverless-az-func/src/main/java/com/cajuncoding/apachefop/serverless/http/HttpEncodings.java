package com.cajuncoding.apachefop.serverless.http;

public final class HttpEncodings {
    //Prevent instantiation...
    private HttpEncodings() { throw new AssertionError("Class should not be instantiated!"); }

    public static final String IDENTITY_ENCODING = "identity";
    public static final String GZIP_ENCODING = "gzip";
    public static final String BASE64_ENCODING = "base64";
}
