import UIKit
import WebKit
import UniformTypeIdentifiers
import AVKit

@MainActor
final class PlayerController: UIViewController, WKScriptMessageHandler, WKNavigationDelegate, UIDocumentPickerDelegate {
    private let store: NativeStore = {
        #if DEBUG
        if let profile = ProcessInfo.processInfo.environment["HALCYON_UI_PROFILE"], UUID(uuidString: profile) != nil,
           let root = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.appendingPathComponent("UIRegression").appendingPathComponent(profile) {
            return NativeStore(root: root, account: "Halcyon.UIRegression." + profile)
        }
        #endif
        return NativeStore()
    }()
    private lazy var player = NativePlayer(store: store)
    private var web: WKWebView!
    private var picker: CheckedContinuation<[URL], Error>?
    private var pickerImport = false
    private var downloads: [String: Task<[String: Any], Error>] = [:]
    private let scheme = WebAssets()
    private let bridge = """
    (() => {
      let sequence = 0; const pending = new Map(); const listeners = new Set();
      if (!crypto.randomUUID) crypto.randomUUID = () => '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, c => (Number(c) ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> Number(c) / 4).toString(16));
      window.halcyon = {platform:'ios', invoke(method,args={}) { return new Promise((resolve,reject) => { const id=++sequence; pending.set(id,{resolve,reject}); window.webkit.messageHandlers.halcyon.postMessage({id,method,args}); }); }, onEvent(listener) { listeners.add(listener); return () => listeners.delete(listener); }};
      window.__halcyonResolve = (id,value,error) => { const item=pending.get(id); if (!item) return; pending.delete(id); error ? item.reject(new Error(error)) : item.resolve(value); };
      window.__halcyonEvent = event => listeners.forEach(listener => listener(event));
    })();
    """
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .systemBackground
        let configuration = WKWebViewConfiguration()
        configuration.setURLSchemeHandler(scheme, forURLScheme: "halcyon")
        configuration.userContentController.add(self, name: "halcyon")
        configuration.userContentController.addUserScript(WKUserScript(source: bridge, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        web = WKWebView(frame: .zero, configuration: configuration); web.navigationDelegate = self
        web.isOpaque = false; web.backgroundColor = .systemBackground; web.scrollView.bounces = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        view.addSubview(web); web.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([web.topAnchor.constraint(equalTo: view.topAnchor), web.bottomAnchor.constraint(equalTo: view.bottomAnchor), web.leadingAnchor.constraint(equalTo: view.leadingAnchor), web.trailingAnchor.constraint(equalTo: view.trailingAnchor)])
        player.event = { [weak self] event in self?.emit(event) }
        #if DEBUG
        if ProcessInfo.processInfo.environment["HALCYON_UI_SMOKE"] == "1" {
            Task { do { try await seedAudioFixtures() } catch { print("Fixture import failed: \(error)") }; web.load(URLRequest(url: URL(string: "halcyon://app/index.html")!)) }
        } else { web.load(URLRequest(url: URL(string: "halcyon://app/index.html")!)) }
        #else
        web.load(URLRequest(url: URL(string: "halcyon://app/index.html")!))
        #endif
        NotificationCenter.default.addObserver(self, selector: #selector(foreground), name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    #if DEBUG
    // UI tests exercise the real file store and audio engine without automating Files.
    private func seedAudioFixtures() async throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("HalcyonFixtures")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        var files: [URL] = []
        for (n, title) in ["Native First", "Native Second"].enumerated() {
            var data = Data()
            func ascii(_ text: String) { data.append(contentsOf: text.utf8) }
            func u16(_ value: UInt16) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
            func u32(_ value: UInt32) { var v = value.littleEndian; withUnsafeBytes(of: &v) { data.append(contentsOf: $0) } }
            // Keep UI playback alive through slow simulator automation. Native tests
            // retain short WAVs to verify automatic advance and end-of-queue behavior.
            let seconds = 120
            let frames = 48000 * seconds
            ascii("RIFF"); u32(UInt32(36 + frames * 2)); ascii("WAVEfmt "); u32(16); u16(1); u16(1); u32(48000); u32(96000); u16(2); u16(16); ascii("data"); u32(UInt32(frames * 2))
            // Integer-Hz tones repeat exactly every second; generate the period once.
            let headerLength = data.count
            for i in 0..<48000 { u16(UInt16(bitPattern: Int16(sin(Double(i) / 48000 * 2 * .pi * Double(220 + n * 110)) * 500))) }
            let period = Data(data.dropFirst(headerLength))
            for _ in 1..<seconds { data.append(period) }
            let url = folder.appendingPathComponent("Fixture - " + title + ".wav"); try data.write(to: url); files.append(url)
        }
        _ = try await store.importFiles(files)
    }
    #endif
    @objc private func foreground() { player.publishStats() }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let url = navigationAction.request.url
        decisionHandler(url?.scheme == "halcyon" && url?.host == "app" ? .allow : .cancel)
    }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame, message.frameInfo.request.url?.scheme == "halcyon", message.frameInfo.request.url?.host == "app", let body = message.body as? [String: Any], let id = body["id"] as? Int, let method = body["method"] as? String else { return }
        let args = body["args"] as? [String: Any] ?? [:]
        Task {
            do { let result = try await invoke(method, args: args); reply(id, value: result, error: nil) }
            catch { reply(id, value: nil, error: error.localizedDescription) }
        }
    }
    private func encoded(_ value: Any?) -> String {
        guard let value, let data = try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed]), let text = String(data: data, encoding: .utf8) else { return "null" }; return text
    }
    private func reply(_ id: Int, value: Any?, error: String?) { web.evaluateJavaScript("window.__halcyonResolve(\(id),\(encoded(value)),\(encoded(error)))", completionHandler: nil) }
    private func emit(_ event: [String: Any]) {
        // The native queue owns background playback. Do not accumulate script evaluations
        // against a suspended WebKit process; foreground() delivers the latest snapshot.
        guard UIApplication.shared.applicationState == .active else { return }
        web.evaluateJavaScript("window.__halcyonEvent(\(encoded(event)))", completionHandler: nil)
    }
    private func choose(_ types: [UTType]) async throws -> [URL] {
        guard picker == nil, presentedViewController == nil else { throw PortError.message("请先完成当前文件操作") }
        return try await withCheckedThrowingContinuation { continuation in
            picker = continuation
            let controller = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: false)
            controller.delegate = self; controller.allowsMultipleSelection = pickerImport
            present(controller, animated: true)
        }
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { picker?.resume(returning: urls); picker = nil }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { picker?.resume(returning: []); picker = nil }
    private func invoke(_ method: String, args: [String: Any]) async throws -> Any? {
        switch method {
        case "library.list": return store.library()
        case "library.import":
            pickerImport = true
            let types: [UTType] = args["folder"] as? Bool == true ? [.folder] : [.audio, .data]
            return try await store.importFiles(choose(types))
        case "video.import":
            pickerImport = false; guard let url = try await choose([.movie]).first else { return false }
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            let id = args["id"] as? String ?? ""; _ = try store.localURL(id)
            let directory = store.root.appendingPathComponent("Videos"); try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let name = UUID().uuidString + "." + url.pathExtension; let destination = directory.appendingPathComponent(name); try FileManager.default.copyItem(at: url, to: destination)
            var videos = try store.read("videos.json") as? [String: String] ?? [:]; videos[id] = name; try store.write("videos.json", value: videos); return true
        case "video.has": return (try store.read("videos.json") as? [String: String])?[args["id"] as? String ?? ""] != nil
        case "video.play":
            guard let name = (try store.read("videos.json") as? [String: String])?[args["id"] as? String ?? ""], !name.contains("/") else { throw PortError.message("请先关联 MV") }
            player.toggle(false); let controller = AVPlayerViewController(); controller.player = AVPlayer(url: store.root.appendingPathComponent("Videos/" + name)); controller.allowsPictureInPicturePlayback = true
            present(controller, animated: true) { controller.player?.play() }; return nil
        case "library.download":
            let id = args["id"] as? String ?? ""; guard downloads[id] == nil else { throw PortError.message("这首歌曲正在下载") }
            let source = try store.source(args["sourceId"] as? String ?? "")
            let task = Task { try await store.download(args, source: source) }; downloads[id] = task; defer { downloads[id] = nil }; return try await task.value
        case "download.cancel": downloads[args["id"] as? String ?? ""]?.cancel(); return nil
        case "remote.asset": return try await RemoteClient.asset(source: store.source(args["sourceId"] as? String ?? ""), args: args)
        case "lyrics.save": try store.saveLyrics(args["id"] as? String ?? "", text: args["text"] as? String ?? ""); return nil
        case "image.import":
            pickerImport = false; guard let url = try await choose([.image]).first else { return "" }
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            guard (try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) < 16000000, let image = UIImage(data: try Data(contentsOf: url)) else { throw PortError.message("图片无效或超过 16 MB") }
            let scale = min(1, 512 / max(image.size.width, image.size.height)); let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
            guard let jpeg = resized.jpegData(compressionQuality: 0.8) else { return "" }; return "data:image/jpeg;base64," + jpeg.base64EncodedString()
        case "library.remove": try store.remove(args["id"] as? String ?? ""); return nil
        case "state.load":
            var state = try store.read("state.json") as? [String: Any]
            if state != nil, let playback = try store.read("native-playback.json") as? [String: Any] { for key in ["queue", "index", "shuffle", "repeat"] { state?[key] = playback[key] } }
            if var value = state, let stats = try store.read("native-history.json") as? [String: Any] {
                var counts = value["counts"] as? [String: Int] ?? [:]; for (id, n) in stats["counts"] as? [String: Int] ?? [:] { counts[id] = max(counts[id] ?? 0, n) }; value["counts"] = counts
                let old = value["history"] as? [[String: Any]] ?? []; let incoming = stats["history"] as? [[String: Any]] ?? []
                var seen = Set<String>(); value["history"] = Array((incoming + old).sorted { ($0["at"] as? Double ?? 0) > ($1["at"] as? Double ?? 0) }.filter { seen.insert("\($0["id"] ?? ""):\($0["at"] ?? 0)").inserted }.prefix(1000)); state = value
            }
            return state
        case "history.restore": try player.restoreStats(args); return nil
        case "state.save": try store.write("state.json", value: args); return nil
        case "sources.list": if let error = store.sourceError { throw PortError.message(error) }; return store.publicSources()
        case "sources.save":
            var values = args
            if let old = try? store.source(args["id"] as? String ?? ""), old["url"] as? String == args["url"] as? String, old["username"] as? String == args["username"] as? String, old["type"] as? String == args["type"] as? String, (args["password"] as? String ?? "").isEmpty { values["password"] = old["password"]; values["token"] = old["token"]; values["userId"] = old["userId"] }
            return try store.saveSource(await RemoteClient.prepare(values))
        case "sources.remove": try store.removeSource(args["id"] as? String ?? ""); return nil
        case "ai.request": return try await RemoteClient.ai(source: store.source(args["sourceId"] as? String ?? ""), prompt: args["prompt"] as? String ?? "")
        case "remote.request": return try await RemoteClient.request(source: store.source(args["sourceId"] as? String ?? ""), args: args)
        case "text.import":
            pickerImport = false
            let types: [UTType] = [.json, .plainText, .xml, .data]
            guard let url = try await choose(types).first else { return "" }
            let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
            let values = try url.resourceValues(forKeys: [.fileSizeKey]); if (values.fileSize ?? 0) > 16000000 { throw PortError.message("文本超过 16 MB") }
            return try String(contentsOf: url, encoding: .utf8)
        case "text.export":
            guard let text = args["text"] as? String, text.utf8.count <= 16000000 else { throw PortError.message("无效导出内容") }
            let name = (args["name"] as? String ?? "Halcyon.json").components(separatedBy: "/").last ?? "Halcyon.json"
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).appendingPathComponent(name)
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try text.write(to: url, atomically: true, encoding: .utf8)
            let share = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            share.popoverPresentationController?.sourceView = view; share.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
            share.completionWithItemsHandler = { _, _, _, _ in try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
            present(share, animated: true); return nil
        case "history.remove": player.removeHistory(args["id"] as? String ?? ""); return nil
        case "player.queue": try player.setQueue(args); return nil
        case "player.toggle": player.toggle(args["playing"] as? Bool ?? false); return nil
        case "player.seek": player.seek(args["position"] as? Double ?? 0); return nil
        case "player.skip": player.skip(args["direction"] as? Int ?? 1); return nil
        case "player.editQueue": try player.editQueue(args); return nil
        case "player.effects": player.configure(args); return nil
        case "player.modes": player.modes(args); return nil
        case "player.volume": player.setVolume(args["volume"] as? Double ?? 1); return nil
        case "player.sleep": player.sleep(args["minutes"] as? Double ?? 0, finish: args["finishTrack"] as? Bool ?? false); return nil
        case "player.append": if let song = args["song"] as? [String: Any] { player.append(song) }; return nil
        case "player.stop": player.stop(); return nil
        case "player.route":
            let sheet = UIViewController(); sheet.view.backgroundColor = .systemBackground
            let label = UILabel(); label.text = "点击下方按钮选择 AirPlay 输出设备"; label.textAlignment = .center; label.font = .systemFont(ofSize: 16)
            let route = AVRoutePickerView(); route.activeTintColor = .systemPurple; route.tintColor = .systemPurple
            let stack = UIStackView(arrangedSubviews: [label, route]); stack.axis = .vertical; stack.spacing = 24; stack.translatesAutoresizingMaskIntoConstraints = false
            sheet.view.addSubview(stack)
            NSLayoutConstraint.activate([stack.centerXAnchor.constraint(equalTo: sheet.view.centerXAnchor), stack.centerYAnchor.constraint(equalTo: sheet.view.centerYAnchor), route.heightAnchor.constraint(equalToConstant: 60)])
            sheet.sheetPresentationController?.detents = [.medium()]; sheet.sheetPresentationController?.prefersGrabberVisible = true
            present(sheet, animated: true); return nil
        default: throw PortError.message("未知客户端命令")
        }
    }
}
