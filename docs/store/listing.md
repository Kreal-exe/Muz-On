# Muz On: Google Play listing

Package: `dev.kreal.muzon`. Everything below is ready to paste into Play Console; only the account owner can submit it.

## Graphics

| Asset | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512×512, 32-bit PNG, full-bleed square (Play rounds it) |
| Feature graphic | `feature-graphic.png` | 1024×500, 24-bit PNG |
| Phone screenshots | `screenshots/01-…05-*.png` | see below, 24-bit PNG, ratio within 1:2…2:1 |

Screenshots, in upload order:

1. `01-home-inner.png`: home screen, inner screen of the foldable (2172×2352)
2. `02-songs-outer.png`: Songs list, outer screen (1106×2212; status bar cut off, 13 px of background added at each side to reach 1:2)
3. `03-player-lyrics-inner.png`: player with timed lyrics and queue, inner screen (2352×2172)
4. `04-player-outer.png`: player, outer screen (1080×2150; status bar and the bottom of the list cut off)
5. `05-albums-inner.png`: albums grid, inner screen (2172×2352)

The inner-screen shots can also go in the 7-inch and 10-inch tablet sections.

Sources: `src/feature-graphic.html` (rendered with headless Chrome at 1024×500) and `src/icon-512.py` (draws the launcher bars on #E8542E; `python3 src/icon-512.py icon-512.png`).

Note: the screenshots show real album covers of other artists (some carry an "AUDIOVK.com" watermark). Play can reject a listing for third-party artwork; if that happens, retake the screenshots with a library of royalty-free or self-made covers.

## App name (30)

Muz On

## Short description (80)

Your own music in bold colours. Lyrics, playlists, foldable layouts, no account.

## Full description: English

Muz On plays the music stored on your phone, with a look of its own.

• A bold, poster-style home screen: show, hide and reorder its sections
• Songs, artists, albums, genres and playlists, with a fast A–Z index
• Make your own playlists and sort any playlist by artist, album, title, genre, date added or duration
• The artwork embedded in each track, not one picture per album
• Timed lyrics from the file's tags: tap a line to jump to it
• Smooth gestures: swipe the player down, swipe the artwork to change track
• Themes Poster, Detailed and Aero, each in dark, black or light
• Separate layouts for the outer and inner screens of a foldable, with the queue and lyrics side by side on the big screen
• 14 languages
• Keep music on a Mac too? Copy your library file to the phone to see the same playlists in the same order

No account, no ads, no analytics, no in-app purchases.

The internet is used for one thing only: to find a cover for a track that has none. Only the artist name and track title are sent, to the Deezer and iTunes search services. You can turn this off in settings, and then the app never goes online.

## Полное описание: русский

Muz On играет музыку, которая хранится на телефоне, и выглядит по-своему.

• Яркий главный экран в стиле плаката: разделы можно скрыть, показать и переставить
• Песни, исполнители, альбомы, жанры и плейлисты с быстрым алфавитным указателем
• Свои плейлисты и сортировка любого плейлиста: по исполнителю, альбому, названию, жанру, дате добавления или длительности
• Обложка, встроенная в каждый трек, а не одна картинка на альбом
• Синхронизированный текст песни из тегов файла: нажмите на строку, чтобы перейти к ней
• Плавные жесты: смахните плеер вниз, смахните обложку, чтобы сменить трек
• Темы «Плакат», «Детальная» и «Аэро», каждая в тёмном, чёрном или светлом варианте
• Отдельные раскладки для внешнего и внутреннего экранов складного телефона; на большом экране очередь и текст рядом
• 14 языков
• Музыка есть и на Mac? Скопируйте файл медиатеки на телефон и увидите те же плейлисты в том же порядке

Без аккаунта, без рекламы, без аналитики, без покупок в приложении.

Интернет нужен только для одного: найти обложку для трека, у которого её нет. Отправляются только имя исполнителя и название трека, в поиск Deezer и iTunes. Это можно выключить в настройках, и тогда приложение не выходит в сеть.

Russian short description (80): Ваша музыка в ярких цветах. Тексты, плейлисты, складные экраны, без аккаунта.

## Category and details

- App or game: App
- Category: Music & Audio
- Tags (pick in Console): Music player, Offline music
- Contains ads: No
- In-app purchases: No
- Price: Free
- Contact email: (the developer contact address set in Play Console)
- Website: optional, e.g. the GitHub repository
- Privacy policy URL: `PRIVACY_POLICY_URL` (placeholder; the app links to `https://kreal-exe.github.io/Muz-On/privacy.html`, which works once GitHub Pages is on and `docs/privacy.html` is published)

## Target audience and content

- Target age groups: 13–15, 16–17, 18 and over (13+). Not designed for children; no appeal to children in the listing.
- Ads: none. News app: no. COVID-19 app: no. Government app: no. Financial features: none. Health features: none.
- Data safety and content rating: as below.

## Content rating (IARC questionnaire)

- Category: All other app types (a media player; it ships no content of its own and plays only the user's files)
- Violence: No
- Fear / horror: No
- Sexuality or nudity: No
- Bad language: No (the app contains none; it shows the user's own lyrics and covers)
- Controlled substances (drugs, alcohol, tobacco): No
- Crude humour: No
- Gambling or simulated gambling: No
- Users can interact or exchange content with each other: No
- Shares the user's current physical location with other users: No
- Allows purchases of digital goods: No
- Unrestricted internet access (e.g. a web browser): No
- Hate speech, extremism: No

Expected rating: Everyone / PEGI 3 / USK 0.

## Data safety

What the code does (checked in `data/OnlineArt.kt`, `ui/Art.kt`, `data/Prefs.kt`, `data/Lyrics.kt`):

- Lyrics are read only from the tags of the audio file on the device (`LyricsReader`, jaudiotagger). The app does not look lyrics up online: no LRCLIB, NetEase or other lyrics service is contacted.
- Covers: for a track with no embedded picture, and only while the setting "Find missing covers online" is on (it is on by default), the app sends the cleaned-up artist name and track title over HTTPS to:
  - `https://api.deezer.com/search` (artist + title),
  - then, if nothing is found, `https://itunes.apple.com/search` (artist + title),
  - then, if still nothing, `https://api.deezer.com/search/artist` (artist name only),
  and downloads the returned image (Deezer or Apple image CDN). User-Agent is `MuzOn/1.0`. No identifier, account, device ID, location or file path is sent; the services see the device's IP address as with any request.
- The found picture is cached in the app's private storage. The developer runs no server and receives nothing. No analytics, crash reporting or advertising SDKs.
- Permissions: READ_MEDIA_AUDIO (READ_EXTERNAL_STORAGE up to Android 12), POST_NOTIFICATIONS, WAKE_LOCK, INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK.

Answers for the form:

- Does your app collect or share any of the required user data types? **Yes** (conservative; see the note).
- Data type: **App activity → Other actions** (names of the tracks the user has in the library, as artist + title search terms).
  - Collected: Yes. Shared: Yes, with Deezer and Apple (iTunes Search API), for **App functionality**.
  - Processed ephemerally: Yes (the developer stores nothing; only the returned cover is kept on the device).
  - Required or optional: **Optional** (users can turn it off in settings).
- Is all user data encrypted in transit? **Yes** (HTTPS only).
- Do you provide a way for users to request that their data is deleted? **No data is stored by the developer**; in-app, clearing app storage or uninstalling removes cached covers and settings. Answer "No" to the deletion-request question if the form insists, with this explanation in the policy.
- No other data types: no personal info, location, contacts, messages, photos, audio files, files and docs, device IDs, financial info, health, web history or diagnostics leave the device.

Note: an argument can be made for declaring "no data collected", since only public song metadata is sent as a search term and it is not linked to the user. The declaration above is the safer one; whichever is chosen, the privacy policy must say the same thing (`docs/privacy.html` already describes the Deezer/iTunes lookups).

## Other declarations

- Foreground service: Media playback. "Continues playing the music the user started when the app is in the background." Effect if interrupted: "Music stops." Needs a link to a short video of a user starting playback and leaving the app.
- Advertising ID: the app does not use it.
- Before a public release, review the terms of the Deezer and iTunes Search APIs.
- Do not use Apple product names or artwork beyond saying a Mac library is supported.
