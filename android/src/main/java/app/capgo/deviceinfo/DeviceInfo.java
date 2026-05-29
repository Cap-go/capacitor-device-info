package app.capgo.deviceinfo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.StatFs;
import com.getcapacitor.JSObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;

public class DeviceInfo {

    private CpuTicks previousCpuTicks;
    private String cachedCpuModel;
    private Long cachedMaxCpuFrequencyHz;
    private JSObject cachedGpuInfo;

    public JSObject getInfo(Context context) {
        JSObject result = new JSObject();
        result.put("timestamp", currentTimestamp());
        result.put("platform", "android");
        result.put("cpu", getCpuInfo());
        result.put("memory", getMemoryInfo(context));
        result.put("storage", getStorageInfo(context));
        result.put("gpu", getGpuInfo(context));
        result.put("thermalState", getThermalState(context));
        result.put("lowPowerMode", isLowPowerMode(context));
        result.put("sensors", getOnboardSensorsInfo(context));
        return result;
    }

    public String getPluginVersion() {
        return "8.0.0";
    }

    private JSObject getCpuInfo() {
        JSObject cpu = new JSObject();
        int cores = Runtime.getRuntime().availableProcessors();
        cpu.put("cores", cores);
        cpu.put("activeCores", cores);
        cpu.put("architecture", getArchitecture());

        String model = getCpuModel();
        if (model != null && !model.isEmpty()) {
            cpu.put("model", model);
        }

        Double usagePercent = getCpuUsagePercent();
        if (usagePercent != null) {
            cpu.put("usagePercent", usagePercent);
        }

        Long maxFrequencyHz = getMaxCpuFrequencyHz();
        if (maxFrequencyHz != null) {
            cpu.put("maxFrequencyHz", maxFrequencyHz.doubleValue());
        }
        putIfNotNull(cpu, "temperatureCelsius", getCpuTemperatureCelsius());

        return cpu;
    }

    private JSObject getMemoryInfo(Context context) {
        JSObject memory = new JSObject();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Runtime runtime = Runtime.getRuntime();
        long appUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        long appLimitBytes = runtime.maxMemory();

        memory.put("appUsedBytes", (double) appUsedBytes);
        memory.put("appLimitBytes", (double) appLimitBytes);
        memory.put("pressure", "unknown");

        if (activityManager == null) {
            return memory;
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalBytes = memoryInfo.totalMem;
        long freeBytes = memoryInfo.availMem;
        long usedBytes = Math.max(totalBytes - freeBytes, 0);

        memory.put("totalBytes", (double) totalBytes);
        memory.put("freeBytes", (double) freeBytes);
        memory.put("usedBytes", (double) usedBytes);
        memory.put("usedPercent", percent(usedBytes, totalBytes));
        memory.put("lowMemory", memoryInfo.lowMemory);
        memory.put("pressure", memoryPressure(memoryInfo));

        return memory;
    }

    private JSObject getStorageInfo(Context context) {
        JSObject storage = new JSObject();
        File filesDir = context.getFilesDir();
        File target = filesDir != null ? filesDir : context.getDataDir();
        StatFs statFs = new StatFs(target.getAbsolutePath());
        long totalBytes = statFs.getTotalBytes();
        long freeBytes = statFs.getAvailableBytes();
        long usedBytes = Math.max(totalBytes - freeBytes, 0);

        storage.put("totalBytes", (double) totalBytes);
        storage.put("freeBytes", (double) freeBytes);
        storage.put("usedBytes", (double) usedBytes);
        storage.put("usedPercent", percent(usedBytes, totalBytes));
        return storage;
    }

    private synchronized JSObject getGpuInfo(Context context) {
        if (cachedGpuInfo != null) {
            return gpuInfoWithDynamicValues(cachedGpuInfo);
        }

        JSObject gpu = new JSObject();
        gpu.put("api", "opengl");

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ConfigurationInfo config = activityManager.getDeviceConfigurationInfo();
            if (config != null) {
                gpu.put("version", formatGlEsVersion(config.reqGlEsVersion));
            }
        }

        GpuStrings gpuStrings = readGpuStrings();
        if (gpuStrings.vendor != null) {
            gpu.put("vendor", gpuStrings.vendor);
        }
        if (gpuStrings.renderer != null) {
            gpu.put("renderer", gpuStrings.renderer);
        }
        if (gpuStrings.version != null) {
            gpu.put("version", gpuStrings.version);
        }
        if (gpuStrings.maxTextureSize > 0) {
            gpu.put("maxTextureSize", gpuStrings.maxTextureSize);
        }

        cachedGpuInfo = gpu;
        return gpuInfoWithDynamicValues(gpu);
    }

    private JSObject gpuInfoWithDynamicValues(JSObject staticGpuInfo) {
        JSObject gpu = copyObject(staticGpuInfo);
        putIfNotNull(gpu, "temperatureCelsius", getGpuTemperatureCelsius());
        return gpu;
    }

    private JSObject getOnboardSensorsInfo(Context context) {
        JSObject sensors = new JSObject();
        JSONArray availableSensors = new JSONArray();
        JSONArray readings = new JSONArray();
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        if (sensorManager != null) {
            for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                availableSensors.put(sensorDescriptor(sensor));
            }

            Map<Integer, Double> sensorValues = readSensorValues(sensorManager, commonEnvironmentalSensorTypes());
            addSensorReading(
                sensorManager,
                sensorValues,
                sensors,
                readings,
                Sensor.TYPE_AMBIENT_TEMPERATURE,
                "ambientTemperature",
                "celsius",
                "ambientTemperatureCelsius"
            );
            addSensorReading(
                sensorManager,
                sensorValues,
                sensors,
                readings,
                Sensor.TYPE_RELATIVE_HUMIDITY,
                "relativeHumidity",
                "percent",
                "relativeHumidityPercent"
            );
            addSensorReading(sensorManager, sensorValues, sensors, readings, Sensor.TYPE_PRESSURE, "pressure", "hPa", "pressureHpa");
            addSensorReading(sensorManager, sensorValues, sensors, readings, Sensor.TYPE_LIGHT, "light", "lux", "illuminanceLux");
            addSensorReading(
                sensorManager,
                sensorValues,
                sensors,
                readings,
                Sensor.TYPE_PROXIMITY,
                "proximity",
                "cm",
                "proximityDistanceCm"
            );
        }

        Double batteryTemperature = getBatteryTemperatureCelsius(context);
        if (batteryTemperature != null) {
            sensors.put("batteryTemperatureCelsius", batteryTemperature);
            readings.put(readingObject("batteryTemperature", "celsius", batteryTemperature, "Battery temperature"));
        }

        sensors.put("availableSensors", availableSensors);
        sensors.put("readings", readings);
        return sensors;
    }

    private int[] commonEnvironmentalSensorTypes() {
        return new int[] {
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY
        };
    }

    private Map<Integer, Double> readSensorValues(SensorManager sensorManager, int[] sensorTypes) {
        Map<Integer, Double> values = new HashMap<>();
        List<Sensor> sensors = new ArrayList<>();

        for (int sensorType : sensorTypes) {
            Sensor sensor = sensorManager.getDefaultSensor(sensorType);
            if (sensor != null) {
                sensors.add(sensor);
            }
        }

        if (sensors.isEmpty()) {
            return values;
        }

        CountDownLatch latch = new CountDownLatch(sensors.size());
        HandlerThread handlerThread = new HandlerThread("CapgoDeviceInfoSensors");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        List<SensorEventListener> listeners = new ArrayList<>();

        try {
            for (Sensor sensor : sensors) {
                SensorEventListener listener = new SensorEventListener() {
                    private boolean recorded;

                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (recorded || event.values.length == 0) {
                            return;
                        }

                        recorded = true;
                        synchronized (values) {
                            values.put(sensor.getType(), (double) event.values[0]);
                        }
                        sensorManager.unregisterListener(this);
                        latch.countDown();
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };

                listeners.add(listener);
                if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)) {
                    sensorManager.unregisterListener(listener);
                    latch.countDown();
                }
            }

            if (!latch.await(180, TimeUnit.MILLISECONDS)) {
                return values;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            for (SensorEventListener listener : listeners) {
                sensorManager.unregisterListener(listener);
            }
            handlerThread.quitSafely();
        }

        return values;
    }

    private void addSensorReading(
        SensorManager sensorManager,
        Map<Integer, Double> sensorValues,
        JSObject sensors,
        JSONArray readings,
        int sensorType,
        String type,
        String unit,
        String fieldName
    ) {
        Double value = sensorValues.get(sensorType);
        if (value == null) {
            return;
        }

        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        sensors.put(fieldName, value);
        readings.put(readingObject(type, unit, value, sensor != null ? sensor.getName() : null));
    }

    private JSObject sensorDescriptor(Sensor sensor) {
        JSObject descriptor = new JSObject();
        descriptor.put("type", sensorTypeLabel(sensor.getType()));
        descriptor.put("platformType", sensor.getType());
        descriptor.put("name", sensor.getName());
        descriptor.put("vendor", sensor.getVendor());
        descriptor.put("maximumRange", (double) sensor.getMaximumRange());
        descriptor.put("resolution", (double) sensor.getResolution());
        descriptor.put("powerMilliamp", (double) sensor.getPower());
        descriptor.put("minDelayMicroseconds", sensor.getMinDelay());
        descriptor.put("wakeUp", sensor.isWakeUpSensor());
        return descriptor;
    }

    private JSObject readingObject(String type, String unit, Double value, String name) {
        JSObject reading = new JSObject();
        reading.put("type", type);
        reading.put("unit", unit);
        reading.put("value", value);
        reading.put("timestamp", currentTimestamp());
        if (name != null && !name.isEmpty()) {
            reading.put("name", name);
        }
        return reading;
    }

    private String sensorTypeLabel(int sensorType) {
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                return "accelerometer";
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return "ambientTemperature";
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return "gameRotationVector";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                return "geomagneticRotationVector";
            case Sensor.TYPE_GRAVITY:
                return "gravity";
            case Sensor.TYPE_GYROSCOPE:
                return "gyroscope";
            case Sensor.TYPE_LIGHT:
                return "light";
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return "linearAcceleration";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "magneticField";
            case Sensor.TYPE_PRESSURE:
                return "pressure";
            case Sensor.TYPE_PROXIMITY:
                return "proximity";
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return "relativeHumidity";
            case Sensor.TYPE_ROTATION_VECTOR:
                return "rotationVector";
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                return "significantMotion";
            case Sensor.TYPE_STEP_COUNTER:
                return "stepCounter";
            case Sensor.TYPE_STEP_DETECTOR:
                return "stepDetector";
            default:
                return "android_" + sensorType;
        }
    }

    private Double getBatteryTemperatureCelsius(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return null;
        }

        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (temperature == Integer.MIN_VALUE) {
            return null;
        }

        return temperature / 10.0;
    }

    private Double getCpuTemperatureCelsius() {
        return getThermalZoneTemperature("cpu", "soc", "ap", "tsens");
    }

    private Double getGpuTemperatureCelsius() {
        return getThermalZoneTemperature("gpu", "gpuss");
    }

    private Double getThermalZoneTemperature(String... typeHints) {
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) {
            return null;
        }

        for (File zone : zones) {
            String type = readFirstString(new File(zone, "type"));
            if (!matchesThermalType(type, typeHints)) {
                continue;
            }

            Double temperature = normalizeThermalTemperature(readFirstString(new File(zone, "temp")));
            if (temperature != null) {
                return temperature;
            }
        }

        return null;
    }

    private String getArchitecture() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0];
        }
        return "unknown";
    }

    private String getCpuModel() {
        if (cachedCpuModel != null) {
            return cachedCpuModel;
        }

        cachedCpuModel = readCpuModel();
        return cachedCpuModel;
    }

    private String readCpuModel() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase(Locale.US);
                if (lower.startsWith("hardware") || lower.startsWith("processor") || lower.startsWith("model name")) {
                    int separator = line.indexOf(':');
                    if (separator >= 0 && separator + 1 < line.length()) {
                        String value = line.substring(separator + 1).trim();
                        if (!value.isEmpty()) {
                            return value;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Some devices restrict /proc/cpuinfo. Other CPU fields still work.
        }
        return null;
    }

    private synchronized Double getCpuUsagePercent() {
        CpuTicks currentTicks = readCpuTicks();
        if (currentTicks == null) {
            return null;
        }

        try {
            if (previousCpuTicks == null) {
                return null;
            }

            long totalDelta = currentTicks.total - previousCpuTicks.total;
            long idleDelta = currentTicks.idle - previousCpuTicks.idle;
            if (totalDelta <= 0) {
                return null;
            }

            return clampPercent(((double) (totalDelta - idleDelta) / (double) totalDelta) * 100.0);
        } finally {
            previousCpuTicks = currentTicks;
        }
    }

    private CpuTicks readCpuTicks() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }

            String[] parts = line.trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < parts.length; i++) {
                total += parseLong(parts[i]);
            }

            long idle = parts.length > 4 ? parseLong(parts[4]) : 0;
            long ioWait = parts.length > 5 ? parseLong(parts[5]) : 0;
            return new CpuTicks(total, idle + ioWait);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Long getMaxCpuFrequencyHz() {
        if (cachedMaxCpuFrequencyHz != null) {
            return cachedMaxCpuFrequencyHz;
        }

        long maxKHz = 0;
        int cores = Math.max(Runtime.getRuntime().availableProcessors(), 1);
        for (int index = 0; index < cores; index++) {
            File file = new File("/sys/devices/system/cpu/cpu" + index + "/cpufreq/cpuinfo_max_freq");
            Long value = readFirstLong(file);
            if (value != null) {
                maxKHz = Math.max(maxKHz, value);
            }
        }

        cachedMaxCpuFrequencyHz = maxKHz > 0 ? maxKHz * 1000L : null;
        return cachedMaxCpuFrequencyHz;
    }

    private Long readFirstLong(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseLong(reader.readLine());
        } catch (IOException ignored) {
            return null;
        }
    }

    private String readFirstString(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException ignored) {
            return null;
        }
    }

    private GpuStrings readGpuStrings() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            return new GpuStrings();
        }

        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;

        try {
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return new GpuStrings();
            }

            int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, configs.length, configCount, 0) || configCount[0] == 0) {
                return new GpuStrings();
            }

            int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
            if (context == EGL14.EGL_NO_CONTEXT) {
                return new GpuStrings();
            }

            int[] surfaceAttributes = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttributes, 0);
            if (surface == EGL14.EGL_NO_SURFACE) {
                return new GpuStrings();
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return new GpuStrings();
            }

            GpuStrings strings = new GpuStrings();
            strings.vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            strings.renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            strings.version = GLES20.glGetString(GLES20.GL_VERSION);
            int[] maxTextureSize = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
            strings.maxTextureSize = maxTextureSize[0];
            return strings;
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, surface);
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context);
            }
            EGL14.eglTerminate(display);
        }
    }

    private String formatGlEsVersion(int reqGlEsVersion) {
        int major = (reqGlEsVersion & 0xffff0000) >> 16;
        int minor = reqGlEsVersion & 0x0000ffff;
        return "OpenGL ES " + major + "." + minor;
    }

    private String memoryPressure(ActivityManager.MemoryInfo memoryInfo) {
        if (memoryInfo.lowMemory) {
            return "critical";
        }
        if (memoryInfo.threshold > 0 && memoryInfo.availMem < memoryInfo.threshold * 2) {
            return "warning";
        }
        return "normal";
    }

    private String getThermalState(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "unknown";
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return "unknown";
        }

        switch (powerManager.getCurrentThermalStatus()) {
            case PowerManager.THERMAL_STATUS_NONE:
                return "nominal";
            case PowerManager.THERMAL_STATUS_LIGHT:
            case PowerManager.THERMAL_STATUS_MODERATE:
                return "fair";
            case PowerManager.THERMAL_STATUS_SEVERE:
                return "serious";
            case PowerManager.THERMAL_STATUS_CRITICAL:
            case PowerManager.THERMAL_STATUS_EMERGENCY:
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "critical";
            default:
                return "unknown";
        }
    }

    private boolean isLowPowerMode(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    private double currentTimestamp() {
        return (double) System.currentTimeMillis();
    }

    private long parseLong(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Double normalizeThermalTemperature(String value) {
        if (value == null) {
            return null;
        }

        try {
            double temperature = Double.parseDouble(value.trim());
            double absolute = Math.abs(temperature);
            if (absolute > 1000) {
                temperature /= 1000.0;
            } else if (absolute > 150) {
                temperature /= 10.0;
            }

            return temperature >= -50 && temperature <= 150 ? temperature : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean matchesThermalType(String type, String... hints) {
        if (type == null) {
            return false;
        }

        String normalized = type.toLowerCase(Locale.US);
        for (String hint : hints) {
            if (normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private void putIfNotNull(JSObject object, String key, Object value) {
        if (value != null) {
            object.put(key, value);
        }
    }

    private JSObject copyObject(JSObject object) {
        try {
            return new JSObject(object.toString());
        } catch (JSONException ignored) {
            return object;
        }
    }

    private double percent(long used, long total) {
        if (total <= 0) {
            return 0;
        }
        return clampPercent(((double) used / (double) total) * 100.0);
    }

    private double clampPercent(double value) {
        return Math.min(Math.max(value, 0), 100);
    }

    private static class CpuTicks {

        final long total;
        final long idle;

        CpuTicks(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }

    private static class GpuStrings {

        String vendor;
        String renderer;
        String version;
        int maxTextureSize;
    }
}
