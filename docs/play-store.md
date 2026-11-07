# Google Play: what to fill in

Only the account owner can do this, in Play Console.

**App name:** Muz On

**Short description (80):** Your own music in bold colours. Lyrics, playlists, foldable layouts, no account.

**Full description:**
Muz On plays the music stored on your phone, with a look of its own.

• Bold poster-style home screen you can rearrange
• Playlists with a choice of sorting
• The artwork of every track
• Timed lyrics: tap a line to jump to it
• Smooth gestures: swipe the player down, swipe the artwork to change track
• Three looks, dark, black and light colours
• Separate layouts for the outer and inner screens of a foldable
• Keep music on a Mac too? Copy your library file to the phone to see the same playlists in the same order

No account, no ads. The internet is used only to find missing covers, and that can be turned off.

**Category:** Music & Audio. **Contains ads:** no. **Target audience:** 13+.

**Privacy policy URL:** the published `docs/privacy.html`. The link in the app's settings is `https://kreal-exe.github.io/Muz-On/privacy.html`; it starts working once GitHub Pages is on for the repository.

**Data safety:** artist and title of tracks without a picture go to Deezer and iTunes search, for app functionality, while the setting is on (it is on by default). The developer stores nothing. Check how Play wants this declared: this is not settled yet.

**Foreground service declaration:** type Media playback. Description: "Continues playing the music the user started when the app is in the background." Effect if interrupted: "Music stops." Needs a link to a short video of a user starting playback and leaving the app.

**Permissions used:** READ_MEDIA_AUDIO, POST_NOTIFICATIONS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, INTERNET. No MANAGE_EXTERNAL_STORAGE.

**Upload:** `app-release.aab`. Assets still needed: 512×512 icon, 1024×500 feature graphic, phone screenshots.

Do not use Apple's product names or artwork in the store text or graphics beyond saying a Mac library is supported.

The terms of the Deezer and iTunes search APIs have not been reviewed yet: check them before a public release.
