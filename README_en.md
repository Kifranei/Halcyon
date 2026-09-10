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
  <a href="https://t.me/HalcyonPlayer"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a>
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

- Supports local MediaStore scanning and custom folder scanning, with browsing by album, artist, folder, genre, year, composer, and lyricist; refactored two-stage rapid scanning architecture: Stage 1 zero-overhead shallow scan presents songs and covers in seconds, Stage 2 background multi-coroutine concurrency completes deep tag enrichment; supports pull-to-refresh and folder change auto-rescanning.
- Before the first scan, choose whether to enable full-tag search. It searches composer, lyricist, comments, aliases, and custom tags, while the faster basic MediaStore scan searches title, artist, album, and other core metadata only.
- The library source can switch between Local, Navidrome, and Emby, and the visible library refreshes to the selected source instead of keeping songs from the previous source.
- Provides a dedicated library search page with song, album, artist, lyric, duplicate-song, and full-tag search, plus search history, multi-select, and range selection.
- Supports local playlists, favorites, five-star songs, playlist import / export, desktop shortcuts, and custom drag sorting.
- Playlist and folder-playlist pages can expose independent rating / favorite filters. The library supports list, two-/multi-column, and artwork-grid layouts with pinch-to-switch, separate phone/tablet grid columns, and position preservation when switching.
- Album grouping uses both album name and album artist to avoid merging same-name albums from different artists.
- Artist matching & multi-source info: supports custom local artist folder matching, associating images and video backgrounds; supports custom artist splitters (e.g. `/`, `;`, `&`, `、`, etc.) to accurately parse and group collaboration tracks; concurrently fetches artist bios and multilingual info from Wikipedia, Last.fm, and NetEase Cloud Music, with a built-in Cloudflare challenge Sheet for secure retries.
- Smart artist artwork & background priority: custom local resources > sole/collaborative album artist > sole/collaborative song artist; supports multi-image carousel and MP4/MKV/WebM/MOV video backgrounds, prioritizing video when both are available.
- Album details include a dedicated, editable introduction page with one-click NetEase album intro fetching. Local albums prefer the `<review>` field in a neighboring `album.nfo`; when the folder is not writable, the introduction is stored inside the app.
- Includes library analytics, listening calendar, play-count ranking, listening-duration ranking, format distribution, and quality distribution.
- Listening counts can use configurable percentage and elapsed-time thresholds, and long-pressing an analytics legend entry opens its matching tracks.
- Supports Last.fm authorization, full-history sync, automatic scrobbling, and Local / Last.fm / combined listening-history views. Records can be deleted individually, while cached Last.fm entries can be hidden locally. Sensitive credentials are encrypted with Android Keystore and excluded from backups.
- Library analytics are cached and prewarmed after scanning, so larger local libraries can open the statistics page faster.

### 🖼 Player UI & Dynamic Covers

- Provides selectable landscape playback styles plus independent status/navigation-bar visibility and optional reserved space for hidden bars, covering phones, tablets, car displays, and ultra-wide screens.
- New installs default to the non-immersive rounded-cover player; non-square artwork is rounded at its actual image bounds.
- Supports dynamic video covers matched by song, album, or global fallback.
- Supports custom artist cover folders, multi-cover carousel, and a full-screen gallery preview with swipe navigation, bottom save/share pill bar, and a 3-column thumbnail drawer.
- Supports local MP4/MKV/WebM/MOV artist videos as dynamic backgrounds, prioritizing video playback when both images and video exist; full-screen preview always places the video first (`1/xxx`) with click-to-play/pause.
- Static artist artwork is selected in this order: custom asset, sole/collaborative album artist, then sole/collaborative song artist.
- Apple Music immersive player supports a 1:1 square cover toggle; MV immersive lyrics mode defaults to hiding system status and navigation bars on entry.
- Supports global custom wallpapers, launch posters, custom Hi-Res badges, and optional player button outlines.
- Supports Beautiful Lyrics-style dynamic backgrounds for the lyrics page, tablet landscape player, and landscape cover page, with speed, blur, and brightness controls.
- The Compose lyrics page supports an Apple Music-style dynamic background, word-lift animation, smooth relayout, and consistent transitions between immersive lyrics and the player.
- Apple Music flow has an independent speed control. The current track's flow background can extend across Home, Library, and list pages while sharing coordinates and animation state with immersive player / lyrics pages.
- Supports Monet dynamic color derived from the system wallpaper or the current song cover.
- Non-immersive player covers can show a Hi-Res / MQ badge.
- The player supports pull-down dismissal, dynamic backgrounds, blurred cover backgrounds, cover swipe-to-skip, and landscape queue-cover switching; tablet landscape docks can show the current lyric, and bottom dock supports normal, floating, and Liquid Glass styles with customizable parameters.
- Player progress styles include glow, waveform, and segmented ticks, with flowing-curve and RawS mirrored-spectrum visualizers. Quality, audio details, ReplayGain, and output-device capsules are independently configurable.
- Supports both local and NetEase Cloud Music MVs. Local videos may use MP4, MKV, WebM, or MOV, can play silently in sync on the player, and can play independently with audio from the detail page. When a `163 key` contains `mvid`, the detail page adds a clearly labeled NetEase MV link; both entries can be shown together.
- Local MVs opened from song details start directly in landscape and support manual/automatic background picture-in-picture, a glowing progress bar, screenshot sharing, a translucent scrollable caption-settings panel, draggable/lockable captions, KTV lyrics, accompaniment testing, and LunaBeat `mv_offsets.json` subtitle offsets.
- New installs enable Apple Music background motion, transport-button outlines, total-duration display, and silent synchronized MV playback by default. Existing saved choices are preserved on upgrade.
- Long-press player artwork to preview the original cover, with double-tap zoom, one-finger panning, sharing, and saving.
- Artwork is resolved per track rather than only per album, keeping the current song's embedded cover and palette consistent in the player, MiniPlayer, action sheets, media notification, and lock screen even when an album contains multiple covers.

### 🎤 Lyrics

- Supports LRC, Enhanced LRC, ELRC, TTML, AMLL TTML, and Lyricify lyric parsing.
- Supports word-by-word lyrics, line-timed TTML, translations, romanization / phonetics, background vocals, TTML duets, and ELRC V1/V2 duet tags.
- The lyric-page more menu now keeps font size, scaling, and perspective sliders inside a secondary "Lyric style" page to reduce the length of the top-level menu.
- Reads embedded lyrics and external lyric files, including matching `.lrc`, `.ttml`, and `.elrc` files.
- Supports online lyric matching for local songs: fully compatible with **Lyrico Plugin API 5** specification plugins, supporting batch import and deletion from zip bundles; supports concurrent multi-source search, match scoring, and album art preview; supports independent plugin parameter configuration and persistence; matching results can be written directly to embedded audio tags (`TTMLLYRIC`, `USLT`, etc.) or exported as external `.lrc` / `.ttml` files.
- Includes a LunaBeat TTML Hub source for high-precision beat-timed TTML lyrics; Lyrico match results display artwork for easier identification.
- Provides floating desktop lyrics, status-bar lyrics, media notification lyrics, lyric barrage, Xiaomi HyperOS Super Island / Focus Notification lyrics, SuperLyricApi, and Lyric Getter API integration.
- Long status-bar lyrics loop continuously with a gap instead of visibly jumping back to the start.
- Lyricist/composer credit lines, including short `词` / `曲` forms, can be hidden on demand; MV caption translations have a separate visibility switch.
- Lyric card sharing upgraded to Apple Music sticker style with flowing frosted chin; supports font import with a system-font picker, lyric offset, tap-to-seek, and secondary-line configuration.
- Player mini lyrics have independent scale, font-size, spacing, and alignment controls. Lyric taps can seek to an exact timed word or a continuous timestamp and may show an optional expanding outline response.
- A configurable opening template can show title, artist, album, and other metadata before the first lyric. Paused playback can highlight only the current line, and immersive lyric-area swipe-to-skip is separately gated by a setting.
- Includes a dedicated full-screen lyric timing editor with line/word timing, fixed transport controls, undo/redo, V1/V2/V1000 vocal roles, translation, romanization, `x-bg` backing vocals, and embedded or exported LRC, ELRC, and TTML.

### 🌐 WebDAV, Navidrome, Emby & LX Online Music

- Supports WebDAV remote libraries with connection testing, Digest authentication, remote browsing, and remote playback.
- Supports Navidrome / Subsonic and Emby music library entries with the same directory-browsing style and input-field styling as WebDAV.
- Navidrome / Emby remote music libraries support paginated loading and whole-library caching; Navidrome libraries exceeding 500 tracks continue to load in batches.
- Supports LX Music API source import, online search, online playback, cover/lyric retrieval, and local downloading.
- LX search allows switching between NetEase Cloud Music, QQ Music, Kugou, and Migu, and supports encrypted LX user source runtimes.
- Provides a local Web Music Service Beta to browse, play, and upload music via a browser within a trusted LAN. Currently unauthenticated; do not expose on public networks or forwarded ports.

### 🎚 Audio Effects, Decoding, Tags & Quality

- Built-in 10-band software parametric equalizer independent of the system equalizer, with bass-boost and virtualization toggles based on device capabilities.
- The equalizer includes an overall post-effects gain control with a clipping risk indicator for positive peaks.
- Supports native Oboe audio output and USB DAC exclusive mode; crossfade supports constant-loudness, linear, smooth, and retain-original-volume curves.
- Real-time audio output details on the player show source parameters, actual playback pipeline, resampling status, and output device; crossfade hands over UI metadata early while preserving pause semantics.
- Local audio tag reading uses the lyrico-audiotag main pipeline, supporting cover artwork, basic metadata, embedded lyrics, and multi-value tags across common formats.
- Built-in card-based tag editor for basic metadata, track details, creative credits, lyrics, artwork, and interactive star ratings.
- Offers System, FFmpeg, and Auto decoding modes for broader ALAC / AAC / M4A compatibility.
- Supports ReplayGain, shuffle-queue restoration, quality badges, and hi-res format detection up to 24-bit / 96 kHz.
- Reads 163 key metadata from dedicated tags, comments, and descriptions to extract song, album, artist, and `mvid` identifiers, with navigation to corresponding NetEase pages.
- Includes local audio tools for format conversion, multi-stream export, and CUE sheet splitting; built-in spectrum viewer integrates with Aspect Pro and Kaspek.
- Casting page unifies system audio output, Chromecast, and DLNA MediaRenderer; DLNA streams via an in-app local-network media service.

### 🎨 UI & Integrations

- Built with Miuix 0.9.3 for a MIUI / HyperOS aesthetic, featuring a floating bottom navigation bar, MiniPlayer, blur / Liquid Glass effects, and unified dialog styling; bottom dock supports normal, floating, and iOS-style Liquid Glass, pages support swipe switching, and search bar can be merged into bottom dock custom entry while expanding mini-player to full-width lyric view; launch screen stays dark under dark system theme to prevent white flash.
- Supports 10 UI languages (newly added Turkish and Arabic with complete RTL mirrored layout adaptation), in-app language switching; global font and lyric Latin font default to Inter Bold, CJK fallback defaults to MiSans Bold; supports app font sizing, UI scaling, GitHub update page, app logging, full data backup/restore, and Prism Music history export; log export bundles device properties, `getprop`, and readable `build.prop`.
- Backups include artist/album pin states and library display settings; WebDAV prompts when a newer cloud backup exists and allows preconfiguring categories for manual and auto restore.
- Supports switching app icons, configuring long-press launcher shortcuts, pinning home-category shortcuts, and compact / expanded playback widgets. Widgets keep artwork across process restarts, use a blurred artwork-derived background, show live playback time and controls, and provide a compatibility-layout switch for launcher grids that crop the play-button outline.
- Supports song information, tag editing, lyric timing tools, external tag-editor adaptation, and generic AI song interpretation.
- Player and list action menus can be independently reordered or hidden. Notification-to-player navigation and search-result queue behavior are configurable as well.
- Supports MediaSession custom commands for favorite and playback-mode controls in notifications / control centers; automatically pauses when Bluetooth audio disconnects and resumes upon reconnect according to settings.

### 🤖 AI & MCP

- AI song interpretation upgraded to generic AI provider configuration with native DeepSeek and Anthropic protocol support, customizable Base URL / models, and one-click model fetching; includes an AI library listening assistant to recommend playlists or explain listening preferences from local library statistics.
- The Home AI playlist-assistant entry is enabled by default, displays the actual local-library track count, and can be disabled in Settings.
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
| Supported ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, `Universal`              |
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
- Thanks to Miuix, Media3, FFmpeg, Lyricon, SuperLyricApi, LyricGetter-API, lyrico-audiotag / Lyrico, TagLib, 163KeyDecrypter, Coil, OkHttp, Reorderable, LySy, Beautiful Lyrics, RawS Music, and other open source projects used by Halcyon.

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
  A mobile lyric timing editor and music player with polished player and lyric pages. Halcyon references aspects of its lyric timing interface design.

