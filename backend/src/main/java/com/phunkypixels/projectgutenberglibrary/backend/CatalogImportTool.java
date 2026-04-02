package com.phunkypixels.projectgutenberglibrary.backend;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CatalogImportTool {
    private static final String HEADER = String.join("\t",
        "id",
        "title",
        "release_date",
        "authors",
        "subjects",
        "languages",
        "bookshelves",
        "summaries",
        "media_type",
        "download_count",
        "copyright",
        "epub_url",
        "cover_url",
        "html_url",
        "text_url"
    );

    public static void main(String[] args) throws Exception {
        Path inputRoot = Path.of(System.getenv().getOrDefault("CATALOG_IMPORT_INPUT", "backend/import"));
        Path outputFile = Path.of(System.getenv().getOrDefault("CATALOG_IMPORT_OUTPUT", "backend/data/catalog.tsv"));

        List<Path> rdfFiles = collectRdfFiles(inputRoot);
        List<Row> rows = new ArrayList<>();

        for (Path rdfFile : rdfFiles) {
            parseRdf(rdfFile).ifPresent(rows::add);
        }

        rows.sort(Comparator.comparingInt(row -> row.id));
        writeTsv(outputFile, rows);

        System.out.println("Imported " + rows.size() + " books from " + inputRoot.toAbsolutePath());
        System.out.println("Wrote catalog TSV to " + outputFile.toAbsolutePath());
    }

    private static List<Path> collectRdfFiles(Path inputRoot) throws IOException {
        if (!Files.exists(inputRoot)) {
            throw new IOException("Import input path not found: " + inputRoot);
        }

        if (Files.isRegularFile(inputRoot) && inputRoot.toString().toLowerCase(Locale.US).endsWith(".rdf")) {
            return List.of(inputRoot);
        }

        try (Stream<Path> stream = Files.walk(inputRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase(Locale.US).endsWith(".rdf"))
                .sorted()
                .toList();
        }
    }

    private static java.util.Optional<Row> parseRdf(Path rdfFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(Files.newInputStream(rdfFile));
            document.getDocumentElement().normalize();

            Element ebook = firstElementByLocalName(document, "ebook");
            if (ebook == null) {
                return java.util.Optional.empty();
            }

            int id = parseId(ebook.getAttribute("rdf:about"));
            String title = firstText(document, "title");
            List<String> authors = personNames(document, "creator");
            List<String> subjects = valuesFromBag(document, "subject");
            List<String> languages = valuesFromBag(document, "language");
            List<String> bookshelves = valuesFromBag(document, "bookshelf");
            String summary = firstText(document, "description");
            String releaseDate = parseReleaseDate(document);
            Boolean copyright = parseNullableBoolean(firstText(document, "isFormatOf"));
            Map<String, String> formats = collectFormats(document);

            if (id <= 0 || title.isBlank()) {
                return java.util.Optional.empty();
            }

            return java.util.Optional.of(new Row(
                id,
                title,
                releaseDate,
                authors,
                subjects,
                languages,
                bookshelves,
                summary.isBlank() ? List.of() : List.of(summary),
                "Text",
                0,
                copyright,
                pickFormat(formats, "application/epub+zip"),
                pickFormat(formats, "image/jpeg"),
                pickFormat(formats, "text/html"),
                pickFormat(formats, "text/plain")
            ));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private static Map<String, String> collectFormats(Document document) {
        Map<String, String> formats = new LinkedHashMap<>();
        NodeList files = document.getElementsByTagNameNS("*", "file");
        for (int index = 0; index < files.getLength(); index++) {
            Node node = files.item(index);
            if (!(node instanceof Element file)) continue;
            String url = file.getAttribute("rdf:about");
            if (url == null || url.isBlank()) continue;

            NodeList formatNodes = file.getElementsByTagNameNS("*", "format");
            for (int formatIndex = 0; formatIndex < formatNodes.getLength(); formatIndex++) {
                Node formatNode = formatNodes.item(formatIndex);
                if (!(formatNode instanceof Element formatElement)) continue;
                String mimeType = firstChildTextByLocalName(formatElement, "value");
                if (!mimeType.isBlank()) {
                    formats.putIfAbsent(mimeType, url);
                }
            }
        }
        return formats;
    }

    private static String pickFormat(Map<String, String> formats, String prefix) {
        return formats.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase(Locale.US).startsWith(prefix.toLowerCase(Locale.US)))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("");
    }

    private static int parseId(String rdfAbout) {
        if (rdfAbout == null) return 0;
        String digits = rdfAbout.replaceAll(".*?(\\d+)$", "$1");
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static List<String> personNames(Document document, String roleLocalName) {
        NodeList roleNodes = document.getElementsByTagNameNS("*", roleLocalName);
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < roleNodes.getLength(); index++) {
            Node roleNode = roleNodes.item(index);
            if (!(roleNode instanceof Element role)) continue;
            NodeList namesNodes = role.getElementsByTagNameNS("*", "name");
            for (int nameIndex = 0; nameIndex < namesNodes.getLength(); nameIndex++) {
                String name = namesNodes.item(nameIndex).getTextContent().trim();
                if (!name.isBlank()) {
                    names.add(cleanCell(name));
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static List<String> valuesFromBag(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (!(node instanceof Element element)) continue;

            NodeList valueNodes = element.getElementsByTagNameNS("*", "value");
            if (valueNodes.getLength() > 0) {
                for (int valueIndex = 0; valueIndex < valueNodes.getLength(); valueIndex++) {
                    String value = valueNodes.item(valueIndex).getTextContent().trim();
                    if (!value.isBlank()) {
                        values.add(cleanCell(value));
                    }
                }
                continue;
            }

            NodeList rdfValueNodes = element.getElementsByTagNameNS("*", "Description");
            for (int valueIndex = 0; valueIndex < rdfValueNodes.getLength(); valueIndex++) {
                String value = rdfValueNodes.item(valueIndex).getTextContent().trim();
                if (!value.isBlank()) {
                    values.add(cleanCell(value));
                }
            }
        }
        return new ArrayList<>(values);
    }

    private static Element firstElementByLocalName(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String firstText(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            String text = nodes.item(index).getTextContent().trim();
            if (!text.isBlank()) {
                return cleanCell(text);
            }
        }
        return "";
    }

    private static String firstChildTextByLocalName(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            String text = nodes.item(index).getTextContent().trim();
            if (!text.isBlank()) {
                return cleanCell(text);
            }
        }
        return "";
    }

    private static Boolean parseNullableBoolean(String ignoredValue) {
        return null;
    }

    private static String parseReleaseDate(Document document) {
        for (String localName : List.of("issued", "release_date")) {
            String value = firstText(document, localName);
            if (!value.isBlank()) {
                return normalizeReleaseDate(value);
            }
        }
        return "";
    }

    private static String normalizeReleaseDate(String value) {
        String trimmed = cleanCell(value);
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return trimmed;
        }
        if (trimmed.length() >= 10) {
            String prefix = trimmed.substring(0, 10);
            if (prefix.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return prefix;
            }
        }
        return trimmed;
    }

    private static void writeTsv(Path outputFile, List<Row> rows) throws IOException {
        Files.createDirectories(outputFile.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Row row : rows) {
            lines.add(row.toTsvLine());
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    private static String cleanCell(String value) {
        return value
            .replace('\t', ' ')
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim();
    }

    private record Row(
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
        String epubUrl,
        String coverUrl,
        String htmlUrl,
        String textUrl
    ) {
        private String toTsvLine() {
            return Stream.of(
                    String.valueOf(id),
                    title,
                    releaseDate,
                    joinList(authors),
                    joinList(subjects),
                    joinList(languages),
                    joinList(bookshelves),
                    joinList(summaries),
                    mediaType,
                    String.valueOf(downloadCount),
                    copyright == null ? "null" : copyright.toString(),
                    epubUrl,
                    coverUrl,
                    htmlUrl,
                    textUrl
                )
                .map(CatalogImportTool::cleanCell)
                .collect(Collectors.joining("\t"));
        }

        private static String joinList(List<String> values) {
            return values.stream()
                .map(CatalogImportTool::cleanCell)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("|"));
        }
    }
}
