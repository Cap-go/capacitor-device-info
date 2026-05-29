import XCTest
@testable import DeviceInfoPlugin

class DeviceInfoTests: XCTestCase {
    func testGetInfo() {
        let implementation = DeviceInfo()
        let result = implementation.getInfo()

        XCTAssertEqual("ios", result["platform"] as? String)
        XCTAssertNotNil(result["cpu"])
        XCTAssertNotNil(result["memory"])
        XCTAssertNotNil(result["storage"])
        XCTAssertNotNil(result["sensors"])
    }

    func testGetPluginVersion() {
        let implementation = DeviceInfo()
        let result = implementation.getPluginVersion()

        XCTAssertEqual("8.0.2", result)
    }
}
