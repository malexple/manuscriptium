package ru.mcs.plugin.mdeditor.reference;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.mcs.spi.editor.ObsidianLinkService;
import ru.mcs.spi.service.AtomicFileStore;

/** BibTeX-источники, CSL author-date labels, completion и вычисляемый список литературы. */
public final class CitationIndex {

    public static final CitationIndex EMPTY = new CitationIndex(List.of(), CslStyle.DEFAULT);

    private static final Pattern CITATION = Pattern.compile("\\[@[^\\]\\n]+]");
    private static final Pattern KEY = Pattern.compile("@([A-Za-z0-9_:.#$%&+?<>~/-]+)");

    private final Map<String, Entry> entries;
    private final CslStyle style;

    private CitationIndex(List<Entry> entries, CslStyle style) {
        Map<String, Entry> byKey = new LinkedHashMap<>();
        entries.forEach(entry -> byKey.putIfAbsent(entry.key(), entry));
        this.entries = Map.copyOf(byKey);
        this.style = style;
    }

    public static CitationIndex of(String bibtex, String csl) {
        List<Entry> entries = BibTeXParser.parse(bibtex == null ? "" : bibtex);
        CslStyle style = csl == null ? CslStyle.DEFAULT : CslStyle.parse(csl.getBytes(StandardCharsets.UTF_8));
        return entries.isEmpty() ? EMPTY : new CitationIndex(entries, style);
    }

    public static Configuration configuration(String markdown) {
        if (!(markdown.startsWith("---\n") || markdown.startsWith("---\r\n"))) {
            return Configuration.NONE;
        }
        int offset = markdown.startsWith("---\r\n") ? 5 : 4;
        String bibliography = null;
        String csl = null;
        while (offset < markdown.length()) {
            int end = lineEnd(markdown, offset);
            String line = markdown.substring(offset, end).trim();
            if (line.equals("---") || line.equals("...")) {
                break;
            }
            if (line.startsWith("bibliography:")) {
                bibliography = scalar(line.substring("bibliography:".length()));
            } else if (line.startsWith("csl:")) {
                csl = scalar(line.substring(4));
            }
            offset = nextLine(markdown, end);
        }
        return bibliography == null || bibliography.isEmpty() ? Configuration.NONE
                : new Configuration(bibliography, csl);
    }

    public static CompletableFuture<CitationIndex> load(Configuration configuration, Path noteFile,
                                                         ObsidianLinkService links, AtomicFileStore store,
                                                         Executor executor) {
        if (configuration == null || configuration.bibliography() == null || noteFile == null
                || links == null || store == null) {
            return CompletableFuture.completedFuture(EMPTY);
        }
        return read(configuration.bibliography(), noteFile, links, store, executor)
                .thenCombine(read(configuration.csl(), noteFile, links, store, executor),
                        (bib, csl) -> of(bib, csl))
                .exceptionally(error -> EMPTY);
    }

    /** Возвращает вычисленный label либо null, если это не citation или хотя бы один ключ неизвестен. */
    public String citation(String construct) {
        if (entries.isEmpty() || construct == null || !construct.startsWith("[") || !construct.endsWith("]")) {
            return null;
        }
        String[] parts = construct.substring(1, construct.length() - 1).split(";");
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            Matcher key = KEY.matcher(value);
            if (!key.find()) {
                return null;
            }
            Entry entry = entries.get(key.group(1));
            if (entry == null) {
                return null;
            }
            String locator = value.substring(key.end()).trim();
            if (locator.startsWith(",")) {
                locator = locator.substring(1).trim();
            }
            String label = style.yearFirst() ? entry.year() + ", " + shortAuthor(entry) : shortAuthor(entry) + ", "
                    + entry.year();
            label = label.replaceAll("^, |, $", "").trim();
            labels.add(label + (locator.isEmpty() ? "" : ", " + locator));
        }
        return labels.isEmpty() ? null : style.prefix() + String.join(style.delimiter(), labels) + style.suffix();
    }

    public List<String> complete(String prefix) {
        String wanted = prefix == null ? "" : prefix;
        return entries.keySet().stream().filter(key -> key.startsWith(wanted)).sorted().toList();
    }

    public List<String> bibliography(String markdown) {
        Set<String> cited = citedKeys(markdown);
        List<String> result = new ArrayList<>();
        for (String key : cited) {
            Entry entry = entries.get(key);
            if (entry != null) {
                result.add(fullAuthor(entry) + " (" + entry.year() + "). " + entry.title() + ".");
            }
        }
        return List.copyOf(result);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private Set<String> citedKeys(String markdown) {
        Set<String> result = new LinkedHashSet<>();
        Matcher citations = CITATION.matcher(markdown);
        while (citations.find()) {
            Matcher key = KEY.matcher(citations.group());
            while (key.find()) {
                result.add(key.group(1));
            }
        }
        return result;
    }

    private static String shortAuthor(Entry entry) {
        String[] authors = entry.author().split("\\s+and\\s+");
        String first = authors.length == 0 ? "" : authors[0].trim();
        if (first.contains(",")) {
            first = first.substring(0, first.indexOf(','));
        } else {
            String[] words = first.split("\\s+");
            first = words.length == 0 ? "" : words[words.length - 1];
        }
        return first + (authors.length > 1 ? " et al." : "");
    }

    private static String fullAuthor(Entry entry) {
        return entry.author().replaceAll("\\s+and\\s+", ", ");
    }

    private static CompletableFuture<String> read(String file, Path noteFile, ObsidianLinkService links,
                                                   AtomicFileStore store, Executor executor) {
        if (file == null || file.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return links.resolve(noteFile, file).thenCompose(target -> {
            if (target.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(store.read(target.orElseThrow().file()), StandardCharsets.UTF_8);
                } catch (Exception error) {
                    throw new java.util.concurrent.CompletionException(error);
                }
            }, executor);
        });
    }

    private static String scalar(String value) {
        String result = value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'")))) {
            return result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private static int lineEnd(String source, int start) {
        int end = start;
        while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
            end++;
        }
        return end;
    }

    private static int nextLine(String source, int end) {
        if (end < source.length() && source.charAt(end) == '\r' && end + 1 < source.length()
                && source.charAt(end + 1) == '\n') {
            return end + 2;
        }
        return end < source.length() ? end + 1 : source.length();
    }

    public record Entry(String key, String author, String title, String year) {
    }

    public record Configuration(String bibliography, String csl) {
        public static final Configuration NONE = new Configuration(null, null);
    }
}
