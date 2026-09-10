import Foundation
import XCTest
@testable import ModelsMeterCore

final class CodexModelCatalogTests: XCTestCase {
    func testOnlyVisibleModelsAreParsedAndDeduplicated() throws {
        let models = try CodexModelCatalog.parse(Data(#"""
        {"models":[
          {"slug":"a","display_name":"Model A","visibility":"list"},
          {"slug":"a","visibility":"list"},
          {"slug":"hidden","visibility":"hide"},
          {"slug":"disabled","visibility":"none"},
          {"slug":" ","visibility":"list"},
          {"slug":"b","display_name":" ","visibility":"list"}
        ]}
        """#.utf8))
        XCTAssertEqual(models, [.init(id: "a", name: "Model A"), .init(id: "b", name: "b")])
    }

    func testMalformedCatalogIsRejectedRatherThanOverwritingHistory() {
        for input in ["{}", #"{"models":null}"#, #"{"models":[{"slug":"a"}]}"#, "not json"] {
            XCTAssertThrowsError(try CodexModelCatalog.parse(Data(input.utf8)))
        }
    }

    func testBaselineAdditionsPersistenceAndReappearance() throws {
        let a = CodexModel(id: "a", name: "A")
        let b = CodexModel(id: "b", name: "B")
        var state = CodexModelDiscoveryState()
        state.record([])
        XCTAssertTrue(state.additions(in: [a]).isEmpty)
        state.record([a])
        XCTAssertEqual(state.additions(in: [a, b]), [b])
        // Failed notification delivery does not acknowledge the model.
        XCTAssertEqual(state.additions(in: [b]), [b])
        state.record([b])
        state.record([])
        state = try JSONDecoder().decode(CodexModelDiscoveryState.self, from: JSONEncoder().encode(state))
        XCTAssertTrue(state.additions(in: [.init(id: "a", name: "Renamed"), b]).isEmpty)
        XCTAssertTrue(CodexModelDiscoveryState().additions(in: [a, b]).isEmpty)
    }
}
