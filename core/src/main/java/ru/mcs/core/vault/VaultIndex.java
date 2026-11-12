package ru.mcs.core.vault;

import java.io.IOException;
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
import java.util.List;
import java.util.Optional;

/** Пересобираемый SQLite-кэш. Все методы сериализуются; источником истины остаются Markdown-файлы. */
final class VaultIndex implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(VaultIndex.class.getName());
    private static final int BATCH_SIZE = 500;

    private final Path root;
    private final Connection connection;

    VaultIndex(Path root) throws IOException, SQLException {
        this.root = VaultPaths.normalize(root);
        Path database = VaultPaths.databaseFile(root);
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS notes ("
                    + "path TEXT PRIMARY KEY,"
                    + "title TEXT NOT NULL,"
                    + "front_matter TEXT NOT NULL,"
                    + "content TEXT NOT NULL,"
                    + "size INTEGER NOT NULL,"
                    + "modified_ms INTEGER NOT NULL"
                    + ")");
            statement.executeUpdate("PRAGMA user_version=1");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    synchronized void rebuild() throws IOException, SQLException {
        replaceAll(MarkdownScanner.scan(root, root));
    }

    private void replaceAll(List<MarkdownDocument> documents) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO notes(path,title,front_matter,content,size,modified_ms) VALUES(?,?,?,?,?,?)")) {
            delete.executeUpdate("DELETE FROM notes");
            int pending = 0;
            for (MarkdownDocument document : documents) {
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
    }

    synchronized void refresh(Path path) {
        if (VaultPaths.isExcluded(root, path)) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            return;
        }
        try {
            if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                refreshTree(normalized);
            } else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    && VaultPaths.isMarkdown(normalized)) {
                upsert(MarkdownDocument.read(root, normalized));
            } else {
                removePath(normalized);
            }
        } catch (IOException | SQLException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot refresh vault index for " + normalized, e);
        }
    }

    private void refreshTree(Path directory) throws IOException, SQLException {
        for (MarkdownDocument document : MarkdownScanner.scan(root, directory)) {
            upsert(document);
        }
    }

    private void upsert(MarkdownDocument document) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notes(path,title,front_matter,content,size,modified_ms) VALUES(?,?,?,?,?,?) "
                        + "ON CONFLICT(path) DO UPDATE SET title=excluded.title,"
                        + "front_matter=excluded.front_matter,content=excluded.content,"
                        + "size=excluded.size,modified_ms=excluded.modified_ms")) {
            bind(statement, document);
            statement.executeUpdate();
        }
    }

    private void removePath(Path path) throws SQLException {
        String relative = VaultPaths.relativePath(root, path);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM notes WHERE path=? OR substr(path,1,length(?)+1)=? || '/'")) {
            statement.setString(1, relative);
            statement.setString(2, relative);
            statement.setString(3, relative);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, MarkdownDocument document) throws SQLException {
        statement.setString(1, document.path());
        statement.setString(2, document.title());
        statement.setString(3, document.frontMatter());
        statement.setString(4, document.content());
        statement.setLong(5, document.size());
        statement.setLong(6, document.modifiedMs());
    }

    synchronized List<VaultNote> notes() throws SQLException {
        List<VaultNote> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path,title FROM notes ORDER BY path");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String path = rs.getString(1);
                result.add(new VaultNote(root.resolve(path).normalize(), path, rs.getString(2)));
            }
        }
        return List.copyOf(result);
    }

    synchronized Optional<MarkdownDocument> find(String path) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT path,title,front_matter,content,size,modified_ms FROM notes WHERE path=?")) {
            statement.setString(1, path);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MarkdownDocument(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getLong(6)));
            }
        }
    }

    synchronized int count() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM notes")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot close vault index", e);
        }
    }
}
