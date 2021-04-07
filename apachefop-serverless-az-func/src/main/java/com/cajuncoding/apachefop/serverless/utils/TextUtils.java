package com.cajuncoding.apachefop.serverless.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class TextUtils {
    public final static SimpleDateFormat DateFormatW3C = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");

    public static String getCurrentW3cDateTime() {
        Calendar cal = Calendar.getInstance();
        String currentW3cDateTime = TextUtils.DateFormatW3C.format(cal.getTime());
        return currentW3cDateTime;
    }

    /**
     * Truncates a string to the number of characters that fit in X bytes avoiding multi byte characters being cut in
     * half at the cut off point. Also handles surrogate pairs where 2 characters in the string is actually one literal
     * character.
     *
     * Based on: http://www.jroller.com/holy/entry/truncating_utf_string_to_the
     * More Details on Stack Overflow here: https://stackoverflow.com/a/35148974/7293142
     */
    public static String truncateToFitUtf8ByteLength(String text, int maxBytes) {
        if (text == null) {
            return null;
        }

        Charset utf8 = StandardCharsets.UTF_8;
        CharsetDecoder decoder = utf8.newDecoder();
        byte[] sba = text.getBytes(utf8);

        if (sba.length <= maxBytes) {
            return text;
        }

        // Ensure truncation by having the byte buffer the same size as the specified  maxBytes...
        ByteBuffer byteBuffer = ByteBuffer.wrap(sba, 0, maxBytes);
        CharBuffer charBuffer = CharBuffer.allocate(maxBytes);

        // Ignore an incomplete character...
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        decoder.decode(byteBuffer, charBuffer, true);
        decoder.flush(charBuffer);
        return new String(charBuffer.array(), 0, charBuffer.position());
    }
}
