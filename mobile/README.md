# SMS Fraud Detector Mobile App

Modern Android client for the AI-Based Spam/Fraud SMS Detector backend.

## What It Does

- Authenticates users against the backend API
- Analyzes SMS messages with the backend ML model
- Shows prediction history and stats
- Allows report submission for suspicious messages
- Supports admin dataset import, evaluation, and retraining
- Monitors incoming SMS messages when permissions are granted

## Tech Stack

- Kotlin
- Jetpack Compose
- MVVM
- Retrofit + OkHttp
- DataStore
- WorkManager

## Backend Connection

The app uses this default base URL for Android emulators:

- `http://10.0.2.2:8000/`

You can change the backend base URL in the app settings screen.

## Important Permissions

The app requests:

- `INTERNET`
- `RECEIVE_SMS`
- `READ_SMS`
- `POST_NOTIFICATIONS`

SMS permissions are needed for incoming-message monitoring.

## Main Screens

- Splash
- Login/Register
- Home dashboard
- SMS analysis
- History
- Reports
- Profile
- Settings
- Admin dashboard

## Running the App

1. Open the `mobile/` folder in Android Studio.
2. Sync Gradle.
3. Run on an emulator or device.
4. Make sure the backend is running before logging in or analyzing SMS messages.

## Build Notes

- The module is structured as a real Android app, not a CLI sample.
- The mobile app depends on the backend API being reachable.
- If you use a physical device, replace the default base URL with your machine IP address and backend port.

