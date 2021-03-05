package com.cajuncoding.apachefop.serverless.helpers;

import org.apache.fop.events.Event;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.EventListener;
import org.apache.fop.events.model.EventSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** A simple event listener that captures the events into a Collection handling. */
public class ApacheFOPEventListener implements EventListener {
    private List<Event> eventsList = new ArrayList<>();
    private Logger logger = null;

    public ApacheFOPEventListener() {
    }

    public ApacheFOPEventListener(Logger optionalLogger) {
        this.logger = optionalLogger;
    }

    /** {@inheritDoc} */
    public void processEvent(Event event) {
        eventsList.add(event);

        if(logger != null)
            logger.info(eventToString(event));
    }

    private String eventToString(Event event) {
        String msg = EventFormatter.format(event);
        EventSeverity severity = event.getSeverity();

        String eventMsg = "[".concat(severity.getName()).concat("] ").concat(msg);
        return eventMsg;
    }

    public String GetEventsText(boolean includeLineSeparators) {
        return GetEventsText(e -> true, includeLineSeparators);
    }

    public String GetEventsText(Predicate<Event> eventFilter, boolean includeLineSeparators)
    {
        var stringBuilder = new StringBuilder();
        for(var event : eventsList) {
            if(eventFilter.test(event)) {
                String msg = EventFormatter.format(event);
                EventSeverity severity = event.getSeverity();

                stringBuilder
                        .append("[").append(severity.getName()).append("] ")
                        .append(msg);

                if(includeLineSeparators) {
                    //Proper Header Line Break is CRLF...
                    stringBuilder.append("\r\n");
                }
            }
        }

        return stringBuilder.toString();
    }
}

