import Foundation

public struct CodexModel: Codable, Equatable, Sendable, Identifiable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

/// The account's Codex picker catalog, not the public API model list or usage buckets.
public enum CodexModelCatalog {
    public static func parse(_ data: Data) throws -> [CodexModel] {
        struct Response: Decodable { let models: [Entry] }
        struct Entry: Decodable {
            let slug: String
            let display_name: String?
            let visibility: String
        }
        let response = try JSONDecoder().decode(Response.self, from: data)
        var seen = Set<String>()
        return response.models.compactMap { entry in
            let id = entry.slug.trimmingCharacters(in: .whitespacesAndNewlines)
            guard entry.visibility == "list", !id.isEmpty, seen.insert(id).inserted else { return nil }
            let name = entry.display_name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return CodexModel(id: id, name: name.isEmpty ? id : name)
        }
    }
}

/// Keep every observed ID so removal/reappearance and display-name changes never alert again.
public struct CodexModelDiscoveryState: Codable, Equatable, Sendable {
    public private(set) var knownIDs: Set<String>

    public init(knownIDs: Set<String> = []) { self.knownIDs = knownIDs }

    public func additions(in models: [CodexModel]) -> [CodexModel] {
        guard !knownIDs.isEmpty else { return [] }
        return models.filter { !knownIDs.contains($0.id) }
    }

    public mutating func record(_ models: [CodexModel]) {
        knownIDs.formUnion(models.map(\.id))
    }
}
