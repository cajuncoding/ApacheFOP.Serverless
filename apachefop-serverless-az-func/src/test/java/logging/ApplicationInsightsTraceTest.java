package logging;

import com.azure.json.JsonProviders;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApplicationInsightsTraceTest {
    @Test
    void exportsPropertiesSeparatelyFromTheTraceMessage() throws Exception {
        var requests = new LinkedBlockingQueue<byte[]>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange; var body = exchange.getRequestBody()) {
                if ("gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
                    try (var payload = new GZIPInputStream(body)) {
                        requests.add(payload.readAllBytes());
                    }
                } else {
                    requests.add(body.readAllBytes());
                }
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();

        String connectionString = "InstrumentationKey=00000000-0000-0000-0000-000000000001;IngestionEndpoint=http://127.0.0.1:" + server.getAddress().getPort();
        var properties = Map.of(
            "CveVulnerability",
            "CVE-2026-114938",
            "VulnerabilityNickname",
            "Java4Shell",
            "VulnerabilityStatus",
            "MitigationNotActive"
        );

        try (var appInsightsSdk = ApplicationInsightsTrace.createAppInsightsSdk(connectionString)) {
            var appInsightsTrace = new ApplicationInsightsTrace(appInsightsSdk);
            appInsightsTrace.log(Level.WARNING, "Worker mitigation check", properties);
            appInsightsSdk.getSdkLoggerProvider().forceFlush().join(10, TimeUnit.SECONDS);

            byte[] payload = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull(payload, "No log arrived at the local ingestion endpoint");
            
            try (var reader = JsonProviders.createReader(payload)) {
                var envelope = reader.readMap(value -> value.readUntyped());
                var data = (Map<?, ?>) envelope.get("data");
                assertEquals("MessageData", data.get("baseType"));
                var appInsightsTraceData = (Map<?, ?>) data.get("baseData");
                assertEquals("Worker mitigation check", appInsightsTraceData.get("message"));
                assertEquals("Warning", appInsightsTraceData.get("severityLevel"));
                var dimensions = (Map<?, ?>) appInsightsTraceData.get("properties");
                properties.forEach((name, value) -> assertEquals(value, dimensions.get(name)));
            }

            appInsightsTrace.log(Level.SEVERE, "Another worker diagnostic", Map.of("DiagnosticType", "Startup"));
            appInsightsSdk.getSdkLoggerProvider().forceFlush().join(10, TimeUnit.SECONDS);

            byte[] nextPayload = requests.poll(5, TimeUnit.SECONDS);
            assertNotNull(nextPayload, "No second log arrived at the local ingestion endpoint");
            try (var reader = JsonProviders.createReader(nextPayload)) {
                var envelope = reader.readMap(value -> value.readUntyped());
                var data = (Map<?, ?>) envelope.get("data");
                var appInsightsTraceData = (Map<?, ?>) data.get("baseData");
                assertEquals("Another worker diagnostic", appInsightsTraceData.get("message"));
                assertEquals("Error", appInsightsTraceData.get("severityLevel"));
                var dimensions = (Map<?, ?>) appInsightsTraceData.get("properties");
                assertEquals("Startup", dimensions.get("DiagnosticType"));
                properties.keySet().forEach(name -> assertFalse(dimensions.containsKey(name), name));
            }
        } finally {
            server.stop(0);
        }
    }
}