# 🎵 Musio

### A polished, offline-first music streaming experience built with Expo, React Native, and Expo Router.

<p align="center">
  <strong>Home · Explore · Library · Search · Mini Player · Now Playing · Lyrics · Up Next</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Expo-SDK%2056-000020?style=flat-square&logo=expo&logoColor=white" alt="Expo SDK 56" />
  <img src="https://img.shields.io/badge/React%20Native-Expo-61DAFB?style=flat-square&logo=react&logoColor=white" alt="React Native" />
  <img src="https://img.shields.io/badge/Expo%20Router-File--based%20Navigation-000020?style=flat-square&logo=expo&logoColor=white" alt="Expo Router" />
  <img src="https://img.shields.io/badge/JavaScript-ES6%2B-F7DF1E?style=flat-square&logo=javascript&logoColor=black" alt="JavaScript" />
  <img src="https://img.shields.io/badge/StyleSheet-No%20Tailwind%2FNativeWind-111111?style=flat-square" alt="StyleSheet" />
</p>

---

## 01 · Overview

**Musio** is a fully serverless music streaming app clone designed around a local mock-data architecture.

The application recreates the core interaction patterns of a modern music streaming experience:

* Home discovery
* Explore and category browsing
* Library
* Live search
* Persistent global mini-player
* Full-screen Now Playing experience
* Up Next queue
* Lyrics
* Related tracks
* Upgrade / Premium screen

There is **no backend or cloud service required** for the current implementation.

The entire experience is driven by a single local dataset:

```text
data/mockData.js
```

This makes Musio easy to run, inspect, customize, and extend without configuring APIs, databases, authentication, or external services.

---

## 02 · Product Experience

```text
                         MUSIO
                           │
          ┌────────────────┼────────────────┐
          │                │                │
        Discover         Search           Library
          │                │                │
     ┌────┴────┐           │          Saved content
     │         │           │
    Home    Explore   Live results
     │         │           │
     └────┬────┘           │
          │                │
          └────────┬───────┘
                   ▼
                Track
                   │
                   ▼
              Mini Player
                   │
                   ▼
             Now Playing
          ┌────────┼─────────┐
          │        │         │
       Up Next   Lyrics   Related
```

The goal is to keep navigation and playback feeling connected rather than treating every screen as an isolated experience.

---

## 03 · Core Features

### 🏠 Home

The Home tab provides the primary discovery experience with locally defined:

* Songs
* Albums
* Artists
* Playlists
* Charts
* Moods

---

### 🔎 Explore

Explore provides another entry point into the local music catalog and discovery-oriented content.

---

### 📚 Library

The Library acts as the user's personal music area within the mock application.

---

### 🔍 Live Search

Musio includes a dedicated search experience that works against the local mock dataset.

Search can be opened independently through the application navigation and provides live filtering of the available content.

---

### ▶️ Persistent Mini Player

Once a track is selected, playback state is available globally through:

```text
MusicPlayerContext
```

The MiniPlayer remains available while navigating between tabs and provides quick access to the current track and playback state.

---

### 🎧 Full-Screen Now Playing

The player expands into a dedicated full-screen experience containing:

* Track information
* Artwork
* Playback controls
* Progress
* Draggable seek position
* Up Next
* Lyrics
* Related tracks

---

### 📜 Up Next

The player maintains the concept of a playback queue.

When a track reaches the end, the mock playback system can automatically advance to the next track.

---

### 🎤 Lyrics

Track lyrics are supplied through the local dataset and displayed inside the Now Playing experience.

---

### 🔗 Related Tracks

Related content can be surfaced from the local dataset without requiring an external recommendation service.

---

### ⭐ Upgrade

The Upgrade tab provides a static Premium / Upgrade experience for the application UI.

---

## 04 · Playback Architecture

Musio uses a global React Context to keep playback state synchronized throughout the application.

```text
                 MusicPlayerContext
                         │
          ┌──────────────┼──────────────┐
          │              │              │
       Current        Playback       Progress
        Track           State         Position
          │              │              │
          └──────────────┼──────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
      MiniPlayer     Now Playing     Queue
          │              │              │
          └──────────────┼──────────────┘
                         │
                    Audio.Sound
                         │
                      expo-av
```

This keeps playback behavior centralized rather than duplicating player state across individual screens.

---

## 05 · Mock Playback System

The project intentionally works without bundled audio files.

Tracks currently use:

```js
audioUrl: null
```

When no playable audio source exists, `MusicPlayerContext` falls back to a **local timer-based playback simulation**.

This allows the interface to remain interactive without requiring external audio hosting.

### The simulated player supports

* Playback progress
* Progress bar updates
* Mini-player progress
* Full-screen seek interaction
* Play / pause state
* Queue progression
* Up Next auto-advance

```text
Track Selected
      │
      ▼
audioUrl available?
      │
   ┌──┴───┐
  YES     NO
   │       │
   ▼       ▼
expo-av   Local
Audio     Timer
   │       │
   └───┬───┘
       ▼
Playback State
       │
       ▼
Global UI
```

---

## 06 · Real Audio Support

The architecture also allows the mock dataset to be connected to actual audio.

A track can provide either:

```js
audioUrl: "https://example.com/audio.mp3"
```

or a local asset through `require()`.

The existing player context can then use `expo-av` to play the supplied audio source instead of relying on the timer fallback.

This means the UI can be developed and tested independently from the final audio source.

---

## 07 · Navigation Architecture

Musio uses **Expo Router** for file-based navigation.

```text
app/
│
├── _layout.js
│
├── player.js
│
├── search.js
│
└── (tabs)/
    ├── _layout.js
    ├── index.js
    ├── explore.js
    ├── library.js
    └── upgrade.js
```

### Root layout

```text
app/_layout.js
```

Responsible for the root navigation structure, including the tab application and player/search modal routes.

### Tab layout

```text
app/(tabs)/_layout.js
```

Provides:

* Bottom navigation
* Persistent MiniPlayer

### Player

```text
app/player.js
```

Provides the full-screen Now Playing experience.

### Search

```text
app/search.js
```

Provides the dedicated search experience.

---

## 08 · Component Architecture

Reusable UI components are separated from screens.

```text
components/
├── AlbumCard.js
├── MarqueeText.js
├── MiniPlayer.js
├── PillFilters.js
├── ProgressSlider.js
├── SectionHeader.js
└── TrackRow.js
```

### Component responsibilities

| Component        | Purpose                       |
| ---------------- | ----------------------------- |
| `AlbumCard`      | Album/content presentation    |
| `MarqueeText`    | Long text presentation        |
| `MiniPlayer`     | Persistent playback control   |
| `PillFilters`    | Filter/category chips         |
| `ProgressSlider` | Playback progress and seeking |
| `SectionHeader`  | Consistent section headings   |
| `TrackRow`       | Track/list presentation       |

The component layer keeps repeated UI patterns centralized and easier to maintain.

---

## 09 · Data Architecture

All mock content is centralized inside:

```text
data/mockData.js
```

The dataset contains:

```text
Songs
Albums
Artists
Playlists
Charts
Moods
Lyrics
Recent Searches
```

The current architecture intentionally avoids:

```text
Backend API
Database
Authentication
Cloud storage
Remote music catalog
```

### Current data flow

```text
mockData.js
     │
     ▼
Screens / Components
     │
     ▼
User Interaction
     │
     ▼
MusicPlayerContext
     │
     ▼
Playback UI
```

---

## 10 · Project Structure

```text
musio/
│
├── app.json
├── babel.config.js
├── package.json
│
├── app/
│   ├── _layout.js
│   ├── player.js
│   ├── search.js
│   │
│   └── (tabs)/
│       ├── _layout.js
│       ├── index.js
│       ├── explore.js
│       ├── library.js
│       └── upgrade.js
│
├── components/
│   ├── AlbumCard.js
│   ├── MarqueeText.js
│   ├── MiniPlayer.js
│   ├── PillFilters.js
│   ├── ProgressSlider.js
│   ├── SectionHeader.js
│   └── TrackRow.js
│
├── context/
│   └── MusicPlayerContext.js
│
├── constants/
│   └── theme.js
│
└── data/
    └── mockData.js
```

---

## 11 · Technology Stack

| Layer      | Technology                       |
| ---------- | -------------------------------- |
| Framework  | React Native                     |
| Runtime    | Expo SDK 56                      |
| Navigation | Expo Router                      |
| Language   | JavaScript                       |
| Styling    | React Native StyleSheet          |
| Icons      | `@expo/vector-icons`             |
| Audio      | `expo-av`                        |
| Gestures   | `react-native-gesture-handler`   |
| Animations | `react-native-reanimated`        |
| Safe Areas | `react-native-safe-area-context` |
| Screens    | `react-native-screens`           |
| Data       | Local JavaScript dataset         |

### Styling philosophy

Musio intentionally uses:

```text
React Native StyleSheet
```

and does **not** use:

```text
NativeWind
Tailwind CSS
```

---

## 12 · Visual Design

The interface follows a clean music-focused visual language based on the supplied reference design.

### Design characteristics

* White background
* Bold black typography
* Colorful square artwork
* Rounded/pill filter controls
* Strong visual hierarchy
* Compact music rows
* Circular black playback control
* Full-width draggable progress interaction

### Player interaction

```text
Artwork
   │
Track / Artist
   │
   ▼
──────────────●────────────
        Progress
   │
   ▼
Playback Controls
   │
   ├── Previous
   ├── Play / Pause
   └── Next
```

The full-screen player uses a draggable progress scrubber to make seeking feel like a real music player.

---

## 13 · Theme System

Visual tokens are centralized in:

```text
constants/theme.js
```

The theme contains reusable values for:

* Colors
* Spacing
* Border radii
* Typography scale

Centralizing these values makes it easier to maintain visual consistency across the application.

---

## 14 · Installation

### Requirements

Make sure Node.js and the Expo development environment are available before starting.

Create the project:

```bash
npx create-expo-app@latest musio --template blank
cd musio
```

Copy the project files into the new project root.

Musio uses Expo Router, so the application entry is:

```text
app/_layout.js
```

rather than a manually registered `App.js`.

---

## 15 · Install Dependencies

Install the Expo dependencies:

```bash
npx expo install expo-router expo-av expo-status-bar expo-constants expo-linking expo-splash-screen
```

Install navigation and animation dependencies:

```bash
npx expo install react-native-gesture-handler react-native-reanimated react-native-safe-area-context react-native-screens
```

Install icons:

```bash
npm install @expo/vector-icons
```

Then allow Expo to resolve package versions compatible with SDK 56:

```bash
npx expo install --fix
```

---

## 16 · Babel Configuration

Confirm that:

```text
babel.config.js
```

uses the provided Expo Babel configuration and that the Reanimated plugin is registered as required by the project.

The Reanimated plugin should remain the final plugin entry.

---

## 17 · Package Entry Point

The project's `package.json` should use:

```json
{
  "main": "expo-router/entry"
}
```

No manual `App.js` registration is required.

---

## 18 · Run Musio

Start the Expo development server:

```bash
npx expo start
```

Then launch the application using the available Expo development target.

---

## 19 · Application Flow

The primary navigation experience can be summarized as:

```text
                     ┌───────────┐
                     │   HOME    │
                     └─────┬─────┘
                           │
             ┌─────────────┼─────────────┐
             ▼             ▼             ▼
         Explore        Search        Library
             │             │             │
             └─────────────┼─────────────┘
                           ▼
                        Track
                           │
                           ▼
                     Mini Player
                           │
                           ▼
                    Now Playing
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
           Up Next       Lyrics      Related
```

---

## 20 · Current Architecture

Musio is deliberately **serverless and local-data driven**.

```text
┌─────────────────────────────────────────┐
│                  MUSIO                  │
├─────────────────────────────────────────┤
│                                         │
│  Expo Router                            │
│       │                                 │
│       ▼                                 │
│  React Native Screens                   │
│       │                                 │
│       ├───────────────┐                 │
│       ▼               ▼                 │
│  mockData.js    MusicPlayerContext      │
│                       │                 │
│                       ▼                 │
│                   expo-av               │
│                       │                 │
│                 Audio / Timer           │
│                                         │
└─────────────────────────────────────────┘
```

There is currently no dependency on:

* REST APIs
* Backend servers
* Databases
* Authentication services
* Cloud music services

---

## 21 · Why This Architecture?

The local-first mock architecture provides several useful development advantages:

### Fast development

No backend setup is required before working on the interface.

### Deterministic data

The same local dataset is available every time the application runs.

### Easy UI experimentation

Songs, albums, artists, charts, moods, lyrics, and searches can be modified directly in:

```text
data/mockData.js
```

### Independent playback UI

The timer fallback allows the player interface to be developed even when actual audio files are unavailable.

### Clear separation

Navigation, UI components, playback state, theme tokens, and data remain separated into dedicated areas.

---

## 22 · Current Scope

### Implemented

* [x] Expo SDK 56 application
* [x] Expo Router navigation
* [x] Home tab
* [x] Explore tab
* [x] Library tab
* [x] Upgrade tab
* [x] Search experience
* [x] Global MiniPlayer
* [x] Full-screen Now Playing
* [x] Playback progress
* [x] Seek interaction
* [x] Up Next
* [x] Lyrics
* [x] Related tracks
* [x] Local mock dataset
* [x] `expo-av` integration
* [x] Timer-based playback fallback
* [x] Centralized theme
* [x] Reusable UI components

### Not part of the current implementation

* [ ] Backend music service
* [ ] User authentication
* [ ] Cloud database
* [ ] Real streaming catalog
* [ ] Cloud synchronization
* [ ] Production subscription system

---

## 23 · Future Extension Path

The current architecture leaves room for replacing the mock layer with a real music service.

```text
CURRENT
────────────────────────────────────

mockData.js
     │
     ▼
React Native UI
     │
     ▼
MusicPlayerContext
     │
     ▼
expo-av / Timer


POTENTIAL FUTURE
────────────────────────────────────

Music API / Backend
        │
        ▼
   Data Service
        │
        ▼
React Native UI
        │
        ▼
MusicPlayerContext
        │
        ▼
Real Audio Source
```

The existing separation between data, UI, navigation, and playback state provides a clear place to introduce a remote data layer later.

---

## 24 · Engineering Focus

Musio is primarily an exploration of:

```text
React Native UI Architecture
        +
Expo Router Navigation
        +
Global Playback State
        +
Reusable Components
        +
Local Data Modeling
        +
Interactive Player UX
```

Rather than depending on a backend to make the application feel complete, the project focuses on making the **frontend interaction model functional and cohesive on its own**.

---

## 25 · What This Project Demonstrates

Musio demonstrates practical experience with:

* React Native application structure
* Expo SDK development
* File-based navigation
* React Context
* Global application state
* Audio playback integration
* Playback simulation
* Interactive progress controls
* Reusable component architecture
* Local data modeling
* Responsive music-oriented UI
* Modal-based application flows
* Separation of UI, state, data, and configuration

---

## 26 · Project Philosophy

> **Build the experience first. Connect the services later.**

Musio deliberately starts with a complete local experience rather than introducing backend complexity before it is needed.

The result is a lightweight application where the major product interactions can be developed, tested, and demonstrated independently.

---

## 27 · Final Architecture

```text
                    ┌───────────────────┐
                    │      MUSIO        │
                    │ Music App Clone   │
                    └─────────┬─────────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
                ▼             ▼             ▼
             Home          Explore       Library
                │             │             │
                └─────────────┼─────────────┘
                              │
                         Live Search
                              │
                              ▼
                         Track Data
                              │
                              ▼
                  MusicPlayerContext
                              │
                  ┌───────────┴───────────┐
                  │                       │
                  ▼                       ▼
             expo-av                Timer Fallback
                  │                       │
                  └───────────┬───────────┘
                              ▼
                       Playback State
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
               MiniPlayer  Now Playing  Queue
                              │
                     ┌────────┼────────┐
                     ▼        ▼        ▼
                  Up Next   Lyrics   Related
```

---

## 🚀 Musio

**A local-first music streaming experience built for exploration, interaction, and future extensibility.**

```text
Discover → Search → Play → Explore → Listen
```

---

### License

This repository is intended as a development and UI/architecture project. Add the license appropriate for your repository before publishing it as an open-source project.
