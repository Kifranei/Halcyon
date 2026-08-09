<!--suppress ALL -->

<h1 align="center">Halcyon</h1>

<p align="center">
  <b>An Android Music Player Inspired by MIUI / HyperOS</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Halcyon/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Halcyon?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Halcyon/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Halcyon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Halcyon/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Halcyon?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Halcyon/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Halcyon?style=flat" alt="License"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/Document-Chinese-red.svg" alt="CN"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/6MHSXRrjTq"><img src="https://img.shields.io/badge/QQ交流群-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"></a>
  <!-- <a href="https://t.me/halcyonplayer"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a> -->
</p>

<p align="center">
  <b>Local Music · Online Libraries · Dynamic Player UI · Word-by-Word Lyrics · Floating Lyrics · Status Bar Lyrics · Multilingual UI</b>
</p>

---

## ✨ Overview

**Halcyon** is an Android local music player built with **Jetpack Compose, Miuix, and AndroidX Media3**.

It focuses on local music and lyrics, with a MIUI / HyperOS-inspired interface, Compose word-by-word lyrics, floating lyrics, status-bar lyrics, dynamic covers, an in-app equalizer, Monet dynamic color, online lyric matching, WebDAV / Navidrome / Emby remote libraries, LX Music API sources, Last.fm listening history, library analytics, full app-data backup, and a highly customizable player experience.

---

## 🚀 Features

### 🎵 Library & Playlists

- Supports local MediaStore scanning and custom folder scanning, with browsing by album, artist, folder, genre, year, composer, and lyricist; long-press the scan button to trigger a deep full-tag rescan.
- Before the first scan, choose whether to enable full-tag search. It searches composer, lyricist, comments, aliases, and custom tags, while the faster basic MediaStore scan searches title, artist, album, and other core metadata only.
- The library source can switch between Local, Navidrome, and Emby, and the visible library refreshes to the selected source instead of keeping songs from the previous source.
- Provides a dedicated library search page with song, album, artist, lyric, duplicate-song, and full-tag search, plus search history, multi-select, and range selection.
- Supports local playlists, favorites, five-star songs, playlist import / export, desktop shortcuts, and custom drag sorting.
- Album grouping uses both album name and album artist to avoid merging same-name albums from different artists.
- Album details include a dedicated, editable introduction page. Local albums prefer the `<review>` field in a neighboring `album.nfo`; when the folder is not writable, the introduction is stored inside the app.
- Includes library analytics, listening calendar, play-count ranking, listening-duration ranking, format distribution, and quality distribution.
- Supports Last.fm authorization, full-history sync, automatic scrobbling, and Local / Last.fm / combined listening-history views. Records can be deleted individually, while cached Last.fm entries can be hidden locally. Sensitive credentials are encrypted with Android Keystore and excluded from backups.
- Library analytics are cached and prewarmed after scanning, so larger local libraries can open the statistics page faster.

### 🖼 Player UI & Dynamic Covers

- Provides selectable landscape playback styles plus independent status/navigation-bar visibility and optional reserved space for hidden bars, covering phones, tablets, car displays, and ultra-wide screens.
- New installs default to the non-immersive rounded-cover player; non-square artwork is rounded at its actual image bounds.
- Supports dynamic video covers matched by song, album, or global fallback.
- Supports a custom artist-cover folder and dynamic artist video covers on artist pages.
- Static artist artwork is selected in this order: custom asset, sole/collaborative album artist, then sole/collaborative song artist.
- Supports global custom wallpapers, launch posters, custom Hi-Res badges, and optional player button outlines.
- Supports Beautiful Lyrics-style dynamic backgrounds for the lyrics page, tablet landscape player, and landscape cover page, with speed, blur, and brightness controls.
- The Compose lyrics page supports an Apple Music-style dynamic background, word-lift animation, smooth relayout, and consistent transitions between immersive lyrics and the player.
- Supports Monet dynamic color derived from the system wallpaper or the current song cover.
- Non-immersive player covers can show a Hi-Res / MQ badge.
- The player supports pull-down dismissal, dynamic backgrounds, blurred cover backgrounds, cover swipe-to-skip, and landscape queue-cover switching; tablet landscape docks can show the current lyric.
- Supports both local and NetEase Cloud Music MVs. Local videos may use MP4, MKV, WebM, or MOV, can play silently in sync on the player, and can play independently with audio from the detail page. When a `163 key` contains `mvid`, the detail page adds a clearly labeled NetEase MV link; both entries can be shown together.
- Local MVs opened from song details start directly in landscape and support manual/automatic background picture-in-picture, a glowing progress bar, screenshot sharing, a translucent scrollable caption-settings panel, draggable/lockable captions, KTV lyrics, accompaniment testing, and LunaBeat `mv_offsets.json` subtitle offsets.
- New installs enable Apple Music background motion, transport-button outlines, total-duration display, and silent synchronized MV playback by default. Existing saved choices are preserved on upgrade.
- Long-press player artwork to preview the original cover, with double-tap zoom, one-finger panning, sharing, and saving.

### 🎤 Lyrics

- Supports LRC, Enhanced LRC, ELRC, TTML, AMLL TTML, and Lyricify lyric parsing.
- Supports word-by-word lyrics, line-timed TTML, translations, romanization / phonetics, background vocals, TTML duets, and ELRC V1/V2 duet tags.
- The lyric-page more menu now keeps font size, scaling, and perspective sliders inside a secondary "Lyric style" page to reduce the length of the top-level menu.
- Reads embedded lyrics and external lyric files, including matching `.lrc`, `.ttml`, and `.elrc` files.
- Supports online lyric matching for local songs via Lyrico-compatible plugins: import / delete plugin bundles from zip files, configure plugin fields, and write results to embedded tags, `TTMLLYRIC`, or a `.lrc` file.
- Provides floating desktop lyrics, status-bar lyrics, media notification lyrics, lyric barrage, SuperLyricApi, and Lyric Getter API integration.
- Long status-bar lyrics loop continuously with a gap instead of visibly jumping back to the start.
- Lyricist/composer credit lines, including short `词` / `曲` forms, can be hidden on demand; MV caption translations have a separate visibility switch.
- Supports lyric card sharing, font import with a system-font picker, lyric offset, tap-to-seek, and secondary-line configuration.
- Includes a dedicated full-screen lyric timing editor with line/word timing, fixed transport controls, undo/redo, V1/V2/V1000 vocal roles, translation, romanization, `x-bg` backing vocals, and embedded or exported LRC, ELRC, and TTML.

### 🌐 WebDAV, Navidrome, Emby & LX Online Music

- Supports WebDAV remote libraries with connection testing, Digest authentication, remote browsing, and remote playback.
- Supports Navidrome / Subsonic and Emby music library entries with the same directory-browsing style and input-field styling as WebDAV.
- Navidrome / Emby remote libraries support paged loading and full-library caching; Navidrome libraries over 500 songs continue loading additional pages.
- Supports LX Music API sources, online search, streaming playback, cover / lyric retrieval, and local downloads.
- Includes a Beta LAN Web music service for browsing, playback, and upload from a browser on a trusted local network. This release has no access password; do not expose it to public networks or port forwarding.

### 🎚 Audio Effects, Decoding, Tags & Quality

- Includes an in-app software 10-band parametric equalizer that does not depend on the system Equalizer, with bass boost and virtualizer shown based on device capability.
- Supports the native Oboe output backend and USB DAC exclusive mode. Crossfade offers equal-power, linear, smooth, and full-volume curves.
- Uses lyrico-audiotag as the primary local metadata path, supporting artwork, basic tags, embedded lyrics, and multi-value tags for common audio formats.
- The built-in tag editor supports editing basic tags, lyrics, embedded artwork, and interactive star ratings.
- Provides system, FFmpeg, and automatic decoding modes for better ALAC / AAC / M4A compatibility.
- Supports ReplayGain, shuffle queue restoration, quality labels, and 24-bit / 96 kHz recognition.
- Reads 163 keys from standalone tags, Comment, and Description fields, extracting song, album, artist, and `mvid` data with links to the corresponding NetEase song, album, artist, and MV pages.
- Provides local audio tools for format conversion, multi-stream audio export, and CUE album splitting; the built-in spectrum viewer can also launch Aspect Pro or Kaspek directly.

### 🎨 UI & Integrations

- Built with Miuix 0.9.3 for a MIUI / HyperOS-inspired interface, including floating bottom navigation, MiniPlayer, blur / Liquid Glass effects, and unified sheets. The launch screen follows the dark system theme to avoid a bright flash under system launch masks.
- Supports 8 interface languages, in-app language switching, app font sizing and full interface scaling, GitHub update page, app logs, full app-data backup / restore, and Prism Music listening-history export.
- Supports switching app icons, configuring long-press launcher shortcuts, pinning home-category shortcuts, and compact / expanded playback widgets. Widgets keep artwork across process restarts, use a blurred artwork-derived background, show live playback time and controls, and provide a compatibility-layout switch for launcher grids that crop the play-button outline.
- Supports song information, tag editing, lyric timing tools, external tag-editor adaptation, and AI song interpretation.
- Supports MediaSession custom commands for favorite and playback-mode controls in notifications / control centers.

### 🤖 AI & MCP

- Includes an OpenAI library listening assistant that can make recommendations from the local library, recent plays, and listening statistics, or explain listening preferences. It can only read the library and play local songs; it cannot delete or modify files.
- Includes an MCP server built with the official Kotlin SDK, Ktor CIO, and Streamable HTTP, allowing MCP hosts such as Claude Desktop to control Halcyon playback.
- Enable it from Settings → MCP server, then connect to `http://<device-ip>:8384/mcp`.
- Available tools: `play_song`, `search_music`, `get_now_playing`, `skip_next`, `skip_previous`, `toggle_play_pause`, `toggle_shuffle`, `seek_to`, `get_queue`, and `get_library_stats`.
- Available read-only resources: `halcyon://playback/current` and `halcyon://library/stats`.
- The MCP server runs as an Android Foreground Service and stops when the setting is turned off.

---

## 📱 Requirements

| Item | Requirement                                                             |
|:--|:------------------------------------------------------------------------|
| Android Version | Android 11 / API 30 or higher                                           |
| Target SDK | Android 17 / API 37                                                     |
| Default ABI | `arm64-v8a`                                                             |
| Network | Required for WebDAV, LX online sources, and online lyrics               |
| Video Permission | Android 13+ may require video media permission for dynamic video covers |
| Overlay Permission | Required when using floating lyrics                                     |
| Notification Permission | Required on Android 13 and above                                        |

---

## 📦 Download

Download the latest version from [Releases](https://github.com/Kifranei/Halcyon/releases).

Recommended first-time setup:

1. Install Halcyon.
2. Grant music file access permission and choose a scanning mode (media library scanning or custom folder scanning).
3. After scanning completes, the app is ready to use. To display lyrics on other pages, enable the option in the settings page.
4. Configure WebDAV manually if using a remote library.
---

## 🖼 Dynamic Video Covers

Dynamic video covers are used in the playback page cover area. Album-level configuration is recommended:

```text
Music/
├── Album Name.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

or
```text
Music/Album Name Folder/
├── cover.mp4
├── Song A.flac
├── Song B.flac
└── Song C.flac
```

All songs in the same album can share the same video, avoiding duplicate video files for each song.

Centralized management is supported:

```text
Movies/Halcyon/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

Single-file configuration is also supported:
```text
Music/Song File Name.m4a
Music/Song File Name.mp4
```

Actual matching order depends on the implementation: it usually checks the song's local folder first, then checks DynamicCovers for song/album videos, and finally uses the global fallback video.

---

## 🎬 Local MVs, NetEase MVs, and Subtitle Offsets

### Recommended: use dedicated MV folders

Choose one or more MV-only folders from **Settings → Appearance → MV folders**. Dedicated MV folders support:

- Containers: `.mp4`, `.mkv`, `.webm`, and `.mov`.
- Names: `Artist - Title`, `Artist-Title`, `Title - Artist`, or the audio file's base name.
- For multi-artist tags, both the full artist string and the first split artist are tried.
- Plain names and names ending in `_MV` or `-MV` are accepted.

Example:

```text
Movies/My Music Videos/
├── Charlie Puth - Attention.mkv
├── Charlie Puth-We Don't Talk Anymore.mp4
├── See You Again - Wiz Khalifa.webm
└── 01 One Call Away-MV.mov
```

### Store an MV beside its song

To keep ambient dynamic covers separate from music videos, an MV stored beside a song or in a legacy `DynamicCovers` folder must end in `_MV` or `-MV`:

```text
Music/Nine Track Mind/
├── 01 One Call Away.flac
├── 01 One Call Away_MV.mkv
├── 02 Dangerously.flac
└── Charlie Puth - Dangerously-MV.mp4
```

Lookup priority is: dedicated MV folders → the song's folder → legacy dynamic-cover folders. Ambient dynamic video covers remain MP4-only, so an MKV MV is never mistaken for a looping cover.

The player detail page shows separate entries:

- **MV section → Local MV** — shows a video thumbnail and duration, and opens the audible built-in Halcyon player.
- **NetEase section → NetEase Cloud Music MV** — shown when the song's `163 key` contains a non-zero `mvid`, and opens `https://y.music.163.com/m/mv?id=<mvid>`.

When both exist, each remains visible in its own section. A NetEase link does not displace or replace the local MV and is not used for silent synchronized playback.

### LunaBeat MV offsets

Halcyon supports LunaBeat's `mv_offsets.json`. Put it beside a local MV, or import it from **Settings → Library → Tags & scraping → LunaBeat MV offsets**:

```json
{
  "offsets": {
    "Charlie Puth - Attention.mkv": 1.25,
    "01 One Call Away_MV.mp4": -0.4
  }
}
```

Each key is the full MV file name and each value is measured in seconds. Positive values show captions/lyrics later; negative values show them earlier. An imported file applies to both detail-page MVs and landscape player MVs.

> MKV, WebM, and MOV are container formats; recognizing a file does not guarantee that the device can decode every audio/video stream inside it. Common 8-bit H.264, H.265, and VP9 streams usually play directly, while H.264 High 10, some HEVC Main 10 streams, and TrueHD combinations require matching hardware support. Halcyon currently has no FFmpeg software-video fallback, so unsupported streams can still fail.

---

## 🛠 Build

```bash
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
./gradlew :app:assembleDebug -PellaAbi=arm64-v8a
```

Windows PowerShell:

```powershell
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
.\gradlew.bat :app:assembleDebug -PellaAbi=arm64-v8a
```

Release builds prioritize the following environment variables:

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

If these variables are not set, the build uses `release.jks` in the project root. If no usable release keystore is available, local release builds fail by default; in CI or when `ALLOW_DEBUG_SIGNED_RELEASE=true` is set explicitly, the release APK is produced with the debug signing key for testable GitHub Actions artifacts.

For daily development, use `assembleDebug` for validation. `fastRelease` / release builds are intended for release preparation only. Native libraries are packaged from prebuilt `.so` files by default; rerun the corresponding scripts only when updating FFmpeg or lyrico-audiotag native outputs. Release commits and tags should be synchronized to GitHub and GitLab.

---

## 🎧 Native Libraries

Prebuilt FFmpeg and lyrico-audiotag native libraries are located at:

```text
ffmpeg-decoder/src/main/jniLibs/arm64-v8a/libffmpegJNI.so
lyrico-audiotag/src/main/jniLibs/arm64-v8a/liblyrico_taglib.so
```

To restore FFmpeg prebuilt inputs after a fresh clone, run:

```powershell
.\scripts\download_ffmpeg_prebuilt.ps1
```

To update FFmpeg native outputs manually on Windows, run:

```powershell
.\build_ffmpeg.ps1
```

To update the lyrico-audiotag / TagLib native output, run:

```powershell
.\build_lyrico_taglib.ps1
```

Normal `assembleDebug` builds do not rebuild native code by default. Before release, verify the APK contains the required arm64-v8a `.so` files. For routine builds, the full FFmpeg source tree is unnecessary.

`liblyrico_taglib.so` is the native tag read/write output from lyrico-audiotag.

---

## 🧱 Open Source & Licenses

The Halcyon main project is licensed under **Apache-2.0**. Third-party components retain their own licenses; see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

---

## 👥 Credits

- **BetterLyrics** — Visual reference for blurred cover backgrounds and lyric display.
- **Beautiful Lyrics** — Reference for dynamic backgrounds, fullscreen lyrics, and lyric visual experience.
- **LySy** — Interaction and timeline-algorithm reference for the built-in native Kotlin / Compose lyric-timing screen (MIT; its Web source code and dependencies are not bundled).
- **Lyrico** — Reference for external tag editor adaptation and log page interaction.
- **LX Music Mobile** — Provides LX Music API compatibility implementation and testing reference.
- **RawS Music** — Apache-2.0 reference and Kotlin-port foundation for the 10-band EQ, BiQuad parametric EQ, 360-degree surround / panoramic audio, loudness processing, dynamic EQ/de-essing, Moog ladder filtering, and peak protection.
- **Light Cone Music** — Interface design and feature implementation reference.
- Thanks to Miuix, Media3, FFmpeg, Lyricon, SuperLyricApi, LyricGetter-API, lyrico-audiotag / Lyrico, TagLib, 163KeyDecrypter, Coil, OkHttp, Reorderable, accompanist-lyrics-core, LySy, Beautiful Lyrics, RawS Music, and other open source projects used by Halcyon.

---

## 👀 Visitor Count

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_halcyon?theme=capoo-2" alt="Visitor Count" />
</p>

---

## Community Recommendations

- [Lyrico](https://github.com/Replica0110/Lyrico)
  A powerful Miuix-based music tag editor with lyric and cover matching, ReplayGain, NetEase Music comments, and more.

- [RawS Music](https://github.com/QFDY-GZC/RawS-Music)
  An open-source Miuix-based local player with USB DAC exclusive mode, EQ, surround effects, and more.

- [Lyricon](https://github.com/tomakino/Lyricon)
  An Android status-bar lyric plugin with word-by-word and duet display. Xposed or LSPosed is required.

- [Prism Music](https://github.com/Ryderwe/PrismMusic-Release)
  A local music player by leguan with a beautifully designed playback experience.

- [LunaBeat](https://github.com/2755337087/LunaBeat)
  A mobile lyric timing editor and music player with polished player and lyric pages. It can also match lyrics for songs.
