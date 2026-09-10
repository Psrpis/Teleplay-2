# TelePlay v2.0.0 Release Notes

**TelePlay 2.0: Major Mobile Redesign & Feature Parity Release**

TelePlay 2.0 introduces a comprehensive overhaul of the Android mobile experience, delivering full parity with the Web media center through a modern, mobile-first Jetpack Compose interface.

---

### What's New in v2.0.0

#### 1. Mobile-First 4-Tab Navigation & Tablet Rail
* **Glassmorphic Bottom Navigation**: Streamlined 12+ separate menu items into 4 intuitive tabs: **Home**, **Search**, **Library**, and **More**.
* **Tablet Navigation Rail**: Automatic responsive side-rail layout when running on foldables and tablets (≥600dp).

#### 2. Cinematic Home Experience
* **Featured Hero Banner**: Prominently highlights in-progress or recently added media with high-resolution backdrops, genre badges, and instant "Resume / Play" and "Details" actions.
* **Smart Media Shelves**:
  * **Continue Watching**: Displays exact playback progress bars with one-tap instant resume.
  * **Recently Added**: Quick access to newly uploaded files.
  * **Favorites & Recently Watched**: Directly accessible from the home feed.
  * **Collections Shelf**: Curated media folders and user lists.
* **Deep-Link Navigation**: Every section header features a "See All" button that links directly into the relevant Library tab.

#### 3. Advanced Search & Faceted Filtering
* **Instant Faceted Filters**: Filter search results on the fly across **All**, **Movies**, **Series**, and **Actors**.
* **Recent Searches**: Tap-to-search history chips for quick re-queries.
* **Rich Result Cards**: Displays poster art, ratings, release years, and instant detail inspection.

#### 4. Unified Library Hub
* **Segmented Tabs**: Houses Continue Watching, Recently Added, Favorites, History, and Collections in a clean swipeable/tabbed view.
* **Playback History**: Detailed viewing timestamps with watched status toggles.
* **Collections Management**: Explore collections and playlist groupings.

#### 5. Extended Capabilities ("More" Section)
* **Movies & Series Explorer**: Browse television shows organized hierarchically by seasons and individual episodes.
* **Actors Directory**: Discover films and shows grouped by performer.
* **Viewing Statistics**: Visual metrics for total watch time (hours & minutes), movie and episode completion counts, and recent activity history.
* **Surprise Me**: Interactive random movie picker with preview synopsis and instant playback.
* **Settings & Account**: Easy server endpoint reconfiguration, connection test, and Telegram account authentication.

#### 6. Reusable Media Detail Sheet & Components
* **MediaDetailSheet**: Bottom sheet showing full backdrops, posters, synopsis overview, rating badges, cast chips, and instant favorite/watched toggles.
* **MediaPosterCard & MediaWideCard**: Polished cards with progress indicators and high-contrast typography.

---

### Artifacts & Build Information
* **Version Name**: `2.0.0`
* **Version Code**: `14`
* **Target SDK**: `34` (Android 14)
* **Min SDK**: `21` (Android 5.0+)
* **Universal Debug APK**: `app-universal-debug.apk` with SHA-256 checksum
