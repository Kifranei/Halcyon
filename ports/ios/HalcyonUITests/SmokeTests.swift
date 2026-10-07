import XCTest

final class SmokeTests: XCTestCase {
    private func application(fixtures: Bool = true) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["HALCYON_UI_PROFILE"] = UUID().uuidString
        if fixtures { app.launchEnvironment["HALCYON_UI_SMOKE"] = "1" }
        return app
    }
    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<6 { if element.exists && element.isHittable { return }; app.swipeUp() }
        XCTAssertTrue(element.exists && element.isHittable, "Control must remain reachable on this device")
    }
    private func replace(_ field: XCUIElement, with text: String) {
        let old = field.value as? String ?? ""
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count + 2) + text)
    }
    private func dismissKeyboard(in app: XCUIApplication, at heading: XCUIElement) {
        guard app.keyboards.firstMatch.exists else { return }
        XCTAssertTrue(heading.exists && heading.isHittable)
        heading.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.keyboards.firstMatch)
        waitForExpectations(timeout: 5)
    }
    func testFavoritesSearchPlaylistRenameAndDeletion() throws {
        continueAfterFailure = false
        let app = application(); app.launch()
        XCTAssertTrue(app.buttons["播放 Native First"].firstMatch.waitForExistence(timeout: 30))
        let search = app.textFields["搜索歌曲、艺术家、专辑"].firstMatch
        search.tap(); search.typeText("Native Second")
        XCTAssertTrue(app.buttons["播放 Native Second"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["播放 Native First"].firstMatch.exists)
        replace(search, with: "")
        XCTAssertTrue(app.buttons["播放 Native First"].firstMatch.waitForExistence(timeout: 5))
        dismissKeyboard(in: app, at: app.staticTexts["音乐库"].firstMatch)
        // WebKit exposes aria-pressed controls as toggles, rather than buttons.
        let favorite = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "收藏 Native First")).firstMatch
        reveal(favorite, in: app); favorite.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "取消收藏 Native First")).firstMatch.waitForExistence(timeout: 5))
        app.buttons["收藏"].firstMatch.tap()
        XCTAssertTrue(app.buttons["播放 Native First"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["歌单"].firstMatch.tap(); app.buttons["新建歌单"].firstMatch.tap()
        let name = app.textFields["歌单名称"].firstMatch
        name.tap(); name.typeText("Functional Playlist")
        dismissKeyboard(in: app, at: app.staticTexts["新建歌单"].firstMatch)
        app.buttons["创建"].firstMatch.tap()
        let playlist = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Functional Playlist")).firstMatch
        XCTAssertTrue(playlist.waitForExistence(timeout: 5)); playlist.tap()
        app.buttons["重命名"].firstMatch.tap()
        let rename = app.textFields["歌单名称"].firstMatch
        replace(rename, with: "Renamed Playlist")
        dismissKeyboard(in: app, at: app.staticTexts["重命名歌单"].firstMatch)
        app.buttons["保存名称"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Renamed Playlist"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["删除歌单"].firstMatch.tap()
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Renamed Playlist")).firstMatch.exists)
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["导入音乐"].firstMatch.waitForExistence(timeout: 30))
        app.buttons["收藏"].firstMatch.tap()
        XCTAssertTrue(app.buttons["播放 Native First"].firstMatch.waitForExistence(timeout: 5))
    }
    func testLyricEditorUndoRedoAndMetadataPersistence() throws {
        continueAfterFailure = false
        let app = application(); app.launch()
        XCTAssertTrue(app.buttons["Native First 更多操作"].firstMatch.waitForExistence(timeout: 30))
        app.buttons["播放 Native First"].firstMatch.tap()
        app.buttons["打开播放页"].firstMatch.tap()
        let progress = app.sliders["播放页进度"].firstMatch
        XCTAssertTrue(progress.waitForExistence(timeout: 5))
        let initial = progress.value as? String ?? ""
        let advances = NSPredicate { _, _ in (progress.value as? String ?? "") != initial }
        expectation(for: advances, evaluatedWith: progress); waitForExpectations(timeout: 8)
        app.buttons["播放页暂停"].firstMatch.tap(); app.buttons["关闭"].firstMatch.tap()
        app.buttons["Native First 更多操作"].firstMatch.tap()
        let timing = app.buttons["歌词打轴器"].firstMatch; reveal(timing, in: app); timing.tap()
        let lyrics = app.textViews["歌词文本"].firstMatch
        XCTAssertTrue(lyrics.waitForExistence(timeout: 5)); lyrics.tap(); lyrics.typeText("Fixture lyric one\nFixture lyric two")
        dismissKeyboard(in: app, at: app.staticTexts["歌词打轴器"].firstMatch)
        app.buttons["应用文本"].firstMatch.tap()
        let firstTime = app.textFields["第 1 行时间"].firstMatch
        let stamp = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "标记第 1 行")).firstMatch
        reveal(stamp, in: app); stamp.tap()
        let stampedTime = NSPredicate { _, _ in (Double(firstTime.value as? String ?? "") ?? 0) > 0 }
        expectation(for: stampedTime, evaluatedWith: firstTime); waitForExpectations(timeout: 5)
        let stamped = firstTime.value as? String ?? ""
        XCTAssertGreaterThan(Double(stamped) ?? 0, 0)
        app.buttons["撤销"].firstMatch.tap(); XCTAssertEqual(firstTime.value as? String, "0")
        app.buttons["重做"].firstMatch.tap(); XCTAssertEqual(firstTime.value as? String, stamped)
        let save = app.buttons["保存同步歌词"].firstMatch; reveal(save, in: app); save.tap()
        app.buttons["Native First 更多操作"].firstMatch.tap()
        let edit = app.buttons["编辑显示信息"].firstMatch; reveal(edit, in: app); edit.tap()
        let title = app.textFields["歌曲名"].firstMatch
        replace(title, with: "Edited First")
        dismissKeyboard(in: app, at: app.staticTexts["Native First"].firstMatch)
        app.buttons["保存"].firstMatch.tap()
        XCTAssertTrue(app.buttons["播放 Edited First"].firstMatch.waitForExistence(timeout: 5))
        app.terminate(); app.launchEnvironment.removeValue(forKey: "HALCYON_UI_SMOKE"); app.launch()
        XCTAssertTrue(app.buttons["播放 Edited First"].firstMatch.waitForExistence(timeout: 30))
        // Restore the title so subsequent native playback tests use the same fixture.
        app.buttons["Edited First 更多操作"].firstMatch.tap()
        let restore = app.buttons["编辑显示信息"].firstMatch; reveal(restore, in: app); restore.tap()
        let restoredTitle = app.textFields["歌曲名"].firstMatch
        replace(restoredTitle, with: "Native First")
        dismissKeyboard(in: app, at: app.staticTexts["Edited First"].firstMatch)
        app.buttons["保存"].firstMatch.tap()
    }
    func testLibraryAndPlaylistThroughNativeWebBridge() throws {
        continueAfterFailure = false
        let app = application(fixtures: false)
        app.launch()
        let library = app.buttons["导入音乐"].firstMatch
        XCTAssertTrue(library.waitForExistence(timeout: 25), "Bundled WebKit interface must render")
        XCTAssertTrue(library.isEnabled, "Native library/state bridge must finish loading")
        app.buttons["歌单"].firstMatch.tap()
        let create = app.buttons["新建歌单"].firstMatch
        XCTAssertTrue(create.waitForExistence(timeout: 5))
        create.tap()
        let name = app.textFields["歌单名称"].firstMatch
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("Simulator Playlist")
        dismissKeyboard(in: app, at: app.staticTexts["新建歌单"].firstMatch)
        app.buttons["创建"].firstMatch.tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Simulator Playlist")).firstMatch.waitForExistence(timeout: 5))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "iOS shared interface and saved playlist"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["导入音乐"].firstMatch.waitForExistence(timeout: 25))
        app.buttons["歌单"].firstMatch.tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Simulator Playlist")).firstMatch.waitForExistence(timeout: 5), "Playlist must persist through native state storage")
    }
    func testNativeAudioEffectsAndPausedQueueEditing() throws {
        continueAfterFailure = false
        let app = application(); app.launch()
        XCTAssertTrue(app.buttons["播放 Native First"].firstMatch.waitForExistence(timeout: 30))
        app.buttons["设置"].firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        XCTAssertTrue(app.staticTexts["设置"].firstMatch.waitForExistence(timeout: 5))
        let preset = app.buttons["人声"].firstMatch
        reveal(preset, in: app); preset.tap()
        app.buttons["音乐库"].firstMatch.tap()
        app.buttons["播放 Native First"].firstMatch.tap()
        app.buttons["打开播放页"].firstMatch.tap()
        let pause = app.buttons["播放页暂停"].firstMatch
        XCTAssertTrue(pause.waitForExistence(timeout: 10), "Native engine must start real PCM playback with EQ enabled")
        let progress = app.sliders["播放页进度"].firstMatch
        XCTAssertTrue(progress.waitForExistence(timeout: 5))
        let initial = progress.value as? String ?? ""
        let advances = NSPredicate { _, _ in (progress.value as? String ?? "") != initial }
        expectation(for: advances, evaluatedWith: progress); waitForExpectations(timeout: 8)
        pause.tap()
        app.buttons["播放页下一首"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["正在播放 Native Second"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["播放页播放"].firstMatch.exists, "Skipping while paused must keep playback paused")
        app.swipeUp(); app.swipeUp()
        app.buttons["队列上移 2"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["正在播放 Native Second"].firstMatch.exists, "Queue reordering preserves the current track")
        app.buttons["移除队列 2"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["正在播放 Native Second"].firstMatch.exists)
        app.swipeDown(); app.swipeDown()
        app.buttons["播放页播放"].firstMatch.tap()
        XCTAssertTrue(app.buttons["播放页暂停"].firstMatch.waitForExistence(timeout: 5))
        let screenshot = XCTAttachment(screenshot: app.screenshot()); screenshot.name = "Native EQ playback and edited queue"; screenshot.lifetime = .keepAlways; add(screenshot)
    }
}
