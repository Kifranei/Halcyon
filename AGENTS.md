# Halcyon Agent Notes

- 完成一批 issue/功能改动并编译验证通过后，**每次都打 release 包**:`./gradlew :app:assembleRelease`，产物在 `app/build/outputs/apk/release/`(universal + 各 ABI 分包）。
- 改动完成后记得更新 `CHANGELOG.md`（中英双语）与所有 `values-*` 字符串资源（共 10 个 locale:values、values-en、values-zh-rTW、values-ja、values-ko、values-de、values-fr、values-ru、values-tr、values-ar)。
- 快速编译验证：`./gradlew :app:compileReleaseKotlin`。
