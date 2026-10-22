import XCTest

@MainActor
final class GardenUITests: XCTestCase {
  func testOverviewSpaceFirstPersonAndBack() throws {
    let app = XCUIApplication(bundleIdentifier: "com.zenflow.local")
    app.activate()
    XCUIDevice.shared.orientation = .landscapeLeft
    if !app.buttons["选择空间"].exists {
      let rest = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "休息")).firstMatch
      if rest.exists { rest.tap() }
      if app.buttons["进入园境"].waitForExistence(timeout: 5) { app.buttons["进入园境"].tap() }
    }
    if app.buttons["重新打开"].exists { app.buttons["重新打开"].tap() }
    XCTAssertTrue(app.buttons["设置"].waitForExistence(timeout: 45))
    XCTAssertTrue(app.buttons["选择空间"].isEnabled)
    if app.buttons["合上"].exists { app.buttons["合上"].tap() }
    if app.buttons["返回空间"].exists { app.buttons["返回空间"].tap() }
    if app.buttons["返回全园"].exists { app.buttons["返回全园"].tap() }
    XCTAssertTrue(app.staticTexts["我的园境"].waitForExistence(timeout: 10))
    let world = XCTAttachment(screenshot: app.screenshot())
    world.name = "world-overview-landscape"; world.lifetime = .keepAlways; add(world)
    for place in ["鱼塘", "许愿树", "廊下"] {
      app.buttons["选择空间"].tap()
      XCTAssertTrue(app.buttons[place].waitForExistence(timeout: 5))
      app.buttons[place].tap()
      XCTAssertTrue(app.buttons["进入第一视角"].waitForExistence(timeout: 25))
      XCTAssertTrue(app.staticTexts["\(place) · 空间全景"].exists)
      let area = XCTAttachment(screenshot: app.screenshot())
      area.name = "space-overview-\(place)"; area.lifetime = .keepAlways; add(area)
      app.buttons["进入第一视角"].tap()
      XCTAssertTrue(app.buttons["回正视角"].waitForExistence(timeout: 20))
      XCTAssertTrue(app.staticTexts["\(place) · 第一视角"].exists)
      let first = XCTAttachment(screenshot: app.screenshot())
      first.name = "first-person-\(place)"; first.lifetime = .keepAlways; add(first)
      app.buttons["返回空间"].tap()
      XCTAssertTrue(app.buttons["进入第一视角"].waitForExistence(timeout: 10))
      app.buttons["返回全园"].tap()
      XCTAssertTrue(app.staticTexts["我的园境"].waitForExistence(timeout: 10))
    }
    app.buttons["设置"].tap()
    XCTAssertTrue(app.buttons["合上"].waitForExistence(timeout: 5))
    XCUIDevice.shared.orientation = .portrait
    XCTAssertTrue(app.buttons["合上"].exists)
    XCUIDevice.shared.orientation = .landscapeLeft
    app.buttons["合上"].tap()
    XCUIDevice.shared.press(.home)
    app.activate()
    XCTAssertTrue(app.staticTexts["我的园境"].waitForExistence(timeout: 10))
    XCTAssertFalse(app.staticTexts["园境已暂停"].exists)
  }
  func testEncryptedLocalStore() throws {
    let app = XCUIApplication(bundleIdentifier: "com.zenflow.local")
    app.activate()
    let run = app.buttons["运行加密存储验证"]
    XCTAssertTrue(run.waitForExistence(timeout: 20))
    run.tap()
    XCTAssertTrue(app.staticTexts["PASS：加密、关闭重开、账号隔离、代次拒绝、错误钥匙拒绝"].waitForExistence(timeout: 25))
    let proof = XCTAttachment(screenshot: app.screenshot())
    proof.name = "sqlcipher-native-probe"; proof.lifetime = .keepAlways; add(proof)
  }
  func testColdEncryptedDraftRestore() throws {
    let app = XCUIApplication(bundleIdentifier: "com.zenflow.local")
    // The QA driver restarts the app process and opens this route before invoking XCTest.
    // XCUIApplication.open(URL) relaunches Expo's development launcher rather than dispatching a live deep link.
    app.activate()
    let restore = app.buttons["只读验证重启恢复"]
    XCTAssertTrue(restore.waitForExistence(timeout: 25))
    restore.tap()
    XCTAssertTrue(app.staticTexts["PASS：进程重启后草稿恢复（未重新写入）"].waitForExistence(timeout: 20))
    let proof = XCTAttachment(screenshot: app.screenshot())
    proof.name = "sqlcipher-cold-restore"; proof.lifetime = .keepAlways; add(proof)
  }
  func testNativeNoteEditorDraftRecovery() throws {
    let app = XCUIApplication(bundleIdentifier: "com.zenflow.local")
    app.activate()
    XCTAssertTrue(app.buttons["关闭并重开编辑器"].waitForExistence(timeout: 15))
    app.buttons["关闭并重开编辑器"].tap()
    let writing = app.buttons["写一篇 ↗"]
    if app.buttons["恢复本机草稿"].waitForExistence(timeout: 3) {
      app.buttons["恢复本机草稿"].tap()
    } else {
      XCTAssertTrue(writing.waitForExistence(timeout: 15)); writing.tap()
    }
    let editor=app.textViews.matching(NSPredicate(format: "label BEGINSWITH %@", "私人文字")).firstMatch
    XCTAssertTrue(editor.waitForExistence(timeout: 10))
    editor.tap()
    editor.typeText(" native-draft-retained")
    app.buttons["关闭并重开编辑器"].tap()
    XCTAssertTrue(app.buttons["恢复本机草稿"].waitForExistence(timeout: 15))
    app.buttons["恢复本机草稿"].tap()
    XCTAssertTrue(editor.waitForExistence(timeout: 10))
    XCTAssertTrue((editor.value as? String)?.contains("native-draft-retained") == true)
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="native-note-draft-restored";proof.lifetime = .keepAlways;add(proof)
  }
  func testNativeMultipleDrafts() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local")
    app.activate()
    XCUIDevice.shared.orientation = .landscapeLeft
    XCTAssertTrue(app.buttons["关闭并重开编辑器"].waitForExistence(timeout:20))
    app.buttons["关闭并重开编辑器"].tap()
    let editor=app.textViews.matching(NSPredicate(format:"label BEGINSWITH %@","私人文字")).firstMatch
    let expected=["first-native-independent-page","second-native-independent-page"]
    for text in expected {
      let writing=app.buttons["写一篇 ↗"]
      XCTAssertTrue(writing.waitForExistence(timeout:15));writing.tap()
      XCTAssertTrue(editor.waitForExistence(timeout:10));editor.tap();editor.typeText(text)
      app.buttons["← 回到记录"].tap()
      XCTAssertTrue(writing.waitForExistence(timeout:15))
    }
    app.buttons["关闭并重开编辑器"].tap()
    let drafts=app.buttons.matching(NSPredicate(format:"label BEGINSWITH %@","草稿 "))
    XCTAssertTrue(drafts.firstMatch.waitForExistence(timeout:15))
    let labels=drafts.allElementsBoundByIndex.map { $0.label }
    var found=Set<String>()
    for label in labels {
      let draft=app.buttons[label]
      if !draft.isHittable { app.swipeUp() }
      draft.tap()
      XCTAssertTrue(app.buttons["恢复本机草稿"].waitForExistence(timeout:10))
      app.buttons["恢复本机草稿"].tap()
      XCTAssertTrue(editor.waitForExistence(timeout:10))
      if let value=editor.value as? String,expected.contains(value){found.insert(value)}
      app.buttons["← 回到记录"].tap()
    }
    XCTAssertEqual(found,Set(expected))
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="multiple-native-drafts-after-reopen";proof.lifetime = .keepAlways;add(proof)
  }
  func testNativeGardenJournal() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    XCUIDevice.shared.orientation = .landscapeLeft
    XCTAssertTrue(app.buttons["关闭并重开编辑器"].waitForExistence(timeout:20))
    app.buttons["关闭并重开编辑器"].tap()
    let prepare=app.buttons["保存到本机"]
    if prepare.waitForExistence(timeout:5) {
      if app.buttons["夜晚"].exists {app.buttons["夜晚"].tap()};prepare.tap()
      XCTAssertTrue(app.staticTexts["已存本机，云端连接待接通。"].waitForExistence(timeout:10))
    }
    app.buttons["关闭并重开编辑器"].tap()
    XCTAssertTrue(app.buttons["夜晚 ✓"].waitForExistence(timeout:15))
    XCTAssertTrue(app.staticTexts["园境选择已留在本机，不会自动发送。"].exists || app.staticTexts["有一份保存请求待核对，原内容已保留。"].exists)
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="garden-local-journal-restored";proof.lifetime = .keepAlways;add(proof)
  }
  func testGardenEnvironmentSaveAndReload() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    XCUIDevice.shared.orientation = .landscapeLeft
    let rest=app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","休息")).firstMatch
    if rest.exists {rest.tap()}
    if app.buttons["进入园境"].waitForExistence(timeout:8){app.buttons["进入园境"].tap()}
    XCTAssertTrue(app.buttons["设置"].waitForExistence(timeout:50))
    app.buttons["设置"].tap()
    let day=app.buttons["白昼"]
    if day.exists {day.tap()}
    app.buttons["保存到本机"].tap();app.buttons["合上"].tap()
    XCTAssertTrue(app.staticTexts["白昼光线"].waitForExistence(timeout:15))
    let daytime=XCTAttachment(screenshot:app.screenshot());daytime.name="garden-day-light";daytime.lifetime = .keepAlways;add(daytime)
    app.buttons["设置"].tap();app.buttons["夜晚"].tap();app.buttons["保存到本机"].tap()
    XCTAssertTrue(app.staticTexts["已存本机，云端连接待接通。"].waitForExistence(timeout:10))
    app.buttons["合上"].tap()
    XCTAssertTrue(app.staticTexts["夜晚光线"].waitForExistence(timeout:15))
    let night=XCTAttachment(screenshot:app.screenshot());night.name="garden-night-light";night.lifetime = .keepAlways;add(night)
    app.buttons["设置"].tap();app.buttons["重新打开园境"].tap()
    XCTAssertTrue(app.buttons["选择空间"].waitForExistence(timeout:50))
    if app.buttons["合上"].exists {app.buttons["合上"].tap()}
    XCTAssertTrue(app.staticTexts["夜晚光线"].waitForExistence(timeout:15))
  }
  func testGardenSavedEnvironmentAfterColdLaunch() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    XCUIDevice.shared.orientation = .landscapeLeft
    let enter=app.buttons["进入园境"]
    XCTAssertTrue(enter.waitForExistence(timeout:30));enter.tap()
    XCTAssertTrue(app.staticTexts["夜晚光线"].waitForExistence(timeout:60))
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="garden-night-after-cold-launch";proof.lifetime = .keepAlways;add(proof)
    app.buttons["设置"].tap()
    XCTAssertTrue(app.buttons["夜晚 ✓"].waitForExistence(timeout:10))
    XCTAssertTrue(app.staticTexts["园境选择已留在本机，不会自动发送。"].exists)
  }
  func testOpenConnectedAccountLogin() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local")
    app.activate()
    let notes=app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","记录")).firstMatch
    XCTAssertTrue(notes.waitForExistence(timeout:15));notes.tap()
    let login=app.buttons["登录"]
    XCTAssertTrue(login.waitForExistence(timeout:10));login.tap()
    let email=app.buttons["用邮箱登录"]
    XCTAssertTrue(email.waitForExistence(timeout:5));email.tap()
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="connected-login-entry";proof.lifetime = .keepAlways;add(proof)
  }
  func testConnectedDedicatedAccountSignIn() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local")
    let system=XCUIApplication(bundleIdentifier:"com.apple.springboard")
    if system.alerts.buttons["继续"].exists { system.alerts.buttons["继续"].tap() }
    let safari=XCUIApplication(bundleIdentifier:"com.apple.SafariViewService")
    let email=safari.webViews.textFields.firstMatch
    XCTAssertTrue(email.waitForExistence(timeout:20))
    let path=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("invelle-test-account.json")
    let value=try JSONSerialization.jsonObject(with:Data(contentsOf:path)) as! [String:String]
    try FileManager.default.removeItem(at:path)
    email.tap();email.typeText(value["email"]!)
    let password=safari.webViews.secureTextFields.firstMatch
    XCTAssertTrue(password.exists);password.tap();password.typeText(value["password"]!)
    safari.webViews.buttons["登录"].tap()
    XCTAssertTrue(app.buttons["我的账户"].waitForExistence(timeout:30))
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="connected-dedicated-account";proof.lifetime = .keepAlways;add(proof)
  }
  func testConnectedNoteSaveAndReopen() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    XCTAssertTrue(app.buttons["我的账户"].waitForExistence(timeout:15))
    let title="QA Single Account 20261022"
    let content="Synthetic QA note for the single account round trip."
    app.buttons["写一篇 ↗"].tap()
    let titleField=app.textFields.matching(NSPredicate(format:"label BEGINSWITH %@","标题")).firstMatch
    XCTAssertTrue(titleField.waitForExistence(timeout:10));titleField.tap();titleField.typeText(title)
    let editor=app.textViews.matching(NSPredicate(format:"label BEGINSWITH %@","私人文字")).firstMatch
    editor.tap();editor.typeText(content)
    let save=app.buttons.matching(NSPredicate(format:"label BEGINSWITH %@","保存到记录")).firstMatch
    for _ in 0..<7 { if save.isHittable { break }; app.swipeUp() }
    XCTAssertTrue(save.isHittable);save.tap()
    XCTAssertTrue(app.staticTexts["已私人保存。"].waitForExistence(timeout:25))
    let back=app.buttons["← 回到记录"]
    for _ in 0..<7 { if back.isHittable { break }; app.swipeDown() }
    back.tap()
    let item=app.buttons.matching(NSPredicate(format:"label CONTAINS %@",title)).firstMatch
    XCTAssertTrue(item.waitForExistence(timeout:15));item.tap()
    XCTAssertTrue(app.staticTexts[content].waitForExistence(timeout:10))
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="connected-cloud-note-reopened";proof.lifetime = .keepAlways;add(proof)
  }
  func testConnectedSavedNoteAfterRestart() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    XCTAssertTrue(app.buttons["我的账户"].waitForExistence(timeout:30))
    app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","记录")).firstMatch.tap()
    let note=app.buttons.matching(NSPredicate(format:"label CONTAINS %@","QA Single Account 20261022")).firstMatch
    XCTAssertTrue(note.waitForExistence(timeout:20));note.tap()
    XCTAssertTrue(app.staticTexts["Synthetic QA note for the single account round trip."].waitForExistence(timeout:10))
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="connected-account-cloud-note-after-restart";proof.lifetime = .keepAlways;add(proof)
  }
  func testConnectedChatRoundTrip() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","聊天")).firstMatch.tap()
    let message=app.textViews.matching(NSPredicate(format:"label BEGINSWITH %@","消息")).firstMatch
    XCTAssertTrue(message.waitForExistence(timeout:15));message.tap()
    let answer=app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","42")).firstMatch
    if !answer.exists {
      message.typeText("Compute 19 + 23 and answer with just the number.")
      app.buttons["发送消息"].tap()
    }
    XCTAssertTrue(answer.waitForExistence(timeout:45))
    let stopped=NSPredicate(format:"exists == false")
    expectation(for:stopped,evaluatedWith:app.buttons["停止"])
    waitForExpectations(timeout:20)
    app.buttons["先聊到这"].tap()
    expectation(for:NSPredicate(format:"hittable == true"),evaluatedWith:app.buttons["进入园境"])
    waitForExpectations(timeout:10)
    app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","聊天")).firstMatch.tap()
    XCTAssertTrue(answer.waitForExistence(timeout:10))
    let proof=XCTAttachment(screenshot:app.screenshot())
    proof.name="connected-chat-roundtrip";proof.lifetime = .keepAlways;add(proof)
  }
  func testInspectConnectedChatState() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    let tab=app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","聊天")).firstMatch
    XCTAssertTrue(tab.exists);tab.tap()
    let input=app.textViews.matching(NSPredicate(format:"label BEGINSWITH %@","消息")).firstMatch
    XCTAssertTrue(input.waitForExistence(timeout:10))
    print(app.debugDescription)
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="connected-chat-state";proof.lifetime = .keepAlways;add(proof)
  }
  func testEncryptedPrivateMedia() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    let run=app.buttons["运行原声密文验证"]
    XCTAssertTrue(run.waitForExistence(timeout:40));run.tap()
    XCTAssertTrue(app.staticTexts["PASS：原声密文落盘、重开恢复、篡改拒绝、账号隔离"].waitForExistence(timeout:25),app.debugDescription)
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="private-media-native-encryption";proof.lifetime = .keepAlways;add(proof)
  }

  func testEncryptedPrivateMediaColdRestore() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    let run=app.buttons["只读恢复原声密文"]
    XCTAssertTrue(run.waitForExistence(timeout:40));run.tap()
    XCTAssertTrue(app.staticTexts["PASS：进程重启后原声密文恢复（未重新写入）"].waitForExistence(timeout:25),app.debugDescription)
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="private-media-cold-restore";proof.lifetime = .keepAlways;add(proof)
  }

  func testConnectedPrivateAudioDraft() throws {
    let app=XCUIApplication(bundleIdentifier:"com.zenflow.local");app.activate()
    addUIInterruptionMonitor(withDescription:"Microphone permission") { alert in
      for label in ["Allow","允许","OK","好"] { if alert.buttons[label].exists { alert.buttons[label].tap();return true } }
      return false
    }
    let notes=app.descendants(matching:.any).matching(NSPredicate(format:"label == %@","记录")).firstMatch
    XCTAssertTrue(notes.waitForExistence(timeout:40));notes.tap()
    let audio=app.buttons["本机原声草稿"]
    XCTAssertTrue(audio.waitForExistence(timeout:20));audio.tap()
    let start=app.buttons["开始录音"]
    XCTAssertTrue(start.waitForExistence(timeout:10));start.tap()
    let alert=app.alerts.firstMatch
    if alert.waitForExistence(timeout:2) {
      for label in ["Allow","允许","OK","好"] { if alert.buttons[label].exists { alert.buttons[label].tap();break } }
    }
    let stop=app.buttons["停止并存本机"]
    XCTAssertTrue(stop.waitForExistence(timeout:15),app.debugDescription)
    let elapsed=expectation(description:"Capture short simulator recording")
    DispatchQueue.main.asyncAfter(deadline:.now()+2){elapsed.fulfill()};wait(for:[elapsed],timeout:4)
    stop.tap()
    XCTAssertTrue(app.staticTexts["原声已加密保存到本机。"].waitForExistence(timeout:15),app.debugDescription)
    let listen=app.buttons["试听原声草稿 1"]
    XCTAssertTrue(listen.exists);listen.tap()
    XCTAssertTrue(app.buttons["停止试听"].waitForExistence(timeout:10),app.debugDescription)
    app.buttons["停止试听"].tap()
    let proof=XCTAttachment(screenshot:app.screenshot());proof.name="connected-private-audio-draft";proof.lifetime = .keepAlways;add(proof)
    app.buttons["返回记事"].tap();audio.tap()
    XCTAssertTrue(app.buttons["试听原声草稿 1"].waitForExistence(timeout:10))
  }

}
