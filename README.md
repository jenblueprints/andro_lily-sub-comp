# Subtitle Companion (Android app)

A native Android app version of the subtitle tool: load an .srt file, play it
with a scrub bar and manual sync nudge, and float the current line over
another app using a real system overlay. It also tries to auto-detect when
Fanjiao starts/stops playing, using Android's notification-listener
permission — see "How the auto-sync actually behaves" below for the honest
version of what that can and can't do.

Nothing here has been compiled or tested on a real device — it was written
directly as source code, without access to an Android build toolchain or a
phone to try it on. Treat the first build as the first real test. If
something goes wrong, copy the exact error text back and it can be fixed.

---

## Part 1 — Turn this folder into a working APK (no software to install)

You'll use GitHub's free "Actions" feature to build the app in the cloud.
Nothing installs on your computer.

1. **Create a free GitHub account** at github.com, if you don't have one —
   just needs an email address.
2. Click the **+** in the top right → **New repository**. Name it anything
   (e.g. `subtitle-companion-android`). Leave it **Public**, don't add a
   README/gitignore/license (this folder already has them). Click
   **Create repository**.
3. On the new repo's page, click **uploading an existing file**.
4. Open this folder on your computer, select everything inside it
   (including the hidden `.github` folder — if you can't see it, your file
   manager needs "show hidden files" turned on), and drag it all onto the
   GitHub upload page. It preserves the folder structure automatically.
5. Scroll down, click **Commit changes**.
6. Click the **Actions** tab at the top of the repo. You should see a
   workflow run start automatically (it might take a few seconds to appear —
   refresh if needed). Click into it and watch it run.
7. If it finishes with a green check: click into the run, scroll to
   **Artifacts** at the bottom, and download `subtitle-companion-debug-apk`.
   That's a zip containing your `.apk` file.
8. If it finishes with a red X: click the failed step to see the error, and
   send that error back — most likely a small, fixable version mismatch,
   since none of this was compiled ahead of time.

## Part 2 — Install it on your Samsung phone

1. Get the `.apk` file onto your phone (email it to yourself, upload to
   Google Drive, whatever's easiest) and open it from your phone.
2. Android will warn you it's from outside the Play Store and ask to allow
   installing from that source (Chrome, Files, whichever app you opened it
   with). This is the normal prompt for any sideloaded app — allow it, then
   install.
3. Open **Subtitle Companion** from your app drawer.

## Part 3 — One-time permission setup, inside the app

The app has three setup buttons near the top — do these once:

- **Enable notification access** — opens Android's settings list of apps
  allowed to read notifications. Find "Subtitle Companion" and turn it on.
  This is also what lets it read Fanjiao's play/pause state — Android
  bundles both under the same permission.
- **Enable floating-window permission** — lets the app actually draw the
  overlay on top of Fanjiao. Same category of permission as Messenger's
  chat heads.
- **Detect now** — start playing something in Fanjiao first, *then* tap
  this. It lists every app currently publishing a media session. Find
  Fanjiao's entry (likely something like `com.xxxxx.xxxxx`), paste it into
  the "Fanjiao package name" box below, and tap **Save package name**. This
  step matters — without the right package name, the app has no way to
  know which app's notifications to pay attention to, since it's never
  told your phone's Fanjiao package name.

## Part 4 — Using it

1. **Load .srt file** → pick your subtitle file.
2. **Style settings** → set position, background, size, colour the same
   way as the web version.
3. **Start floating subtitles** → the caption appears as a real floating
   window, draggable anywhere on screen.
4. Switch to Fanjiao and press play as usual. If detection is working, the
   caption starts on its own. If not yet, use **Play** in the app manually,
   and the ± sync buttons to correct drift, exactly like the browser
   version did.

## How the auto-sync actually behaves (read this before expecting magic)

Nobody has verified what Fanjiao actually publishes in its media session,
so the app is written to cope with either possibility automatically:

- **If Fanjiao reports a real, advancing playback position** — the app
  notices the position moving in step with real time across a couple of
  updates, and switches to trusting it directly. That gives genuine
  continuous sync: play, pause, seek, fast-forward all mirror automatically,
  no manual nudging needed.
- **If Fanjiao only reports play/pause (no usable position)** — the app
  falls back to using that as a start/pause trigger only. The caption
  starts and pauses in step with Fanjiao, but if you scrub around inside
  Fanjiao afterward, the two clocks can still drift apart — the ± buttons
  in the app stay there for exactly that.

There's no way to know which of these you'll get until you actually test it
against the real Fanjiao app on your phone with **Detect now**.

## Known rough edges

- The app icon is a plain placeholder shape, not real artwork.
- Loading an .srt doesn't remember the file between app launches yet — you
  reload it each time you open the app.
- This is a debug build (unsigned with a proper release key), which is
  completely fine for installing on your own phone, but Android will always
  flag it as "unknown source" — that's expected and not a problem.

## Updating an existing repo with this version

If you already have this repo set up on GitHub and building via Actions,
apply this update by dragging this whole `SubtitleCompanion` folder onto
**Add file > Upload files** in your repo, the same way you did the first
time. GitHub overwrites any file with a matching path and adds new ones --
you don't need to delete anything first. Commit, then check the Actions tab
for the new build.

## What's new in this version

- **Stop button**: the floating-subtitles button now actually toggles --
  tap it again to stop, or use Stop on the notification.
- **Dragging** the floating window now moves it the direction your finger
  actually goes.
- **Style settings apply live** to a floating window that's already running
  -- no need to stop and restart it.
- **Pinch the floating window with two fingers** to resize the text/panel
  in real time; your zoom level is remembered.
- Short natural gaps between subtitle lines no longer cause a blank flicker.
- Seeking/skipping inside Fanjiao (once auto-sync has locked on) now
  mirrors immediately, including jumps like -15s, not just play/pause.
- **New file types**: load `.ass`/`.ssa` and `.lrc` files, not just `.srt`.
- **Folder mode**: tap "Load subtitle folder" and pick a folder containing
  one subtitle file per episode/title. As Fanjiao's reported track title
  changes, the app matches it by name and auto-loads the right file. If a
  title doesn't match anything well enough, whatever's currently loaded
  stays put rather than guessing wrong.
- Your last-loaded single file (or folder) reopens automatically the next
  time you launch the app.
- **Support button**: add a link (e.g. your Patreon) in Style settings and
  a small "Support" button appears on the main screen; a one-time, low-key
  invite also shows the first time you start floating subtitles each
  session. Leave the link blank and neither appears.
- More text colors, panel colors, and a bold-text option in Style settings.
- App renamed to "Sub on Top" with a proper launcher icon and in-app
  branding, both built from your lily logo.
