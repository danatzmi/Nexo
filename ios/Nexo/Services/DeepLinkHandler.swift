//
//  DeepLinkHandler.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation

enum DeepLink: Equatable {
    case joinGym(code: String)
}

struct DeepLinkParser {
    static func parse(url: URL) -> DeepLink? {
        // 1. Query parameter: ?code=XYZ
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
           let codeItem = components.queryItems?.first(where: { $0.name.lowercased() == "code" })?.value,
           !codeItem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .joinGym(code: codeItem.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())
        }

        // 2. Path-based: /join/XYZ
        let pathComponents = url.pathComponents.filter { $0 != "/" && !$0.isEmpty }
        if let joinIdx = pathComponents.firstIndex(where: { $0.lowercased() == "join" }),
           joinIdx + 1 < pathComponents.count {
            let code = pathComponents[joinIdx + 1].trimmingCharacters(in: .whitespacesAndNewlines)
            if !code.isEmpty {
                return .joinGym(code: code.uppercased())
            }
        }

        // 3. Custom scheme: nexo://join/XYZ or nexo://XYZ
        if url.scheme?.lowercased() == "nexo" {
            if url.host?.lowercased() == "join" {
                if let first = pathComponents.first {
                    return .joinGym(code: first.uppercased())
                }
            } else if let host = url.host, host.count >= 4 && host.count <= 10 {
                return .joinGym(code: host.uppercased())
            }
        }

        return nil
    }
}
