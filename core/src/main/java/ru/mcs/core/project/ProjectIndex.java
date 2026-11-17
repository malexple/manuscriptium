package ru.mcs.core.project;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import ru.mcs.spi.project.IndexContributorRegistry;
import ru.mcs.spi.project.IndexedDocument;

/** Пересобираемый SQLite-кэш. Все методы сериализуются; источником истины остаются файлы Project. */
final class ProjectIndex implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ProjectIndex.class.getName());
    private static final int BATCH_SIZE = 500;
    private static final int SCHEMA_VERSION = 2;

    private final Path root;
    private final Connection connection;
    private final LuceneProjectIndex lucene;
    private final IndexContributorRegistry contributors;

    ProjectIndex(Path root) throws IOException, SQLException {
        this(root, new ProjectIndexContributors());
    }

    ProjectIndex(Path root, IndexContributorRegistry contributors) throws IOException, SQLException {
        this.root = ProjectPaths.normalize(root);
        this.contributors = java.util.Objects.requireNonNull(contributors);
        Path database = ProjectPaths.databaseFile(root);
        Files.createDirectories(database.getParent());
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is missing", e);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            int version;
            try (ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
                version = rs.next() ? rs.getInt(1) : 0;
            }
            if (version != SCHEMA_VERSION) {
                statement.executeUpdate("DROP TABLE IF EXISTS notes");
                statement.executeUpdate("DROP TABLE IF EXISTS documents");
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS documents ("
                    + "path TEXT PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "metadata TEXT NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "search_content TEXT NOT NULL,"
                    + "attributes TEXT NOT NULL,"
                    + "size INTEGER NOT NULL,"
                    + "modified_ms INTEGER NOT NULL"
                    + ")");
            statement.executeUpdate("PRAGMA user_version=" + SCHEMA_VERSION);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        try {
            lucene = new LuceneProjectIndex(ProjectPaths.luceneDir(root));
        } catch (IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    synchronized void rebuild() throws IOException, SQLException {
        replaceAll(ProjectFileScanner.scan(root, root, contributors));
    }

    private void replaceAll(List<IndexedDocument> documents) throws IOException, SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO documents(path,title,metadata,content,search_content,attributes,size,modified_ms) "
                             + "VALUES(?,?,?,?,?,?,?,?)")) {
            delete.executeUpdate("DELETE FROM documents");
            int pending = 0;
            for (IndexedDocument document : documents) {
                bind(insert, document);
                insert.addBatch();
                if (++pending % BATCH_SIZE == 0) {
                    insert.executeBatch();
                }
            }
            insert.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        lucene.replaceAll(documents);
    }

    synchronized void refresh(Path path) {
        if (ProjectPaths.isExcluded(root, path)) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            return;
        }
        try {
            if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                refreshTree(normalized);
            } else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                Optional<IndexedDocument> document = ProjectFileScanner.read(root, normalized, contributors);
                if (document.isPresent()) {
                    upsert(document.orElseThrow());
                } else {
                    removePath(normalized);
                }
            } else {
                removePath(normalized);
            }
        } catch (IOException | SQLException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot refresh project index for " + normalized, e);
        }
    }

    private void refreshTree(Path directory) throws IOException, SQLException {
        for (IndexedDocument document : ProjectFileScanner.scan(root, directory, contributors)) {
            upsert(document);
        }
    }

    private void upsert(IndexedDocument document) throws IOException, SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO documents(path,title,metadata,content,search_content,attributes,size,modified_ms) "
                        + "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(path) DO UPDATE SET title=excluded.title,"
                        + "metadata=excluded.metadata,content=excluded.content,search_content=excluded.search_content,"
                        + "attributes=excluded.attributes,size=excluded.size,modified_ms=excluded.modified_ms")) {
            bind(statement, document);
            statement.executeUpdate();
        }
        lucene.update(document);
    }

    private void removePath(Path path) throws IOException, SQLException {
        String relative = ProjectPaths.relativePath(root, path);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM documents WHERE path=? OR substr(path,1,length(?)+1)=? || '/'")) {
            statement.setString(1, relative);
            statement.setString(2, relative);
            statement.setString(3, relative);
            statement.executeUpdate();
        }
        lucene.removePath(relative);
    }

    private static void bind(PreparedStatement statement, IndexedDocument document) throws SQLException {
        statement.setString(1, document.path());
        statement.setString(2, document.title());
        statement.setString(3, document.metadata());
        statement.setString(4, document.content());
        statement.setString(5, document.searchContent());
        statement.setString(6, attributesToString(document.attributes()));
        statement.setLong(7, document.size());
        statement.setLong(8, document.modifiedMs());
    }

    synchronized List<ProjectNote> notes() throws SQLException {
        List<ProjectNote> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path,title FROM documents ORDER BY path");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String path = rs.getString(1);
                result.add(new ProjectNote(root.resolve(path).normalize(), path, rs.getString(2)));
            }
        }
        return List.copyOf(result);
    }

    synchronized Optional<IndexedDocument> find(String path) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path,title,metadata,content,search_content,attributes,size,modified_ms "
                        + "FROM documents WHERE path=?")) {
            statement.setString(1, path);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new IndexedDocument(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getLong(7), rs.getLong(8),
                        attributesFromString(rs.getString(6))));
            }
        }
    }

    synchronized List<ProjectSearchResult> search(ProjectSearchQuery query) throws IOException {
        return lucene.search(root, query);
    }

    synchronized int count() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM documents")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String attributesToString(Map<String, String> attributes) {
        try {
            Properties properties = new Properties();
            attributes.forEach(properties::setProperty);
            StringWriter result = new StringWriter();
            properties.store(result, null);
            return result.toString();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Map<String, String> attributesFromString(String stored) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(stored));
            Map<String, String> result = new LinkedHashMap<>();
            properties.forEach((key, value) -> result.put(key.toString(), value.toString()));
            return result;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            lucene.close();
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot close Lucene index", e);
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot close project index", e);
        }
    }
}
