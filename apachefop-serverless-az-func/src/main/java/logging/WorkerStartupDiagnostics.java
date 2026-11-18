package logging;

import com.sun.management.HotSpotDiagnosticMXBean;

import static logging.VulnerabilityDiagnosticsConstants.BASE64_INTRINSICS_OPTION;
import static logging.VulnerabilityDiagnosticsConstants.FALSE_VALUE;
import static logging.VulnerabilityDiagnosticsConstants.LOG_PROP_CVE;
import static logging.VulnerabilityDiagnosticsConstants.LOG_PROP_NICKNAME;
import static logging.VulnerabilityDiagnosticsConstants.LOG_PROP_STATUS;
import static logging.VulnerabilityDiagnosticsConstants.MITIGATION_ACTIVE;
import static logging.VulnerabilityDiagnosticsConstants.MITIGATION_NOT_ACTIVE;
import static logging.VulnerabilityDiagnosticsConstants.MITIGATION_UNKNOWN;
import static logging.VulnerabilityDiagnosticsConstants.TRUE_VALUE;
import static logging.VulnerabilityDiagnosticsConstants.VULNERABILITY_JAVA4SHELL_CVE;
import static logging.VulnerabilityDiagnosticsConstants.VULNERABILITY_JAVA4SHELL_NICKNAME;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WorkerStartupDiagnostics {
    private static final WorkerStartupDiagnostics WORKER = new WorkerStartupDiagnostics(() -> {
        var hotSpot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        return hotSpot == null ? null : hotSpot.getVMOption(BASE64_INTRINSICS_OPTION).getValue();
    }, ApplicationInsightsTrace::tryLog);

    private final AtomicBoolean reported = new AtomicBoolean();
    private final Supplier<String> readFlag;
    private final TraceSink traceSink;

    WorkerStartupDiagnostics(Supplier<String> readFlag, TraceSink traceSink) {
        this.readFlag = readFlag;
        this.traceSink = traceSink;
    }

    @FunctionalInterface
    interface TraceSink {
        boolean tryLog(Level level, String message, Map<String, String> properties);
    }

    public static void report(Logger logger) {
        WORKER.logOnce(logger);
    }

    void logOnce(Logger logger) {
        if (!reported.compareAndSet(false, true)) {
            return;
        }

        String status = determineMitigationStatus();
        Level level = MITIGATION_ACTIVE.equals(status) ? Level.INFO : Level.WARNING;
        String message = "%s '%s' Vulnerability Mitigation Status: [%s]".formatted(
            VULNERABILITY_JAVA4SHELL_CVE,
            VULNERABILITY_JAVA4SHELL_NICKNAME,
            status
        );
        var properties = Map.of(
            LOG_PROP_CVE, VULNERABILITY_JAVA4SHELL_CVE,
            LOG_PROP_NICKNAME, VULNERABILITY_JAVA4SHELL_NICKNAME,
            LOG_PROP_STATUS, status
        );

        try {
            if (traceSink.tryLog(level, message, properties)) {
                return;
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, message + " (Application Insights export unavailable)", exception);
            return;
        }
        logger.log(level, message);
    }

    private String determineMitigationStatus() {
        try {
            String flag = readFlag.get();
            if (FALSE_VALUE.equalsIgnoreCase(flag)) {
                return MITIGATION_ACTIVE;
            }
            if (TRUE_VALUE.equalsIgnoreCase(flag)) {
                return MITIGATION_NOT_ACTIVE;
            }
        } catch (IllegalArgumentException | SecurityException | UnsupportedOperationException ignored) {
            return MITIGATION_UNKNOWN;
        }
        return MITIGATION_UNKNOWN;
    }
}