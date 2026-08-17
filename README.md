# WhatsChat

A native Android messaging app (Kotlin + Jetpack Compose) inspired by WhatsApp,
backed by Firebase (Authentication, Firestore, Storage, Cloud Messaging), built
around real-time chat plus a set of on-device translation features aimed at
talking across languages — e.g. while traveling. The app icon (a chat bubble
with a paper plane inside, `drawable/ic_launcher_foreground.xml`) reflects
that: a hand-drawn vector icon, since no image-generation tooling was
available to produce custom artwork for it.

## Features (MVP)

- Email/password sign-up and login
- A single chat list screen: existing conversations (sorted by most recent
  message), plus every other registered contact below — tap any of them,
  chatted-with or not, to open/start a conversation directly (no separate
  "new chat" screen to navigate to)
- Real-time text messaging, with long-press-to-select and multi-select
  deletion of your own messages
- Image sharing in a conversation (uploaded to Firebase Storage)
- Editable profile (name, status, photo)
- 1:1 voice and video calls (WebRTC, signaled through Firestore — see "Voice
  & video calls" below), with an incoming-call prompt that shows up no
  matter which screen you're on, not just the chat list
- Voice messages send the instant you stop recording — pick a fun voice
  (Woman / Man / Baby / Giant / Robot / Alien / Tired / Laughing) ahead of
  time if you want one, otherwise it's sent as a plain recording — see
  "Voice messages" below
- Emoji picker and a set of large "stickers" (see "Emoji & stickers" below)
- "Typing..." indicator, shown while the other participant is composing a message
- Selfie filters (14 presets: 10 emoji overlays with a live viewfinder
  preview, plus 4 warp effects that reshape your actual face) — see "Selfie
  filters" below
- A collapsible toolbar in the chat composer (tap the arrow to reveal emoji /
  stickers / photo / translate / voice) so the message field gets the full
  width; every icon in the app has a long-press tooltip explaining what it does
- Translate: type or speak a message, pick a language, and send it either as
  a voice note or as text in that language (31 languages) — see "Voice
  translation" below
- Every message has a small icon to translate it in place or have it read
  aloud — see "Translate/listen to a received message" below
- Calls ring even when the app isn't open on screen, via a background
  listener service with a full-screen incoming-call notification — see
  "Background call ringing" below
- A Settings screen (gear icon on the chat list) to switch the app's own
  display language — see "App language setting" below

Not included yet: end-to-end encryption, group chats, status/stories, push
notification delivery for new *messages* (the FCM token is stored per user,
but no Cloud Function sends notifications yet — calls are handled separately,
see below).

## Voice & video calls

The chat header has two call buttons: phone (audio-only) and camera (video).
Both use [WebRTC](https://webrtc.org/) for the actual media stream; Firestore
only exchanges the connection setup data (SDP offer/answer and trickled ICE
candidates) between the two phones — no audio/video ever passes through
Firebase. A call's `isVideo` flag is decided upfront by whichever button was
tapped (caller) or by the incoming call doc (callee), since it determines
whether the camera is opened at all before the connection exists.

Video call UI: the remote video fills the screen (or the contact's avatar
while the connection is still being established), with a small local preview
in the top-right corner. Controls: mute, camera on/off, front/back camera
switch, hang up.

The incoming-call prompt (Accept/Decline) is shown from `WhatsChatNavGraph`
itself rather than any one screen, so it pops up no matter what you're doing
in the app — a conversation, your profile, the filter camera, anywhere.
Accepting takes you straight into the call already connecting; there's no
second "answer" step once you're in `CallScreen`.

- A call rings even while the app is backgrounded, thanks to a foreground
  listener service — see "Background call ringing" below for how it works
  and its limits (it can't survive the app being force-stopped).
- Only Google's public STUN servers are configured. This works for most
  networks, but a small fraction of restrictive networks (symmetric NAT,
  some corporate/campus Wi-Fi) may fail to connect without a TURN server,
  which isn't included here.
- The `calls` Firestore documents aren't automatically cleaned up after a
  call ends — fine for testing, but worth adding a scheduled cleanup (or a
  Cloud Function) before any real usage.
- Microphone access (and camera access, for video calls) is requested at
  runtime the first time you start or answer a call.
- Video capture uses `Camera2Enumerator`/`CameraVideoCapturer` from the
  WebRTC SDK directly (front camera by default) at 1280x720@30fps — no
  CameraX involved in the call path.
- Video is encoded/decoded in software (`SoftwareVideoEncoderFactory`/
  `SoftwareVideoDecoderFactory`) rather than using hardware acceleration —
  hardware codec support varies a lot across Android chipsets, and a
  hardware codec failing silently to initialize is a well-known way to end
  up with a call that has audio but never shows any video, with no error
  surfaced. Software VP8 works identically on every device at the cost of
  more CPU/battery, a good trade for a small 1:1 call. `WebRtcClient.kt`
  also now logs camera/ICE/SDP events (`Log.d`/`Log.w`, tag `WebRtcClient`)
  so a future issue can actually be diagnosed from Logcat.

## Voice messages

Tapping the mic icon in a conversation records a voice message; tapping it
again stops recording and sends it immediately — no extra dialog in the way.
The theater-masks icon next to the mic lets you pick a "funny voice" effect
ahead of time for your *next* recording (**Normal, Woman, Man, Baby, Giant,
Robot, Alien, Tired, Laughing**, defaulting to Normal); it stays selected
until you change it again. None of these model or imitate any real person —
they're generic playback-rate presets (see `VoiceEffect.kt`).

Recording uses raw 16-bit PCM audio (`AudioRecord`). Before sending,
`PcmResampler` walks through the recorded samples at a rate driven by the
chosen effect — a constant rate for a flat pitch/speed shift (Woman, Man,
Baby, Giant, Robot, Alien, Tired), or a rate that oscillates over time for a
wobble effect (Laughing). This is genuine resampling of the audio data, not
just a relabeled WAV header, so it plays back correctly everywhere. The
result is wrapped in a WAV file and uploaded.

- "Robot" and "Alien" are approximations (an extreme pitch/speed shift) —
  a truly metallic/otherworldly timbre would need real DSP (e.g. ring
  modulation), which isn't implemented here.
- Voice messages are uploaded as uncompressed WAV files, so they're larger
  than a typical compressed voice note (roughly 170 KB per 10 seconds at the
  default 44.1 kHz mono recording rate) — fine for testing, but worth
  switching to a compressed format (e.g. re-encoding to AAC/Opus) before any
  real usage to save Storage bandwidth.
- Playback uses `MediaPlayer` streaming directly from the Storage download
  URL.

## Emoji & stickers

- The emoji icon opens a grid of common emoji that get appended to the
  message text field.
- The star icon opens a grid of larger "stickers" — sent immediately as
  their own message, rendered oversized without a chat bubble. These are
  Unicode emoji rendered large, not custom artwork (no image-generation
  tooling was available while building this) — swap `STICKER_EMOJIS` in
  `EmojiPicker.kt` for real illustrations later if wanted.

## Selfie filters

The face icon in a conversation opens the front camera. There are two kinds
of filter, both picked from the same row at the bottom:

- **Emoji overlays** — Dog, Cat, Clown, Alien, Party, Glasses, Heart Eyes,
  Disguise, Crown, Santa (emoji standing in for custom artwork). A live
  approximate preview tracks your face in the viewfinder, and you can
  **drag the emoji** with your finger any time to override the auto-tracked
  spot with an exact position of your choosing; take the photo and it's
  composited at that same spot. If you never drag it, the filter is
  auto-positioned from [ML Kit](https://developers.google.com/ml-kit)'s
  detected face bounding box and landmarks (eyes for Glasses/Heart Eyes,
  nose for Disguise, etc. — see `FilterAnchor` in `FaceFilter.kt`),
  re-detected on the still photo for precision.
- **Warp filters** — Big Nose, Small Nose, Big Eyes, Small Eyes. These
  actually reshape the photo's pixels around the detected landmark (a
  radial bulge for "big", a pinch for "small" — see
  `FaceWarpCompositor.kt`) rather than drawing anything on top. Reshaping
  pixels frame-by-frame in the live viewfinder would be too slow to stay
  smooth, so unlike the emoji overlays there's no live preview for these —
  the effect is applied once, to the photo, right after you tap capture.

Either way you can retake or send the result as a normal image message.

- Auto-tracking (emoji overlays, no drag) uses two different code paths on
  purpose: the **live viewfinder preview** uses `ImageAnalysis` on the
  streaming camera frames, which is inherently approximate — getting
  per-frame camera rotation and front-camera mirroring exactly right on
  every device without testing on real hardware is genuinely hard, so treat
  it as a rough guide. The **final sent photo** re-runs detection on the
  still image itself (no rotation/mirroring ambiguity there) and composites
  precisely, so a live-preview misalignment never affects an
  auto-positioned result.
- Once you **drag** an emoji overlay, that exact screen position is what
  gets used for the sent photo too (converted from preview to photo pixel
  space) — no re-detection involved, so it's exactly where you left it, not
  an approximation. Picking a different filter resets back to auto-tracking.
  Warp filters can't be dragged — they always target whichever landmark
  ML Kit detects on the captured photo.
- If no face is detected at capture time and an emoji overlay was never
  dragged, the filter is skipped and the plain photo is offered instead of
  failing. A warp filter with no detected face is skipped the same way.
- Camera capture uses CameraX (`Preview` + `ImageCapture` + `ImageAnalysis`,
  front camera); detection uses ML Kit's bundled (on-device, no network)
  face detector, with a faster/lower-accuracy mode for the live stream and a
  higher-accuracy mode for the final capture.

## Voice translation

The translate icon next to the message field opens a choice of three modes,
then a language picker (31 languages, in `AppLanguage.kt` — English, French,
Portuguese, German, Spanish, Italian, Arabic, Chinese, Japanese, Korean,
Russian, Dutch, Turkish, Polish, Hindi, Vietnamese, Thai, Swedish, Greek,
Ukrainian, Hebrew, Indonesian, Romanian, Czech, Danish, Norwegian,
Hungarian, Finnish, Persian, Urdu, and Filipino):

- **⌨️ Type text → send as voice** — translate what's currently typed in the
  message field and send it as a spoken voice note (only enabled once
  there's text to translate).
- **🎤 Speak → send as voice** — say something instead of typing it; it's
  transcribed, translated, and sent as a voice note in the chosen language.
- **🎤 Speak → send as text** — say something in your own language and it's
  transcribed, translated, and sent as a normal *text* message in the chosen
  language, for a reader who doesn't speak yours.

Under the hood, once there's text (typed or transcribed) to work with:

1. Detects its language (ML Kit Language Identification).
2. Translates it into the chosen language (ML Kit Translation — downloads a
   small model for that language pair the first time it's used, then works
   offline).
3. For the two "→ send as voice" modes: speaks the translated text using
   Android's built-in text-to-speech engine, writing the audio straight to a
   WAV file (`TextToSpeech.synthesizeToFile`), then sends it as a voice
   message. For "→ send as text": sends the translated string directly as a
   text message.

The two "🎤 Speak" modes transcribe what you say first (Android's built-in
speech recognizer — `SpeechToText.kt`, the same engine behind the keyboard's
voice-typing button, hinted with the phone's own language setting for
accuracy). Everything else runs on-device — no translation API key, no
per-request cost.

- If a language's TTS voice isn't installed on the device, sending as voice
  fails with an error message rather than silently producing nothing; the
  user can install additional TTS voices from the system Settings
  ("Text-to-speech output").
- The first use of a given language pair pauses briefly to download ML Kit's
  translation model (a few MB); subsequent uses are fast.
- Speech is synthesized at 0.8x the engine's default rate — a translated
  phrase read at normal conversational speed is easy to miss on first
  listen, especially in an unfamiliar language.
- Speech recognition needs the device's speech-recognition service (present
  on virtually all phones with Google Play Services) and asks for microphone
  access the first time either "Speak" mode is used, separately from the
  permission prompt for plain voice messages.

### Translate/listen to a received message

Every text message (yours or theirs) has a small translate icon next to its
timestamp. Tapping it opens a two-item menu:

- **Translate** — pick a language and the translation appears right under
  the original text, in italics, inside that same bubble (translations are
  kept in memory per message; they reset if you leave and reopen the chat).
- **Listen** — reads the message aloud immediately through the speaker, in
  whatever language it detects the message was written in (`VoiceTranslator.speakNow`)
  — no language picker needed, no file written, just instant playback.

Both reuse the same on-device ML Kit translation/language-ID and Android
TextToSpeech pipeline as the composer's translate feature above.

## Background call ringing

`CallListenerService` (`data/service/CallListenerService.kt`) is a foreground
service, started as soon as someone signs in (and stopped on sign-out — see
the `FirebaseAuth.AuthStateListener` in `WhatsChatNavGraph`), that keeps the
same "any call for me?" Firestore listener alive independent of which screen
(if any) is on screen. It shows two notifications:

- A silent, minimum-importance "Listening for calls" notification, required
  by Android for any foreground service to keep running.
- When a call comes in: a high-importance, full-screen incoming-call
  notification (with **Accept**/**Decline** actions) that pops over the lock
  screen and plays the phone's ringtone + vibration pattern on a loop until
  it's answered, declined, or the caller cancels. **Accept** deep-links
  straight into `CallScreen` to join the already-ringing call (via
  `MainActivity.incomingCallIntent`); **Decline** updates the call's
  Firestore status without needing to open the app at all.

The service declares itself as `foregroundServiceType="specialUse"` (Android
14's generic long-running-task category), not `"phoneCall"` — the latter
carries stricter OS assumptions (Telecom/`ConnectionService` integration)
this app doesn't implement, which was found to make the foreground service
fail to start on some devices with no visible error, so the app just never
rang in the background. `CallListenerService.start()`/`onCreate()` also now
log a warning (tag `CallListenerService`) if starting still fails, so a
remaining issue can be diagnosed from Logcat instead of guessed at blind.

Limits worth knowing:

- This is a real Android foreground service, not a push notification — it
  only rings while the app's process is alive. Force-stopping the app (or a
  phone restart, until you reopen the app once) stops it too. A fully
  "even after force-quit" experience needs server-side push (an FCM message
  sent by a Cloud Function whenever a `calls` doc is created) — not set up
  here, since it requires deploying backend code, not just an app change.
- Some phone brands (Xiaomi/MIUI, Huawei, Oppo, etc.) aggressively kill
  background apps by default. On those, open Settings → Apps → WhatsChat and
  enable "Autostart"/"No restrictions" battery usage so the listener isn't
  killed a few minutes after you leave the app.
- On Android 14+, the OS may require you to grant the "Display over other
  apps"/full-screen notification permission manually the first time (Settings
  → Apps → WhatsChat → Notifications) for the incoming-call screen to pop up
  automatically while the phone is locked.

## Deleting messages

Long-press any message you sent (text, image, voice note, or sticker) to
enter selection mode; tap other messages you sent to add or remove them from
the selection, then tap the trash icon in the top bar to delete all of them
at once (a confirm dialog shows how many). They're removed from Firestore
for both participants — there's no "delete for me only" option and no undo.
Deleting the most recent message in a chat recomputes the chat list's
preview from what's now the latest remaining message (or clears it if the
chat is now empty).

## App language setting

The gear icon on the chat list (next to the profile icon) opens Settings,
where you can pick the app's own display language — English, French, or
"follow the phone's language" — independent of the per-message translation
feature above. It uses Android's official per-app language API
(`AppCompatDelegate.setApplicationLocales`, via `SettingsScreen.kt` and
`AppUiLanguage.kt`), which remembers your choice by itself (no extra storage
code needed) and re-applies it on every future launch, and swaps in
`res/values-fr/strings.xml` for the French UI text.

- Picking a language recreates the current screen to apply it, which can
  land you back on the chat list — that's normal, not a bug.
- On Android 13+ this takes effect immediately, anywhere in the app. On
  older versions it can need a full close-and-reopen (swipe the app away
  and relaunch it) to fully apply everywhere, since the automatic backport
  for older Android versions expects an `AppCompatActivity` and this app's
  `MainActivity` deliberately stays a plain Compose `ComponentActivity` (a
  bigger, riskier change to the app's theme/activity setup than was worth
  making for this).
- Only the most common, always-visible screens (login/register, chat list,
  profile, settings) have been translated into French so far — deeper
  screens (selfie filters, the translate dialogs, call screens) still show
  their original English strings for now. Extending `strings.xml` /
  `values-fr/strings.xml` with more keys is straightforward, just
  time-consuming to do exhaustively.

## Typing indicator

Each `chats/{chatId}` document carries a `typingUid`/`typingUpdatedAt` pair.
Typing a character in the composer marks the current user as typing (throttled
to one write per burst of typing rather than per keystroke) and automatically
clears it after 3 seconds of inactivity or when the message is sent; a status
older than 6 seconds is also treated as stale on the reading side, so a killed
app can't leave a permanent "typing..." shown to the other person.

## Project structure

```
app/src/main/java/com/whatschat/app/
├── MainActivity.kt              Compose entry point
├── WhatsChatApp.kt               Application class
├── data/
│   ├── model/                    User, Chat, Message, Call
│   ├── repository/               AuthRepository, UserRepository, ChatRepository, CallRepository
│   ├── webrtc/                   WebRtcClient (PeerConnection wrapper)
│   ├── audio/                    VoiceRecorder, PcmResampler, WavFile, VoiceEffect
│   ├── filter/                   FaceFilter, FaceFilterCompositor
│   ├── translate/                 AppLanguage, VoiceTranslator
│   └── service/                  FCM token sync service
└── ui/
    ├── navigation/                Navigation Compose graph
    ├── theme/                     Material 3 theme
    ├── components/                Shared composables (Avatar, EmojiPicker)
    ├── viewmodel/                 AuthViewModel, ChatListViewModel, ChatViewModel, ProfileViewModel, CallViewModel
    └── screens/                   auth/, chatlist/, chat/, profile/, call/, filter/
```

## Firebase setup (required before the app can run)

The repo does **not** include `google-services.json` (it contains
project-specific credentials and is gitignored). To connect your own Firebase
project:

1. Go to the [Firebase console](https://console.firebase.google.com/) and
   create a new project (or reuse an existing one).
2. Add an Android app to the project with package name `com.whatschat.app`.
3. Download the generated `google-services.json` and place it at
   `app/google-services.json`.
4. Enable the following in the Firebase console:
   - **Authentication** → Sign-in method → enable **Email/Password**.
   - **Firestore Database** → create a database (start in production mode).
   - **Storage** → create a default bucket.
   - **Cloud Messaging** is enabled automatically; no extra setup needed for
     token storage.
5. Deploy the security rules below (Firestore → Rules, Storage → Rules).

### Firestore security rules

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    match /chats/{chatId} {
      allow read, write: if request.auth != null
        && request.auth.uid in resource.data.participants;
      allow create: if request.auth != null
        && request.auth.uid in request.resource.data.participants;

      match /messages/{messageId} {
        allow read, write: if request.auth != null
          && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;
      }
    }

    match /calls/{callId} {
      allow read, write: if request.auth != null
        && request.auth.uid in [resource.data.callerId, resource.data.calleeId];
      allow create: if request.auth != null
        && request.auth.uid == request.resource.data.callerId;

      match /callerCandidates/{candidateId} {
        allow read, write: if request.auth != null;
      }
      match /calleeCandidates/{candidateId} {
        allow read, write: if request.auth != null;
      }
    }
  }
}
```

### Storage security rules

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /profile_photos/{userId}/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    match /chat_images/{chatId}/{fileName} {
      allow read, write: if request.auth != null;
    }
    match /chat_audio/{chatId}/{fileName} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Build & run

Requires Android Studio (Koala or newer) or the command line with an Android
SDK installed.

```
./gradlew assembleDebug
```

Or open the project in Android Studio and run the `app` configuration on an
emulator or device (minSdk 24 / Android 7.0+).

> Note: building requires network access to Google's Maven repository
> (`dl.google.com`) for the Android Gradle Plugin and Firebase SDKs — this is
> normal for any Android project and works out of the box on a regular
> development machine.

## Data model (Firestore)

- `users/{uid}`: `name`, `email`, `photoUrl`, `status`, `lastSeen`, `fcmToken`
- `chats/{chatId}`: `participants` (2 uids), `lastMessage`, `lastMessageTime`,
  `lastMessageSenderId`, `typingUid`/`typingUpdatedAt`. `chatId` is the two
  participant uids sorted and joined with `_`, so a conversation between the
  same two users always resolves to the same document.
- `chats/{chatId}/messages/{messageId}`: `senderId`, `text` (also used for
  stickers — the emoji character), `imageUrl`, or `audioUrl`/`audioDurationMs`,
  `type` (`TEXT`/`IMAGE`/`AUDIO`/`STICKER`), `timestamp`
- `calls/{callId}`: `callerId`, `calleeId`, `status` (`RINGING` / `ACCEPTED` /
  `DECLINED` / `ENDED`), `offerSdp`, `answerSdp`, `isVideo`, `createdAt`
- `calls/{callId}/callerCandidates` and `.../calleeCandidates`: trickled ICE
  candidates (`sdpMid`, `sdpMLineIndex`, `candidate`)

## Suggested next steps

- Group chats (extend `participants` beyond 2, adjust the chat id scheme)
- Push notifications via a Cloud Function triggered on new messages (and, for
  calls, an FCM push so ringing survives a force-stopped app, not just a
  backgrounded one — see "Background call ringing")
- End-to-end encryption
- A TURN server for calls on restrictive networks
- Message delivery/read receipts
