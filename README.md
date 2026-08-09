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

Not included yet: voice/video calls, end-to-end encryption, group chats,
status/stories, push notification delivery (the FCM token is stored per user,
but no Cloud Function sends notifications yet).

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

## Suggested next steps

- Group chats (extend `participants` beyond 2, adjust the chat id scheme)
- Push notifications via a Cloud Function triggered on new messages
- End-to-end encryption
- Voice/video calls (e.g. WebRTC)
- Message delivery/read receipts, typing indicators
