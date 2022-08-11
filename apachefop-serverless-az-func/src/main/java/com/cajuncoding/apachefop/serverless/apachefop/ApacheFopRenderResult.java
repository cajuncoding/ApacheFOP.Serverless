package com.cajuncoding.apachefop.serverless.apachefop;

public class ApacheFopRenderResult {
    public static final String EventLogSeparator = "||";

    private final byte[] pdfBytes;
    private final ApacheFopEventListener eventListener;

    public ApacheFopRenderResult(byte[] pdfBytes, ApacheFopEventListener eventListener) {
        this.pdfBytes = pdfBytes != null ? pdfBytes : new byte[0];
        this.eventListener = eventListener;
    }

    public byte[] getPdfBytes() {
        return pdfBytes;
    }

    public ApacheFopEventListener getEventListener()
    {
        return eventListener;
    }

    public String getEventsLogAsBodyValue() {
        //Do NOT include line separators in teh Header value...
        String headerValue = getEventListener().GetEventsText();
        return headerValue;
    }

    public String getEventsLogAsHeaderValue() {
        //Do NOT include line separators in the Header value,
        //  instead we separate with a valid ASCII Delimiter...
        String headerValue = getEventListener().GetEventsText(EventLogSeparator);
        return headerValue;
    }
}
