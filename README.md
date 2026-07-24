# AI-Based Spam/Fraud SMS Detector

An end-to-end SMS fraud detection platform with a backend API and a modern Android client.

## Project Overview

This project helps users detect spam and fraudulent SMS messages before they trust them. The system classifies a message as legitimate, spam, or fraud using a trained machine learning pipeline on the backend and a mobile app for real-time use.

## Repository Structure

- `be/` Backend API, ML pipeline, training, evaluation, and admin endpoints
- `mobile/` Android mobile app built with Jetpack Compose

## Core Features

- SMS message classification
- Spam and fraud detection
- ML model training and evaluation
- Analysis history and statistics
- Admin retraining and dataset import
- Mobile dashboard, reports, profile, and settings

## Backend

The backend exposes REST endpoints for:

- Authentication
- SMS analysis
- Analysis history and dashboard stats
- Report creation and review
- Admin retraining
- Dataset import
- Model evaluation

See the backend README inside `be/` for setup and API details.

## Mobile App

The Android app is located in `mobile/` and connects to the backend by default at:

- `http://10.0.2.2:8000/`

This is the correct localhost address for the Android emulator.
The app reads its API base URL from the mobile settings and `BuildConfig.DEFAULT_BASE_URL`; it does not use the backend `FRONTEND_URL` values.

## Getting Started

1. Set up and run the backend from `be/`.
2. Open `mobile/` in Android Studio.
3. Make sure an Android SDK is installed.
4. Run the app on an emulator or device.

## Notes

- SMS permissions are required for receiver-based monitoring.
- The app uses the backend API for login, analysis, history, reports, and admin features.
- For production, update the backend base URL in the app settings.
- Backend `FRONTEND_URL` and `FRONTEND_LOGIN_URL` are only for emailed auth links, not for the Android API connection.
