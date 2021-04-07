package com.cajuncoding.apachefop.serverless.utils;

import org.apache.commons.io.IOUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipUtils {
    public static byte[] compressData(byte[] bytes) throws IOException {
        try (
            var outputByteArrayStream = new ByteArrayOutputStream(bytes.length);
            var outputGzipStream = new GZIPOutputStream(outputByteArrayStream);
        ) {
            outputGzipStream.write(bytes);
            //We must flush & close the stream (especially if GZIP is enabled) to ensure the outputs are finalized...
            outputGzipStream.flush();
            outputGzipStream.close();

            var compressedBytes = outputByteArrayStream.toByteArray();
            return compressedBytes;
        }
    }

    public static String compressToBase64(byte[] bytes) throws IOException {
        var encoder = Base64.getEncoder();
        var compressedBytes= compressData(bytes);
        var result = encoder.encodeToString(compressedBytes);
        return result;
    }

    public static String compressToBase64(String textData) throws IOException {
        return compressToBase64(textData.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] decompressData(byte[] compressedBytes) throws IOException {
        try (
            var inputByteArrayStream = new ByteArrayInputStream(compressedBytes);
            var inputGzipStream = new GZIPInputStream(inputByteArrayStream);
        ) {
            var uncompressedBytes = IOUtils.toByteArray(inputGzipStream);
            return uncompressedBytes;
        }
    }

    public static String decompressToString(byte[] compressedBytes, Charset encoding) throws IOException {
        var bytes = decompressData(compressedBytes);
        return new String(bytes, encoding);
    }

    public static String decompressToString(byte[] compressedBytes) throws IOException {
        return decompressToString(compressedBytes, StandardCharsets.UTF_8);
    }

    public static String decompressBase64ToString(String compressedBase64, Charset encoding) throws IOException {
        var compressedBytes = Base64.getDecoder().decode(compressedBase64);
        var bytes = decompressData(compressedBytes);
        var result = new String(bytes, encoding);
        return result;
    }

    public static String decompressBase64ToString(String compressedBase64) throws IOException {
        var result = decompressBase64ToString(compressedBase64, StandardCharsets.UTF_8);
        return result;
    }
}
