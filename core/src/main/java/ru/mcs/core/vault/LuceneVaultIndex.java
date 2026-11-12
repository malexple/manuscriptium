package ru.mcs.core.vault;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Локальный пересобираемый Lucene-кэш. Все вызовы сериализуются владельцем {@link VaultIndex}. */
final class LuceneVaultIndex implements AutoCloseable {

    private static final String ID = "id";
    private static final String PATH = "path";
    private static final String TITLE = "title";
    private static final String CONTENT = "content";
    private static final String FRONT_MATTER = "frontMatter";
    private static final String TAGS = "tags";
    private static final String DRAFT = "draft";
    private static final int MAX_TERMS = 32;
    private static final int SNIPPET_RADIUS = 90;

    private final StandardAnalyzer analyzer;
    private final Directory directory;
    private final IndexWriter writer;

    LuceneVaultIndex(Path directoryPath) throws IOException {
        analyzer = new StandardAnalyzer();
        Directory opened = null;
        IndexWriter indexWriter = null;
        try {
            opened = new NIOFSDirectory(directoryPath);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(opened, config);
            indexWriter.commit();
        } catch (IOException | RuntimeException e) {
            if (indexWriter != null) {
                indexWriter.rollback();
            }
            if (opened != null) {
                opened.close();
            }
            analyzer.close();
            throw e;
        }
        directory = opened;
        writer = indexWriter;
    }

    void replaceAll(List<MarkdownDocument> documents) throws IOException {
        writer.deleteAll();
        for (MarkdownDocument document : documents) {
            writer.addDocument(toLucene(document));
        }
        writer.commit();
    }

    void update(MarkdownDocument document) throws IOException {
        writer.updateDocument(new Term(ID, document.path()), toLucene(document));
        writer.commit();
    }

    void removePath(String relative) throws IOException {
        Query exact = new TermQuery(new Term(ID, relative));
        Query children = new PrefixQuery(new Term(ID, relative + "/"));
        BooleanQuery query = new BooleanQuery.Builder()
                .add(exact, BooleanClause.Occur.SHOULD)
                .add(children, BooleanClause.Occur.SHOULD)
                .build();
        writer.deleteDocuments(query);
        writer.commit();
    }

    List<VaultSearchResult> search(Path root, VaultSearchQuery request) throws IOException {
        if (request.isEmpty()) {
            return List.of();
        }
        List<String> terms = terms(request.text());
        if (!request.text().isEmpty() && terms.isEmpty()) {
            return List.of();
        }
        Query query = query(request, terms);
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs top = searcher.search(query, request.limit());
            List<VaultSearchResult> results = new ArrayList<>(top.scoreDocs.length);
            for (ScoreDoc scoreDoc : top.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                String path = document.get(ID);
                String title = document.get(TITLE);
                String snippet = snippet(request.scope(), document, terms);
                results.add(new VaultSearchResult(root.resolve(path).normalize(), path, title, snippet, scoreDoc.score));
            }
            return List.copyOf(results);
        }
    }

    private Query query(VaultSearchQuery request, List<String> terms) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(terms.isEmpty() ? MatchAllDocsQuery.INSTANCE : textQuery(request.scope(), terms),
                BooleanClause.Occur.MUST);
        if (!request.subtree().isEmpty()) {
            query.add(new PrefixQuery(new Term(ID, request.subtree() + "/")), BooleanClause.Occur.FILTER);
        }
        if (request.draft() != null) {
            query.add(new TermQuery(new Term(DRAFT, request.draft().toString())), BooleanClause.Occur.FILTER);
        }
        return query.build();
    }

    private static Query textQuery(VaultSearchScope scope, List<String> terms) {
        BooleanQuery.Builder allTerms = new BooleanQuery.Builder();
        for (String term : terms) {
            BooleanQuery.Builder fields = new BooleanQuery.Builder();
            for (String field : fields(scope)) {
                Query query = new TermQuery(new Term(field, term));
                if (scope == VaultSearchScope.ALL) {
                    float boost = switch (field) {
                        case TITLE -> 4.0f;
                        case TAGS -> 3.0f;
                        case PATH, FRONT_MATTER -> 2.0f;
                        default -> 1.0f;
                    };
                    query = new BoostQuery(query, boost);
                }
                fields.add(query, BooleanClause.Occur.SHOULD);
            }
            allTerms.add(fields.build(), BooleanClause.Occur.MUST);
        }
        return allTerms.build();
    }

    private static List<String> fields(VaultSearchScope scope) {
        return switch (scope) {
            case ALL -> List.of(TITLE, CONTENT, FRONT_MATTER, PATH, TAGS);
            case TITLE -> List.of(TITLE);
            case CONTENT -> List.of(CONTENT);
            case FRONT_MATTER -> List.of(FRONT_MATTER);
            case PATH -> List.of(PATH);
            case TAGS -> List.of(TAGS);
        };
    }

    private List<String> terms(String text) throws IOException {
        if (text.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        try (var stream = analyzer.tokenStream(CONTENT, text)) {
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && terms.size() < MAX_TERMS) {
                terms.add(attribute.toString());
            }
            stream.end();
        }
        return List.copyOf(terms);
    }

    private static Document toLucene(MarkdownDocument source) {
        FrontMatterMetadata metadata = FrontMatterMetadata.parse(source.frontMatter());
        Document document = new Document();
        document.add(new StringField(ID, source.path(), Field.Store.YES));
        document.add(new TextField(PATH, source.path(), Field.Store.YES));
        document.add(new TextField(TITLE, source.title(), Field.Store.YES));
        document.add(new TextField(FRONT_MATTER, source.frontMatter(), Field.Store.YES));
        document.add(new TextField(CONTENT, source.body(), Field.Store.YES));
        document.add(new TextField(TAGS, String.join("\n", metadata.tags()), Field.Store.NO));
        document.add(new StringField(DRAFT, Boolean.toString(metadata.draft()), Field.Store.NO));
        return document;
    }

    private static String snippet(VaultSearchScope scope, Document document, List<String> terms) {
        List<String> sources = switch (scope) {
            case TITLE -> List.of(document.get(TITLE));
            case PATH -> List.of(document.get(PATH));
            case FRONT_MATTER, TAGS -> List.of(document.get(FRONT_MATTER));
            case CONTENT -> List.of(document.get(CONTENT));
            case ALL -> List.of(document.get(CONTENT), document.get(TITLE),
                    document.get(FRONT_MATTER), document.get(PATH));
        };
        for (String source : sources) {
            String normalized = source.replaceAll("\\s+", " ").trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                int match = lower.indexOf(term);
                if (match >= 0) {
                    return around(normalized, match, term.length());
                }
            }
        }
        String fallback = sources.getFirst().replaceAll("\\s+", " ").trim();
        return abbreviate(fallback, SNIPPET_RADIUS * 2);
    }

    private static String around(String text, int match, int length) {
        int start = Math.max(0, match - SNIPPET_RADIUS);
        int end = Math.min(text.length(), match + length + SNIPPET_RADIUS);
        return (start > 0 ? "…" : "") + text.substring(start, end).trim()
                + (end < text.length() ? "…" : "");
    }

    private static String abbreviate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit).trim() + "…";
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            writer.close();
        } catch (IOException e) {
            failure = e;
        }
        try {
            directory.close();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        analyzer.close();
        if (failure != null) {
            throw failure;
        }
    }

    /** Невалидный YAML не мешает полнотекстовой индексации; structured-фильтры тогда используют значения по умолчанию. */
    private record FrontMatterMetadata(List<String> tags, boolean draft) {

        static FrontMatterMetadata parse(String frontMatter) {
            if (frontMatter == null || frontMatter.isBlank()) {
                return new FrontMatterMetadata(List.of(), false);
            }
            try {
                Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
                if (!(yaml.load(frontMatter) instanceof Map<?, ?> map)) {
                    return new FrontMatterMetadata(List.of(), false);
                }
                List<String> tags = tags(map.get("tags"));
                boolean draft = draft(map.get("draft"));
                return new FrontMatterMetadata(tags, draft);
            } catch (RuntimeException e) {
                return new FrontMatterMetadata(List.of(), false);
            }
        }

        private static List<String> tags(Object value) {
            if (value instanceof Collection<?> collection) {
                List<String> result = new ArrayList<>();
                for (Object item : collection) {
                    if (item != null) {
                        result.add(item.toString());
                    }
                }
                return List.copyOf(result);
            }
            return value == null || value.toString().isBlank() ? List.of() : List.of(value.toString());
        }

        private static boolean draft(Object value) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            return value != null && Boolean.parseBoolean(value.toString());
        }
    }
}
