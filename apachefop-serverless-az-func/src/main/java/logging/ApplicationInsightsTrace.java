package logging;

import com.azure.monitor.opentelemetry.autoconfigure.AzureMonitorAutoConfigure;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

final class ApplicationInsightsTrace {
    private static final String CONNECTION_STRING_SETTING_KEY = "APPLICATIONINSIGHTS_CONNECTION_STRING";
    private static volatile Optional<ApplicationInsightsTrace> appInsightsWorkerTrace;

    private final Logger logger;

    ApplicationInsightsTrace(OpenTelemetrySdk appInsightsSdk) {
        logger = appInsightsSdk.getLogsBridge().loggerBuilder(ApplicationInsightsTrace.class.getName()).build();
    }

    static boolean tryLog(Level level, String message, Map<String, String> properties) {
        if (Level.OFF.equals(level)) {
            return true;
        }
        var appInsightsTrace = appInsightsWorkerTrace;
        if (appInsightsTrace == null) {
            appInsightsTrace = initializeAppInsightsWorkerTrace();
        }
        if (appInsightsTrace.isEmpty()) {
            return false;
        }

        appInsightsTrace.get().log(level, message, properties);
        return true;
    }

    private static synchronized Optional<ApplicationInsightsTrace> initializeAppInsightsWorkerTrace() {
        if (appInsightsWorkerTrace == null) {
            String connectionString = System.getenv(CONNECTION_STRING_SETTING_KEY);
            appInsightsWorkerTrace = connectionString == null || connectionString.isBlank()
                ? Optional.empty()
                : Optional.of(new ApplicationInsightsTrace(createAppInsightsSdk(connectionString)));
        }
        return appInsightsWorkerTrace;
    }

    static OpenTelemetrySdk createAppInsightsSdk(String connectionString) {
        var builder = AutoConfiguredOpenTelemetrySdk.builder();
        AzureMonitorAutoConfigure.customize(builder, connectionString);
        return builder.addPropertiesCustomizer(config -> Map.of(
                "otel.traces.exporter", "none",
                "otel.metrics.exporter", "none")
            )
            .build()
            .getOpenTelemetrySdk();
    }

    void log(Level level, String message, Map<String, String> properties) {
        if (Level.OFF.equals(level)) {
            return;
        }
        var record = logger
            .logRecordBuilder()
            .setBody(message)
            .setSeverity(mapSeverity(level))
            .setSeverityText(level.getName());

        properties.forEach((name, value) -> record.setAttribute(AttributeKey.stringKey(name), value));
        record.emit();
    }

    private static Severity mapSeverity(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return Severity.ERROR;
        }
        if (value >= Level.WARNING.intValue()) {
            return Severity.WARN;
        }
        if (value >= Level.INFO.intValue()) {
            return Severity.INFO;
        }
        if (value >= Level.FINE.intValue()) {
            return Severity.DEBUG;
        }
        return Severity.TRACE;
    }
}