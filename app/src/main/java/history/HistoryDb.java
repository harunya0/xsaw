package history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HistoryDb implements AutoCloseable {
    private final Path dbPath;
    private Connection connection;

    public HistoryDb() {
        this(XsawPaths.getDatabasePath());
    }

    public HistoryDb(Path dbPath) {
        this.dbPath = dbPath;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                if (dbPath.getParent() != null) {
                    Files.createDirectories(dbPath.getParent());
                }
            } catch (IOException e) {
                throw new SQLException("Failed to create database directory", e);
            }
        

            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }

            initSchema();
        }
        return connection;
    }

    private void initSchema() throws SQLException {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS operations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                batch_id TEXT NOT NULL,
                operation_type TEXT NOT NULL,
                source_path TEXT NOT NULL,
                destination_path TEXT,
                trash_uuid TEXT,
                file_size INTEGER NOT NULL,
                is_directory INTEGER NOT NULL,
                status TEXT NOT NULL,
                timestamp TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_operations_batch_id ON operations(batch_id);
            CREATE INDEX IF NOT EXISTS idx_operations_timestamp ON operations(timestamp);
            CREATE INDEX IF NOT EXISTS idx_operations_status ON operations(status);
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTableSql);
        }
    }

    public synchronized long record(OperationRecord record) throws SQLException {
        return recordBatch(List.of(record)).get(0);
    }

    public synchronized List<Long> recordBatch(List<OperationRecord> records) throws SQLException {
        Connection conn = getConnection();
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        String sql = """
            INSERT INTO operations (
                batch_id, operation_type, source_path, destination_path,
                trash_uuid, file_size, is_directory, status, timestamp
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        
        List<Long> generatedIds = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (OperationRecord rec : records) {
                pstmt.setString(1, rec.batchID());
                pstmt.setString(2, rec.operationType().name());
                pstmt.setString(3, rec.sourcePath());
                pstmt.setString(4, rec.destinationPath());
                pstmt.setString(5, rec.trashUuid());
                pstmt.setLong(6, rec.fileSize());
                pstmt.setInt(7, rec.isDirectory() ? 1 : 0);
                pstmt.setString(8, rec.status().name());
                pstmt.setString(9, rec.timestamp().toString());
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedIds.add(rs.getLong(1));
                    }
                }
            }
            conn.commit();
            return generatedIds;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    public synchronized Optional<String> findLatestBatchId() throws SQLException {
        String sql = "SELECT batch_id FROM operations WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return Optional.of(rs.getString("batch_id"));
            }
            return Optional.empty();
        }
    }

    public synchronized List<OperationRecord> findByBatchId(String batchId) throws SQLException {
        String sql = "SELECT * FROM operations WHERE batch_id = ? ORDER BY id ASC";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, batchId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<OperationRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    public synchronized List<OperationRecord> findRecent(int limit) throws SQLException {
        String sql = "SELECT * FROM operations ORDER BY id DESC LIMIT ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<OperationRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    public synchronized void updateBatchStatus(String batchId, OperationStatus status) throws SQLException {
        String sql = "UPDATE operations SET status = ? WHERE batch_id = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, batchId);
            pstmt.executeUpdate();
        }
    }

    public synchronized void updateAllActiveTrashToPurged() throws SQLException {
        String sql = "UPDATE operations SET status = ? WHERE trash_uuid IS NOT NULL AND status = ?";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, OperationStatus.PURGED.name());
            pstmt.setString(2, OperationStatus.ACTIVE.name());
            pstmt.executeUpdate();
        }
    }

    public synchronized List<OperationRecord> findOlderThan(Instant cutoff) throws SQLException {
        String sql = "SELECT * FROM operations WHERE timestamp < ? AND trash_uuid IS NOT NULL AND status = 'ACTIVE'";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, cutoff.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<OperationRecord> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    private OperationRecord mapRow(ResultSet rs) throws SQLException {
        return new OperationRecord(
            rs.getLong("id"),
            rs.getString("batch_id"),
            OperationType.valueOf(rs.getString("operation_type")),
            rs.getString("source_path"),
            rs.getString("destination_path"),
            rs.getString("trash_uuid"),
            rs.getLong("file_size"),
            rs.getInt("is_directory") == 1,
            OperationStatus.valueOf(rs.getString("status")),
            Instant.parse(rs.getString("timestamp"))
        );
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
