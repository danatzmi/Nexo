//
//  DeepLinkTests.swift
//  NexoTests
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Testing
import Foundation
@testable import Nexo

@Suite("DeepLinkParser")
struct DeepLinkTests {
    @Test("parse handles query parameter code correctly")
    func parseQueryParam() {
        let url1 = URL(string: "https://nexo.fit/join?code=IRON99")!
        let url2 = URL(string: "nexo://join?code=danagym")!

        #expect(DeepLinkParser.parse(url: url1) == .joinGym(code: "IRON99"))
        #expect(DeepLinkParser.parse(url: url2) == .joinGym(code: "DANAGYM"))
    }

    @Test("parse handles path-based join link correctly")
    func parsePathBasedLink() {
        let url1 = URL(string: "https://nexo.fit/join/IRON99")!
        let url2 = URL(string: "https://nexo.app/join/BOXING10")!

        #expect(DeepLinkParser.parse(url: url1) == .joinGym(code: "IRON99"))
        #expect(DeepLinkParser.parse(url: url2) == .joinGym(code: "BOXING10"))
    }

    @Test("parse handles custom nexo scheme correctly")
    func parseCustomScheme() {
        let url1 = URL(string: "nexo://join/IRON99")!
        let url2 = URL(string: "nexo://DANAGYM")!

        #expect(DeepLinkParser.parse(url: url1) == .joinGym(code: "IRON99"))
        #expect(DeepLinkParser.parse(url: url2) == .joinGym(code: "DANAGYM"))
    }

    @Test("parse returns nil for unrelated or malformed URLs")
    func parseInvalidUrls() {
        let url1 = URL(string: "https://nexo.fit/about")!
        let url2 = URL(string: "https://google.com")!

        #expect(DeepLinkParser.parse(url: url1) == nil)
        #expect(DeepLinkParser.parse(url: url2) == nil)
    }
}
