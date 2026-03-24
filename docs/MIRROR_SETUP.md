# Mirror Setup

This project is now structured so the Android app can:

1. read catalog metadata from your own backend, and
2. read EPUB and cover assets from your own Project Gutenberg mirror.

The app-side configuration lives in Gradle properties:

- `catalogApiBaseUrl`
- `catalogApiUserAgent`
- `gutenbergMirrorBaseUrl`

## Recommended Architecture

Use two layers:

1. Mirror sync
   - Sync official Project Gutenberg mirror content with `rsync`.
   - Keep generated EPUB/cover files locally.

2. Catalog backend
   - Import machine-readable catalog metadata into your own database.
   - Expose a small API that matches the app contract:
     - `GET /books`
     - `GET /books/{id}`
     - paginated `next` URLs for `GET /books`

3. Android app
   - Uses `catalogApiBaseUrl` for metadata.
   - Uses `gutenbergMirrorBaseUrl` for mirrored covers and EPUB files.

## Official Mirror Sync

Project Gutenberg recommends `rsync` for mirrors.

On Windows, the recommended approach is:

1. install WSL/Ubuntu
2. install `rsync` inside WSL
3. let the existing Windows scheduler task call `wsl rsync ...` automatically

Main collection:

```bash
rsync -av --del aleph.gutenberg.org::gutenberg /srv/gutenberg
```

Generated EPUB content:

```bash
rsync -av --del aleph.gutenberg.org::gutenberg-epub /srv/gutenberg-generated
```

If you want a single tree, place generated files under your own served `cache/epub` path.

## Suggested Local Paths

```text
/srv/gutenberg-main
/srv/gutenberg-generated
/srv/gutenberg-catalog
```

## App Config

Put these in `~/.gradle/gradle.properties` or your project `gradle.properties`:

```properties
catalogApiBaseUrl=http://10.0.2.2:8080/
catalogApiUserAgent=ProjectGutenbergLibrary/1.0 (+https://your-domain.example/contact)
gutenbergMirrorBaseUrl=https://books.your-domain.example/
```

For the Android emulator, `10.0.2.2` points to your local machine.

## Backend Contract

The Android app expects the API shape defined by:

- `app/src/main/java/com/kdhuf/projectgutenberglibrary/data/remote/CatalogBackendApi.kt`

That means your backend should return:

- `results`
- `next`
- per-book details with `formats`, `authors`, `subjects`, and `download_count`

This repo now includes a small local backend scaffold in:

- `backend/src/main/java/com/kdhuf/projectgutenberglibrary/backend/CatalogBackendServer.java`

Run it with:

- `scripts/run-catalog-backend.ps1`
- `scripts/run-catalog-backend.bat`

Import mirrored RDF metadata with:

- `scripts/sync-mirror.ps1`
- `scripts/sync-mirror.bat`
- `scripts/import-catalog-from-rdf.ps1`
- `scripts/import-catalog-from-rdf.bat`
- `scripts/import-from-local-mirror.ps1`
- `scripts/import-from-local-mirror.bat`

For production HTTPS hosting, there is also a sample reverse-proxy config:

- `deploy/nginx.catalog-and-mirror.conf.example`

## Notes

- Keep the mirror and backend under your control if you want the strongest compliance story.
- The app already rewrites Gutenberg-hosted asset URLs to your configured mirror base.
- The current default metadata source can still be overridden until your own backend is ready.
- For real users, serve both the API and mirror over HTTPS, not plain HTTP.
- The included importer is a practical starting point, not a full production catalog pipeline.
