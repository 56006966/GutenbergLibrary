# Local Catalog Backend

This folder contains a minimal self-hosted catalog API for the Android app.

It serves a Gutendex-compatible subset of endpoints:

- `GET /books`
- `GET /books/{id}`

The backend reads from a TSV catalog file built from offline Project Gutenberg metadata plus an optional popularity snapshot.

## Default Data File

The server looks for:

`backend/data/catalog.tsv`

Header columns:

```text
id	title	release_date	authors	subjects	languages	bookshelves	summaries	media_type	download_count	copyright	epub_url	cover_url	html_url	text_url
```

Multi-value columns use `|` as a separator.

## Run

PowerShell:

```powershell
.\scripts\run-catalog-backend.ps1
```

The default bind is:

`http://127.0.0.1:8080/`

For the Android emulator, use:

`http://10.0.2.2:8080/`

Environment variables:

- `CATALOG_BACKEND_HOST`
- `CATALOG_BACKEND_PORT`
- `CATALOG_DATA_FILE`
- `CATALOG_PUBLIC_BASE_URL`
- `CATALOG_COVER_BASE_URL`
- `CATALOG_FILES_BASE_URL`

Useful examples:

```powershell
$env:CATALOG_BACKEND_HOST="0.0.0.0"
$env:CATALOG_BACKEND_PORT="8080"
$env:CATALOG_PUBLIC_BASE_URL="https://api.example.com"
$env:CATALOG_COVER_BASE_URL="https://books.phunkypixels.com"
$env:CATALOG_FILES_BASE_URL="https://books.phunkypixels.com"
```

Health check:

`GET /health`

## Windows Task Scheduler Flow

For a simple Windows setup:

1. Mirror sync:

```powershell
.\scripts\sync-mirror.ps1
```

2. Catalog import:

```powershell
$env:PG_MIRROR_GENERATED_DIR="C:\gutenberg\generated"
.\scripts\import-from-local-mirror.ps1
```

Task Scheduler wrapper scripts:

- `scripts/task-pg-mirror-sync.cmd`
- `scripts/task-pg-catalog-import.cmd`
- `scripts/task-pg-catalog-backend.cmd`

## WSL rsync

The Windows mirror sync task is set up to prefer `rsync` through WSL.

Default behavior:

- `PG_USE_WSL_RSYNC=1`
- Windows paths like `C:\gutenberg\generated` are translated to `/mnt/c/gutenberg/generated`

So if you install Ubuntu + `rsync` in WSL, the existing sync task can keep running from Task Scheduler without needing a native Windows `rsync.exe`.

## Import Real Mirror Metadata

Set the input folder to a directory containing mirrored `.rdf` files and run:

```powershell
$env:CATALOG_IMPORT_INPUT="C:\gutenberg\generated"
$env:CATALOG_IMPORT_OUTPUT="backend\data\catalog.tsv"
.\scripts\import-catalog-from-rdf.ps1
```

Or on Windows with batch:

```bat
set CATALOG_IMPORT_INPUT=C:\gutenberg\generated
set CATALOG_IMPORT_OUTPUT=backend\data\catalog.tsv
scripts\import-catalog-from-rdf.bat
```

Convenience helper for a typical Windows local mirror:

```powershell
$env:PG_MIRROR_GENERATED_DIR="C:\gutenberg\generated"
.\scripts\import-from-local-mirror.ps1
```

Or:

```bat
set PG_MIRROR_GENERATED_DIR=C:\gutenberg\generated
scripts\import-from-local-mirror.bat
```

Notes:

- the importer scans recursively for `.rdf` files
- it extracts title, release date, authors, subjects, languages, bookshelves, and common format URLs
- `Newest Releases` comes from the mirrored RDF `release_date`, which matches Project Gutenberg's machine-readable metadata guidance
- `download_count` comes from an optional popularity snapshot file because Project Gutenberg RDF metadata does not carry popularity counts
- books without an EPUB URL are skipped during import so the catalog only includes titles that can be opened as EPUBs

## Popularity Snapshot

To build a real `Most Popular` shelf, refresh a snapshot from Project Gutenberg's official popularity listing:

```powershell
.\scripts\refresh-popularity-snapshot.ps1
```

This writes:

`backend/data/popular_books.tsv`

Format:

```text
ebook_id    download_count
```

The importer automatically merges that file when `CATALOG_POPULARITY_INPUT` is set, or when `backend/data/popular_books.tsv` exists in the default local-mirror flow.

## One-Step Offline Build

For the intended end-to-end workflow:

```powershell
$env:PG_MIRROR_GENERATED_DIR="C:\gutenberg\generated"
$env:CATALOG_REFRESH_POPULARITY="1"
.\scripts\build-offline-catalog.ps1
```

That flow:

- refreshes the official popularity snapshot for `Most Popular`
- imports mirrored RDF metadata for `Newest Releases`
- writes the merged offline catalog to `backend/data/catalog.tsv`

## Supported Query Parameters

- `search`
- `topic`
- `sort` (`popular`, `newest`, `ascending`, `descending`)
- `ids`
- `languages`
- `mime_type`
- `page`

## Data Source Summary

- `Newest Releases`: mirrored RDF metadata sorted by Project Gutenberg `release_date`
- `Most Popular`: official popularity snapshot merged into the offline catalog
- offline app shelves: cached copies of those backend responses

For public deployment behind HTTPS, see:

- `deploy/nginx.catalog-and-mirror.conf.example`
