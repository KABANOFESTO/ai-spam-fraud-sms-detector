# SMS Fraud Detector Mobile App

Modern Android client for the AI-Based Spam/Fraud SMS Detector backend.

## What It Does

- Authenticates users against the backend API
- Analyzes SMS messages with the backend ML model
- Shows prediction history and stats
- Exports live report data to PDF from the Reports screen
- Uses separate report views for regular users and admins
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

The app now uses this default backend base URL:

- `https://ai-spam-fraud-sms-detector.onrender.com/`

You can change the backend base URL in the app settings screen.

## Important Permissions

For automatic SMS tracking, the app requests:

- `INTERNET`
- `RECEIVE_SMS`
- `POST_NOTIFICATIONS`

Automatic tracking only works after the app is set as the default SMS handler on the device. That is the supported flow for real-time incoming-message monitoring.
If the user keeps the app in normal mode, SMS analysis still works by manually entering or sharing a message into the app.

## Reports And PDF Export

The Reports screen is export-first and reads live data from the backend.

- Regular users see their own report history and export a personal PDF
- Admins see the full report center with live dashboard totals and review status
- The exported PDF is generated inside the app and then shared through the Android share sheet
- The document is a real dynamic export, not a manually typed report
- If no reports exist yet, the screen shows a clean empty state instead of a fake sample document

The PDF export uses a secure `FileProvider`, so you can send the file to WhatsApp, email, Drive, or any other app that accepts PDFs.

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
- If you need to test against a different backend, change the base URL in the app settings screen.
- Admin and user dashboards intentionally show different data and actions based on the current authenticated role.
