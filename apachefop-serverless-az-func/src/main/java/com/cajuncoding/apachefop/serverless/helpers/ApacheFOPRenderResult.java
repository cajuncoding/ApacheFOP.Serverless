package com.cajuncoding.apachefop.serverless.helpers;

public class ApacheFOPRenderResult {
    private final byte[] pdfBytes;
    private final ApacheFOPEventListener eventListener;

    public ApacheFOPRenderResult(byte[] pdfBytes, ApacheFOPEventListener eventListener) {
        this.pdfBytes = pdfBytes;
        this.eventListener = eventListener;
    }

    public byte[] getPdfBytes() {
        return pdfBytes;
    }

    public ApacheFOPEventListener getEventListener()
    {
        return eventListener;
    }

    public String getEventsLogAsBodyValue() {
        //Do NOT include line separators in teh Header value...
        String headerValue = getEventListener().GetEventsText(true);
        return headerValue;
    }

    public String getEventsLogAsHeaderValue() {
        //Do NOT include line separators in teh Header value...
        String headerValue = getEventListener().GetEventsText(false);
        return headerValue;
    }
}
