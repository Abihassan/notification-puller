# Notification Puller

A privacy-focused Android notification history and management application built with **React Native, TypeScript, Kotlin, Android NotificationListenerService, and Room**.

Notification Puller captures Android notifications locally, stores them on the device, provides a searchable notification history, supports application-level filtering, tracks notification lifecycle events, and allows users to export their notification history as **CSV, Excel (`.xlsx`), or JSON**.

The application is designed as a **local-first / offline-first application**.

> **No backend is required. No Python, FastAPI, PostgreSQL, Firebase, or cloud database is required.**

---

## Table of Contents

* [Overview](#overview)
* [Features](#features)
* [Architecture](#architecture)
* [Technology Stack](#technology-stack)
* [Project Structure](#project-structure)
* [Notification Flow](#notification-flow)
* [Database Architecture](#database-architecture)
* [Notification Lifecycle](#notification-lifecycle)
* [App Filtering](#app-filtering)
* [Persistence](#persistence)
* [Search and Pagination](#search-and-pagination)
* [Export System](#export-system)
* [Background Behavior](#background-behavior)
* [Installation](#installation)
* [Development Setup](#development-setup)
* [Running the Application](#running-the-application)
* [Building the APK](#building-the-apk)
* [Granting Notification Access](#granting-notification-access)
* [Testing](#testing)
* [Troubleshooting](#troubleshooting)
* [Privacy](#privacy)
* [Security Considerations](#security-considerations)
* [Future Improvements](#future-improvements)
* [Contributing](#contributing)
* [License](#license)

---

# Overview

Notification Puller is an Android application that acts as a **local notification archive**.

Android applications generate notifications continuously. Once a notification disappears from the notification shade, the user normally has limited access to its historical content.

Notification Puller uses Android's `NotificationListenerService` to observe notifications and stores relevant information locally.

The application provides a user interface for:

* Viewing notification history
* Searching notifications
* Filtering notifications
* Filtering by application
* Viewing notification details
* Tracking notification state
* Marking notifications as read
* Loading notification history using pagination
* Pulling to refresh
* Exporting notification data
* Monitoring notification listener status

---

# Features

## Notification Collection

The native Android notification listener captures notifications posted by other applications.

Examples include:

* WhatsApp
* Gmail
* Instagram
* YouTube
* SMS applications
* Banking applications
* System applications
* Other applications that generate Android notifications

The application does not require the source application to integrate with Notification Puller.

---

## Notification Persistence

Notifications are stored locally using **Android Room Database**.

The data remains available after:

* React Native UI restart
* Application UI closure
* Application reopening
* Device restart, subject to Android notification-listener behavior

---

## Notification Lifecycle

The application tracks notification lifecycle events including:

### Posted

A new notification is detected.

```text
Android
   ↓
NotificationListenerService
   ↓
POSTED
   ↓
Room Database
```

### Updated

An existing notification is updated or reposted.

```text
Existing notification
        ↓
Content changed
        ↓
UPDATED
```

### Removed

A notification disappears from Android's notification system.

The notification record can remain in the local database while its state changes to removed.

This allows the application to maintain historical records.

---

# Application Filtering

Notification Puller supports application-level filtering.

Users can enable or disable notification collection for individual applications.

Example:

```text
WhatsApp       ON
Instagram      ON
Gmail          OFF
YouTube        ON
Banking App    ON
```

When an application is disabled, new notifications from that application are ignored.

Existing notification history is not automatically deleted.

---

# Read / Unread State

New notifications can be stored as unread.

Example:

```text
New notification
        ↓
isRead = false
```

When the user opens or marks the notification as read:

```text
isRead = true
```

The application can therefore maintain an unread notification count independently from Android's notification shade.

---

# Search

Notification history can be searched using notification content.

Searchable fields include:

* Application name
* Package name
* Notification title
* Notification text
* Notification subtext

The search is performed against the local Room database.

This avoids unnecessarily loading the complete notification history into React Native.

---

# Pagination

Notification history uses pagination instead of loading the entire database at once.

Conceptually:

```text
Initial request
       ↓
50 notifications
       ↓
User reaches bottom
       ↓
Load next page
       ↓
50 more notifications
```

This allows the application to handle large notification histories more efficiently.

---

# Pull to Refresh

The notification history supports pull-to-refresh.

```text
Pull down
   ↓
Refresh
   ↓
Load latest notifications
```

Pagination is reset when the history is refreshed.

---

# Notification Details

A notification can be opened to view its detailed information.

Depending on the notification, information can include:

* Application name
* Package name
* Title
* Message
* Subtext
* Category
* Posted timestamp
* Updated timestamp
* Removed timestamp
* Read state
* Active state
* Ongoing state
* Group information
* Notification key
* Lifecycle status

---

# Dashboard

The application UI provides a dashboard for viewing notification statistics and service information.

Typical information includes:

* Total notification count
* Unread notification count
* Today's notifications
* Number of applications
* Notification listener state
* Service health
* Battery optimization information

---

# Export System

Notification Puller supports local export.

Supported formats:

* CSV
* Excel `.xlsx`
* JSON

---

## CSV

CSV exports are useful for:

* Excel
* Google Sheets
* Data analysis
* Archiving
* Importing into other applications

Example columns:

```text
Date
Time
App Name
Package
Title
Message
Sub Text
Category
Status
Active
Read
Ongoing
Group Key
Posted At
Updated At
Removed At
Notification Key
```

---

## Excel

The application can generate an actual `.xlsx` workbook.

The generated file can be opened using:

* Microsoft Excel
* Google Sheets
* LibreOffice Calc
* Other compatible spreadsheet applications

---

## JSON

JSON exports preserve structured notification information.

Example structure:

```json
{
  "id": 1,
  "notificationKey": "example-key",
  "appName": "Example App",
  "packageName": "com.example.app",
  "title": "Example notification",
  "text": "Notification message",
  "isRead": false,
  "isActive": true,
  "status": "posted"
}
```

---

# Export and Privacy

Export files are generated locally on the Android device.

The application does not automatically upload exported data to a server.

Android's native share/save functionality can be used to send or store an export.

---

# Architecture

The application uses a layered architecture.

```text
┌─────────────────────────────────────────────┐
│              React Native UI                │
│                                             │
│ App.tsx                                     │
│ Search / Filters / Dashboard / History      │
└──────────────────────┬──────────────────────┘
                       │
                       │ React Native Bridge
                       ▼
┌─────────────────────────────────────────────┐
│              Native Kotlin                  │
│                                             │
│ NotificationModule                          │
└──────────────────────┬──────────────────────┘
                       │
             ┌─────────┴─────────┐
             │                   │
             ▼                   ▼
      Notification          App Filtering
       Repository              / System
             │
             ▼
       NotificationDao
             │
             ▼
       Room Database
             │
             ▼
       SQLite Storage
```

---

# Notification Collection Architecture

```text
Android Application
        │
        │ posts notification
        ▼
Android Notification System
        │
        ▼
NotificationListenerService
        │
        ▼
Application Filter
        │
        ▼
Lifecycle Processing
        │
        ├── Posted
        ├── Updated
        └── Removed
        │
        ▼
Room Database
        │
        ▼
React Native UI
```

---

# Technology Stack

## Frontend

* React Native
* TypeScript
* React Native built-in UI components

## Android

* Kotlin
* Android SDK
* `NotificationListenerService`
* Android Broadcast Receiver
* Android FileProvider

## Database

* Room
* SQLite

## Room Code Generation

* Kotlin Symbol Processing (KSP)
* Room Compiler

## Build System

* Gradle
* Android Gradle Plugin

---

# Project Structure

```text
notification-puller/
│
├── App.tsx
├── package.json
│
├── src/
│   └── NotificationListener.ts
│
└── android/
    │
    ├── build.gradle
    ├── gradle.properties
    ├── settings.gradle
    ├── gradlew
    ├── gradlew.bat
    │
    ├── gradle/
    │   └── wrapper/
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    │
    └── app/
        │
        ├── build.gradle
        ├── proguard-rules.pro
        │
        └── src/
            └── main/
                │
                ├── AndroidManifest.xml
                │
                ├── java/
                │   └── com/
                │       └── notificationpuller/
                │           │
                │           ├── MainActivity.kt
                │           ├── MainApplication.kt
                │           │
                │           ├── bridge/
                │           │   ├── NotificationModule.kt
                │           │   └── NotificationPackage.kt
                │           │
                │           ├── database/
                │           │   ├── NotificationEntity.kt
                │           │   ├── NotificationDao.kt
                │           │   ├── NotificationDatabase.kt
                │           │   └── NotificationRepository.kt
                │           │
                │           ├── notification/
                │           │   └── PullerNotificationListenerService.kt
                │           │
                │           ├── filter/
                │           │   ├── NotificationFilterStore.kt
                │           │   └── NotificationAppInfo.kt
                │           │
                │           ├── system/
                │           │   ├── NotificationServiceHealth.kt
                │           │   ├── NotificationServiceStateStore.kt
                │           │   └── NotificationListenerBootReceiver.kt
                │           │
                │           └── export/
                │               └── NotificationExportManager.kt
                │
                └── res/
                    ├── drawable/
                    ├── mipmap-mdpi/
                    ├── mipmap-hdpi/
                    ├── mipmap-xhdpi/
                    ├── mipmap-xxhdpi/
                    ├── mipmap-xxxhdpi/
                    ├── values/
                    └── xml/
                        └── file_paths.xml
```

---

# Database Architecture

The application uses Room instead of a backend database.

```text
NotificationDatabase
        │
        ▼
NotificationDao
        │
        ▼
NotificationRepository
        │
        ▼
NotificationModule
        │
        ▼
React Native
```

---

## Notification Entity

The notification entity stores information such as:

```text
id
notificationKey
packageName
appName
title
text
subText
category
timestamp
createdAt
updatedAt
removedAt
isActive
isRead
isOngoing
groupKey
isGroupSummary
status
```

---

# Native Database API

The native bridge provides operations including:

```text
getNotifications()
getNotification()
getCount()

saveNotification()
updateNotification()

deleteNotification()
clearAll()
```

Pagination is supported by the notification retrieval layer.

---

# Background Behavior

The notification listener is implemented natively using:

```text
NotificationListenerService
```

This is intentionally independent of the React Native UI.

Therefore:

```text
React Native UI closed
        │
        ▼
Android NotificationListenerService
        │
        ▼
Room Database
```

The listener can continue collecting notifications according to Android's notification-listener and device/OEM behavior.

---

# Boot / Restart Behavior

The application includes Android system handling for listener state and boot/restart behavior.

After device restart, Android controls when the notification listener becomes available again.

The application can monitor listener connection state and display its current health to the user.

---

# Battery Optimization

Android manufacturers can impose aggressive background restrictions.

This can affect notification-listener reliability on some devices.

Users should check the application's battery settings if notification collection stops unexpectedly.

This is particularly relevant on devices with aggressive OEM power management.

---

# Installation

## Requirements

Recommended development environment:

* Windows / macOS / Linux
* Node.js LTS
* Java/JDK compatible with the project
* Android Studio
* Android SDK
* Android SDK Platform 36
* Android SDK Build Tools
* Android Platform Tools
* Android device or emulator

A physical Android device is recommended for testing notification-listener behavior.

---

# Clone the Repository

```bash
git clone https://github.com/Abihassan/notification-puller.git
cd notification-puller
```

---

# Install JavaScript Dependencies

```bash
npm install
```

---

# Start Metro

```bash
npm start
```

Keep Metro running.

Open another terminal for Android commands.

---

# Run Android

```bash
npx react-native run-android
```

Alternatively, build the APK directly using Gradle:

```bash
cd android
```

Windows:

```powershell
.\gradlew.bat app:assembleDebug
```

Linux/macOS:

```bash
./gradlew app:assembleDebug
```

---

# APK Location

After a successful debug build:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

---

# Install APK Using ADB

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

# Grant Notification Access

Notification Puller requires Android Notification Access.

After installing the application:

```text
Android Settings
        ↓
Notifications
        ↓
Notification Access
        ↓
Notification Puller
        ↓
Enable
```

The exact location may vary depending on the Android manufacturer.

Without Notification Access, Android will not deliver notifications to the application's `NotificationListenerService`.

---

# Testing

## Basic Notification Test

1. Install the application.
2. Grant Notification Access.
3. Open Notification Puller.
4. Send a notification from another application.
5. Open Notification Puller.
6. Verify that the notification appears.

---

## Persistence Test

1. Receive several notifications.
2. Close the React Native UI.
3. Reopen the application.
4. Verify that notification history remains available.

---

## Filtering Test

1. Open the application filter screen.
2. Disable an application.
3. Send a new notification from that application.
4. Verify that it is not stored.
5. Enable the application again.
6. Verify that new notifications are collected.

---

## Search Test

Search for:

```text
Application name
Notification title
Notification text
Package name
```

Verify that matching records are returned.

---

## Lifecycle Test

Test:

```text
POSTED
UPDATED
REMOVED
```

and verify that the corresponding database state is maintained.

---

## Export Test

Test:

```text
CSV
XLSX
JSON
```

Open the generated files using appropriate applications and verify their contents.

---

# Troubleshooting

## `NotificationDatabase_Impl does not exist`

If the application crashes with:

```text
Cannot find implementation for
com.notificationpuller.database.NotificationDatabase
```

Room's generated implementation was not created.

Verify that Room KSP is configured.

The project should contain:

```gradle
plugins {
    id "com.google.devtools.ksp" version "..."
}
```

and:

```gradle
ksp("androidx.room:room-compiler:...")
```

Then clean and rebuild:

```powershell
cd android
.\gradlew.bat clean
.\gradlew.bat app:assembleDebug
```

---

## `Unresolved reference STATUS_POSTED`

Make sure status constants are referenced through `NotificationEntity`:

```kotlin
NotificationEntity.STATUS_POSTED
```

rather than:

```kotlin
STATUS_POSTED
```

---

## Notification Access is disabled

The application cannot receive Android notifications until Notification Access has been granted.

Open Android Settings and enable Notification Puller under Notification Access.

---

## Notifications stop arriving

Check:

1. Notification Access
2. Battery optimization
3. Background restrictions
4. Application-specific OEM power management
5. Listener connection state
6. Device restart behavior

---

## Gradle build problems

Clean the Android build:

```powershell
cd android
.\gradlew.bat clean
```

Then rebuild:

```powershell
.\gradlew.bat app:assembleDebug
```

---

# Privacy

Notification Puller is designed as a local-first application.

Notification data is sensitive because notifications can contain:

* Personal messages
* OTPs
* Banking information
* Emails
* Account information
* Private conversations
* Application content

The application therefore stores notification history locally.

There is no required:

* Backend server
* FastAPI server
* PostgreSQL database
* Firebase database
* Cloud synchronization service

---

# Security Considerations

Because Notification Puller can access notification content, users should only install and run the application on trusted devices.

Exported notification files may contain sensitive information.

Users should treat exported:

```text
.csv
.xlsx
.json
```

files as sensitive data.

Do not upload notification exports to public repositories.

---

# What the Application Does Not Do

Notification Puller does not intentionally:

* Send notification content to a backend
* Upload notification history automatically
* Require an account
* Require authentication
* Require a cloud database
* Require Python
* Require FastAPI
* Require PostgreSQL

The application is designed around local Android storage.

---

# Development Roadmap

## Core Native Storage

* [x] `getNotifications()`
* [x] `getNotification()`
* [x] `getCount()`
* [x] `saveNotification()`
* [x] `updateNotification()`
* [x] `deleteNotification()`
* [x] `clearAll()`
* [x] Pagination

## Persistent UI

* [x] Persistent notification history
* [x] Pagination
* [x] Pull-to-refresh
* [x] Notification cards

## Application Filtering

* [x] Application filtering
* [x] Individual application enable/disable
* [x] System notification handling

## Notification Lifecycle

* [x] Posted
* [x] Updated
* [x] Removed
* [x] Existing notification detection
* [x] Duplicate handling
* [x] Group information

## Production Behavior

* [x] Native notification listener
* [x] Listener state
* [x] Boot/restart handling
* [x] Battery optimization status
* [x] Background collection architecture

## UI

* [x] Dashboard
* [x] Notification history
* [x] Search
* [x] Filters
* [x] Read/unread
* [x] Date filtering
* [x] Application filtering
* [x] Notification details
* [x] Service health

## Export

* [x] CSV
* [x] XLSX
* [x] JSON
* [x] Android share/save integration

---

# Future Improvements

Possible future versions may include:

* [ ] Material 3 visual redesign
* [ ] Advanced notification grouping
* [ ] Notification statistics
* [ ] Charts and analytics
* [ ] Storage usage dashboard
* [ ] Export scheduling
* [ ] Automatic export backup
* [ ] Import notification archives
* [ ] More advanced notification deduplication
* [ ] Better OEM-specific background handling
* [ ] Unit tests
* [ ] Instrumentation tests
* [ ] Automated CI builds
* [ ] Release APK/AAB pipeline
* [ ] GitHub Actions
* [ ] Play Store release preparation

Cloud synchronization is intentionally **not part of the current architecture**.

---

# Contributing

Contributions are welcome.

Before submitting a pull request:

1. Create a feature branch.
2. Keep changes focused.
3. Test Android behavior on a physical device when modifying notification functionality.
4. Verify Room migrations when changing database entities.
5. Verify export compatibility when modifying export functionality.
6. Ensure no sensitive notification data is committed to the repository.

Example:

```bash
git checkout -b feature/my-feature
```

Make your changes, test them, then:

```bash
git add .
git commit -m "feat: add my feature"
git push origin feature/my-feature
```

---

# License

Add the project's chosen open-source license before publishing the repository for general reuse.

For example:

* MIT License
* Apache License 2.0
* GPL-3.0

---

# Author

**Abihassan K.**

GitHub:

https://github.com/Abihassan

Project:

https://github.com/Abihassan/notification-puller

---

# Project Status

**Development Status: Active**

Notification Puller currently provides a complete local notification collection, persistence, management, filtering, lifecycle tracking, and export architecture.

The next stage should focus primarily on:

* Real-device testing
* OEM compatibility
* Performance testing
* Room migration testing
* Notification edge cases
* Security review
* Automated tests
* Release build configuration
* CI/CD
* Production hardening
