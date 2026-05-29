import Capacitor
import Foundation

@objc(DeviceInfoPlugin)
public class DeviceInfoPlugin: CAPPlugin, CAPBridgedPlugin {
    private let pluginVersion = "8.0.3"
    private let defaultIntervalMs = 1000
    private let minimumIntervalMs = 250

    public let identifier = "DeviceInfoPlugin"
    public let jsName = "DeviceInfo"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getInfo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isMonitoring", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAllListeners", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]

    private let implementation = DeviceInfo()
    private var timer: Timer?
    private var intervalMs: Int?
    private var startedAt: Double?
    private var sampleLimit: Int?
    private var stopAt: Double?
    private var samplesEmitted = 0

    @objc func getInfo(_ call: CAPPluginCall) {
        call.resolve(implementation.getInfo())
    }

    @objc func startMonitoring(_ call: CAPPluginCall) {
        stopMonitoringInternal()

        let effectiveIntervalMs = max(call.getInt("intervalMs") ?? defaultIntervalMs, minimumIntervalMs)
        let now = currentTimestamp()
        intervalMs = effectiveIntervalMs
        startedAt = now
        samplesEmitted = 0
        sampleLimit = positiveInt(call.getInt("sampleCount"))

        if let durationMs = positiveDouble(call.getDouble("durationMs")) {
            stopAt = now + durationMs
        }

        if call.getBool("emitImmediately") != false {
            emitSample()
        }

        if startedAt != nil {
            let timer = Timer(timeInterval: Double(effectiveIntervalMs) / 1000, repeats: true) { [weak self] _ in
                self?.emitSample()
            }
            RunLoop.main.add(timer, forMode: .common)
            self.timer = timer
        }

        call.resolve([
            "monitoring": startedAt != nil,
            "intervalMs": effectiveIntervalMs,
            "startedAt": now
        ])
    }

    @objc func stopMonitoring(_ call: CAPPluginCall) {
        stopMonitoringInternal()
        call.resolve(["monitoring": false])
    }

    @objc func isMonitoring(_ call: CAPPluginCall) {
        var result: [String: Any] = [
            "monitoring": timer != nil
        ]

        if let intervalMs {
            result["intervalMs"] = intervalMs
        }
        if let startedAt {
            result["startedAt"] = startedAt
        }
        if timer != nil {
            result["samplesEmitted"] = samplesEmitted
        }

        call.resolve(result)
    }

    @objc override public func removeAllListeners(_ call: CAPPluginCall) {
        super.removeAllListeners(call)
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": pluginVersion])
    }

    deinit {
        stopMonitoringInternal()
    }

    private func emitSample() {
        guard let startedAt else {
            return
        }

        var sample = implementation.getInfo()
        samplesEmitted += 1
        let timestamp = sample["timestamp"] as? Double ?? currentTimestamp()
        sample["sequence"] = samplesEmitted
        sample["startedAt"] = startedAt
        sample["elapsedMs"] = timestamp - startedAt

        notifyListeners("deviceInfoUpdate", data: sample)

        if shouldStopMonitoring() {
            stopMonitoringInternal()
        }
    }

    private func shouldStopMonitoring() -> Bool {
        if let sampleLimit, samplesEmitted >= sampleLimit {
            return true
        }
        if let stopAt, currentTimestamp() >= stopAt {
            return true
        }
        return false
    }

    private func stopMonitoringInternal() {
        timer?.invalidate()
        timer = nil
        intervalMs = nil
        startedAt = nil
        sampleLimit = nil
        stopAt = nil
        samplesEmitted = 0
    }

    private func positiveInt(_ value: Int?) -> Int? {
        guard let value, value > 0 else {
            return nil
        }
        return value
    }

    private func positiveDouble(_ value: Double?) -> Double? {
        guard let value, value > 0 else {
            return nil
        }
        return value
    }

    private func currentTimestamp() -> Double {
        return Date().timeIntervalSince1970 * 1000
    }
}
