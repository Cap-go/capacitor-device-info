import { WebPlugin } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import type {
  DeviceInfoPlugin,
  DeviceInfoSnapshot,
  DeviceInfoUpdate,
  GpuInfo,
  MemoryInfo,
  MonitoringOptions,
  MonitoringState,
  OnboardSensorsInfo,
  PluginVersionResult,
  StartMonitoringResult,
  StopMonitoringResult,
  StorageInfo,
} from './definitions';

type TimerHandle = ReturnType<typeof setInterval>;

interface NavigatorWithDeviceMemory extends Navigator {
  deviceMemory?: number;
}

interface PerformanceWithMemory extends Performance {
  memory?: {
    jsHeapSizeLimit?: number;
    totalJSHeapSize?: number;
    usedJSHeapSize?: number;
  };
}

const DEFAULT_INTERVAL_MS = 1000;
const MIN_INTERVAL_MS = 250;

export class DeviceInfoWeb extends WebPlugin implements DeviceInfoPlugin {
  private timer: TimerHandle | null = null;
  private startedAt: number | undefined;
  private intervalMs: number | undefined;
  private samplesEmitted = 0;
  private sampleLimit: number | undefined;
  private stopAt: number | undefined;
  private isEmitting = false;

  async getInfo(): Promise<DeviceInfoSnapshot> {
    return {
      timestamp: Date.now(),
      platform: 'web',
      cpu: {
        cores: navigator.hardwareConcurrency || 1,
        usagePercent: null,
      },
      memory: this.getMemoryInfo(),
      storage: await this.getStorageInfo(),
      gpu: this.getGpuInfo(),
      sensors: this.getOnboardSensorsInfo(),
    };
  }

  async startMonitoring(options: MonitoringOptions = {}): Promise<StartMonitoringResult> {
    await this.stopMonitoring();

    const intervalMs = this.normalizeInterval(options.intervalMs);
    const now = Date.now();
    this.intervalMs = intervalMs;
    this.startedAt = now;
    this.samplesEmitted = 0;
    this.sampleLimit = this.normalizePositiveInteger(options.sampleCount);
    this.stopAt = this.normalizePositiveNumber(options.durationMs) ? now + Number(options.durationMs) : undefined;

    if (options.emitImmediately !== false) {
      await this.emitSample();
    }

    this.timer = setInterval(() => {
      void this.emitSample();
    }, intervalMs);

    return {
      monitoring: true,
      intervalMs,
      startedAt: now,
    };
  }

  async stopMonitoring(): Promise<StopMonitoringResult> {
    if (this.timer) {
      clearInterval(this.timer);
    }
    this.timer = null;
    this.startedAt = undefined;
    this.intervalMs = undefined;
    this.sampleLimit = undefined;
    this.stopAt = undefined;
    this.samplesEmitted = 0;
    return { monitoring: false };
  }

  async isMonitoring(): Promise<MonitoringState> {
    return {
      monitoring: this.timer !== null,
      intervalMs: this.intervalMs,
      startedAt: this.startedAt,
      samplesEmitted: this.samplesEmitted,
    };
  }

  async addListener(
    eventName: 'deviceInfoUpdate',
    listenerFunc: (event: DeviceInfoUpdate) => void,
  ): Promise<PluginListenerHandle> {
    return super.addListener(eventName, listenerFunc);
  }

  async removeAllListeners(): Promise<void> {
    await super.removeAllListeners();
  }

  async getPluginVersion(): Promise<PluginVersionResult> {
    return { version: 'web' };
  }

  private async emitSample(): Promise<void> {
    if (this.isEmitting || !this.startedAt) {
      return;
    }

    this.isEmitting = true;
    try {
      const snapshot = await this.getInfo();
      this.samplesEmitted += 1;
      const event: DeviceInfoUpdate = {
        ...snapshot,
        sequence: this.samplesEmitted,
        startedAt: this.startedAt,
        elapsedMs: snapshot.timestamp - this.startedAt,
      };
      this.notifyListeners('deviceInfoUpdate', event);

      if (this.shouldStop()) {
        await this.stopMonitoring();
      }
    } finally {
      this.isEmitting = false;
    }
  }

  private getMemoryInfo(): MemoryInfo {
    const nav = navigator as NavigatorWithDeviceMemory;
    const perf = performance as PerformanceWithMemory;
    const totalBytes = nav.deviceMemory ? nav.deviceMemory * 1024 * 1024 * 1024 : undefined;
    const appUsedBytes = perf.memory?.usedJSHeapSize;
    const appLimitBytes = perf.memory?.jsHeapSizeLimit;

    return {
      totalBytes,
      appUsedBytes,
      appLimitBytes,
      usedPercent: totalBytes && appUsedBytes ? this.toPercent(appUsedBytes, totalBytes) : undefined,
      pressure: 'unknown',
    };
  }

  private async getStorageInfo(): Promise<StorageInfo> {
    if (!navigator.storage?.estimate) {
      return {};
    }

    const estimate = await navigator.storage.estimate();
    const totalBytes = estimate.quota;
    const usedBytes = estimate.usage;

    return {
      totalBytes,
      usedBytes,
      freeBytes: totalBytes !== undefined && usedBytes !== undefined ? Math.max(totalBytes - usedBytes, 0) : undefined,
      usedPercent: totalBytes && usedBytes !== undefined ? this.toPercent(usedBytes, totalBytes) : undefined,
    };
  }

  private getGpuInfo(): GpuInfo | undefined {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2') ?? canvas.getContext('webgl');
    if (!gl) {
      return undefined;
    }

    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
    const gpu: GpuInfo = {
      api: 'webgl',
      version: gl.getParameter(gl.VERSION) as string,
      maxTextureSize: gl.getParameter(gl.MAX_TEXTURE_SIZE) as number,
    };

    if (debugInfo) {
      gpu.vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) as string;
      gpu.renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) as string;
    } else {
      gpu.vendor = gl.getParameter(gl.VENDOR) as string;
      gpu.renderer = gl.getParameter(gl.RENDERER) as string;
    }

    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return gpu;
  }

  private getOnboardSensorsInfo(): OnboardSensorsInfo {
    return {
      availableSensors: [],
      readings: [],
    };
  }

  private shouldStop(): boolean {
    const reachedSampleLimit = this.sampleLimit !== undefined && this.samplesEmitted >= this.sampleLimit;
    const reachedDuration = this.stopAt !== undefined && Date.now() >= this.stopAt;
    return reachedSampleLimit || reachedDuration;
  }

  private normalizeInterval(intervalMs: number | undefined): number {
    if (!Number.isFinite(intervalMs)) {
      return DEFAULT_INTERVAL_MS;
    }
    return Math.max(Math.round(Number(intervalMs)), MIN_INTERVAL_MS);
  }

  private normalizePositiveInteger(value: number | undefined): number | undefined {
    if (!Number.isFinite(value)) {
      return undefined;
    }
    const normalized = Math.floor(Number(value));
    return normalized > 0 ? normalized : undefined;
  }

  private normalizePositiveNumber(value: number | undefined): number | undefined {
    if (!Number.isFinite(value)) {
      return undefined;
    }
    const normalized = Number(value);
    return normalized > 0 ? normalized : undefined;
  }

  private toPercent(value: number, total: number): number {
    return total > 0 ? Math.min(Math.max((value / total) * 100, 0), 100) : 0;
  }
}
