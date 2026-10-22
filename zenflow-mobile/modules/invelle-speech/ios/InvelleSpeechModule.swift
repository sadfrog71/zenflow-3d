import ExpoModulesCore
import Speech
import AVFoundation

public final class InvelleSpeechModule: Module {
  private static let voiceShortcutTokenKey = "com.invelle.siri.voice-note.token"
  private static let voiceShortcutRequestedAtKey = "com.invelle.siri.voice-note.requested-at"
  private static let voiceShortcutNotification = Notification.Name("InvelleSiriVoiceNoteRequested")

  private var engine: AVAudioEngine?
  private var task: SFSpeechRecognitionTask?
  private var request: SFSpeechAudioBufferRecognitionRequest?
  private var session: String?
  private var transcript = ""
  private var tapped = false
  private var deadline: DispatchWorkItem?
  private var voiceShortcutObserver: NSObjectProtocol?

  public func definition() -> ModuleDefinition {
    Name("InvelleSpeech")
    Events("onSpeech", "onVoiceShortcut")
    OnCreate {
      self.voiceShortcutObserver = NotificationCenter.default.addObserver(
        forName: Self.voiceShortcutNotification,
        object: nil,
        queue: .main
      ) { [weak self] _ in
        self?.sendEvent("onVoiceShortcut", [:])
      }
    }
    AsyncFunction("takePendingVoiceShortcutRequest") { () -> String? in
      self.takePendingVoiceShortcutRequest()
    }.runOnQueue(.main)
    AsyncFunction("start") { (locale: String, id: String, promise: Promise) in
      self.cancel()
      self.session = id
      SFSpeechRecognizer.requestAuthorization { status in
        DispatchQueue.main.async {
          guard self.session == id else { promise.reject("CANCELLED", "Cancelled"); return }
          guard status == .authorized else { self.cancel(); promise.reject("SPEECH_PERMISSION", "Speech access is required"); return }
          AVAudioSession.sharedInstance().requestRecordPermission { allowed in
            DispatchQueue.main.async {
              guard self.session == id else { promise.reject("CANCELLED", "Cancelled"); return }
              guard allowed else { self.cancel(); promise.reject("MIC_PERMISSION", "Microphone access is required"); return }
              do { try self.begin(locale, id); promise.resolve(nil) }
              catch { self.cancel(); promise.reject("SPEECH_UNAVAILABLE", error.localizedDescription) }
            }
          }
        }
      }
    }.runOnQueue(.main)
    AsyncFunction("stop") { self.finishInput() }.runOnQueue(.main)
    AsyncFunction("cancel") { self.cancel() }.runOnQueue(.main)
    OnDestroy {
      if let observer = self.voiceShortcutObserver {
        NotificationCenter.default.removeObserver(observer)
        self.voiceShortcutObserver = nil
      }
      DispatchQueue.main.async { self.cancel() }
    }
  }

  private func takePendingVoiceShortcutRequest() -> String? {
    let defaults = UserDefaults.standard
    guard let token = defaults.string(forKey: Self.voiceShortcutTokenKey) else { return nil }
    guard let requestedAt = defaults.object(forKey: Self.voiceShortcutRequestedAtKey) as? Date else {
      defaults.removeObject(forKey: Self.voiceShortcutTokenKey)
      defaults.removeObject(forKey: Self.voiceShortcutRequestedAtKey)
      return nil
    }
    let age = Date().timeIntervalSince(requestedAt)
    guard age >= 0, age <= 60 else {
      defaults.removeObject(forKey: Self.voiceShortcutTokenKey)
      defaults.removeObject(forKey: Self.voiceShortcutRequestedAtKey)
      return nil
    }
    defaults.removeObject(forKey: Self.voiceShortcutTokenKey)
    defaults.removeObject(forKey: Self.voiceShortcutRequestedAtKey)
    return token
  }

  private func emit(_ state: String, _ id: String, _ code: String = "") {
    sendEvent("onSpeech", ["sessionId": id, "state": state, "text": transcript, "code": code])
  }
  private func begin(_ locale: String, _ id: String) throws {
    guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: locale)),
          recognizer.isAvailable, recognizer.supportsOnDeviceRecognition else {
      throw NSError(domain: "InvelleSpeech", code: 1, userInfo: [NSLocalizedDescriptionKey: "On-device speech recognition is unavailable for this language"])
    }
    let audio = AVAudioSession.sharedInstance()
    try audio.setCategory(.record, mode: .measurement, options: [.duckOthers])
    try audio.setActive(true, options: .notifyOthersOnDeactivation)
    let engine = AVAudioEngine()
    self.engine = engine
    let request = SFSpeechAudioBufferRecognitionRequest()
    request.shouldReportPartialResults = true
    request.requiresOnDeviceRecognition = true
    self.request = request
    transcript = ""
    let node = engine.inputNode
    let format = node.outputFormat(forBus: 0)
    guard format.sampleRate > 0, format.channelCount > 0 else {
      throw NSError(domain: "InvelleSpeech", code: 2)
    }
    node.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in request.append(buffer) }
    tapped = true
    task = recognizer.recognitionTask(with: request) { result, error in
      DispatchQueue.main.async {
        guard self.session == id else { return }
        if let result {
          self.transcript = result.bestTranscription.formattedString
          self.emit(result.isFinal ? "complete" : "listening", id)
          if result.isFinal { self.cancel(); return }
        }
        if error != nil {
          self.emit(self.transcript.isEmpty ? "error" : "complete", id, "RECOGNITION_ENDED")
          self.cancel()
        }
      }
    }
    engine.prepare()
    try engine.start()
    emit("listening", id)
    let limit = DispatchWorkItem { [weak self] in self?.finishInput() }
    deadline = limit
    DispatchQueue.main.asyncAfter(deadline: .now() + 55, execute: limit)
  }
  private func stopAudio() {
    engine?.stop()
    if tapped { engine?.inputNode.removeTap(onBus: 0); tapped = false }
    request?.endAudio()
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
  }
  private func finishInput() {
    guard let id = session else { return }
    deadline?.cancel()
    stopAudio()
    emit("finishing", id)
    let limit = DispatchWorkItem { [weak self] in
      guard let self, self.session == id else { return }
      self.emit("complete", id)
      self.cancel()
    }
    deadline = limit
    DispatchQueue.main.asyncAfter(deadline: .now() + 3, execute: limit)
  }
  private func cancel() {
    session = nil
    deadline?.cancel(); deadline = nil
    stopAudio()
    task?.cancel(); task = nil
    request = nil; engine = nil
  }
}
