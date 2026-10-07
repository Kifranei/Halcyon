import Foundation
import CryptoKit
import UIKit

class RedirectGuard: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
enum RemoteClient {
    static func root(_ value: String) throws -> URL {
        guard let components = URLComponents(string: value), ["https", "http"].contains(components.scheme?.lowercased() ?? ""), components.host != nil, components.user == nil, components.password == nil, components.query == nil, components.fragment == nil else { throw PortError.message("请使用不含凭据、参数或片段的 HTTP(S) 服务器地址") }
        guard let url = URL(string: value.hasSuffix("/") ? value : value + "/") else { throw PortError.message("无效服务器地址") }
        return url
    }
    static func url(source: [String: Any], path: String, query: [String: String] = [:]) throws -> URL {
        let base = try root(source["url"] as? String ?? "")
        let decoded = path.removingPercentEncoding ?? path
        guard !path.hasPrefix("/"), !decoded.contains("\\"), !decoded.components(separatedBy: "/").contains(where: { $0 == ".." || $0 == "." }), let target = path.isEmpty ? base : URL(string: path, relativeTo: base)?.absoluteURL,
              target.scheme == base.scheme, target.host == base.host, target.port == base.port, target.path.hasPrefix(base.path) else { throw PortError.message("远程路径不能离开配置的服务器") }
        guard var components = URLComponents(url: target, resolvingAgainstBaseURL: true) else { throw PortError.message("无效远程地址") }
        var parameters = query
        if source["type"] as? String == "subsonic" {
            let salt = UUID().uuidString.replacingOccurrences(of: "-", with: "")
            let bytes = Data(((source["password"] as? String ?? "") + salt).utf8)
            parameters.merge(["u": source["username"] as? String ?? "", "t": Insecure.MD5.hash(data: bytes).map { String(format: "%02x", $0) }.joined(), "s": salt, "v": "1.16.1", "c": "Halcyon", "f": "json"]) { _, new in new }
        }
        components.queryItems = parameters.isEmpty ? nil : parameters.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let result = components.url else { throw PortError.message("无效请求地址") }; return result
    }
    static func headers(_ source: [String: Any]) -> [String: String] {
        if source["type"] as? String == "webdav", let username = source["username"] as? String, !username.isEmpty {
            let value = Data((username + ":" + (source["password"] as? String ?? "")).utf8).base64EncodedString()
            return ["Authorization": "Basic " + value]
        }
        if source["type"] as? String == "emby" { return ["X-Emby-Token": source["token"] as? String ?? ""] }
        return [:]
    }
    static func fetch(_ request: URLRequest) async throws -> Data {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 30; configuration.timeoutIntervalForResource = 60
        let session = URLSession(configuration: configuration, delegate: RedirectGuard(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { throw PortError.message("服务器返回 HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0)") }
        var data = Data()
        for try await byte in bytes { if data.count >= 16000000 { throw PortError.message("服务器响应超过 16 MB") }; data.append(byte) }
        return data
    }
    static func request(source: [String: Any], args: [String: Any]) async throws -> String {
        let method = args["method"] as? String ?? "GET"
        guard ["GET", "PROPFIND"].contains(method) else { throw PortError.message("不支持该请求方法") }
        var request = URLRequest(url: try url(source: source, path: args["path"] as? String ?? "", query: args["query"] as? [String: String] ?? [:]))
        request.httpMethod = method; request.allHTTPHeaderFields = headers(source)
        if method == "PROPFIND" { request.setValue("1", forHTTPHeaderField: "Depth"); request.setValue("application/xml; charset=utf-8", forHTTPHeaderField: "Content-Type"); request.httpBody = (args["body"] as? String)?.data(using: .utf8) }
        let data = try await fetch(request)
        guard let text = String(data: data, encoding: .utf8) else { throw PortError.message("服务器返回了非 UTF-8 内容") }; return text
    }
    static func ai(source: [String: Any], prompt: String) async throws -> String {
        guard source["type"] as? String == "ai", prompt.utf8.count <= 300000, !(source["model"] as? String ?? "").isEmpty, !(source["password"] as? String ?? "").isEmpty else { throw PortError.message("请检查 AI 模型、密钥和请求内容") }
        let anthropic = source["protocol"] as? String == "anthropic"
        var request = URLRequest(url: try url(source: source, path: anthropic ? "messages" : "chat/completions")); request.httpMethod = "POST"; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if anthropic { request.setValue(source["password"] as? String ?? "", forHTTPHeaderField: "x-api-key"); request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version") }
        else { request.setValue("Bearer " + (source["password"] as? String ?? ""), forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: ["model": source["model"] ?? "", "messages": [["role": "user", "content": prompt]], "max_tokens": 1800, "stream": false])
        let data = try JSONSerialization.jsonObject(with: await fetch(request)) as? [String: Any]
        let choices = data?["choices"] as? [[String: Any]]; let message = choices?.first?["message"] as? [String: Any]
        let content = data?["content"] as? [[String: Any]]
        let text = anthropic ? content?.compactMap { $0["text"] as? String }.joined(separator: "\n") : message?["content"] as? String
        guard let text, !text.isEmpty else { throw PortError.message("AI 服务未返回文本") }; return text
    }
    static func asset(source: [String: Any], args: [String: Any]) async throws -> String {
        var request = URLRequest(url: try url(source: source, path: args["path"] as? String ?? "", query: args["query"] as? [String: String] ?? [:])); request.allHTTPHeaderFields = headers(source)
        let data = try await fetch(request)
        guard data.count <= 4000000, let image = UIImage(data: data) else { throw PortError.message("无效服务器封面") }
        let scale = min(1, 512 / max(image.size.width, image.size.height)); let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let jpeg = resized.jpegData(compressionQuality: 0.8) else { throw PortError.message("无效服务器封面") }
        return "data:image/jpeg;base64," + jpeg.base64EncodedString()
    }
    static func download(source: [String: Any], song: [String: Any]) async throws -> URL {
        var (target, auth) = try stream(source: source, song: song)
        if source["type"] as? String == "subsonic" { target = URL(string: target.absoluteString.replacingOccurrences(of: "stream.view?", with: "download.view?"))! }
        var request = URLRequest(url: target); request.allHTTPHeaderFields = auth
        let configuration = URLSessionConfiguration.ephemeral; configuration.timeoutIntervalForRequest = 30; configuration.timeoutIntervalForResource = 300
        let session = URLSession(configuration: configuration, delegate: DownloadGuard(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (file, response) = try await session.download(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { try? FileManager.default.removeItem(at: file); throw PortError.message("下载失败 (HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0))") }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size > 0, size <= 2147483648 else { try? FileManager.default.removeItem(at: file); throw PortError.message("下载内容为空或超过 2 GB") }
        return file
    }
    static func prepare(_ raw: [String: Any]) async throws -> [String: Any] {
        guard ["webdav", "subsonic", "emby", "ai"].contains(raw["type"] as? String ?? ""), let id = raw["id"] as? String, UUID(uuidString: id) != nil else { throw PortError.message("无效曲库配置") }
        var source: [String: Any] = ["id": id, "type": raw["type"] ?? "", "name": raw["name"] ?? "", "url": try root(raw["url"] as? String ?? "").absoluteString, "username": raw["username"] ?? "", "password": raw["password"] ?? "", "model": raw["model"] ?? "", "protocol": raw["protocol"] ?? "openai"]
        if source["type"] as? String == "emby", raw["token"] == nil {
            var request = URLRequest(url: try url(source: source, path: "Users/AuthenticateByName"))
            request.httpMethod = "POST"; request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("MediaBrowser Client=\"Halcyon\", Device=\"iOS\", DeviceId=\"Halcyon-iOS\", Version=\"0.1.0\"", forHTTPHeaderField: "X-Emby-Authorization")
            request.httpBody = try JSONSerialization.data(withJSONObject: ["Username": source["username"] ?? "", "Pw": source["password"] ?? ""])
            let result = try JSONSerialization.jsonObject(with: await fetch(request)) as? [String: Any]
            guard let token = result?["AccessToken"] as? String, let user = result?["User"] as? [String: Any], let userId = user["Id"] as? String else { throw PortError.message("Emby 登录返回了无效信息") }
            source["token"] = token; source["userId"] = userId; source.removeValue(forKey: "password")
        }
        if let token = raw["token"] { source["token"] = token; source["userId"] = raw["userId"]; source.removeValue(forKey: "password") }
        return source
    }
    static func stream(source: [String: Any], song: [String: Any]) throws -> (URL, [String: String]) {
        let type = source["type"] as? String; var path = song["remotePath"] as? String ?? ""; var query: [String: String] = [:]
        if type == "subsonic" { path = "rest/stream.view"; query = ["id": song["remoteId"] as? String ?? ""] }
        if type == "emby" { let id = song["remoteId"] as? String ?? ""; guard id.range(of: "^[a-zA-Z0-9-]+$", options: .regularExpression) != nil else { throw PortError.message("无效 Emby 歌曲 ID") }; path = "Audio/\(id)/stream"; query = ["static": "true"] }
        return (try url(source: source, path: path, query: query), headers(source))
    }
}

final class DownloadGuard: RedirectGuard, URLSessionDownloadDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {}
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten > 2147483648 || totalBytesExpectedToWrite > 2147483648 { downloadTask.cancel() }
    }
}
