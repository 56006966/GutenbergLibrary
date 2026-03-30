package com.kdhuf.projectgutenberglibrary.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class CatalogBackendServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int PAGE_SIZE = 32;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("CATALOG_BACKEND_PORT", String.valueOf(DEFAULT_PORT)));
        String bindHost = System.getenv().getOrDefault("CATALOG_BACKEND_HOST", "127.0.0.1");
        Path catalogPath = Path.of(System.getenv().getOrDefault("CATALOG_DATA_FILE", "backend/data/catalog.tsv"));
        List<BookRecord> books = CatalogStore.load(catalogPath);

        HttpServer server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        server.createContext("/books", new BooksHandler(books));
        server.createContext("/health", new HealthHandler(catalogPath, books.size()));
        server.setExecutor(null);
        server.start();

        System.out.println("Catalog backend listening on http://" + bindHost + ":" + port + "/");
        System.out.println("Loaded " + books.size() + " books from " + catalogPath.toAbsolutePath());
    }

    private static final class HealthHandler implements HttpHandler {
        private final Path catalogPath;
        private final int bookCount;

        private HealthHandler(Path catalogPath, int bookCount) {
            this.catalogPath = catalogPath;
            this.bookCount = bookCount;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, "{\"detail\":\"Method not allowed\"}");
                return;
            }
            String body = "{"
                + "\"status\":\"ok\","
                + "\"book_count\":" + bookCount + ","
                + "\"catalog_file\":\"" + escapeJson(catalogPath.toAbsolutePath().toString()) + "\""
                + "}";
            writeJson(exchange, 200, body);
        }
    }

    private static final class BooksHandler implements HttpHandler {
        private final List<BookRecord> books;

        private BooksHandler(List<BookRecord> books) {
            this.books = books;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    writeJson(exchange, 405, "{\"detail\":\"Method not allowed\"}");
                    return;
                }

                URI requestUri = exchange.getRequestURI();
                String path = requestUri.getPath();
                if (path.matches("^/books/\\d+$")) {
                    int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                    BookRecord book = books.stream().filter(item -> item.id == id).findFirst().orElse(null);
                    if (book == null) {
                        writeJson(exchange, 404, "{\"detail\":\"Not found\"}");
                        return;
                    }
                    writeJson(exchange, 200, book.toJson());
                    return;
                }

                if (!"/books".equals(path)) {
                    writeJson(exchange, 404, "{\"detail\":\"Not found\"}");
                    return;
                }

                Map<String, String> query = parseQuery(requestUri.getRawQuery());
                List<BookRecord> filtered = filterBooks(books, query);
                int page = Math.max(parseInt(query.get("page"), 1), 1);
                int fromIndex = Math.min((page - 1) * PAGE_SIZE, filtered.size());
                int toIndex = Math.min(fromIndex + PAGE_SIZE, filtered.size());
                List<BookRecord> pageItems = filtered.subList(fromIndex, toIndex);

                String next = toIndex < filtered.size() ? buildPageUrl(exchange, query, page + 1) : "null";
                String previous = page > 1 ? buildPageUrl(exchange, query, page - 1) : "null";

                String json = "{"
                    + "\"count\":" + filtered.size() + ","
                    + "\"next\":" + next + ","
                    + "\"previous\":" + previous + ","
                    + "\"results\":[" + pageItems.stream().map(BookRecord::toJson).collect(Collectors.joining(",")) + "]"
                    + "}";
                writeJson(exchange, 200, json);
            } catch (Exception error) {
                String body = "{\"detail\":\"" + escapeJson(error.getMessage() == null ? "Server error" : error.getMessage()) + "\"}";
                writeJson(exchange, 500, body);
            }
        }

        private List<BookRecord> filterBooks(List<BookRecord> source, Map<String, String> query) {
            List<BookRecord> filtered = new ArrayList<>(source);

            String ids = query.get("ids");
            if (ids != null && !ids.isBlank()) {
                List<Integer> wanted = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Integer::parseInt)
                    .toList();
                filtered = filtered.stream().filter(book -> wanted.contains(book.id)).collect(Collectors.toList());
            }

            String languages = query.get("languages");
            if (languages != null && !languages.isBlank()) {
                List<String> wanted = Arrays.stream(languages.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList();
                filtered = filtered.stream()
                    .filter(book -> book.languages.stream().map(String::toLowerCase).anyMatch(wanted::contains))
                    .collect(Collectors.toList());
            }

            String mimeType = query.get("mime_type");
            if (mimeType != null && !mimeType.isBlank()) {
                String wanted = mimeType.toLowerCase(Locale.US);
                filtered = filtered.stream()
                    .filter(book -> book.formats.keySet().stream().anyMatch(key -> key.toLowerCase(Locale.US).startsWith(wanted)))
                    .collect(Collectors.toList());
            }

            String search = query.get("search");
            if (search != null && !search.isBlank()) {
                List<String> words = Arrays.stream(search.toLowerCase(Locale.US).split("\\s+"))
                    .filter(word -> !word.isBlank())
                    .toList();
                filtered = filtered.stream().filter(book -> matchesSearch(book, words)).collect(Collectors.toList());
            }

            String topic = query.get("topic");
            if (topic != null && !topic.isBlank()) {
                String lowered = topic.toLowerCase(Locale.US);
                filtered = filtered.stream()
                    .filter(book -> book.subjects.stream().anyMatch(value -> value.toLowerCase(Locale.US).contains(lowered))
                        || book.bookshelves.stream().anyMatch(value -> value.toLowerCase(Locale.US).contains(lowered)))
                    .collect(Collectors.toList());
            }

            String sort = query.getOrDefault("sort", "popular");
            Comparator<BookRecord> comparator = switch (sort) {
                case "ascending" -> Comparator.comparingInt(book -> book.id);
                case "descending" -> Comparator.<BookRecord>comparingInt(book -> book.id).reversed();
                case "newest" -> Comparator
                    .<BookRecord, LocalDate>comparing(book -> parseReleaseDate(book.releaseDate), Comparator.reverseOrder())
                    .thenComparing(Comparator.comparingInt((BookRecord book) -> book.id).reversed());
                default -> Comparator.<BookRecord>comparingInt(book -> book.downloadCount).reversed()
                    .thenComparingInt(book -> book.id);
            };

            filtered.sort(comparator);
            return filtered;
        }

        private boolean matchesSearch(BookRecord book, List<String> words) {
            String haystack = (book.title + " " + String.join(" ", book.authors)).toLowerCase(Locale.US);
            return words.stream().allMatch(haystack::contains);
        }

        private String buildPageUrl(HttpExchange exchange, Map<String, String> originalQuery, int targetPage) {
            Map<String, String> params = new LinkedHashMap<>(originalQuery);
            params.put("page", String.valueOf(targetPage));
            String queryString = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
            String externalBaseUrl = System.getenv("CATALOG_PUBLIC_BASE_URL");
            if (externalBaseUrl != null && !externalBaseUrl.isBlank()) {
                String normalized = externalBaseUrl.endsWith("/") ? externalBaseUrl.substring(0, externalBaseUrl.length() - 1) : externalBaseUrl;
                return "\"" + normalized + "/books?" + queryString + "\"";
            }
            String host = exchange.getLocalAddress().getHostString();
            int port = exchange.getLocalAddress().getPort();
            return "\"" + "http://" + host + ":" + port + "/books?" + queryString + "\"";
        }
    }

    private static final class CatalogStore {
        private CatalogStore() {}

        private static List<BookRecord> load(Path path) throws IOException {
            if (!Files.exists(path)) {
                throw new IOException("Catalog file not found: " + path);
            }

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }

            List<BookRecord> books = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 14) continue;
                books.add(BookRecord.from(parts));
            }
            return books;
        }
    }

    private static final class BookRecord {
        private final int id;
        private final String title;
        private final String releaseDate;
        private final List<String> authors;
        private final List<String> subjects;
        private final List<String> languages;
        private final List<String> bookshelves;
        private final List<String> summaries;
        private final String mediaType;
        private final int downloadCount;
        private final Boolean copyright;
        private final Map<String, String> formats;

        private BookRecord(
            int id,
            String title,
            String releaseDate,
            List<String> authors,
            List<String> subjects,
            List<String> languages,
            List<String> bookshelves,
            List<String> summaries,
            String mediaType,
            int downloadCount,
            Boolean copyright,
            Map<String, String> formats
        ) {
            this.id = id;
            this.title = title;
            this.releaseDate = releaseDate;
            this.authors = authors;
            this.subjects = subjects;
            this.languages = languages;
            this.bookshelves = bookshelves;
            this.summaries = summaries;
            this.mediaType = mediaType;
            this.downloadCount = downloadCount;
            this.copyright = copyright;
            this.formats = formats;
        }

        private static BookRecord from(String[] parts) {
            boolean hasReleaseDate = parts.length >= 15;
            int shift = hasReleaseDate ? 1 : 0;
            Map<String, String> formats = new LinkedHashMap<>();
            putIfPresent(formats, "application/epub+zip", parts[10 + shift]);
            putIfPresent(formats, "image/jpeg", parts[11 + shift]);
            putIfPresent(formats, "text/html", parts[12 + shift]);
            putIfPresent(formats, "text/plain; charset=utf-8", parts[13 + shift]);
            return new BookRecord(
                Integer.parseInt(parts[0]),
                parts[1],
                hasReleaseDate ? parts[2] : "",
                splitList(parts[2 + shift]),
                splitList(parts[3 + shift]),
                splitList(parts[4 + shift]),
                splitList(parts[5 + shift]),
                splitList(parts[6 + shift]),
                blankToDefault(parts[7 + shift], "Text"),
                parseInt(parts[8 + shift], 0),
                parseNullableBoolean(parts[9 + shift]),
                formats
            );
        }

        private String toJson() {
            String authorsJson = authors.stream()
                .map(name -> "{\"birth_year\":null,\"death_year\":null,\"name\":\"" + escapeJson(name) + "\"}")
                .collect(Collectors.joining(","));

            String formatsJson = formats.entrySet().stream()
                .map(entry -> "\"" + escapeJson(entry.getKey()) + "\":\"" + escapeJson(entry.getValue()) + "\"")
                .collect(Collectors.joining(","));

            return "{"
                + "\"id\":" + id + ","
                + "\"title\":\"" + escapeJson(title) + "\","
                + "\"release_date\":" + (releaseDate.isBlank() ? "null" : "\"" + escapeJson(releaseDate) + "\"") + ","
                + "\"subjects\":" + toJsonArray(subjects) + ","
                + "\"authors\":[" + authorsJson + "],"
                + "\"summaries\":" + toJsonArray(summaries) + ","
                + "\"translators\":[],"
                + "\"bookshelves\":" + toJsonArray(bookshelves) + ","
                + "\"languages\":" + toJsonArray(languages) + ","
                + "\"copyright\":" + (copyright == null ? "null" : copyright.toString()) + ","
                + "\"media_type\":\"" + escapeJson(mediaType) + "\","
                + "\"formats\":{" + formatsJson + "},"
                + "\"download_count\":" + downloadCount
                + "}";
        }
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\|"))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static Boolean parseNullableBoolean(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return query;
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) continue;
            String[] pieces = pair.split("=", 2);
            String key = urlDecode(pieces[0]);
            String value = pieces.length > 1 ? urlDecode(pieces[1]) : "";
            query.put(key, value);
        }
        return query;
    }

    private static String toJsonArray(List<String> values) {
        return "[" + values.stream()
            .filter(Objects::nonNull)
            .map(value -> "\"" + escapeJson(value) + "\"")
            .collect(Collectors.joining(",")) + "]";
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static LocalDate parseReleaseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.MIN;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return LocalDate.MIN;
        }
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
