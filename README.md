# WhatsChat

A native Android messaging app (Kotlin + Jetpack Compose) inspired by WhatsApp,
backed by Firebase (Authentication, Firestore, Storage, Cloud Messaging).

## Features (MVP)

- Email/password sign-up and login
- Directory of registered users to start a new 1:1 conversation
- Real-time chat list, sorted by most recent message
- Real-time text messaging
- Image sharing in a conversation (uploaded to Firebase Storage)
- Editable profile (name, status, photo)
- 1:1 voice calls (WebRTC, signaled through Firestore — see "Voice calls" below)

Not included yet: video calls, end-to-end encryption, group chats,
status/stories, push notification delivery (the FCM token is stored per user,
but no Cloud Function sends notifications yet).

## Voice calls

Tapping the phone icon in a conversation starts a 1:1 audio call using
[WebRTC](https://webrtc.org/) for the actual audio stream. Firestore is only
used to exchange the connection setup data (SDP offer/answer and ICE
candidates) between the two phones — no audio ever passes through Firebase.

- A call only rings while the recipient has the app open on the chat list
  screen; there's no background/lock-screen ringing yet (that would require a
  foreground service with a full-screen notification — a natural next step).
- Only Google's public STUN servers are configured. This works for most
  networks, but a small fraction of restrictive networks (symmetric NAT,
  some corporate/campus Wi-Fi) may fail to connect without a TURN server,
  which isn't included here.
- The `calls` Firestore documents aren't automatically cleaned up after a
  call ends — fine for testing, but worth adding a scheduled cleanup (or a
  Cloud Function) before any real usage.
- Microphone access is requested at runtime the first time you start or
  answer a call.

## Project structure

```
app/src/main/java/com/whatschat/app/
├── MainActivity.kt              Compose entry point
├── WhatsChatApp.kt               Application class
├── data/
│   ├── model/                    User, Chat, Message
│   ├── repository/               AuthRepository, UserRepository, ChatRepository
│   └── service/                  FCM token sync service
└── ui/
    ├── navigation/                Navigation Compose graph
    ├── theme/                     Material 3 theme
    ├── components/                Shared composables (Avatar)
    ├── viewmodel/                 AuthViewModel, ChatListViewModel, ChatViewModel, ProfileViewModel
    └── screens/                   auth/, chatlist/, chat/, profile/
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
  `lastMessageSenderId`. `chatId` is the two participant uids sorted and
  joined with `_`, so a conversation between the same two users always
  resolves to the same document.
- `chats/{chatId}/messages/{messageId}`: `senderId`, `text` or `imageUrl`,
  `type`, `timestamp`
- `calls/{callId}`: `callerId`, `calleeId`, `status` (`RINGING` / `ACCEPTED` /
  `DECLINED` / `ENDED`), `offerSdp`, `answerSdp`, `createdAt`
- `calls/{callId}/callerCandidates` and `.../calleeCandidates`: trickled ICE
  candidates (`sdpMid`, `sdpMLineIndex`, `candidate`)

## Suggested next steps

- Group chats (extend `participants` beyond 2, adjust the chat id scheme)
- Push notifications via a Cloud Function triggered on new messages
- End-to-end encryption
- Video calls (the WebRTC plumbing already supports adding a video track)
- A TURN server for voice calls on restrictive networks, and a foreground
  service so calls can ring outside the app
- Message delivery/read receipts, typing indicators
