import Foundation
import AVFoundation
import MediaPlayer
import UIKit

@MainActor
final class NativePlayer {
    let audio = AVPlayer()
    let store: NativeStore
    var event: (([String: Any]) -> Void)?
    private(set) var queue: [[String: Any]] = []
    private(set) var index = 0
    private var shuffle = false
    private var repeatMode = "off"
    private var timer: Timer?
    private var statusObserver: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private var observers: [NSObjectProtocol] = []
    private var sleepDeadline: Date?
    private var interrupted = false
    private var error = ""
    private var volume: Float = 1
    private let local = LocalAudioEngine()
    private var usingLocal = false
    private var speed: Float = 1
    private var effects: [String: Any] = [:]
    private var finishTrack = false
    private var finishAtDeadline = false
    private var heard: Double = 0
    private var lastPosition: Double = 0
    private var counted = false
    private var counts: [String: Int] = [:]
    private var history: [[String: Any]] = []
    private var statsChanged = false
    private var isPlaying: Bool { usingLocal ? local.playing : audio.rate > 0 }
    private var position: Double { usingLocal ? local.position : (audio.currentTime().seconds.isFinite ? audio.currentTime().seconds : 0) }
    private var duration: Double { usingLocal ? local.duration : (audio.currentItem?.duration.seconds ?? 0) }
    private func pauseAudio() { if usingLocal { local.pause() } else { audio.pause() } }


    init(store: NativeStore) {
        self.store = store
        do { try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default) } catch { self.error = error.localizedDescription }
        if let data = try? store.read("native-history.json") as? [String: Any] {
            counts = data["counts"] as? [String: Int] ?? [:]; history = data["history"] as? [[String: Any]] ?? []
        }
        local.ended = { [weak self] in self?.ended() }
        timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        if let timer { RunLoop.main.add(timer, forMode: .common) }
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let kind = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let options = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            Task { @MainActor in
                guard let self else { return }
                if kind == AVAudioSession.InterruptionType.began.rawValue { self.interrupted = self.isPlaying; self.pauseAudio() }
                else if self.interrupted && AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) { self.toggle(true); self.interrupted = false }
                self.publish()
            }
        })
        observers.append(NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { Task { @MainActor in self?.toggle(false) } }
        })
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in Task { @MainActor in self?.toggle(true) }; return .success }
        center.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.toggle(false) }; return .success }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in Task { @MainActor in guard let self else { return }; self.toggle(!self.isPlaying) }; return .success }
        center.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.skip(1) }; return .success }
        center.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.skip(-1) }; return .success }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let seek = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let position = seek.positionTime; Task { @MainActor in self?.seek(position) }; return .success
        }
        controls()
    }
    func setQueue(_ args: [String: Any]) throws {
        guard let items = args["queue"] as? [[String: Any]], !items.isEmpty else { stop(); return }
        queue = items; index = max(0, min(items.count - 1, args["index"] as? Int ?? 0))
        shuffle = args["shuffle"] as? Bool ?? false; repeatMode = args["repeat"] as? String ?? "off"
        volume = Float(args["volume"] as? Double ?? 1)
        try load(autoplay: args["autoplay"] as? Bool ?? true)
    }
    private func load(autoplay: Bool) throws {
        statusObserver?.invalidate(); statusObserver = nil
        audio.pause(); local.stop(); usingLocal = false; error = ""; heard = 0; lastPosition = 0; counted = false
        audio.replaceCurrentItem(with: nil)
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        let song = queue[index]; let url: URL; var headers: [String: String] = [:]
        if let sourceId = song["sourceId"] as? String, song["offline"] as? Bool != true { (url, headers) = try RemoteClient.stream(source: store.source(sourceId), song: song) }
        else { url = try store.localURL(song["id"] as? String ?? "") }
        if url.isFileURL {
            do {
                try local.load(url); usingLocal = true; applyEffects()
                if autoplay { try AVAudioSession.sharedInstance().setActive(true); try local.play() }
                controls(); persist(); updateMetadata(); publish(); return
            } catch {
                local.stop(); usingLocal = false
                #if DEBUG
                if ProcessInfo.processInfo.environment["HALCYON_UI_SMOKE"] == "1" { throw error }
                #endif
            } // System decoder remains a fallback in normal use.
        }
        let asset = AVURLAsset(url: url, options: headers.isEmpty ? nil : ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let item = AVPlayerItem(asset: asset); audio.replaceCurrentItem(with: item); audio.volume = volume
        statusObserver = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            if item.status == .failed { let message = item.error?.localizedDescription ?? "当前设备无法解码此音频"; Task { @MainActor in self?.error = message; self?.audio.pause(); self?.publish() } }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.ended() }
        }
        applyEffects()
        if autoplay { try AVAudioSession.sharedInstance().setActive(true); audio.playImmediately(atRate: speed) }
        controls(); persist(); updateMetadata(); publish()
    }
    func append(_ song: [String: Any]) { queue.append(song); persist(); controls() }
    func toggle(_ playing: Bool) {
        guard !queue.isEmpty else { return }
        do { if playing { try AVAudioSession.sharedInstance().setActive(true); if usingLocal { try local.play() } else { audio.playImmediately(atRate: speed) } } else { pauseAudio() } }
        catch { self.error = error.localizedDescription }
        publish()
    }
    func seek(_ position: Double) {
        guard position.isFinite else { return }
        let duration = self.duration
        let target = max(0, duration.isFinite && duration > 0 ? min(duration, position) : position)
        if usingLocal { local.seek(target) } else { audio.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) }
        lastPosition = target
        publish()
    }
    func skip(_ direction: Int, automatic: Bool = false) {
        guard !queue.isEmpty else { return }
        if direction < 0 && position > 3 { seek(0); return }
        var next = index + (direction < 0 ? -1 : 1)
        if shuffle && queue.count > 1 { next = (index + Int.random(in: 1..<queue.count)) % queue.count }
        if next < 0 || next >= queue.count {
            if repeatMode == "all" { next = (next + queue.count) % queue.count }
            else { if automatic { pauseAudio(); publish() }; return }
        }
        let autoplay = automatic || isPlaying
        index = next
        do { try load(autoplay: autoplay) } catch { self.error = error.localizedDescription; pauseAudio(); persist(); publish() }
    }
    func modes(_ args: [String: Any]) { shuffle = args["shuffle"] as? Bool ?? false; repeatMode = args["repeat"] as? String ?? "off"; persist() }
    func setVolume(_ value: Double) { volume = Float(max(0, min(1, value))); audio.volume = volume; local.volume = volume }
    func sleep(_ minutes: Double, finish: Bool) { sleepDeadline = minutes > 0 ? Date().addingTimeInterval(minutes * 60) : nil; finishTrack = minutes == -1; finishAtDeadline = finish }
    func stop() {
        audio.pause(); local.stop(); usingLocal = false; audio.replaceCurrentItem(with: nil); queue = []; index = 0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; controls(); persist(); publish()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    func editQueue(_ args: [String: Any]) throws {
        guard let items = args["queue"] as? [[String: Any]], !items.isEmpty else { stop(); return }
        let next = max(0, min(items.count - 1, args["index"] as? Int ?? 0))
        let oldID = queue.indices.contains(index) ? queue[index]["id"] as? String : nil
        let autoplay = isPlaying; queue = items; index = next
        if oldID != items[next]["id"] as? String { try load(autoplay: autoplay) }
        else { applyEffects(); updateMetadata(); controls(); persist(); publish() }
    }
    func configure(_ args: [String: Any]) {
        effects = args; speed = Float(max(0.5, min(3, args["rate"] as? Double ?? 1)))
        applyEffects(); publish()
    }
    private func applyEffects() {
        let enabled = effects["eqEnabled"] as? Bool ?? false
        let values = effects["eq"] as? [Double] ?? Array(repeating: 0, count: 10)
        let replay = effects["replayGain"] as? Bool == true && queue.indices.contains(index) ? queue[index]["replayGain"] as? Double ?? 0 : 0
        local.configure(rate: speed, preservePitch: effects["preservePitch"] as? Bool ?? true, gains: enabled ? values : Array(repeating: 0, count: 10), preamp: (enabled ? effects["preamp"] as? Double ?? 0 : 0) + replay)
        local.volume = volume
        audio.currentItem?.audioTimePitchAlgorithm = effects["preservePitch"] as? Bool != false ? .timeDomain : .varispeed
        if !usingLocal && audio.rate > 0 { audio.rate = speed }
    }
    private func ended() {
        if finishTrack { finishTrack = false; toggle(false); return }
        if repeatMode == "one" { seek(0); toggle(true) } else { skip(1, automatic: true) }
    }
    private func tick() {
        if let deadline = sleepDeadline, Date() >= deadline { sleepDeadline = nil; if finishAtDeadline && isPlaying { finishTrack = true } else { toggle(false) } }
        let delta = position - lastPosition; lastPosition = position
        if isPlaying && delta > 0 && delta < 4 { heard += delta }
        if !counted, queue.indices.contains(index), heard >= max(1, min(30, duration.isFinite && duration > 0 ? duration / 2 : 30)), let id = queue[index]["id"] as? String {
            counted = true; counts[id, default: 0] += 1
            history.insert(["id": id, "at": Date().timeIntervalSince1970 * 1000], at: 0); history = Array(history.prefix(1000)); statsChanged = true
            try? store.write("native-history.json", value: ["counts": counts, "history": history])
        }
        publish()
    }
    func restoreStats(_ args: [String: Any]) throws {
        counts = args["counts"] as? [String: Int] ?? [:]; history = Array((args["history"] as? [[String: Any]] ?? []).prefix(1000)); statsChanged = false
        try store.write("native-history.json", value: ["counts": counts, "history": history])
    }
    func removeHistory(_ id: String) { history.removeAll { $0["id"] as? String == id }; try? store.write("native-history.json", value: ["counts": counts, "history": history]) }
    func publishStats() { statsChanged = true; publish() }
    private func controls() {
        let center = MPRemoteCommandCenter.shared(); let enabled = !queue.isEmpty
        center.playCommand.isEnabled = enabled; center.pauseCommand.isEnabled = enabled
        center.togglePlayPauseCommand.isEnabled = enabled; center.nextTrackCommand.isEnabled = enabled
        center.previousTrackCommand.isEnabled = enabled; center.changePlaybackPositionCommand.isEnabled = enabled
    }
    private func persist() { try? store.write("native-playback.json", value: ["queue": queue.compactMap { $0["id"] as? String }, "index": index, "shuffle": shuffle, "repeat": repeatMode]) }
    private func updateMetadata() {
        guard queue.indices.contains(index) else { return }
        let song = queue[index]
        var info: [String: Any] = [MPMediaItemPropertyTitle: song["title"] ?? "", MPMediaItemPropertyArtist: song["artist"] ?? "", MPMediaItemPropertyAlbumTitle: song["album"] ?? ""]
        if let artwork = song["artwork"] as? String, let part = artwork.components(separatedBy: ",").last, let data = Data(base64Encoded: part), let image = UIImage(data: data) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
    func publish() {
        let seconds = position; let duration = self.duration
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        if !queue.isEmpty {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = seconds.isFinite ? seconds : 0
            info[MPMediaItemPropertyPlaybackDuration] = duration.isFinite ? duration : 0
            info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? speed : 0
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
        var payload: [String: Any] = ["type": "playback", "index": index, "playing": isPlaying, "position": seconds.isFinite ? seconds : 0, "duration": duration.isFinite ? duration : 0, "error": error, "rate": speed]
        if statsChanged { payload["counts"] = counts; payload["history"] = history; statsChanged = false }
        event?(payload)
    }
}

// Local files are decoded by Apple and processed in a native EQ / time-pitch graph.
@MainActor
final class LocalAudioEngine {
    private let engine = AVAudioEngine()
    private let node = AVAudioPlayerNode()
    private let eq = AVAudioUnitEQ(numberOfBands: 10)
    private let pitch = AVAudioUnitTimePitch()
    private var file: AVAudioFile?
    private var start: AVAudioFramePosition = 0
    private var pausedPosition: Double = 0
    private var generation = 0
    private(set) var playing = false
    var ended: (() -> Void)?
    var duration: Double { guard let file else { return 0 }; return Double(file.length) / file.processingFormat.sampleRate }
    var position: Double {
        guard playing, let file, let time = node.lastRenderTime, let playerTime = node.playerTime(forNodeTime: time) else { return pausedPosition }
        return min(duration, Double(start) / file.processingFormat.sampleRate + Double(playerTime.sampleTime) / playerTime.sampleRate)
    }
    var volume: Float { get { node.volume } set { node.volume = newValue } }
    init() { engine.attach(node); engine.attach(eq); engine.attach(pitch) }
    func load(_ url: URL) throws {
        stop(); let file = try AVAudioFile(forReading: url); self.file = file
        engine.disconnectNodeOutput(node); engine.disconnectNodeOutput(eq); engine.disconnectNodeOutput(pitch)
        engine.connect(node, to: eq, format: file.processingFormat); engine.connect(eq, to: pitch, format: file.processingFormat); engine.connect(pitch, to: engine.mainMixerNode, format: file.processingFormat)
        schedule(0); engine.prepare()
    }
    func configure(rate: Float, preservePitch: Bool, gains: [Double], preamp: Double) {
        pitch.rate = rate; pitch.pitch = preservePitch ? 0 : 1200 * log2(rate)
        let frequencies: [Float] = [31,62,125,250,500,1000,2000,4000,8000,16000]
        for (n, band) in eq.bands.enumerated() { band.filterType = .parametric; band.frequency = min(frequencies[n], Float(file?.processingFormat.sampleRate ?? 44100) * 0.45); band.bandwidth = 1; band.gain = Float(max(-12,min(12,gains.indices.contains(n) ? gains[n] : 0))); band.bypass = false }
        eq.globalGain = Float(max(-24,min(6,preamp)))
    }
    private func schedule(_ seconds: Double) {
        guard let file else { return }; generation += 1; let token = generation; node.stop(); playing = false
        start = min(file.length, AVAudioFramePosition(max(0,seconds) * file.processingFormat.sampleRate)); pausedPosition = Double(start) / file.processingFormat.sampleRate
        let remaining = file.length - start
        if remaining <= 0 { return }
        node.scheduleSegment(file, startingFrame: start, frameCount: AVAudioFrameCount(min(remaining, Int64(UInt32.max))), at: nil, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            Task { @MainActor in guard let self, self.generation == token else { return }; self.pausedPosition = self.duration; self.playing = false; self.ended?() }
        }
    }
    func play() throws { if pausedPosition >= duration { schedule(0) }; if !engine.isRunning { try engine.start() }; node.play(); playing = true }
    func pause() { pausedPosition = position; node.pause(); playing = false }
    func seek(_ seconds: Double) { let resume = playing; schedule(seconds); if resume { try? play() } }
    func stop() { generation += 1; node.stop(); engine.stop(); file = nil; start = 0; pausedPosition = 0; playing = false }
}
