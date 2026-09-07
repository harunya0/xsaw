package history;

import java.time.Instant;

public record OperationRecord(
    long id,
    String batchID,
    OperationType operationType,
    String sourcePath,
    String destinationPath,
    String trashUuid,
    long  fileSize,
    boolean isDirectory,
    OperationStatus status,
    Instant timestamp
) {
    public static OperationRecord create(
        String batchID,
        OperationType operationType,
        String sourcePath,
        String destinationPath,
        String trashUuid,
        long fileSize,
        boolean isDirectory
    ) {
        return new OperationRecord(
            0,
            batchID,
            operationType,
            sourcePath,
            destinationPath,
            trashUuid,
            fileSize,
            isDirectory,
            OperationStatus.ACTIVE,
            Instant.now()
        );
    }
}
