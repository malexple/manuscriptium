package ru.mcs.core.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import ru.mcs.spi.project.IndexedDocument;

/** Локальный пересобираемый Lucene-кэш. Все вызовы сериализуются владельцем {@link ProjectIndex}. */
final class LuceneProjectIndex implements AutoCloseable {

    private static final String ID = "id";
    private static final String PATH = "path";
    private static final String TITLE = "title";
    private static final String CONTENT = "content";
    private static final String METADATA = "metadata";
    private static final String ATTRIBUTES = "attributes";
    private static final String ATTRIBUTE_PREFIX = "attribute.";
    private static final int MAX_TERMS = 32;
    private static final int SNIPPET_RADIUS = 90;

    private final StandardAnalyzer analyzer;
    private final Directory directory;
    private final IndexWriter writer;

    LuceneProjectIndex(Path directoryPath) throws IOException {
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

    void replaceAll(List<IndexedDocument> documents) throws IOException {
        writer.deleteAll();
        for (IndexedDocument document : documents) {
            writer.addDocument(toLucene(document));
        }
        writer.commit();
    }

    void update(IndexedDocument document) throws IOException {
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

    List<ProjectSearchResult> search(Path root, ProjectSearchQuery request) throws IOException {
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
            List<ProjectSearchResult> results = new ArrayList<>(top.scoreDocs.length);
            for (ScoreDoc scoreDoc : top.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                String path = document.get(ID);
                String title = document.get(TITLE);
                String snippet = snippet(request.scope(), document, terms);
                results.add(new ProjectSearchResult(root.resolve(path).normalize(), path, title, snippet, scoreDoc.score));
            }
            return List.copyOf(results);
        }
    }

    private Query query(ProjectSearchQuery request, List<String> terms) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(terms.isEmpty() ? MatchAllDocsQuery.INSTANCE : textQuery(request.scope(), terms),
                BooleanClause.Occur.MUST);
        if (!request.subtree().isEmpty()) {
            query.add(new PrefixQuery(new Term(ID, request.subtree() + "/")), BooleanClause.Occur.FILTER);
        }
        request.attributeFilters().forEach((key, value) -> query.add(
                new TermQuery(new Term(ATTRIBUTE_PREFIX + key, value)), BooleanClause.Occur.FILTER));
        return query.build();
    }

    private static Query textQuery(ProjectSearchScope scope, List<String> terms) {
        BooleanQuery.Builder allTerms = new BooleanQuery.Builder();
        for (String term : terms) {
            BooleanQuery.Builder fields = new BooleanQuery.Builder();
            for (String field : fields(scope)) {
                Query query = new TermQuery(new Term(field, term));
                if (scope == ProjectSearchScope.ALL) {
                    float boost = switch (field) {
                        case TITLE -> 4.0f;
                        case ATTRIBUTES -> 3.0f;
                        case PATH, METADATA -> 2.0f;
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

    private static List<String> fields(ProjectSearchScope scope) {
        return switch (scope) {
            case ALL -> List.of(TITLE, CONTENT, METADATA, PATH, ATTRIBUTES);
            case TITLE -> List.of(TITLE);
            case CONTENT -> List.of(CONTENT);
            case METADATA -> List.of(METADATA);
            case PATH -> List.of(PATH);
            case ATTRIBUTES -> List.of(ATTRIBUTES);
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

    private static Document toLucene(IndexedDocument source) {
        Document document = new Document();
        document.add(new StringField(ID, source.path(), Field.Store.YES));
        document.add(new TextField(PATH, source.path(), Field.Store.YES));
        document.add(new TextField(TITLE, source.title(), Field.Store.YES));
        document.add(new TextField(METADATA, source.metadata(), Field.Store.YES));
        document.add(new TextField(CONTENT, source.searchContent(), Field.Store.YES));
        document.add(new TextField(ATTRIBUTES, String.join("\n", source.attributes().values()), Field.Store.YES));
        source.attributes().forEach((key, value) -> {
            if (key.matches("[A-Za-z0-9_.-]{1,100}")) {
                document.add(new StringField(ATTRIBUTE_PREFIX + key, value, Field.Store.NO));
            }
        });
        return document;
    }

    private static String snippet(ProjectSearchScope scope, Document document, List<String> terms) {
        List<String> sources = switch (scope) {
            case TITLE -> List.of(document.get(TITLE));
            case PATH -> List.of(document.get(PATH));
            case METADATA, ATTRIBUTES -> List.of(document.get(METADATA), document.get(ATTRIBUTES));
            case CONTENT -> List.of(document.get(CONTENT));
            case ALL -> List.of(document.get(CONTENT), document.get(TITLE),
                    document.get(METADATA), document.get(ATTRIBUTES), document.get(PATH));
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
}
