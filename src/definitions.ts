import type { PluginListenerHandle } from '@capacitor/core';

/**
 * CPU snapshot for the current device.
 *
 * All frequency values are reported in hertz. `usagePercent` is `null` when a
 * platform needs at least two samples to calculate CPU usage.
 *
 * @since 8.0.0
 */
export interface CpuInfo {
  /**
   * Total logical CPU cores visible to the app.
   *
   * @since 8.0.0
   */
  cores: number;

  /**
   * Logical CPU cores currently active, when the platform exposes it.
   *
   * @since 8.0.0
   */
  activeCores?: number;

  /**
   * CPU architecture, for example `arm64` or `x86_64`.
   *
   * @since 8.0.0
   */
  architecture?: string;

  /**
   * CPU or SoC model identifier when available.
   *
   * @since 8.0.0
   */
  model?: string;

  /**
   * System CPU usage from 0 to 100.
   *
   * @since 8.0.0
   */
  usagePercent?: number | null;

  /**
   * Highest CPU frequency exposed by the platform.
   *
   * @since 8.0.0
   */
  maxFrequencyHz?: number;

  /**
   * CPU temperature in Celsius when the platform exposes an onboard thermal sensor.
   *
   * Android reads this as a best-effort value from device thermal zones. iOS does
   * not expose raw CPU temperature through public APIs.
   *
   * @since 8.0.0
   */
  temperatureCelsius?: number;
}

/**
 * Memory snapshot for the current device and app process.
 *
 * All size values are reported in bytes.
 *
 * @since 8.0.0
 */
export interface MemoryInfo {
  /**
   * Total physical memory on the device.
   *
   * @since 8.0.0
   */
  totalBytes?: number;

  /**
   * Memory available to the system.
   *
   * @since 8.0.0
   */
  freeBytes?: number;

  /**
   * Memory currently used by the system.
   *
   * @since 8.0.0
   */
  usedBytes?: number;

  /**
   * Memory usage from 0 to 100.
   *
   * @since 8.0.0
   */
  usedPercent?: number;

  /**
   * Memory used by the current app process.
   *
   * @since 8.0.0
   */
  appUsedBytes?: number;

  /**
   * Heap limit visible to the current app process.
   *
   * @since 8.0.0
   */
  appLimitBytes?: number;

  /**
   * Whether the OS currently reports low-memory pressure.
   *
   * @since 8.0.0
   */
  lowMemory?: boolean;

  /**
   * Platform memory pressure label.
   *
   * @since 8.0.0
   */
  pressure?: 'normal' | 'warning' | 'critical' | 'unknown';
}

/**
 * Storage snapshot for the app data volume.
 *
 * All size values are reported in bytes.
 *
 * @since 8.0.0
 */
export interface StorageInfo {
  /**
   * Total bytes on the app data volume.
   *
   * @since 8.0.0
   */
  totalBytes?: number;

  /**
   * Free bytes on the app data volume.
   *
   * @since 8.0.0
   */
  freeBytes?: number;

  /**
   * Used bytes on the app data volume.
   *
   * @since 8.0.0
   */
  usedBytes?: number;

  /**
   * Storage usage from 0 to 100.
   *
   * @since 8.0.0
   */
  usedPercent?: number;
}

/**
 * GPU snapshot for the primary graphics device.
 *
 * @since 8.0.0
 */
export interface GpuInfo {
  /**
   * Graphics API used to query the GPU.
   *
   * @since 8.0.0
   */
  api?: 'metal' | 'opengl' | 'webgl' | 'unknown';

  /**
   * GPU vendor when available.
   *
   * @since 8.0.0
   */
  vendor?: string;

  /**
   * GPU renderer or model name when available.
   *
   * @since 8.0.0
   */
  renderer?: string;

  /**
   * Graphics API version string when available.
   *
   * @since 8.0.0
   */
  version?: string;

  /**
   * Maximum texture size reported by the graphics API.
   *
   * @since 8.0.0
   */
  maxTextureSize?: number;

  /**
   * GPU temperature in Celsius when the platform exposes an onboard thermal sensor.
   *
   * Android reads this as a best-effort value from device thermal zones. iOS does
   * not expose raw GPU temperature through public APIs.
   *
   * @since 8.0.0
   */
  temperatureCelsius?: number;
}

/**
 * Thermal state reported by the platform.
 *
 * @since 8.0.0
 */
export type ThermalState = 'nominal' | 'fair' | 'serious' | 'critical' | 'unknown';

/**
 * Description of an onboard hardware sensor exposed by the platform.
 *
 * @since 8.0.0
 */
export interface OnboardSensorDescriptor {
  /**
   * Stable sensor type label, for example `pressure`, `ambientTemperature`, or `accelerometer`.
   *
   * @since 8.0.0
   */
  type: string;

  /**
   * Platform sensor name when available.
   *
   * @since 8.0.0
   */
  name?: string;

  /**
   * Sensor vendor when available.
   *
   * @since 8.0.0
   */
  vendor?: string;

  /**
   * Android sensor type id when available.
   *
   * @since 8.0.0
   */
  platformType?: number;

  /**
   * Maximum sensor range when available.
   *
   * @since 8.0.0
   */
  maximumRange?: number;

  /**
   * Sensor resolution when available.
   *
   * @since 8.0.0
   */
  resolution?: number;

  /**
   * Sensor power draw in milliamps when available.
   *
   * @since 8.0.0
   */
  powerMilliamp?: number;

  /**
   * Minimum sensor delay in microseconds when available.
   *
   * @since 8.0.0
   */
  minDelayMicroseconds?: number;

  /**
   * Whether this is a wake-up sensor when available.
   *
   * @since 8.0.0
   */
  wakeUp?: boolean;
}

/**
 * Instant onboard sensor reading.
 *
 * @since 8.0.0
 */
export interface OnboardSensorReading {
  /**
   * Stable sensor type label.
   *
   * @since 8.0.0
   */
  type: string;

  /**
   * Human-readable unit, for example `celsius`, `percent`, `hPa`, `lux`, or `cm`.
   *
   * @since 8.0.0
   */
  unit: string;

  /**
   * Sensor value.
   *
   * @since 8.0.0
   */
  value: number;

  /**
   * Platform sensor name when available.
   *
   * @since 8.0.0
   */
  name?: string;

  /**
   * Reading timestamp as Unix epoch milliseconds.
   *
   * @since 8.0.0
   */
  timestamp?: number;
}

/**
 * Onboard sensors snapshot.
 *
 * This only reports hardware sensors exposed by the device or operating system.
 * It does not fetch weather data or any external temperature/humidity source.
 *
 * @since 8.0.0
 */
export interface OnboardSensorsInfo {
  /**
   * Sensors available to the app.
   *
   * @since 8.0.0
   */
  availableSensors?: OnboardSensorDescriptor[];

  /**
   * Instant sensor readings captured for common environmental sensors.
   *
   * @since 8.0.0
   */
  readings?: OnboardSensorReading[];

  /**
   * Battery temperature in Celsius when available.
   *
   * @since 8.0.0
   */
  batteryTemperatureCelsius?: number;

  /**
   * Ambient air temperature from an onboard sensor in Celsius when available.
   *
   * @since 8.0.0
   */
  ambientTemperatureCelsius?: number;

  /**
   * Relative humidity from an onboard sensor as a percentage when available.
   *
   * @since 8.0.0
   */
  relativeHumidityPercent?: number;

  /**
   * Atmospheric pressure from an onboard barometer in hectopascals when available.
   *
   * @since 8.0.0
   */
  pressureHpa?: number;

  /**
   * Ambient light from an onboard light sensor in lux when available.
   *
   * @since 8.0.0
   */
  illuminanceLux?: number;

  /**
   * Proximity distance from an onboard proximity sensor in centimeters when available.
   *
   * @since 8.0.0
   */
  proximityDistanceCm?: number;
}

/**
 * Instant device snapshot returned by {@link DeviceInfoPlugin.getInfo}.
 *
 * @since 8.0.0
 */
export interface DeviceInfoSnapshot {
  /**
   * Snapshot timestamp as Unix epoch milliseconds.
   *
   * @since 8.0.0
   */
  timestamp: number;

  /**
   * Platform implementation that produced the snapshot.
   *
   * @since 8.0.0
   */
  platform: 'ios' | 'android' | 'web';

  /**
   * CPU information and usage.
   *
   * @since 8.0.0
   */
  cpu: CpuInfo;

  /**
   * Memory information and usage.
   *
   * @since 8.0.0
   */
  memory: MemoryInfo;

  /**
   * Storage information and usage.
   *
   * @since 8.0.0
   */
  storage: StorageInfo;

  /**
   * GPU information when the platform exposes it.
   *
   * @since 8.0.0
   */
  gpu?: GpuInfo;

  /**
   * Thermal state when the platform exposes it.
   *
   * @since 8.0.0
   */
  thermalState?: ThermalState;

  /**
   * Low-power mode state when the platform exposes it.
   *
   * @since 8.0.0
   */
  lowPowerMode?: boolean;

  /**
   * Onboard sensor availability and readings.
   *
   * @since 8.0.0
   */
  sensors?: OnboardSensorsInfo;
}

/**
 * Options used to start periodic device snapshots.
 *
 * @since 8.0.0
 */
export interface MonitoringOptions {
  /**
   * Time between samples in milliseconds.
   *
   * Values below 250ms are clamped to 250ms to avoid excessive native work.
   * Defaults to 1000ms.
   *
   * @since 8.0.0
   */
  intervalMs?: number;

  /**
   * Stop automatically after this duration in milliseconds.
   *
   * @since 8.0.0
   */
  durationMs?: number;

  /**
   * Stop automatically after this number of emitted samples.
   *
   * @since 8.0.0
   */
  sampleCount?: number;

  /**
   * Emit one sample immediately when monitoring starts.
   *
   * Defaults to `true`.
   *
   * @since 8.0.0
   */
  emitImmediately?: boolean;
}

/**
 * Result returned when monitoring starts.
 *
 * @since 8.0.0
 */
export interface StartMonitoringResult {
  /**
   * Whether monitoring is active.
   *
   * @since 8.0.0
   */
  monitoring: boolean;

  /**
   * Effective interval in milliseconds.
   *
   * @since 8.0.0
   */
  intervalMs: number;

  /**
   * Monitoring start timestamp as Unix epoch milliseconds.
   *
   * @since 8.0.0
   */
  startedAt: number;
}

/**
 * Result returned by {@link DeviceInfoPlugin.stopMonitoring}.
 *
 * @since 8.0.0
 */
export interface StopMonitoringResult {
  /**
   * Whether monitoring remains active after the stop request.
   *
   * @since 8.0.0
   */
  monitoring: boolean;
}

/**
 * Current monitoring state.
 *
 * @since 8.0.0
 */
export interface MonitoringState {
  /**
   * Whether monitoring is active.
   *
   * @since 8.0.0
   */
  monitoring: boolean;

  /**
   * Effective interval in milliseconds when monitoring is active.
   *
   * @since 8.0.0
   */
  intervalMs?: number;

  /**
   * Monitoring start timestamp as Unix epoch milliseconds.
   *
   * @since 8.0.0
   */
  startedAt?: number;

  /**
   * Number of samples emitted by the active monitoring session.
   *
   * @since 8.0.0
   */
  samplesEmitted?: number;
}

/**
 * Periodic event payload emitted while monitoring is active.
 *
 * @since 8.0.0
 */
export interface DeviceInfoUpdate extends DeviceInfoSnapshot {
  /**
   * One-based sample sequence for the active monitoring session.
   *
   * @since 8.0.0
   */
  sequence: number;

  /**
   * Monitoring start timestamp as Unix epoch milliseconds.
   *
   * @since 8.0.0
   */
  startedAt: number;

  /**
   * Elapsed milliseconds since monitoring started.
   *
   * @since 8.0.0
   */
  elapsedMs: number;
}

/**
 * Plugin version payload.
 *
 * @since 8.0.0
 */
export interface PluginVersionResult {
  /**
   * Version identifier returned by the platform implementation.
   *
   * @since 8.0.0
   */
  version: string;
}

/**
 * Capacitor plugin contract for reading device CPU, memory, GPU, storage, and onboard sensor metrics.
 *
 * @since 8.0.0
 */
export interface DeviceInfoPlugin {
  /**
   * Read one CPU, memory, GPU, storage, thermal, and onboard sensor snapshot.
   *
   * @since 8.0.0
   */
  getInfo(): Promise<DeviceInfoSnapshot>;

  /**
   * Start periodic device snapshots.
   *
   * Listen to `deviceInfoUpdate` to receive samples. Calling this while monitoring
   * is already active restarts monitoring with the new options.
   *
   * @since 8.0.0
   */
  startMonitoring(options?: MonitoringOptions): Promise<StartMonitoringResult>;

  /**
   * Stop periodic device snapshots.
   *
   * @since 8.0.0
   */
  stopMonitoring(): Promise<StopMonitoringResult>;

  /**
   * Return current periodic monitoring state.
   *
   * @since 8.0.0
   */
  isMonitoring(): Promise<MonitoringState>;

  /**
   * Listen for periodic device snapshots.
   *
   * @param eventName Only the `deviceInfoUpdate` event is supported.
   * @param listenerFunc Callback invoked with each snapshot.
   * @since 8.0.0
   */
  addListener(
    eventName: 'deviceInfoUpdate',
    listenerFunc: (event: DeviceInfoUpdate) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners that have been registered on the plugin.
   *
   * @since 8.0.0
   */
  removeAllListeners(): Promise<void>;

  /**
   * Get the native Capacitor plugin version.
   *
   * @since 8.0.0
   */
  getPluginVersion(): Promise<PluginVersionResult>;
}
