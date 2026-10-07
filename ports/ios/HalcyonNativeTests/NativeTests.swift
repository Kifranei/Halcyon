import XCTest
import AVFoundation

// Compiles the production native files into this test bundle. Every store uses
// a fresh directory and Keychain account, independent of the user's library.
@MainActor
final class NativeTests: XCTestCase {
    private var folder: URL!
    private var store: NativeStore!
    private var account: String!
    private let endpoint = "http://127.0.0.1:8765/"

    override func setUp() async throws {
        folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        account = UUID().uuidString
        store = NativeStore(root: folder, account: account)
    }
    override func tearDown() async throws {
        for source in store.sources { try store.removeSource(source["id"] as? String ?? "") }
        try FileManager.default.removeItem(at: folder)
        store = nil
    }
    private func source(_ type: String = "webdav") -> [String: Any] {
        ["id": UUID().uuidString, "type": type, "name": "Fixture", "url": endpoint,
         "username": "user", "password": "fixture-secret"]
    }
    private func wav(_ name: String, frequency: Double = 440, seconds: Int = 2) throws -> URL {
        var data = Data()
        func ascii(_ value: String) { data.append(contentsOf: value.utf8) }
        func u16(_ value: UInt16) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
        func u32(_ value: UInt32) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
        let count = 48000 * seconds
        ascii("RIFF"); u32(UInt32(36 + count * 2)); ascii("WAVEfmt "); u32(16)
        u16(1); u16(1); u32(48000); u32(96000); u16(2); u16(16)
        ascii("data"); u32(UInt32(count * 2))
        for n in 0..<count { u16(UInt16(bitPattern: Int16(sin(Double(n) / 48000 * 2 * .pi * frequency) * 1000))) }
        let url = folder.appendingPathComponent(name + ".wav"); try data.write(to: url); return url
    }
    private func wait(_ seconds: Double) async throws { try await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000)) }
    private func fails(_ operation: () async throws -> Void) async {
        do { try await operation(); XCTFail("Expected rejection") } catch { }
    }

    func testFreshProfilePersistsStateWithoutImportingAudio() throws {
        let root = folder.appendingPathComponent("fresh").appendingPathComponent(UUID().uuidString)
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.path))
        let fresh = NativeStore(root: root, account: account)
        XCTAssertNil(fresh.sourceError)
        XCTAssertTrue(fresh.library().isEmpty)
        let state: [String: Any] = ["playlists": [["id": "first", "name": "Empty Library Playlist", "songIds": []]]]
        try fresh.write("state.json", value: state)
        let restored = NativeStore(root: root, account: account)
        let saved = try XCTUnwrap(try restored.read("state.json") as? [String: Any])
        let playlists = try XCTUnwrap(saved["playlists"] as? [[String: Any]])
        XCTAssertEqual(playlists.first?["name"] as? String, "Empty Library Playlist")
        XCTAssertTrue(restored.library().isEmpty)
    }

    func testImportDedupMetadataLyricsPersistenceAndSafeRemoval() async throws {
        let file = try wav("测试歌手 - 第一首")
        let sidecar = file.deletingPathExtension().appendingPathExtension("lrc")
        try "[00:00.000]原始歌词".write(to: sidecar, atomically: true, encoding: .utf8)
        let first = try await store.importFiles([file]); let id = try XCTUnwrap(first.first?["id"] as? String)
        _ = try await store.importFiles([file])
        XCTAssertEqual(store.library().count, 1)
        XCTAssertEqual(store.library()[0]["title"] as? String, "第一首")
        XCTAssertEqual(store.library()[0]["artist"] as? String, "测试歌手")
        XCTAssertEqual(store.library()[0]["duration"] as? Double ?? 0, 2, accuracy: 0.05)
        XCTAssertEqual(store.library()[0]["sampleRate"] as? Double, 48000)
        XCTAssertEqual(store.library()[0]["lyrics"] as? String, "[00:00.000]原始歌词")
        try store.saveLyrics(id, text: "[00:01.000]修改歌词")
        let restored = NativeStore(root: folder, account: account)
        XCTAssertEqual(restored.library()[0]["lyrics"] as? String, "[00:01.000]修改歌词")
        let copy = try restored.localURL(id)
        XCTAssertEqual(try Data(contentsOf: copy), try Data(contentsOf: file))
        try restored.remove(id)
        XCTAssertEqual(NativeStore(root: folder, account: account).library().count, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: file.path))
        XCTAssertTrue(FileManager.default.fileExists(atPath: copy.path))
        XCTAssertThrowsError(try restored.localURL(id))
    }
    func testKeychainRoundTripDoesNotExposeCredentials() throws {
        let privateSource = source(); let id = try XCTUnwrap(privateSource["id"] as? String)
        let publicSource = try store.saveSource(privateSource)
        XCTAssertNil(publicSource["password"]); XCTAssertNil(publicSource["token"])
        let restored = NativeStore(root: folder, account: account)
        XCTAssertEqual(try restored.source(id)["password"] as? String, "fixture-secret")
        XCTAssertFalse(String(data: try JSONSerialization.data(withJSONObject: restored.publicSources()), encoding: .utf8)?.contains("fixture-secret") == true)
        try restored.removeSource(id)
        XCTAssertThrowsError(try NativeStore(root: folder, account: account).source(id))
    }
    func testRemoteScopeAuthAndOpaqueIDs() throws {
        let webdav = source()
        XCTAssertEqual(RemoteClient.headers(webdav)["Authorization"], "Basic dXNlcjpmaXh0dXJlLXNlY3JldA==")
        for path in ["../track.wav", "%2e%2e/track.wav", "//example.com/track.wav", "x\\track.wav"] {
            XCTAssertThrowsError(try RemoteClient.url(source: webdav, path: path))
        }
        XCTAssertThrowsError(try RemoteClient.root("https://user:secret@example.com/"))
        let subsonic = source("subsonic")
        let (url, _) = try RemoteClient.stream(source: subsonic, song: ["remoteId": "中文?#"])
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        XCTAssertEqual(query.first { $0.name == "id" }?.value, "中文?#")
        XCTAssertFalse(url.absoluteString.contains("fixture-secret"))
    }
    func testNativeHTTPWebDAVAssetsErrorsAndRedirects() async throws {
        let dav = try await RemoteClient.request(source: source(), args: ["method": "PROPFIND", "path": "", "body": "<propfind/>"])
        XCTAssertTrue(dav.contains("multistatus"))
        let cover = try await RemoteClient.asset(source: source(), args: ["path": "cover.png"])
        XCTAssertTrue(cover.hasPrefix("data:image/jpeg;base64,"))
        await fails { _ = try await RemoteClient.request(source: self.source(), args: ["path": "bad"]) }
        await fails { _ = try await RemoteClient.request(source: self.source(), args: ["path": "redirect"]) }
        await fails { _ = try await RemoteClient.request(source: self.source(), args: ["path": "health", "method": "POST"]) }
    }
    func testNativeEmbyAndBothAIProtocols() async throws {
        let emby = try await RemoteClient.prepare(source("emby"))
        XCTAssertEqual(emby["token"] as? String, "fixture-token")
        XCTAssertEqual(emby["userId"] as? String, "fixture-user")
        XCTAssertNil(emby["password"])
        XCTAssertEqual(RemoteClient.headers(emby)["X-Emby-Token"], "fixture-token")
        var ai = source("ai"); ai["password"] = "fixture-key"; ai["model"] = "fixture-model"
        let recommendation = try await RemoteClient.ai(source: ai, prompt: "fixture-prompt")
        XCTAssertEqual(recommendation, "Fixture recommendation")
        ai["protocol"] = "anthropic"
        let explanation = try await RemoteClient.ai(source: ai, prompt: "fixture-prompt")
        XCTAssertEqual(explanation, "Fixture explanation")
        ai["password"] = ""
        await fails { _ = try await RemoteClient.ai(source: ai, prompt: "fixture-prompt") }
    }
    func testNativeDownloadStableIdentityAndOfflinePlayback() async throws {
        var remote = source("subsonic"); let sourceID = try XCTUnwrap(remote["id"] as? String)
        _ = try store.saveSource(remote)
        let song: [String: Any] = ["id": sourceID + ":中文?#", "sourceId": sourceID, "remoteId": "中文?#", "title": "离线音频", "artist": "测试", "album": "Fixture", "duration": 999, "format": "wav"]
        let cached = try await store.download(song, source: remote)
        XCTAssertEqual(cached["id"] as? String, song["id"] as? String)
        XCTAssertEqual(cached["offline"] as? Bool, true)
        XCTAssertEqual(cached["duration"] as? Double ?? 0, 4, accuracy: 0.05)
        // Poison the source address: offline playback must never use the network.
        remote["url"] = "http://127.0.0.1:1/"; _ = try store.saveSource(remote)
        let restored = NativeStore(root: folder, account: account)
        let player = NativePlayer(store: restored); defer { player.stop() }
        var snapshot: [String: Any] = [:]; player.event = { snapshot = $0 }
        try player.setQueue(["queue": restored.library(), "autoplay": true])
        try await wait(0.8)
        XCTAssertEqual(snapshot["playing"] as? Bool, true)
        XCTAssertGreaterThan(snapshot["position"] as? Double ?? 0, 0.2)
        XCTAssertEqual(snapshot["error"] as? String, "")
    }
    func testCancelledNativeDownloadDoesNotRegisterOrLeavePartialAudio() async throws {
        let remote = source()
        let song: [String: Any] = ["id": "cancel", "sourceId": remote["id"]!, "remotePath": "slow.wav", "format": "wav"]
        let task = Task { try await self.store.download(song, source: remote) }
        try await wait(0.3); task.cancel()
        await fails { _ = try await task.value }
        XCTAssertTrue(store.library().isEmpty)
        let music = folder.appendingPathComponent("Music")
        XCTAssertFalse(FileManager.default.fileExists(atPath: music.path))
    }
    func testNativePlaybackSeekPausedSkipQueueEffectsAndSleep() async throws {
        let first = try wav("Fixture - First", seconds: 4)
        let second = try wav("Fixture - Second", frequency: 660, seconds: 4)
        let songs = try await store.importFiles([first, second])
        let player = NativePlayer(store: store); defer { player.stop() }
        var snapshot: [String: Any] = [:]; player.event = { snapshot = $0 }
        player.configure(["rate": 1.5, "preservePitch": true, "eqEnabled": true, "eq": [0,0,0,0,0,3,0,0,0,0], "preamp": -3])
        try player.setQueue(["queue": songs, "autoplay": true])
        try await wait(0.8)
        XCTAssertGreaterThan(snapshot["position"] as? Double ?? 0, 0.5)
        XCTAssertEqual(snapshot["rate"] as? Float, 1.5)
        XCTAssertEqual(snapshot["error"] as? String, "")
        player.toggle(false); player.seek(2)
        XCTAssertEqual(snapshot["position"] as? Double ?? 0, 2, accuracy: 0.1)
        XCTAssertEqual(snapshot["playing"] as? Bool, false)
        player.skip(1)
        XCTAssertEqual(player.index, 1); XCTAssertEqual(snapshot["playing"] as? Bool, false)
        try player.editQueue(["queue": [songs[1], songs[0]], "index": 0])
        XCTAssertEqual(player.queue[0]["id"] as? String, songs[1]["id"] as? String)
        player.toggle(true); player.sleep(0.01, finish: false)
        try await wait(1)
        XCTAssertEqual(snapshot["playing"] as? Bool, false)
        try player.editQueue(["queue": []])
        XCTAssertTrue(player.queue.isEmpty)
    }
    func testNativeAutomaticAdvanceRepeatFinishTrackAndHistoryRestore() async throws {
        let first = try wav("Fixture - First")
        let second = try wav("Fixture - Second", frequency: 660)
        let songs = try await store.importFiles([first, second])
        let player = NativePlayer(store: store); defer { player.stop() }
        var snapshot: [String: Any] = [:]; player.event = { snapshot = $0 }
        try player.setQueue(["queue": songs, "autoplay": true, "repeat": "off"])
        try await wait(4.8)
        XCTAssertEqual(player.index, 1); XCTAssertEqual(snapshot["playing"] as? Bool, false)
        let stats = try XCTUnwrap(try store.read("native-history.json") as? [String: Any])
        XCTAssertEqual((stats["counts"] as? [String: Int])?.values.reduce(0, +), 2)
        try player.setQueue(["queue": [songs[0]], "autoplay": true, "repeat": "one"])
        try await wait(2.5)
        XCTAssertEqual(snapshot["playing"] as? Bool, true)
        player.sleep(-1, finish: false); try await wait(2.5)
        XCTAssertEqual(snapshot["playing"] as? Bool, false)
        let id = try XCTUnwrap(songs[0]["id"] as? String)
        try player.restoreStats(["counts": [id: 9], "history": [["id": id, "at": 1.0]]])
        player.removeHistory(id)
        let restored = try XCTUnwrap(try store.read("native-history.json") as? [String: Any])
        XCTAssertEqual((restored["counts"] as? [String: Int])?[id], 9)
        XCTAssertTrue((restored["history"] as? [[String: Any]])?.isEmpty == true)
    }
}
