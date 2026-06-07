import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { Capacitor } from '@capacitor/core';
import './style.css';
import { DeviceInfo } from '@capgo/capacitor-device-info';

const MAX_POINTS = 60;

const output = document.getElementById('plugin-output');
const state = document.getElementById('monitoring-state');
const readOnceButton = document.getElementById('read-once');
const startButton = document.getElementById('start-monitoring');
const stopButton = document.getElementById('stop-monitoring');
const versionButton = document.getElementById('get-version');
const cpuValue = document.getElementById('cpu-value');
const memoryValue = document.getElementById('memory-value');
const storageValue = document.getElementById('storage-value');
const gpuValue = document.getElementById('gpu-value');
const sensorValue = document.getElementById('sensor-value');
const canvas = document.getElementById('metrics-chart');
const context = canvas.getContext('2d');

const history = [];
const seriesKeys = ['cpu', 'memory', 'storage'];

const setOutput = (value) => {
  output.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

const formatPercent = (value) => (typeof value === 'number' ? `${value.toFixed(1)}%` : '--');

const formatBytes = (value) => {
  if (typeof value !== 'number') {
    return '--';
  }

  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit += 1;
  }
  return `${size.toFixed(size >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`;
};

const formatNumber = (value, unit) =>
  typeof value === 'number' ? `${value.toFixed(1)} ${unit}` : null;

const sensorSummary = (sample) => {
  const sensors = sample.sensors ?? {};
  const parts = [
    formatNumber(sample.cpu?.temperatureCelsius, 'C CPU'),
    formatNumber(sample.gpu?.temperatureCelsius, 'C GPU'),
    formatNumber(sensors.batteryTemperatureCelsius, 'C battery'),
    formatNumber(sensors.ambientTemperatureCelsius, 'C ambient'),
    formatNumber(sensors.relativeHumidityPercent, '% RH'),
    formatNumber(sensors.pressureHpa, 'hPa'),
    formatNumber(sensors.illuminanceLux, 'lux'),
  ].filter(Boolean);

  if (parts.length > 0) {
    return parts.join(' / ');
  }

  const availableCount = sensors.availableSensors?.length;
  return typeof availableCount === 'number' ? `${availableCount} sensors` : '--';
};

const metricPoint = (sample) => ({
  cpu: typeof sample.cpu?.usagePercent === 'number' ? sample.cpu.usagePercent : null,
  memory: typeof sample.memory?.usedPercent === 'number' ? sample.memory.usedPercent : null,
  storage: typeof sample.storage?.usedPercent === 'number' ? sample.storage.usedPercent : null,
});

const pushHistory = (sample) => {
  history.push(metricPoint(sample));
  if (history.length > MAX_POINTS) {
    history.shift();
  }
};

const renderSnapshot = (sample) => {
  cpuValue.textContent =
    typeof sample.cpu?.usagePercent === 'number'
      ? `${formatPercent(sample.cpu.usagePercent)} / ${sample.cpu.cores} cores`
      : `${sample.cpu?.cores ?? '--'} cores`;
  memoryValue.textContent = `${formatPercent(sample.memory?.usedPercent)} / ${formatBytes(sample.memory?.totalBytes)}`;
  storageValue.textContent = `${formatPercent(sample.storage?.usedPercent)} / ${formatBytes(sample.storage?.totalBytes)}`;
  gpuValue.textContent = sample.gpu?.renderer ?? sample.gpu?.vendor ?? '--';
  sensorValue.textContent = sensorSummary(sample);
  setOutput(sample);
  drawChart();
};

const drawChart = () => {
  const width = canvas.width;
  const height = canvas.height;
  const plotTop = 14;
  const plotBottom = height - 18;
  const plotHeight = plotBottom - plotTop;
  const values = history.flatMap((point) =>
    seriesKeys.map((key) => point[key]).filter((value) => typeof value === 'number'),
  );
  const maxValue = values.length > 0 ? Math.max(...values, 0.01) * 1.2 : 100;

  context.clearRect(0, 0, width, height);
  context.fillStyle = '#fbfaf7';
  context.fillRect(0, 0, width, height);

  context.strokeStyle = '#d8d5ca';
  context.lineWidth = 1;
  for (let index = 0; index <= 4; index += 1) {
    const y = plotTop + (plotHeight / 4) * index;
    context.beginPath();
    context.moveTo(0, y);
    context.lineTo(width, y);
    context.stroke();
  }

  drawSeries('cpu', '#1f7a8c', maxValue, plotTop, plotHeight);
  drawSeries('memory', '#8f5f2a', maxValue, plotTop, plotHeight);
  drawSeries('storage', '#3f7d20', maxValue, plotTop, plotHeight);
};

const drawSeries = (key, color, maxValue, plotTop, plotHeight) => {
  const points = history
    .map((point, index) => ({ value: point[key], index }))
    .filter((point) => point.value !== null);

  if (points.length === 0) {
    return;
  }

  context.strokeStyle = color;
  context.lineWidth = 3;
  context.beginPath();
  points.forEach((point, seriesIndex) => {
    const x = history.length === 1 ? 0 : (point.index / (history.length - 1)) * canvas.width;
    const y = plotTop + (1 - point.value / maxValue) * plotHeight;
    if (seriesIndex === 0) {
      context.moveTo(x, y);
    } else {
      context.lineTo(x, y);
    }
  });
  context.stroke();

  context.fillStyle = color;
  points.forEach((point) => {
    const x = history.length === 1 ? 0 : (point.index / (history.length - 1)) * canvas.width;
    const y = plotTop + (1 - point.value / maxValue) * plotHeight;
    context.beginPath();
    context.arc(x, y, 4, 0, Math.PI * 2);
    context.fill();
  });
};

const syncMonitoringState = async () => {
  const result = await DeviceInfo.isMonitoring();
  state.textContent = result.monitoring ? `Streaming ${result.samplesEmitted ?? 0}` : 'Idle';
  startButton.disabled = result.monitoring;
  stopButton.disabled = !result.monitoring;
};

readOnceButton.addEventListener('click', async () => {
  try {
    const result = await DeviceInfo.getInfo();
    pushHistory(result);
    renderSnapshot(result);
    await syncMonitoringState();
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

startButton.addEventListener('click', async () => {
  try {
    const result = await DeviceInfo.startMonitoring({ intervalMs: 1000, emitImmediately: true });
    if (history.length === 0) {
      setOutput(result);
    }
    await syncMonitoringState();
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

stopButton.addEventListener('click', async () => {
  try {
    const result = await DeviceInfo.stopMonitoring();
    setOutput(result);
    await syncMonitoringState();
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

versionButton.addEventListener('click', async () => {
  try {
    const result = await DeviceInfo.getPluginVersion();
    setOutput(result);
  } catch (error) {
    setOutput(`Error: ${error?.message ?? error}`);
  }
});

const setupListener = async () => {
  startButton.disabled = true;
  stopButton.disabled = true;

  await DeviceInfo.addListener('deviceInfoUpdate', async (sample) => {
    pushHistory(sample);
    renderSnapshot(sample);
    await syncMonitoringState();
  });

  drawChart();
  await syncMonitoringState();
};

setupListener().catch((error) => {
  setOutput(`Error: ${error?.message ?? error}`);
});

drawChart();

if (Capacitor.isNativePlatform()) {
  CapacitorUpdater.notifyAppReady().catch((error) => {
    console.error('Capgo notifyAppReady failed', error);
  });
}
