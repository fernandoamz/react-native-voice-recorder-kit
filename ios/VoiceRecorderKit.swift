import Foundation
import AVFoundation
import React

@objc(VoiceRecorderKit)
class VoiceRecorderKit: RCTEventEmitter {

  // MARK: - Audio engine and player properties

  private var audioEngine: AVAudioEngine?
  private var inputNode: AVAudioInputNode?
  private var recordingFile: AVAudioFile?
  private var recordingUrl: URL?

  private var audioPlayer: AVAudioPlayer?
  private var musicPlayer: AVAudioPlayerNode?
  private var mixerNode: AVAudioMixerNode?

  private var recorderTimer: DispatchSourceTimer?
  private var playerTimer: DispatchSourceTimer?

  private var isRecording = false
  private var isPlaying = false
  private var totalDuration: TimeInterval = 0.0
  private var recordingStartDate: Date?

  // Multi-engine properties for mixing music + mic
  private let recordEngine = AVAudioEngine()
  private let playbackEngine = AVAudioEngine()
  private let playerNode = AVAudioPlayerNode()

  private var micFile: AVAudioFile?
  private(set) var micURL: URL?

  private var musicFile: AVAudioFile?
  private var songURL: URL?
  private var startTime: Date?

  private var mixedOutputURL: URL?

  override init() {
    super.init()
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(handleRouteChange),
      name: AVAudioSession.routeChangeNotification,
      object: nil
    )
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  // MARK: - React Native setup

  override static func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func supportedEvents() -> [String]! {
    return [
      "onRecordProgress",
      "onPlaybackProgress",
      "onAudioRouteChanged",
      "onWaveformChunk"
    ]
  }

  // MARK: - Recording methods

  @objc(startRecording:rejecter:)
  func startRecording(_ resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.playAndRecord, mode: .default, options: [
        .allowBluetooth,
        .allowBluetoothA2DP,
        .allowAirPlay,
        .defaultToSpeaker
      ])
      try session.setActive(true)

      audioEngine = AVAudioEngine()
      guard let engine = audioEngine else {
        reject("ERR_ENGINE", "Failed to initialize AVAudioEngine", nil)
        return
      }

      inputNode = engine.inputNode
      let inputFormat = inputNode!.inputFormat(forBus: 0)

      let filename = UUID().uuidString + ".caf"
      let url = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
      recordingUrl = url
      recordingFile = try AVAudioFile(forWriting: url, settings: inputFormat.settings)

      inputNode!.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] (buffer, _) in
        guard let self = self else { return }

        do {
          try self.recordingFile?.write(from: buffer)
        } catch {
          print("❌ Error writing buffer: \(error)")
        }

        guard let channelData = buffer.floatChannelData?[0] else { return }
        let frameLength = Int(buffer.frameLength)
        let samples = Array(UnsafeBufferPointer(start: channelData, count: frameLength))

        let downsampleFactor = 8
        let downsampled = stride(from: 0, to: samples.count, by: downsampleFactor).map { samples[$0] }

        DispatchQueue.main.async {
          self.sendEvent(withName: "onWaveformChunk", body: [
            "samples": downsampled
          ])
        }
      }

      try engine.start()
      isRecording = true
      totalDuration = 0.0
      recordingStartDate = Date()
      startRecorderTimer()
      resolve(url.path)

    } catch {
      reject("ERR_RECORD", "Failed to start recording: \(error.localizedDescription)", error)
    }
  }

  @objc(startRecordingWithMusic:resolver:rejecter:)
  func startRecordingWithMusic(_ musicPath: String,
                               resolver resolve: @escaping RCTPromiseResolveBlock,
                               rejecter reject: @escaping RCTPromiseRejectBlock) {
    do {
      try configureAudioSession()

      let url = URL(fileURLWithPath: musicPath)

      guard FileManager.default.fileExists(atPath: url.path) else {
        reject("ERR_FILE_NOT_FOUND", "Music file not found at path: \(musicPath)", nil)
        return
      }

      self.songURL = url
      self.musicFile = try AVAudioFile(forReading: url)

      try setupRecordingEngine()
      if let musicFile = musicFile {
        try setupPlaybackEngine(with: musicFile)
      }

      startTime = Date()
      isRecording = true
      isPlaying = true

      resolve("Recording started with playback from path")

    } catch {
      reject("ERR_START_RECORD_PLAYBACK", "Failed to start recording with playback: \(error.localizedDescription)", error)
    }
  }

  @objc(stopRecording:rejecter:)
  func stopRecording(_ resolve: @escaping RCTPromiseResolveBlock,
                     rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard isRecording else {
      reject("not_recording", "Recording was not in progress", nil)
      return
    }

    audioEngine?.inputNode.removeTap(onBus: 0)
    musicPlayer?.stop()
    audioEngine?.stop()

    recordEngine.inputNode.removeTap(onBus: 0)
    playerNode.stop()
    recordEngine.stop()
    playbackEngine.stop()

    isRecording = false
    isPlaying = false

    let duration = Date().timeIntervalSince(recordingStartDate ?? startTime ?? Date())


    print("🛑 [stopRecording] Stopped recording")
    print("🛑 micURL: \(micURL?.path ?? "nil")")
    print("🛑 songURL: \(songURL?.path ?? "nil")")
    print("🛑 recordingUrl: \(recordingUrl?.path ?? "nil")")

    if let micURL = micURL, let songURL = songURL {
      let trimmedSongURL = FileManager.default.temporaryDirectory.appendingPathComponent("trimmedSong.m4a")
      let finalMixURL = FileManager.default.temporaryDirectory.appendingPathComponent("finalMix.m4a")

      trimAudio(inputURL: songURL, outputURL: trimmedSongURL, duration: duration) { [weak self] success in
        guard success else {
          reject("ERR_TRIM", "Failed to trim song", nil)
          return
        }
        self?.mixAudioFiles(micURL: micURL, musicURL: trimmedSongURL, outputURL: finalMixURL) { mixSuccess in
          DispatchQueue.main.async {
            if mixSuccess {
              self?.mixedOutputURL = finalMixURL
              resolve([
                "path": finalMixURL.path,
                "duration": duration
              ])
            } else {
              reject("ERR_MIX", "Failed to mix audio files", nil)
            }
          }
        }
      }
    } else {
      resolve([
        "path": recordingUrl?.path ?? "",
        "duration": duration
      ])
    }
  }

  // MARK: - Playback methods

  @objc(startPlayback:resolver:rejecter:)
  func startPlayback(_ path: String,
                    resolver resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {
    print("▶️ [startPlayback] Attempting to play file at path: \(path)")

    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.playAndRecord, mode: .default, options: [.allowBluetooth, .allowBluetoothA2DP, .defaultToSpeaker])
      try session.setActive(true)

      let url = URL(fileURLWithPath: path)
      
      guard FileManager.default.fileExists(atPath: url.path) else {
        print("❌ [startPlayback] File does not exist at path: \(url.path)")
        reject("ERR_NO_FILE", "File does not exist at path: \(url.path)", nil)
        return
      }

      let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
      let fileSize = attributes[.size] as? NSNumber
      print("📦 [startPlayback] File size: \(fileSize ?? 0) bytes")

      audioPlayer = try AVAudioPlayer(contentsOf: url)
      audioPlayer?.delegate = nil // Add delegate if needed
      audioPlayer?.prepareToPlay()
      audioPlayer?.numberOfLoops = -1

      let success = audioPlayer?.play() ?? false

      if success {
        print("✅ [startPlayback] Playback started successfully.")
        isPlaying = true
        startPlayerTimer()
        resolve("Playback started")
      } else {
        print("❌ [startPlayback] Failed to play audio")
        reject("ERR_PLAY", "AVAudioPlayer failed to play", nil)
      }

    } catch {
      print("❌ [startPlayback] Error: \(error)")
      reject("ERR_PLAYBACK", "Failed to play audio: \(error.localizedDescription)", error)
    }
}


  @objc(setLoopPlayback:resolver:rejecter:)
  func setLoopPlayback(_ shouldLoop: Bool,
                       resolver resolve: RCTPromiseResolveBlock,
                       rejecter reject: RCTPromiseRejectBlock) {
    guard let player = audioPlayer else {
      reject("ERR_LOOP", "Audio player not initialized", nil)
      return
    }
    player.numberOfLoops = shouldLoop ? -1 : 0
    resolve("loop set")
  }

  @objc(stopPlayback:rejecter:)
  func stopPlayback(_ resolve: @escaping RCTPromiseResolveBlock,
                    rejecter reject: @escaping RCTPromiseRejectBlock) {
    audioPlayer?.stop()
    stopPlayerTimer()
    isPlaying = false
    resolve(nil)
  }

  @objc(pausePlayingAudio:rejecter:)
  func pausePlayingAudio(_ resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let player = audioPlayer else {
      reject("ERR_PAUSE", "Audio player is not initialized", nil)
      return
    }
    if player.isPlaying {
      player.pause()
      stopPlayerTimer()
      resolve("paused")
    } else {
      resolve("alreadyPaused")
    }
  }

  @objc(resumePlayingAudio:rejecter:)
  func resumePlayingAudio(_ resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let player = audioPlayer else {
      reject("ERR_RESUME", "Audio player is not initialized", nil)
      return
    }
    if !player.isPlaying {
      player.play()
      startPlayerTimer()
      resolve("resumed")
    } else {
      resolve("alreadyPlaying")
    }
  }

  @objc(seekToPosition:resolver:rejecter:)
  func seekToPosition(_ position: NSNumber,
                      resolver resolve: @escaping RCTPromiseResolveBlock,
                      rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let player = audioPlayer else {
      reject("ERR_SEEK", "Audio player is not initialized", nil)
      return
    }
    let time = position.doubleValue
    if time >= 0 && time <= player.duration {
      player.currentTime = time
      resolve(nil)
    } else {
      reject("ERR_SEEK", "Seek time out of bounds", nil)
    }
  }

  // MARK: - Audio session configuration

  private func configureAudioSession() throws {
    let session = AVAudioSession.sharedInstance()

    try session.setCategory(.playAndRecord,
                            mode: .default,
                            options: [.allowBluetooth, .allowBluetoothA2DP])

    try session.setActive(true)

    let route = session.currentRoute

    let bluetoothConnected = route.outputs.contains(where: {
      $0.portType == .bluetoothA2DP || $0.portType == .bluetoothHFP || $0.portType == .bluetoothLE
    })

    if bluetoothConnected {
      if let builtInMic = session.availableInputs?.first(where: { $0.portType == .builtInMic }) {
        try? session.setPreferredInput(builtInMic)
      }
      try? session.overrideOutputAudioPort(.none)
      print("🎧 Bluetooth connected — playback routed to Bluetooth, recording from built-in mic")
    } else {
      let headphonesConnected = route.outputs.contains(where: {
        $0.portType == .headphones || $0.portType == .usbAudio
      })

      if headphonesConnected {
        if let builtInMic = session.availableInputs?.first(where: { $0.portType == .builtInMic }) {
          try? session.setPreferredInput(builtInMic)
        }
        try? session.overrideOutputAudioPort(.none)
        print("🎧 Wired headphones connected — playback routed normally")
      } else {
        if let builtInMic = session.availableInputs?.first(where: { $0.portType == .builtInMic }) {
          try? session.setPreferredInput(builtInMic)
        }
        try? session.overrideOutputAudioPort(.speaker)
        print("🔈 Using device speaker + mic (feedback risk)")
        DispatchQueue.main.async {
          print("⚠️ Please use headphones for better recording quality and to avoid feedback.")
        }
      }
    }
  }

  // MARK: - Setup recording and playback engines

  private func setupRecordingEngine() throws {
    let inputNode = recordEngine.inputNode
    let format = inputNode.inputFormat(forBus: 0)

    let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    micURL = docs.appendingPathComponent("micRecording.caf")
    micFile = try AVAudioFile(forWriting: micURL!, settings: format.settings)

    let mixerNode = AVAudioMixerNode()
    recordEngine.attach(mixerNode)
    recordEngine.connect(inputNode, to: mixerNode, format: format)

    mixerNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
      guard let self = self else { return }

      do {
        try self.micFile?.write(from: buffer)
      } catch {
        print("❌ Failed writing mic buffer: \(error)")
      }

      guard let channelData = buffer.floatChannelData?[0] else { return }
      let frameLength = Int(buffer.frameLength)
      let samples = Array(UnsafeBufferPointer(start: channelData, count: frameLength))

      let downsampleFactor = 8
      let downsampled = stride(from: 0, to: samples.count, by: downsampleFactor).map { samples[$0] }

      DispatchQueue.main.async {
        self.sendEvent(withName: "onWaveformChunk", body: ["samples": downsampled])
      }
    }

    recordEngine.prepare()
    try recordEngine.start()
  }

  private func setupPlaybackEngine(with musicFile: AVAudioFile) throws {
    playbackEngine.attach(playerNode)
    playbackEngine.connect(playerNode, to: playbackEngine.mainMixerNode, format: musicFile.processingFormat)

    playbackEngine.prepare()
    try playbackEngine.start()

    playerNode.scheduleFile(musicFile, at: nil)
    playerNode.play()
  }

  // MARK: - Audio trimming and mixing helpers

  private func trimAudio(inputURL: URL, outputURL: URL, duration: TimeInterval, completion: @escaping (Bool) -> Void) {
    let asset = AVAsset(url: inputURL)

    guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else {
      completion(false)
      return
    }

    if FileManager.default.fileExists(atPath: outputURL.path) {
      try? FileManager.default.removeItem(at: outputURL)
    }

    exportSession.outputURL = outputURL
    exportSession.outputFileType = .m4a

    let safeDuration = max(duration, 0.1)
    let cmDuration = CMTime(seconds: safeDuration, preferredTimescale: 600)
    exportSession.timeRange = CMTimeRange(start: .zero, duration: cmDuration)

    exportSession.exportAsynchronously {
      completion(exportSession.status == .completed)
    }
  }

  private func mixAudioFiles(micURL: URL, musicURL: URL, outputURL: URL, completion: @escaping (Bool) -> Void) {
    let composition = AVMutableComposition()
    let micAsset = AVAsset(url: micURL)
    let musicAsset = AVAsset(url: musicURL)

    guard let micTrack = micAsset.tracks(withMediaType: .audio).first,
          let musicTrack = musicAsset.tracks(withMediaType: .audio).first,
          let micCompTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid),
          let musicCompTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
      completion(false)
      return
    }

    let minDuration = CMTimeMinimum(micAsset.duration, musicAsset.duration)

    do {
      try micCompTrack.insertTimeRange(CMTimeRange(start: .zero, duration: minDuration), of: micTrack, at: .zero)
      try musicCompTrack.insertTimeRange(CMTimeRange(start: .zero, duration: minDuration), of: musicTrack, at: .zero)
    } catch {
      completion(false)
      return
    }

    if FileManager.default.fileExists(atPath: outputURL.path) {
      try? FileManager.default.removeItem(at: outputURL)
    }

    guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
      completion(false)
      return
    }

    exportSession.outputURL = outputURL
    exportSession.outputFileType = .m4a

    exportSession.exportAsynchronously {
      completion(exportSession.status == .completed)
    }
  }

  // MARK: - Timers for progress reporting

  private func startRecorderTimer() {
    guard recorderTimer == nil else { return }
    recorderTimer = DispatchSource.makeTimerSource(queue: DispatchQueue.main)
    recorderTimer?.schedule(deadline: .now(), repeating: 1.0)
    recorderTimer?.setEventHandler { [weak self] in
      self?.reportRecorderProgress()
    }
    recorderTimer?.resume()
  }

  private func stopRecorderTimer() {
    recorderTimer?.cancel()
    recorderTimer = nil
  }

  private func reportRecorderProgress() {
    guard isRecording,
          let startDate = recordingStartDate else { return }

    let currentTime = Date().timeIntervalSince(startDate)
    sendEvent(withName: "onRecordProgress", body: ["currentTime": currentTime])
  }

  private func startPlayerTimer() {
    guard playerTimer == nil else { return }
    playerTimer = DispatchSource.makeTimerSource(queue: DispatchQueue.main)
    playerTimer?.schedule(deadline: .now(), repeating: 0.2)
    playerTimer?.setEventHandler { [weak self] in
      self?.reportPlayerProgress()
    }
    playerTimer?.resume()
  }

  private func stopPlayerTimer() {
    playerTimer?.cancel()
    playerTimer = nil
  }

  private func reportPlayerProgress() {
    guard let player = audioPlayer, player.isPlaying else {
      stopPlayerTimer()
      return
    }

    sendEvent(withName: "onPlaybackProgress", body: [
      "currentTime": player.currentTime,
      "duration": player.duration
    ])
  }

  // MARK: - Audio Route Change

  @objc private func handleRouteChange(notification: Notification) {
    guard let userInfo = notification.userInfo,
          let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
          let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
      return
    }

    let session = AVAudioSession.sharedInstance()
    let input = session.currentRoute.inputs.first
    let output = session.currentRoute.outputs.first

    self.sendEvent(withName: "onAudioRouteChanged", body: [
      "input": input?.portName ?? "unknown",
      "inputType": input?.portType.rawValue ?? "unknown",
      "output": output?.portName ?? "unknown",
      "outputType": output?.portType.rawValue ?? "unknown",
      "reason": reason.rawValue
    ])
  }
}

