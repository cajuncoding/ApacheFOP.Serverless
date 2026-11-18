package logging;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkerStartupDiagnosticsTest {
    @Test
    void reportsDisabledIntrinsicsOnlyOnceAcrossConcurrentCalls() {
        assertSingleRecord(() -> "FaLsE", "MitigationActive", Level.INFO);
    }

    @Test
    void warnsWhenIntrinsicsAreEnabled() {
        assertSingleRecord(() -> "TrUe", "MitigationNotActive", Level.WARNING);
    }

    @Test
    void warnsWhenTheFlagCannotBeRead() {
        assertSingleRecord(() -> null, "Unknown", Level.WARNING);
        assertSingleRecord(() -> "unrecognized", "Unknown", Level.WARNING);
        assertSingleRecord(() -> { throw new IllegalArgumentException(); }, "Unknown", Level.WARNING);
    }

    private void assertSingleRecord(Supplier<String> flag, String status, Level level) {
        Logger logger = mock(Logger.class);
        var traceSink = mock(WorkerStartupDiagnostics.TraceSink.class);
        var diagnostics = new WorkerStartupDiagnostics(flag, traceSink);
        String message = "CVE-2026-114938 'Java4Shell' Vulnerability Mitigation Status: [%s]".formatted(status);
        var properties = Map.of("CveVulnerability", "CVE-2026-114938",
            "VulnerabilityNickname", "Java4Shell", "VulnerabilityStatus", status);
        when(traceSink.tryLog(level, message, properties)).thenReturn(true);

        IntStream.range(0, 32).parallel().forEach(attempt -> diagnostics.logOnce(logger));

        verify(traceSink).tryLog(level, message, properties);
        verifyNoMoreInteractions(traceSink);
        verifyNoInteractions(logger);
        }

        @Test
        void logsPlainTextWhenApplicationInsightsIsNotConfigured() {
        Logger logger = mock(Logger.class);
        var diagnostics = new WorkerStartupDiagnostics(Boolean.FALSE::toString, (level, message, properties) -> false);

        diagnostics.logOnce(logger);
        diagnostics.logOnce(logger);

        verify(logger).log(Level.INFO, "CVE-2026-114938 'Java4Shell' Vulnerability Mitigation Status: [MitigationActive]");
        verifyNoMoreInteractions(logger);
    }
}