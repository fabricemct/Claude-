# WhatsChat

A native Android messaging app (Kotlin + Jetpack Compose) inspired by WhatsApp,
backed by Firebase (Authentication, Firestore, Storage, Cloud Messaging).

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
- Funny voice messages: record a message and send it as Woman / Man / Baby /
  Giant / Robot / Alien / Tired / Laughing — see "Voice messages" below
- Emoji picker and a set of large "stickers" (see "Emoji & stickers" below)
- "Typing..." indicator, shown while the other participant is composing a message
- Selfie filters (14 presets: 10 emoji overlays with a live viewfinder
  preview, plus 4 warp effects that reshape your actual face) — see "Selfie
  filters" below
- A collapsible toolbar in the chat composer (tap the arrow to reveal emoji /
  stickers / photo / translate / voice) so the message field gets the full
  width; every icon in the app has a long-press tooltip explaining what it does
- Translate & speak: type a message, pick a language, and send it as a voice
  note in that language instead of text — see "Voice translation" below
- Calls ring even when the app isn't open on screen, via a background
  listener service with a full-screen incoming-call notification — see
  "Background call ringing" below

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

## Voice messages

Tapping the mic icon in a conversation records a voice message; tapping it
again stops recording and shows a picker for a "funny voice" effect before
sending: **Normal, Woman, Man, Baby, Giant, Robot, Alien, Tired, Laughing**.
None of these model or imitate any real person — they're generic playback-rate
presets (see `VoiceEffect.kt`).

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

The translate icon next to the message field (enabled once you've typed
something) lets you send what you typed as a spoken voice note in another
language instead of as text. Pick a language (English, French, Portuguese,
German, Spanish, Italian) and the app:

1. Detects the language you typed in (ML Kit Language Identification).
2. Translates the text into the chosen language (ML Kit Translation —
   downloads a small model for that language pair the first time it's used,
   then works offline).
3. Speaks the translated text using Android's built-in text-to-speech engine,
   writing the audio straight to a WAV file (`TextToSpeech.synthesizeToFile`).
4. Sends that file as a normal voice message.

Everything runs on-device — no translation API key, no per-request cost.

- If a language's TTS voice isn't installed on the device, this fails with an
  error message rather than silently producing nothing; the user can install
  additional TTS voices from the system Settings ("Text-to-speech output").
- The first use of a given language pair pauses briefly to download ML Kit's
  translation model (a few MB); subsequent uses are fast.
- Speech is synthesized at 0.8x the engine's default rate — a translated
  phrase read at normal conversational speed is easy to miss on first
  listen, especially in an unfamiliar language.

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
