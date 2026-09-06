# TelePlay Media Center

TelePlay now keeps its original Telegram-backed file browser and streaming path while adding a media-center layer around the private library. The new layer is user-scoped by the existing authenticated Telegram user and does not introduce external playback providers, torrents, scrapers, or unauthorized content sources.

## Implemented capabilities

The backend exposes favorites, completed watched state, current watch progress, viewing history, collections, reusable tags, cached descriptive metadata, global search, statistics, random selection, and user preferences. The React web app presents these capabilities through a cinematic Home dashboard, carousels for Continue Watching/Favorites/Recently Added/Recently Watched, dedicated Favorites, History, Collections, Statistics, Search, and Settings pages, plus the existing file browser at `/library`.

The Home dashboard is intentionally built from independently consumable sections. Empty sections are omitted, artwork falls back to the user’s Telegram thumbnail, and media without metadata remains fully playable. Favorite and watched-state controls are available from dashboard cards and the hero area. Progress state is represented as **Unwatched**, **In Progress**, or **Watched**.

## Filename-based indexing

Uploads are automatically indexed from common filename conventions. For example, a filename such as `The.Show.S02E05.oyuncuA.And.oyuncuB.1080p.HEVC.mkv` produces separate searchable facets for the **The Show** series, season 2 episode 5, actors **A** and **B**, 1080p quality, and HEVC codec. The original Telegram filename is never rewritten. Existing libraries can be backfilled with `POST /api/media/auto-tag`, or by using **Tag library now** on the Series or Actors page.

Series and actors are deliberately stored as separate namespaced tags (`series:...`, `actor:...`) so the same actor can be found across unrelated series and films. The web navigation exposes dedicated **Series** and **Actors** pages; selecting a tag immediately filters all matching files, regardless of their physical folder.

Android clients have additive Retrofit models and repository/API methods for the Home response, favorites, watched state, and media search. Existing TV and mobile navigation remain intact; the new endpoints are available for a first-class TV/mobile screen rollout without changing the existing streaming contract.

## Database behavior

The repository did not contain an Alembic environment or versioned migrations at the time of the TelePlay 2.0 work. The existing application initializes schemas with `Base.metadata.create_all` during startup. The new tables are therefore additive and use the same SQLAlchemy metadata path:

| Table | Purpose |
| --- | --- |
| `favorites` | User/file favorite relation |
| `watch_history` | Immutable viewing activity separate from current progress |
| `collections` | User-created logical collections |
| `collection_items` | Many-to-many collection membership and order |
| `tags` | Reusable user-scoped tags |
| `file_tags` | Many-to-many file/tag membership |
| `media_metadata` | Cached title, overview, artwork, rating, cast, genre, and episode information |
| `user_preferences` | User-scoped preference key/value storage |

Existing `users`, `files`, `folders`, `watch_progress`, and `login_codes` tables are preserved. Startup creation only adds missing tables; it does not delete or rewrite existing rows. Production deployments should still take a database backup before upgrading. If the project later adopts Alembic, the additive table definitions can be captured in a generated revision before disabling `create_all`.

## API additions

All endpoints below are under `/api/media` and require the existing bearer authentication:

- `GET /home`
- `GET /search`
- `GET /favorites`
- `POST`/`DELETE /files/{file_id}/favorite`
- `PUT /files/{file_id}/watched`
- `GET`/`DELETE /history`, `DELETE /history/{entry_id}`
- `GET`/`POST /collections`, `PATCH`/`DELETE /collections/{collection_id}`, `PUT /collections/{collection_id}/items`
- `GET`/`POST /tags`, `DELETE /tags/{tag_id}`, `PUT /files/{file_id}/tags`
- `GET`/`PUT /files/{file_id}/metadata`
- `GET /stats`
- `GET /surprise`
- `GET`/`PUT /preferences`

The existing `/api/files/{file_id}/progress` endpoint continues to accept throttled client updates. When a position reaches approximately 95% of the known duration it becomes completed and a history entry is recorded. The web player continues writing progress every ten seconds and on close/pause.

## Metadata configuration

This implementation stores metadata and artwork supplied by a user or an administrator through the metadata endpoint. It does not make playback dependent on metadata, and it does not call or bundle a third-party content provider. A legal provider such as TMDB can be added later by implementing a server-side lookup/cache service and documenting its API key in the deployment environment; no provider key is required for the current build.

## Development and verification

Backend setup and tests:

```bash
cd backend
python3 -m pip install -r requirements.txt
pytest -q
python3 -m compileall -q app
```

Web setup and checks:

```bash
cd web
npm ci
npm run build
npm run lint
```

Android checks require a configured Android SDK and Java/Gradle environment:

```bash
cd android
./gradlew :app:compileDebugKotlin --no-daemon
```

The Android command could not complete in the sandbox because the repository’s local Android SDK configuration is missing; the source changes are additive and do not alter the existing player or authentication flows.
