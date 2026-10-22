import AppIntents
import Foundation

private enum InvelleSiriVoiceNoteBridge {
  static let tokenKey = "com.invelle.siri.voice-note.token"
  static let requestedAtKey = "com.invelle.siri.voice-note.requested-at"
  static let notification = Notification.Name("InvelleSiriVoiceNoteRequested")
}

struct InvelleStartVoiceNoteIntent: AppIntent {
  static let title: LocalizedStringResource = "Start a voice note"
  static let description = IntentDescription("Open Invelle and begin a private voice note.")

  static var openAppWhenRun: Bool { true }

  @available(iOS 26.0, *)
  static var supportedModes: IntentModes { .foreground }

  @MainActor
  func perform() async throws -> some IntentResult {
    UserDefaults.standard.set(UUID().uuidString, forKey: InvelleSiriVoiceNoteBridge.tokenKey)
    UserDefaults.standard.set(Date(), forKey: InvelleSiriVoiceNoteBridge.requestedAtKey)
    NotificationCenter.default.post(name: InvelleSiriVoiceNoteBridge.notification, object: nil)
    return .result()
  }
}

struct InvelleAppShortcuts: AppShortcutsProvider {
  static var appShortcuts: [AppShortcut] {
    AppShortcut(
      intent: InvelleStartVoiceNoteIntent(),
      phrases: [
        "Start a voice note with \(.applicationName)",
        "Open voice notes in \(.applicationName)",
      ],
      shortTitle: "Voice note",
      systemImageName: "mic"
    )
  }
}
