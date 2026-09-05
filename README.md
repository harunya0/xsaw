# xsaw

A cross-platform CLI file manager and directory analyzer written in Java.

xsaw is a command-line tool for analyzing, organizing, and managing
files and directories on Windows and Linux.

## Features

- Directory analysis
- File and directory statistics
- File classification by extension
- File size distribution
- Parallel filesystem analysis
- File moving
- Operation history
- SQLite-based operation log
- Move history
- Conflict detection
- Interactive conflict resolution
- File operation restoration

## Directory Analysis

Analyze the contents of a directory:

```bash
xsaw ls ./Downloads
````

Example:

```text
Directory: ./Downloads

Files:        12,481
Directories:   1,203
Total size:   84.2 GB

Extensions:

  .zip        31.2%
  .mp4        24.8%
  .jpg        18.4%
  .pdf         7.1%
  other       18.5%
```

xsaw can analyze large directory trees in parallel to reduce the time
required for filesystem analysis.

## File Operations

Move files from the command line:

```bash
xsaw mv ./Downloads/foo.zip ./Archive/
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

## Operation History

All file operations are stored in SQLite.

An operation records information such as:

```text
Operation
Timestamp
Source path
Destination path
File size
Hash
Status
```

This allows xsaw to inspect previous operations and use them for
restoration and future file management.

## Conflict Handling

xsaw does not silently overwrite existing files.

If a destination already contains a file with the same name,
xsaw displays a warning and lets the user choose how to proceed.

```text
Conflict detected.

Source:
  ./Downloads/foo.zip

Destination:
  ./Archive/foo.zip

Choose an action:

[1] Overwrite
[2] Rename
[3] Skip
[4] Compare
[5] Cancel
```

## Restore

Because file operations are recorded, xsaw can restore previous
operations when possible.

```bash
xsaw undo <operation-id>
```

The original path is checked before restoration to prevent
accidental overwrites.

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
* Search
* Operation filtering
* Improved restore support
* Additional filesystem operations
* More detailed output formats
