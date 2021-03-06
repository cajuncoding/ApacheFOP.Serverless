package com.cajuncoding.apachefop.serverless.apachefop;

public class ApacheFopRenderResult {
    private final byte[] pdfBytes;
    private final ApacheFopEventListener eventListener;

    public ApacheFopRenderResult(byte[] pdfBytes, ApacheFopEventListener eventListener) {
        this.pdfBytes = pdfBytes;
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
        String headerValue = getEventListener().GetEventsText("; ");
        return headerValue;
    }
}
