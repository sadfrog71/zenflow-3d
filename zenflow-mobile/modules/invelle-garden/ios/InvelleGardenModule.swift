import ExpoModulesCore
import WebKit

public final class InvelleGardenModule: Module {
  public func definition() -> ModuleDefinition {
    Name("InvelleGarden")
    AsyncFunction("privateStoreDirectory") { (digest: String) -> String in
      guard digest.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil else {
        throw NSError(domain: "InvellePrivateStore", code: 400)
      }
      let fm = FileManager.default
      let support = try fm.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
      let owners = support.appendingPathComponent("invelle-owners", isDirectory: true)
      let directory = owners.appendingPathComponent(digest, isDirectory: true)
      for var url in [owners, directory] {
        try fm.createDirectory(at: url, withIntermediateDirectories: true,
          attributes: [.protectionKey: FileProtectionType.complete])
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        try fm.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: url.path)
      }
      // Apply protection to crash leftovers before SQLCipher opens them.
      for file in try fm.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
        if file.lastPathComponent.range(of: "^playback-[a-f0-9-]{36}\\.m4a$", options: .regularExpression) != nil {
          try fm.removeItem(at: file); continue
        }
        try fm.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: file.path)
      }
      return directory.absoluteString
    }
    AsyncFunction("preparePrivateAudioDirectory") { () -> Void in
      let fm = FileManager.default
      let cache = try fm.url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
      var directory = cache.appendingPathComponent("ExpoAudio", isDirectory: true)
      try fm.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
      try fm.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: directory.path)
      var values = URLResourceValues(); values.isExcludedFromBackup = true
      try directory.setResourceValues(values)
      for file in try fm.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
        if file.lastPathComponent.range(of: "^recording-[A-Fa-f0-9-]{36}\\.m4a$", options: .regularExpression) != nil {
          try fm.removeItem(at: file)
        }
      }
    }
    View(InvelleGardenView.self) {
      Events("onMessage")
      Prop("source") { (view: InvelleGardenView, source: [String: String]) in view.load(source) }
      Prop("command") { (view: InvelleGardenView, command: String) in view.send(command) }
    }
  }
}

// Only immutable public asset files from our cache are reachable; no private DB/media roots.
final class PublicGardenFiles: NSObject, WKURLSchemeHandler {
  var directory: URL?
  func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
    guard let root = directory, let url = urlSchemeTask.request.url,
      url.scheme == "invelle-garden", url.host == "bundle",
      url.query == nil,
      url.path.range(of: "^/[a-zA-Z0-9][a-zA-Z0-9._-]*$", options: .regularExpression) != nil else {
      urlSchemeTask.didFailWithError(NSError(domain: "InvelleGarden", code: 403)); return
    }
    let file = root.appendingPathComponent(url.lastPathComponent).resolvingSymlinksInPath()
    guard file.deletingLastPathComponent() == root else {
      urlSchemeTask.didFailWithError(NSError(domain: "InvelleGarden", code: 403)); return
    }
    let types = ["html":"text/html", "gltf":"model/gltf+json", "glb":"model/gltf-binary", "bin":"application/octet-stream", "webp":"image/webp", "png":"image/png", "jpg":"image/jpeg", "json":"application/json"]
    guard let mime = types[file.pathExtension], let data = try? Data(contentsOf: file) else {
      urlSchemeTask.didFailWithError(NSError(domain: "InvelleGarden", code: 404)); return
    }
    urlSchemeTask.didReceive(URLResponse(url: url, mimeType: mime, expectedContentLength: data.count, textEncodingName: mime.hasPrefix("text/") ? "utf-8" : nil))
    urlSchemeTask.didReceive(data)
    urlSchemeTask.didFinish()
  }
  func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}
}

final class GardenMessageProxy: NSObject, WKScriptMessageHandler {
  weak var owner: InvelleGardenView?
  func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
    guard message.frameInfo.isMainFrame, let body = message.body as? String,
      body.utf8.count <= 16384 else { return }
    owner?.onMessage(["data": body])
  }
}

final class InvelleGardenView: ExpoView, WKNavigationDelegate {
  let onMessage = EventDispatcher()
  private let files = PublicGardenFiles()
  private let proxy = GardenMessageProxy()
  private var web: WKWebView!
  private var loadedContext: String?
  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    let config = WKWebViewConfiguration()
    config.websiteDataStore = .nonPersistent()
    config.setURLSchemeHandler(files, forURLScheme: "invelle-garden")
    config.userContentController.add(proxy, name: "garden")
    config.userContentController.addUserScript(WKUserScript(source: "window.ReactNativeWebView={postMessage:function(s){window.webkit.messageHandlers.garden.postMessage(s)}}", injectionTime: .atDocumentStart, forMainFrameOnly: true))
    web = WKWebView(frame: .zero, configuration: config)
    web.isInspectable = true
    web.navigationDelegate = self
    web.scrollView.isScrollEnabled = false
    web.isOpaque = false
    proxy.owner = self
    addSubview(web)
  }
  override func layoutSubviews() { super.layoutSubviews(); web.frame = bounds }
  func load(_ source: [String: String]) {
    guard let raw = source["directory"], let context = source["context"],
      context != loadedContext, context.utf8.count < 2048,
      let directory = URL(string: raw), directory.isFileURL else { return }
    let root = directory.resolvingSymlinksInPath().standardizedFileURL
    let cache = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("invelle-public-garden", isDirectory: true).resolvingSymlinksInPath().standardizedFileURL
    guard root.path.hasPrefix(cache.path + "/") else { return }
    files.directory = root
    loadedContext = context
    var parts = URLComponents(string: "invelle-garden://bundle/index.html")!
    parts.fragment = context
    web.load(URLRequest(url: parts.url!))
  }
  func send(_ command: String) {
    guard command.utf8.count <= 16384, let bytes = command.data(using: .utf8),
      (try? JSONSerialization.jsonObject(with: bytes)) is [String: Any] else { return }
    // Argument is JSON data, never an arbitrary script from the scene.
    web.evaluateJavaScript("window.invelleReceive?.(\(command));void 0", completionHandler: nil)
  }
  func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
    let url = navigationAction.request.url
    decisionHandler(url?.scheme == "invelle-garden" && url?.host == "bundle" ? .allow : .cancel)
  }
  func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
    onMessage(["data":"{\"type\":\"host.terminated\"}"])
  }
}
