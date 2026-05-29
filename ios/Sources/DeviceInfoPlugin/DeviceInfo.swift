import CoreMotion
import Darwin
import Foundation
import Metal

@objc public class DeviceInfo: NSObject {
    private var previousCpuLoad: host_cpu_load_info?

    @objc public func getInfo() -> [String: Any] {
        var result: [String: Any] = [
            "timestamp": currentTimestamp(),
            "platform": "ios",
            "cpu": cpuInfo(),
            "memory": memoryInfo(),
            "storage": storageInfo(),
            "thermalState": thermalState(),
            "lowPowerMode": ProcessInfo.processInfo.isLowPowerModeEnabled,
            "sensors": onboardSensorsInfo()
        ]

        if let gpu = gpuInfo() {
            result["gpu"] = gpu
        }

        return result
    }

    @objc public func getPluginVersion() -> String {
        return "8.0.2"
    }

    private func cpuInfo() -> [String: Any] {
        var info: [String: Any] = [
            "cores": ProcessInfo.processInfo.processorCount,
            "activeCores": ProcessInfo.processInfo.activeProcessorCount,
            "architecture": cpuArchitecture(),
            "model": modelIdentifier()
        ]

        if let usagePercent = cpuUsagePercent() {
            info["usagePercent"] = usagePercent
        }

        return info
    }

    private func cpuUsagePercent() -> Double? {
        guard let currentLoad = currentCpuLoad() else {
            return nil
        }

        defer {
            previousCpuLoad = currentLoad
        }

        guard let previousLoad = previousCpuLoad else {
            return nil
        }

        let user = tickDelta(currentLoad.cpu_ticks.0, previousLoad.cpu_ticks.0)
        let system = tickDelta(currentLoad.cpu_ticks.1, previousLoad.cpu_ticks.1)
        let idle = tickDelta(currentLoad.cpu_ticks.2, previousLoad.cpu_ticks.2)
        let nice = tickDelta(currentLoad.cpu_ticks.3, previousLoad.cpu_ticks.3)
        let total = user + system + idle + nice

        guard total > 0 else {
            return nil
        }

        return clampPercent((Double(total - idle) / Double(total)) * 100)
    }

    private func currentCpuLoad() -> host_cpu_load_info? {
        var cpuInfo = host_cpu_load_info()
        var count = mach_msg_type_number_t(MemoryLayout<host_cpu_load_info>.stride / MemoryLayout<integer_t>.stride)

        let result = withUnsafeMutablePointer(to: &cpuInfo) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO, $0, &count)
            }
        }

        return result == KERN_SUCCESS ? cpuInfo : nil
    }

    private func memoryInfo() -> [String: Any] {
        let totalBytes = Double(ProcessInfo.processInfo.physicalMemory)
        var info: [String: Any] = [
            "totalBytes": totalBytes,
            "pressure": "normal"
        ]

        if let vmInfo = vmMemoryInfo(totalBytes: totalBytes) {
            info.merge(vmInfo) { _, new in new }
        }

        if let appUsedBytes = appMemoryBytes() {
            info["appUsedBytes"] = appUsedBytes
        }

        return info
    }

    private func vmMemoryInfo(totalBytes: Double) -> [String: Any]? {
        var stats = vm_statistics64()
        var count = mach_msg_type_number_t(MemoryLayout<vm_statistics64_data_t>.stride / MemoryLayout<integer_t>.stride)

        let result = withUnsafeMutablePointer(to: &stats) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &count)
            }
        }

        guard result == KERN_SUCCESS else {
            return nil
        }

        var pageSize: vm_size_t = 0
        host_page_size(mach_host_self(), &pageSize)
        let pageBytes = Double(pageSize)
        let freeBytes = Double(UInt64(stats.free_count) + UInt64(stats.inactive_count)) * pageBytes
        let usedBytes = max(totalBytes - freeBytes, 0)

        return [
            "freeBytes": freeBytes,
            "usedBytes": usedBytes,
            "usedPercent": percent(usedBytes, totalBytes)
        ]
    }

    private func appMemoryBytes() -> Double? {
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.stride / MemoryLayout<natural_t>.stride)

        let result = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }

        return result == KERN_SUCCESS ? Double(info.phys_footprint) : nil
    }

    private func storageInfo() -> [String: Any] {
        let url = URL(fileURLWithPath: NSHomeDirectory())

        do {
            let values = try url.resourceValues(forKeys: [
                .volumeTotalCapacityKey,
                .volumeAvailableCapacityKey,
                .volumeAvailableCapacityForImportantUsageKey
            ])
            let totalBytes = values.volumeTotalCapacity.map(Double.init)
            let importantFreeBytes = values.volumeAvailableCapacityForImportantUsage.map(Double.init)
            let freeBytes = importantFreeBytes ?? values.volumeAvailableCapacity.map(Double.init)
            var info: [String: Any] = [:]

            if let totalBytes {
                info["totalBytes"] = totalBytes
            }
            if let freeBytes {
                info["freeBytes"] = freeBytes
            }
            if let totalBytes, let freeBytes {
                let usedBytes = max(totalBytes - freeBytes, 0)
                info["usedBytes"] = usedBytes
                info["usedPercent"] = percent(usedBytes, totalBytes)
            }

            return info
        } catch {
            return [:]
        }
    }

    private func gpuInfo() -> [String: Any]? {
        guard let device = MTLCreateSystemDefaultDevice() else {
            return nil
        }

        return [
            "api": "metal",
            "vendor": "Apple",
            "renderer": device.name
        ]
    }

    private func onboardSensorsInfo() -> [String: Any] {
        let motionManager = CMMotionManager()
        var availableSensors: [[String: Any]] = []

        if motionManager.isAccelerometerAvailable {
            availableSensors.append(sensorDescriptor(type: "accelerometer", name: "Accelerometer"))
        }
        if motionManager.isGyroAvailable {
            availableSensors.append(sensorDescriptor(type: "gyroscope", name: "Gyroscope"))
        }
        if motionManager.isMagnetometerAvailable {
            availableSensors.append(sensorDescriptor(type: "magnetometer", name: "Magnetometer"))
        }
        if motionManager.isDeviceMotionAvailable {
            availableSensors.append(sensorDescriptor(type: "deviceMotion", name: "Device Motion"))
        }
        if CMAltimeter.isRelativeAltitudeAvailable() {
            availableSensors.append(sensorDescriptor(type: "barometer", name: "Barometer"))
        }

        return [
            "availableSensors": availableSensors,
            "readings": []
        ]
    }

    private func sensorDescriptor(type: String, name: String) -> [String: Any] {
        return [
            "type": type,
            "name": name,
            "vendor": "Apple"
        ]
    }

    private func thermalState() -> String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal:
            return "nominal"
        case .fair:
            return "fair"
        case .serious:
            return "serious"
        case .critical:
            return "critical"
        @unknown default:
            return "unknown"
        }
    }

    private func modelIdentifier() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)

        return withUnsafeBytes(of: &systemInfo.machine) { rawBuffer in
            let pointer = rawBuffer.bindMemory(to: CChar.self).baseAddress
            return pointer.map { String(cString: $0) } ?? "unknown"
        }
    }

    private func cpuArchitecture() -> String {
        #if arch(arm64)
        return "arm64"
        #elseif arch(arm)
        return "arm"
        #elseif arch(x86_64)
        return "x86_64"
        #elseif arch(i386)
        return "i386"
        #else
        return "unknown"
        #endif
    }

    private func currentTimestamp() -> Double {
        return Date().timeIntervalSince1970 * 1000
    }

    private func tickDelta(_ current: natural_t, _ previous: natural_t) -> UInt64 {
        return UInt64(current >= previous ? current - previous : 0)
    }

    private func percent(_ used: Double, _ total: Double) -> Double {
        guard total > 0 else {
            return 0
        }
        return clampPercent((used / total) * 100)
    }

    private func clampPercent(_ value: Double) -> Double {
        return min(max(value, 0), 100)
    }
}
