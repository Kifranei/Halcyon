import Foundation
import AVFoundation
import CryptoKit
import Security
import UIKit

enum PortError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case let .message(message) = self { return message }; return nil }
}

@MainActor
final class NativeStore {
    let root: URL
    private(set) var records: [[String: Any]] = []
    private(set) var sources: [[String: Any]] = []
    var sourceError: String?
    private let account: String
    private let extensions: Set<String> = ["mp3", "m4a", "aac", "alac", "flac", "wav", "aif", "aiff", "ogg", "opus", "wma"]

    init(root: URL? = nil, account: String = "Halcyon.RemoteSources.v1") {
        self.root = root ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.account = account
        do { try FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true) }
        catch { sourceError = "资料目录创建失败：\(error.localizedDescription)" }
        do { records = try read("library.json") as? [[String: Any]] ?? [] } catch { sourceError = "曲库索引读取失败：\(error.localizedDescription)" }
        do { sources = try loadSecrets() } catch { sourceError = error.localizedDescription }
    }
    func read(_ name: String) throws -> Any? {
        let url = root.appendingPathComponent(name)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        return try JSONSerialization.jsonObject(with: Data(contentsOf: url))
    }
    func write(_ name: String, value: Any) throws {
        let data = try JSONSerialization.data(withJSONObject: value)
        try data.write(to: root.appendingPathComponent(name), options: .atomic)
    }
    func library() -> [[String: Any]] { records.compactMap { $0["song"] as? [String: Any] } }
    func localURL(_ id: String) throws -> URL {
        guard let record = records.first(where: { ($0["song"] as? [String: Any])?["id"] as? String == id }), let relative = record["file"] as? String else { throw PortError.message("歌曲未导入，请重新导入文件") }
        let url = root.appendingPathComponent(relative).standardizedFileURL
        guard url.path.hasPrefix(root.path + "/Music/") else { throw PortError.message("无效音频路径") }
        return url
    }
    func remove(_ id: String) throws {
        records.removeAll { ($0["song"] as? [String: Any])?["id"] as? String == id }
        try write("library.json", value: records)
        // Imported copies are retained in Documents; never delete the user's source file.
    }
    func source(_ id: String) throws -> [String: Any] {
        guard let value = sources.first(where: { $0["id"] as? String == id }) else { throw PortError.message("远程曲库未连接") }
        return value
    }
    func publicSources() -> [[String: Any]] { sources.map { value in var safe = value; safe.removeValue(forKey: "password"); safe.removeValue(forKey: "token"); return safe } }
    func saveSource(_ value: [String: Any]) throws -> [String: Any] {
        let updated = sources.filter { $0["id"] as? String != value["id"] as? String } + [value]
        try saveSecrets(updated); sources = updated; sourceError = nil
        return publicSources().first { $0["id"] as? String == value["id"] as? String } ?? [:]
    }
    func removeSource(_ id: String) throws {
        let updated = sources.filter { $0["id"] as? String != id }
        try saveSecrets(updated); sources = updated
    }
    private func keychainQuery() -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: Bundle.main.bundleIdentifier ?? "Halcyon", kSecAttrAccount as String: account]
    }
    private func loadSecrets() throws -> [[String: Any]] {
        var query = keychainQuery(); query[kSecReturnData as String] = true; query[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?; let status = SecItemCopyMatching(query as CFDictionary, &value)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess, let data = value as? Data else { throw PortError.message("无法读取钥匙串 (\(status))") }
        return try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
    }
    private func saveSecrets(_ values: [[String: Any]]) throws {
        let data = try JSONSerialization.data(withJSONObject: values)
        let query = keychainQuery(); let changes = [kSecValueData as String: data]
        var status = SecItemUpdate(query as CFDictionary, changes as CFDictionary)
        if status == errSecItemNotFound {
            var item = query; item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(item as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw PortError.message("无法保存钥匙串 (\(status))") }
    }

    func importFiles(_ selected: [URL]) async throws -> [[String: Any]] {
        var incoming: [[String: Any]] = []
        for selection in selected {
            let access = selection.startAccessingSecurityScopedResource()
            defer { if access { selection.stopAccessingSecurityScopedResource() } }
            var isDirectory: ObjCBool = false
            FileManager.default.fileExists(atPath: selection.path, isDirectory: &isDirectory)
            let urls: [URL]
            if isDirectory.boolValue {
                let enumerator = FileManager.default.enumerator(at: selection, includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey], options: [.skipsHiddenFiles, .skipsPackageDescendants])
                urls = (enumerator?.allObjects as? [URL] ?? []).filter { extensions.contains($0.pathExtension.lowercased()) }
            } else { urls = extensions.contains(selection.pathExtension.lowercased()) ? [selection] : [] }
            if urls.count > 100000 { throw PortError.message("单次导入超过 100000 首，请分批导入") }
            for url in urls {
                let root = self.root
                let copied = try await Task.detached(priority: .userInitiated) { () -> (String, String) in
                    let values = try url.resourceValues(forKeys: [.isSymbolicLinkKey])
                    if values.isSymbolicLink == true { throw PortError.message("不导入符号链接") }
                    let coordinator = NSFileCoordinator()
                    var coordinationError: NSError?; var outcome: Result<(String, String), Error>?
                    coordinator.coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinated in
                        outcome = Result {
                            let file = try FileHandle(forReadingFrom: coordinated); defer { try? file.close() }
                            var hasher = SHA256()
                            while let chunk = try file.read(upToCount: 1024 * 1024), !chunk.isEmpty { hasher.update(data: chunk) }
                            let id = hasher.finalize().map { String(format: "%02x", $0) }.joined()
                            let relative = "Music/\(id)/\(url.lastPathComponent)"
                            let destination = root.appendingPathComponent(relative)
                            try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
                            if !FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.copyItem(at: coordinated, to: destination) }
                            for suffix in ["ttml", "elrc", "lrc"] {
                                let sidecar = url.deletingPathExtension().appendingPathExtension(suffix)
                                let target = destination.deletingPathExtension().appendingPathExtension(suffix)
                                if FileManager.default.fileExists(atPath: sidecar.path), !FileManager.default.fileExists(atPath: target.path) { try? FileManager.default.copyItem(at: sidecar, to: target) }
                            }
                            return (id, relative)
                        }
                    }
                    if let error = coordinationError { throw error }
                    guard let result = outcome else { throw PortError.message("无法读取所选文件") }
                    return try result.get()
                }.value
                let file = root.appendingPathComponent(copied.1)
                var song = await metadata(file)
                song["id"] = copied.0; song["folder"] = url.deletingLastPathComponent().lastPathComponent
                records.removeAll { ($0["song"] as? [String: Any])?["id"] as? String == copied.0 }
                records.append(["file": copied.1, "song": song]); incoming.append(song)
            }
        }
        try write("library.json", value: records)
        return incoming
    }
    func download(_ song: [String: Any], source: [String: Any]) async throws -> [String: Any] {
        guard let id = song["id"] as? String, song["sourceId"] as? String == source["id"] as? String else { throw PortError.message("无效下载来源") }
        let hash = SHA256.hash(data: Data(id.utf8)).map { String(format: "%02x", $0) }.joined()
        let suffix = song["format"] as? String ?? URL(string: song["remotePath"] as? String ?? "")?.pathExtension ?? "mp3"
        guard suffix.range(of: "^[a-zA-Z0-9]{1,8}$", options: .regularExpression) != nil else { throw PortError.message("无效音频格式") }
        let temporary = try await RemoteClient.download(source: source, song: song)
        let relative = "Music/Downloads/\(hash)/audio.\(suffix)"; let destination = root.appendingPathComponent(relative)
        try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        let staging = destination.appendingPathExtension("part")
        defer { try? FileManager.default.removeItem(at: staging); try? FileManager.default.removeItem(at: temporary) }
        try? FileManager.default.removeItem(at: staging)
        try FileManager.default.copyItem(at: temporary, to: staging)
        try Task.checkCancellation()
        if FileManager.default.fileExists(atPath: destination.path) { _ = try FileManager.default.replaceItemAt(destination, withItemAt: staging) }
        else { try FileManager.default.moveItem(at: staging, to: destination) }
        let parsed = await metadata(destination); var cached = song; cached["offline"] = true
        for key in ["sampleRate", "bitDepth", "bitrate", "size"] { if let value = parsed[key] { cached[key] = value } }
        if let duration = parsed["duration"] as? Double, duration > 0 { cached["duration"] = duration }
        records.removeAll { ($0["song"] as? [String: Any])?["id"] as? String == id }; records.append(["file": relative, "song": cached])
        try write("library.json", value: records); return cached
    }
    func saveLyrics(_ id: String, text: String) throws {
        guard text.utf8.count <= 2000000 else { throw PortError.message("歌词超过 2 MB") }
        let url = try localURL(id).deletingPathExtension().appendingPathExtension(text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("<") ? "ttml" : "lrc")
        try text.write(to: url, atomically: true, encoding: .utf8)
        if let index = records.firstIndex(where: { ($0["song"] as? [String: Any])?["id"] as? String == id }), var song = records[index]["song"] as? [String: Any] { song["lyrics"] = text; records[index]["song"] = song; try write("library.json", value: records) }
    }
    private func metadata(_ url: URL) async -> [String: Any] {
        let parts = url.deletingPathExtension().lastPathComponent.components(separatedBy: " - ")
        var song: [String: Any] = ["title": parts.count > 1 ? parts.dropFirst().joined(separator: " - ") : parts[0], "artist": parts.count > 1 ? parts[0] : "未知艺术家", "album": "未知专辑", "duration": 0, "fileName": url.lastPathComponent, "format": url.pathExtension]
        let asset = AVURLAsset(url: url)
        song["size"] = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize
        if let track = try? await asset.loadTracks(withMediaType: .audio).first {
            if let bitrate = try? await track.load(.estimatedDataRate) { song["bitrate"] = Double(bitrate) }
            if let formats = try? await track.load(.formatDescriptions), let format = formats.first, let description = CMAudioFormatDescriptionGetStreamBasicDescription(format) { song["sampleRate"] = description.pointee.mSampleRate; if description.pointee.mBitsPerChannel > 0 { song["bitDepth"] = Int(description.pointee.mBitsPerChannel) } }
        }
        if let duration = try? await asset.load(.duration), duration.seconds.isFinite { song["duration"] = duration.seconds }
        if let items = try? await asset.load(.commonMetadata) {
            for item in items {
                if item.commonKey == .commonKeyArtwork, let data = try? await item.load(.dataValue), let image = UIImage(data: data) {
                    let scale = min(1, 512 / max(image.size.width, image.size.height))
                    let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                    let renderer = UIGraphicsImageRenderer(size: size)
                    let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
                    if let jpeg = resized.jpegData(compressionQuality: 0.8) { song["artwork"] = "data:image/jpeg;base64," + jpeg.base64EncodedString() }
                }
                if let value = try? await item.load(.stringValue), !value.isEmpty {
                    if item.commonKey == .commonKeyTitle { song["title"] = value }
                    if item.commonKey == .commonKeyArtist { song["artist"] = value }
                    if item.commonKey == .commonKeyAlbumName { song["album"] = value }
                }
            }
        }
        if let formats = try? await asset.load(.availableMetadataFormats) {
            for format in formats {
                if let items = try? await asset.loadMetadata(for: format) {
                    for item in items where item.identifier?.rawValue.lowercased().contains("uslt") == true || item.identifier?.rawValue.lowercased().contains("lyrics") == true {
                        if let value = try? await item.load(.stringValue) { song["lyrics"] = value }
                    }
                }
            }
        }
        for suffix in ["ttml", "elrc", "lrc"] {
            if let text = try? String(contentsOf: url.deletingPathExtension().appendingPathExtension(suffix), encoding: .utf8) { song["lyrics"] = text; break }
        }
        return song
    }
}
