import Foundation
import WebKit

final class WebAssets: NSObject, WKURLSchemeHandler {
    private let root = Bundle.main.url(forResource: "Web", withExtension: nil)
    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        do {
            guard let url = urlSchemeTask.request.url, url.host == "app", let root else { throw PortError.message("缺少界面资源，请先运行 npm run prepare:ios") }
            let relative = url.path == "/" ? "index.html" : String(url.path.dropFirst())
            let file = root.appendingPathComponent(relative).standardizedFileURL
            guard file.path.hasPrefix(root.path + "/") else { throw PortError.message("无效界面资源路径") }
            let data = try Data(contentsOf: file)
            let mime = ["html": "text/html", "js": "application/javascript", "css": "text/css", "svg": "image/svg+xml", "png": "image/png", "jpg": "image/jpeg"][file.pathExtension] ?? "application/octet-stream"
            let response = URLResponse(url: url, mimeType: mime, expectedContentLength: data.count, textEncodingName: "utf-8")
            urlSchemeTask.didReceive(response); urlSchemeTask.didReceive(data); urlSchemeTask.didFinish()
        } catch { urlSchemeTask.didFailWithError(error) }
    }
    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}
}
