package mv;

public record MoveOptions(
    boolean dryRun,
    boolean force,
    boolean noClobber,
    boolean verbose
) {
    public static final MoveOptions DEFAULT = new MoveOptions(false, false, false, false);
}
