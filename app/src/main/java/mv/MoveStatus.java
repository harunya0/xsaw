package mv;

public enum MoveStatus {
    MOVED,       // Normal move completed
    OVERWRITTEN, // Overwritten due to -f option
    SKIPPED,     // Skipped existing file due to -n option
    DRY_RUN,      // Simulation due to -d option
    RENAME      // Renamed due to -r option
}
