# TelePlay Navigation Redesign Plan

## Executive recommendation

TelePlay should not copy Nuvio screen-for-screen. It should adopt the same product principle: **navigation must be designed for the screen being used**. Nuvio positions its product as a library and playback experience that works across phone, desktop, TV, and smart-TV platforms, while keeping the interface adapted to each screen rather than stretching one layout everywhere [1]. TelePlay should apply that principle by replacing the current long desktop sidebar with a five-destination mobile bottom bar, a compact desktop navigation rail, and a D-pad-friendly TV rail.

The recommended top-level structure is:

| Bottom-bar destination | Role | Contains |
| --- | --- | --- |
| **Home** | Personalized starting point | Featured media, Continue Watching, Recently Added, Favorites, Recently Watched |
| **Discover** | Find new or indexed media | Movies, Series, Actors, global search, Surprise Me |
| **Library** | Manage owned media | My Files, Favorites, Collections, Tags, storage usage |
| **Activity** | Resume and review watching | Continue Watching, Watch History, Statistics |
| **Profile** | Account and application controls | Settings, playback preferences, logout, session management |

This keeps all primary destinations available in one persistent control while moving secondary sections into contextual tabs and menus. Material guidance recommends three to five top-level bottom-navigation destinations and advises against placing settings directly in the bottom bar [3].

## Comparison: current TelePlay and Nuvio-like navigation

| Dimension | Current TelePlay | Nuvio-inspired direction | Result for TelePlay |
| --- | --- | --- | --- |
| Primary navigation | Long left sidebar with many unrelated items | Small set of top-level destinations with screen-specific navigation | Reduce cognitive load and improve thumb reach |
| Information hierarchy | Home, Movies, Series, Actors, Favorites, files, history, collections, statistics, and settings share one level | Top-level destinations contain related secondary sections | Keep the bottom bar stable and move detail into tabs |
| Mobile behavior | Sidebar is adapted from desktop navigation | Mobile-first bottom navigation with persistent reachability | Better one-handed navigation |
| Desktop behavior | Full-width sidebar consumes persistent horizontal space | Compact rail with optional expansion | More room for artwork and media shelves |
| TV behavior | D-pad navigation can traverse a long list | Short, predictable left rail with focus states | Faster remote navigation and fewer focus stops |
| Media discovery | Series and Actors are separate concepts but currently sit alongside generic file operations | Discovery is a dedicated area | Make filename-derived indexing visible and understandable |
| Library management | My Files, Favorites, Collections, and Tags are distributed | Library is a coherent destination | Distinguish “watch something” from “manage files” |
| Watching state | Continue Watching and Watch History are separated in the global list | Activity groups playback-related state | Make resuming and reviewing behavior predictable |
| Account controls | Settings and logout sit at the bottom of the sidebar | Profile/settings are secondary to content navigation | Prevent settings from competing with content destinations |

Nuvio’s official product positioning also emphasizes profiles, cross-screen library state, progress synchronization, and playback as the center of the experience [1]. TelePlay already has user-scoped progress, favorites, history, and media-center endpoints, so the redesign can focus primarily on information architecture and responsive presentation rather than replacing the data model.

## Proposed information architecture

### Home

Home remains the default route and should contain only content that helps the user choose something quickly. It should not expose management controls directly. The existing hero and horizontal shelves are appropriate for this destination.

### Discover

Discover becomes the place for finding media. It should use a local tab bar beneath the page header:

| Tab | Content |
| --- | --- |
| **All** | Search results, filters, Surprise Me |
| **Movies** | `file_type=video` or movie metadata |
| **Series** | Series tags and episode groups |
| **Actors** | Actor tags and cross-series results |

The current `/series` and `/actors` pages should become Discover subroutes rather than global standalone destinations. Suggested routes are `/discover`, `/discover/movies`, `/discover/series`, and `/discover/actors`.

### Library

Library should contain media the user owns or has intentionally organized:

- **All Files**: the existing file browser.
- **Favorites**: saved media.
- **Collections**: user-created logical groups.
- **Tags**: quality, codec, custom, series, and actor tags.
- **Storage**: a compact usage panel rather than a full destination.

Suggested routes are `/library`, `/library/favorites`, `/library/collections`, and `/library/tags`.

### Activity

Activity should group all playback memory:

- **Continue Watching**: active progress records.
- **History**: completed or recorded viewing activity.
- **Statistics**: watched counts and total watch time.

Suggested routes are `/activity`, `/activity/history`, and `/activity/stats`.

### Profile

Profile should be deliberately low-frequency. It should contain settings, session controls, playback preferences, appearance preferences, and account information. It should not contain media discovery or library management.

## Responsive behavior

### Mobile web and mobile application

Use a fixed bottom bar with five equal-width destinations. Each item should have an icon and a short label. The active destination should use a filled or high-contrast icon, a violet active indicator, and a visible label. The bar must respect safe-area insets and should remain visible except during full-screen playback.

The bottom bar should not contain both tabs and nested navigation at the same level. A destination opens a page; that page may then expose a short contextual tab row. The global search field can remain in the top header, while **Surprise Me** can remain a compact header action or a button inside Discover.

### Desktop web

At widths above approximately 1024px, use a compact left rail rather than a full text sidebar. The rail should show the TelePlay logo, five primary icons, and a profile/settings affordance. On hover or via an explicit expand action, it may reveal labels. The main content should gain the horizontal space currently consumed by the 256px sidebar.

The expanded sidebar can remain available as an accessibility and power-user mode, but it should no longer be the default visual hierarchy.

### Android TV

Keep a left navigation rail rather than a bottom bar. A TV screen benefits from visible spatial persistence and D-pad focus movement. Limit the rail to Home, Discover, Library, Activity, and Profile. Use a strong focus ring, predictable focus order, and no hover-only states. The TV rail should open secondary destinations in a panel or row rather than forcing the user through a long list.

## Visual direction

The existing TelePlay 2.0 visual language should remain consistent:

- Near-black navy background.
- Violet primary accent.
- Translucent navigation surface with a light border.
- Rounded navigation container on mobile, compact rail on desktop, and focused rail on TV.
- One active state treatment across every platform.
- No more than one primary accent color in a navigation state.
- Labels should be short and stable: Home, Discover, Library, Activity, Profile.

The navigation should feel like a media product rather than an administration console. File operations such as rename, move, delete, and batch actions should remain available inside Library and context menus, not in the global navigation.

## Implementation plan

### Phase 1: Route and state preparation

Introduce an explicit navigation model in the web store:

```ts
type PrimaryDestination = 'home' | 'discover' | 'library' | 'activity' | 'profile';
```

Add route helpers so navigation state is derived from the URL rather than only from a local `activeSection` value. Preserve deep links and browser back/forward behavior. Add redirects from the current routes:

| Existing route | New canonical route |
| --- | --- |
| `/` | `/` |
| `/search` | `/discover` |
| `/series` | `/discover/series` |
| `/actors` | `/discover/actors` |
| `/favorites` | `/library/favorites` |
| `/collections` | `/library/collections` |
| `/history` | `/activity/history` |
| `/stats` | `/activity/stats` |
| `/settings` | `/profile/settings` |
| `/library` | `/library` |

### Phase 2: Navigation components

Replace the current Sidebar-only structure with three renderers that share one destination configuration:

- `MobileBottomBar` for mobile web.
- `DesktopNavigationRail` for desktop web.
- `TvNavigationRail` for Android TV and TV-oriented web layouts.

The destination configuration should define icon, label, route, accessibility label, and focus order once. This avoids route drift between platforms.

### Phase 3: Page-level secondary navigation

Add a reusable `SectionTabs` component for Discover, Library, and Activity. It should render only the tabs valid for the current primary destination. Tabs should not replace the bottom bar; they refine the selected destination.

### Phase 4: Responsive layout migration

Move the existing Home, Series, Actors, Favorites, Collections, History, Statistics, and Settings screens to the new route groups. Keep the existing API hooks and invalidate behavior. The backend does not need a breaking change for this phase.

### Phase 5: TV and Android parity

Use the already-added Android media-center API methods to create the same five-destination model in the TV/mobile navigation layer. Preserve the left rail for TV and use a bottom navigation bar for touch-oriented mobile screens. Nuvio’s official mobile repository uses Compose Multiplatform and separate platform targets, which supports the same screen-specific approach [2].

### Phase 6: Verification and rollout

Test the following before removing the old navigation:

| Test area | Acceptance criterion |
| --- | --- |
| Mobile reachability | All five primary destinations are reachable with one tap from every non-player screen. |
| Deep links | Existing bookmarked routes redirect without losing context. |
| Back behavior | Browser back and Android back return to the expected previous destination. |
| Keyboard access | Desktop users can tab through all navigation items and see a visible focus state. |
| TV focus | D-pad focus never becomes trapped in the rail or secondary tabs. |
| Playback | Full-screen player hides navigation without losing the current route. |
| Data loading | Switching destinations does not refetch unrelated shelves unnecessarily. |
| Responsive layout | No overlap with safe-area insets, mobile keyboards, or compact desktop widths. |

## Recommended first implementation slice

The safest first slice is a web-only navigation shell change:

1. Add `PrimaryDestination` and route helpers.
2. Add the mobile bottom bar with Home, Discover, Library, Activity, and Profile.
3. Keep the existing desktop sidebar behind a feature flag or breakpoint during validation.
4. Move Series and Actors under Discover.
5. Move Favorites and Collections under Library.
6. Move History and Statistics under Activity.
7. Run the web build, TypeScript check, lint, and a manual route smoke test.
8. Only then migrate the desktop rail and Android TV navigation.

This sequence reduces risk because it changes the navigation hierarchy before changing every platform’s visual component. It also preserves the existing media-center API, which is already organized around Home, search, favorites, history, collections, tags, statistics, and preferences.

## Decision summary

The recommended destination model is **five primary destinations**:

```text
Home     Discover     Library     Activity     Profile
```

The current long sidebar should become a responsive navigation system rather than being deleted outright. Mobile should use the bottom bar. Desktop should use a compact rail. TV should use a focused left rail. Series and Actors should be treated as discovery facets, while Favorites and Collections belong to Library, and History and Statistics belong to Activity.

## References

[1]: https://nuvio.tv/ "Nuvio official product page"
[2]: https://github.com/NuvioMedia/NuvioMobile "Nuvio official mobile repository"
[3]: https://m3.material.io/components/navigation-bar/overview "Material 3 navigation bar guidance"
