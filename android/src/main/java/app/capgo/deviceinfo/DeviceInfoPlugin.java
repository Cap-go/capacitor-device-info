package app.capgo.deviceinfo;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(name = "DeviceInfo")
public class DeviceInfoPlugin extends Plugin {

    private static final String EVENT_DEVICE_INFO_UPDATE = "deviceInfoUpdate";
    private static final int DEFAULT_INTERVAL_MS = 1000;
    private static final int MIN_INTERVAL_MS = 250;

    private final DeviceInfo implementation = new DeviceInfo();
    private final Object monitoringLock = new Object();
    private final ExecutorService readExecutor = Executors.newSingleThreadExecutor();

    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> monitoringTask;
    private int intervalMs;
    private long startedAt;
    private int samplesEmitted;
    private Integer sampleLimit;
    private Long stopAt;

    @PluginMethod
    public void getInfo(PluginCall call) {
        readExecutor.execute(() -> resolveCall(call, implementation.getInfo(getContext())));
    }

    @PluginMethod
    public void startMonitoring(PluginCall call) {
        int effectiveIntervalMs = Math.max(call.getInt("intervalMs", DEFAULT_INTERVAL_MS), MIN_INTERVAL_MS);
        Integer requestedSampleLimit = call.getInt("sampleCount", null);
        Double requestedDurationMs = call.getDouble("durationMs");
        boolean emitImmediately = call.getBoolean("emitImmediately", true);
        long now = System.currentTimeMillis();

        synchronized (monitoringLock) {
            stopMonitoringInternal();
            intervalMs = effectiveIntervalMs;
            startedAt = now;
            samplesEmitted = 0;
            sampleLimit = requestedSampleLimit != null && requestedSampleLimit > 0 ? requestedSampleLimit : null;
            stopAt = requestedDurationMs != null && requestedDurationMs > 0 ? now + requestedDurationMs.longValue() : null;
            executorService = Executors.newSingleThreadScheduledExecutor();

            if (emitImmediately) {
                executorService.execute(this::emitSample);
            }

            if (isMonitoringLocked()) {
                monitoringTask = executorService.scheduleAtFixedRate(
                    this::emitSample,
                    effectiveIntervalMs,
                    effectiveIntervalMs,
                    TimeUnit.MILLISECONDS
                );
            }
        }

        JSObject ret = new JSObject();
        ret.put("monitoring", isMonitoring());
        ret.put("intervalMs", effectiveIntervalMs);
        ret.put("startedAt", (double) now);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopMonitoring(PluginCall call) {
        synchronized (monitoringLock) {
            stopMonitoringInternal();
        }

        JSObject ret = new JSObject();
        ret.put("monitoring", false);
        call.resolve(ret);
    }

    @PluginMethod
    public void isMonitoring(PluginCall call) {
        JSObject ret = new JSObject();

        synchronized (monitoringLock) {
            boolean monitoring = isMonitoringLocked();
            ret.put("monitoring", monitoring);
            if (monitoring) {
                ret.put("intervalMs", intervalMs);
                ret.put("startedAt", (double) startedAt);
                ret.put("samplesEmitted", samplesEmitted);
            }
        }

        call.resolve(ret);
    }

    @PluginMethod
    public void removeAllListeners(PluginCall call) {
        super.removeAllListeners(call);
    }

    @PluginMethod
    public void getPluginVersion(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("version", implementation.getPluginVersion());
        call.resolve(ret);
    }

    @Override
    protected void handleOnDestroy() {
        synchronized (monitoringLock) {
            stopMonitoringInternal();
        }
        readExecutor.shutdownNow();
        super.handleOnDestroy();
    }

    private void resolveCall(PluginCall call, JSObject data) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> call.resolve(data));
        } else {
            call.resolve(data);
        }
    }

    private void emitSample() {
        JSObject sample;
        boolean shouldStop;

        synchronized (monitoringLock) {
            if (!isMonitoringLocked()) {
                return;
            }

            sample = implementation.getInfo(getContext());
            samplesEmitted += 1;
            sample.put("sequence", samplesEmitted);
            sample.put("startedAt", (double) startedAt);
            sample.put("elapsedMs", System.currentTimeMillis() - startedAt);
            shouldStop = shouldStopMonitoringLocked();
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> notifyListeners(EVENT_DEVICE_INFO_UPDATE, sample));
        } else {
            notifyListeners(EVENT_DEVICE_INFO_UPDATE, sample);
        }

        if (shouldStop) {
            synchronized (monitoringLock) {
                stopMonitoringInternal();
            }
        }
    }

    private boolean shouldStopMonitoringLocked() {
        if (sampleLimit != null && samplesEmitted >= sampleLimit) {
            return true;
        }
        return stopAt != null && System.currentTimeMillis() >= stopAt;
    }

    private boolean isMonitoring() {
        synchronized (monitoringLock) {
            return isMonitoringLocked();
        }
    }

    private boolean isMonitoringLocked() {
        return executorService != null && !executorService.isShutdown() && startedAt > 0;
    }

    private void stopMonitoringInternal() {
        if (monitoringTask != null) {
            monitoringTask.cancel(false);
            monitoringTask = null;
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        intervalMs = 0;
        startedAt = 0;
        samplesEmitted = 0;
        sampleLimit = null;
        stopAt = null;
    }
}
