# xsaw

A cross-platform CLI file manager and directory analyzer written in Java.

xsaw is a command-line tool for analyzing, organizing, and managing
files and directories on Windows and Linux.

## Features

- **Fast Parallel File Search** (`fi`, alias `f`): Regex, extension filter, case-sensitivity, file/dir filtering.
- **Directory Analysis & Size Breakdown** (`du`, alias `d`): Top-N rankings, compact 4-column extension grid, extension filtering.
- **Unix-style Pipeline Integration** (`f | du`): Pipe search results directly into directory analysis.
- **Fast Content Search** (`gr`, alias `g`): Regex, line numbering, inverted match, count only.
- **Safe File Move** (`mv`, alias `m`): Interactive conflict resolution (Overwrite, Rename, Skip, Compare, Cancel), batch support, dry-run.
- **Safe File Removal** (`rm`, alias `r`): Moves targets to an isolated trash vault (`~/.xsaw/trash`) with 30-day auto-purge.
- **Trash Purge** (`purge`, alias `clean`): Irreversible permanent deletion with interactive confirmation (`[y/N]`) and `--days` filter.
- **Operation History & Export** (`log`, alias `l`): Inspect past operations in a table or export to JSON (`-o log`).
- **Operation Rollback & Redo** (`undo`, alias `u` / `redo`, alias `re`): Restore deleted or moved files with overwrite protection.
- **SQLite Operation Logging**: Stored in `~/.xsaw/history.db` with WAL mode for speed and durability.
- **Zero-Dependency Native Binaries**: Packaged for Windows (.exe) and Linux via GraalVM/jlink (`dist/xsaw`).

## Directory Analysis

Analyze the contents of a directory (alias: `d`):

```bash
xsaw du ./Downloads
# or using the short alias
xsaw d ./Downloads
```

Example:

```text
Directory: ./Downloads

Files:        12,481
Directories:   1,203
Total size:   84.2 GB
Elapsed:       1.42 s

Extension statistics:

.zip        31.2%
.mp4        24.8%
.jpg        18.4%
.pdf         7.1%
Other       18.5%
```

xsaw can analyze large directory trees in parallel to reduce the time
required for filesystem analysis.

### Options

* `-n, --topN <number>`: Number of top file extensions to display (default: `4`, set `0` to display all).
* `-l, --list-only`: List file extensions in a 4-column compact grid without percentage statistics.
* `-e, --ext <ext...>`: Filter analysis by specific file extensions (comma-separated like `java,txt` or repeated flags).

#### Display custom number of extensions (`-n`)

```bash
# Display top 10 extensions
xsaw du -n 10 ./Downloads

# Display all extensions (without grouping into 'Other')
xsaw du -n 0 ./Downloads
```

#### List extension names only in a grid (`-l`)

```bash
xsaw du -l ./Downloads
```

Example:

```text
Directory: ./Downloads

Files:        12,481
Directories:   1,203
Total size:   84.2 GB
Elapsed:       1.42 s

extensions:
.zip        .mp4        .jpg        .pdf        
.png        .exe        .txt        .java       
```

#### Combine options (`-l -n 0`)

List all existing extensions in a compact grid:

```bash
xsaw du -l -n 0 ./Downloads
```

## File Search

High-speed parallel file and directory search powered by Virtual Threads (alias: `f`):

```bash
xsaw fi <query> [path]
# or using the short 1-letter alias
xsaw f <query> [path]
```

Example:

```bash
xsaw f Result .
```

```text
src\main\java\org\example\FileResult.java
src\main\java\org\example\FindResult.java
src\test\java\org\example\FileResultTest.java
src\test\java\org\example\FindResultTest.java

Found 4 matches in 27 ms.
```

### Options

* `-s, --case-sensitive`: Perform a case-sensitive search (default: case-insensitive).
* `-d, --dir-only`: Search for directories only.
* `-f, --file-only`: Search for files only.
* `-e, --ext <ext...>`: Filter results by file extensions (comma-separated like `java,txt` or repeated flags).
* `-r, --regex`: Treat search query as a regular expression.

#### Regular expression search (`-r`)

```bash
# Match files with digits (e.g. order_123.json, test_01.java)
xsaw f "order_\d+" . -r

# Search with regex and case sensitivity
xsaw f "^[A-Z].*" . -r -s
```

#### Filter by file extension (`-e`)

```bash
# Search for .java files containing "Result"
xsaw f Result . -e java

# Search for both .java and .kt files
xsaw f Result . -e java,kt
# Or using repeated flags
xsaw f Result . -e java -e kt
```

#### Search directories only (`-d`)

```bash
# Find only directories matching "test"
xsaw f test . -d
```

#### Search files only (`-f`)

```bash
# Find only files matching "main"
xsaw f main . -f
```

#### Case-sensitive search (`-s`)

```bash
# Match exact casing
xsaw f UpperCase . -s
```

### Pipeline Integration (`f | du`)

Connect `fi` (or `f`) directly with `du` (or `d`) via standard pipelines to analyze the aggregate size and extension statistics of search results:

```bash
# Search for files matching "Result" and inspect their disk usage and extension breakdown
xsaw f Result . | xsaw du
```

Example:

```text
Found 4 matches in 27 ms.
Directory: (standard input)

Files:                  4
Directories:            0
Total size:        6.6 KB
Elapsed:            12 ms

Extension statistics:

java       100.0%
```

Search options can be combined seamlessly:

```bash
# Analyze only .java files matching "Test"
xsaw f Test . -e java | xsaw du
```

## File Operations

Move files from the command line:

```bash
xsaw mv ./Downloads/foo.zip ./Archive/
# Or using the short 1-letter alias
xsaw m ./Downloads/foo.zip ./Archive/
```

### Options

* `-d, --dry-run`: Perform a trial run without making any changes.
* `-f, --force`: Force overwrite of existing files.
* `-n, --no-clobber`: Do not overwrite existing files (skip them).
* `-v, --verbose`: Enable verbose output.

#### Move multiple files to a directory

```bash
xsaw mv a.txt b.txt c.txt ./Archive/
```

#### Dry-run simulation (`-d`)

```bash
xsaw mv foo.zip ./Archive/ -d
```

File operations are recorded automatically.

The recorded history can be used to determine where a file was
previously moved.

For example:

```text
Previous operation found:

./Downloads/foo.zip
        ↓
./Archive/foo.zip
```

xsaw can use this information when performing subsequent operations.

## Safe Delete (`rm`, alias `r`)

Safely remove files or directories by isolating them into the trash vault (`~/.xsaw/trash/<UUID>`):

```bash
xsaw rm foo.txt
# Or using the short alias
xsaw r foo.txt

# Remove directories recursively
xsaw rm -r ./OldFolder/

# Verbose output (displays trash UUID and size)
xsaw rm -v bar.png
```

### Options

* `-r, -R, --recursive`: Recursively remove directories and their contents.
* `-f, --force`: Ignore non-existent files without error.
* `-v, --verbose`: Display detailed file sizes and trash UUIDs.
* `--purge, --empty-trash`: Completely empty trash vault instead of removing files.
* `?`, `/?`, `help`: Display help message.

---

## Trash Purge (`purge`, alias `clean`, `empty-trash`)

Permanently and irreversibly empty files from the trash vault:

```bash
xsaw purge
# Or using the short alias
xsaw clean
```

By default, xsaw issues an interactive confirmation prompt:
```text
WARNING: This operation permanently deletes items from the trash vault and cannot be undone!
Are you sure you want to proceed? [y/N]: 
```

### Options

* `-y, --yes`: Bypass interactive confirmation prompt.
* `-f, --force`: Bypass confirmation prompt (alias for `-y`).
* `--days <N>`: Only purge items older than N days (e.g. `xsaw purge --days 30`).

---

## Operation History (`log`, alias `l`, `history`)

Inspect past operations stored in the SQLite database (`~/.xsaw/history.db`):

```bash
# View the 10 most recent operations
xsaw log
# Or using the short alias
xsaw l -n 5
```

Example table output:
```text
ID    TIMESTAMP            TYPE     STATUS     DETAILS
--------------------------------------------------------------------------------
15    2026-09-07 10:50:12  REMOVE   ACTIVE     bar.png (UUID: a1b2c3d4...)
14    2026-09-07 10:45:00  MOVE     ACTIVE     foo.txt -> archive/foo.txt
13    2026-09-07 10:30:22  MOVE     ACTIVE     new.txt -> dest.txt [OVERWRITTEN]
--------------------------------------------------------------------------------
Showing 3 recent operations.
```

### Export to JSON (`-o`)
```bash
# Export the last 20 operations to log.json
xsaw l -n 20 -o log
```

---

## Undo & Redo (`undo`, alias `u` / `redo`, alias `re`)

Roll back recent operations or advance previously undone operations:

```bash
# Undo the most recent operation
xsaw undo
# Or using the short 1-letter alias
xsaw u

# Undo a specific operation by UUID, batch ID, or operation ID
xsaw undo <UUID>

# Force overwrite if destination already has a file
xsaw undo -f

# Redo previously undone operations
xsaw redo
# Or using the alias
xsaw re
# Or via undo flag
xsaw undo --redo
```

### Conflict Protection during Undo
If the target location already has a file created after the operation, xsaw will not overwrite it unless `-f` (`--force`) is specified.

---

## Fast Content Search (`gr`, alias `g`)

High-speed parallel text/regex search within files:

```bash
xsaw gr "TODO" .
# Or using the short alias
xsaw g "class" src/ -n
```

### Options
* `-i, --ignore-case`: Case-insensitive pattern match.
* `-n, --line-numbers`: Print 1-based line numbers.
* `-v, --invert`: Invert match (print non-matching lines).
* `-c, --count`: Only print count of matching lines.
* `-r, --recursive`: Search directories recursively (default: true).
* `-s, --case-sensitive`: Force case-sensitive match.


## Cross Platform

xsaw is designed to run on:

* Windows
* Linux

Filesystem operations are implemented using Java's NIO filesystem
APIs to minimize platform-specific code.

## Why Java?

xsaw is designed to take advantage of Java's strengths in:

* `java.nio.file`
* Concurrent processing
* Virtual Threads
* Strong type safety
* Cross-platform filesystem APIs
* Mature SQLite ecosystem

The project is particularly focused on efficient processing of
large directory trees and large numbers of files.

## Planned Features

* Advanced duplicate file detection
* File hashing
* More detailed directory statistics
* Operation filtering
* Improved restore support
* Additional filesystem operations
* More detailed output formats

---

<sub>Built with Antigravity</sub>

