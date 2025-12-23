# ZVFS - A Virtual File System

A simple virtual file system implementation in Python and Java, supporting basic file operations like create, read, write, delete, and defragmentation.

This solution was developed as part of assignment 3 for the Software Construction (L+E) HS 2025 course at the University of Zurich. And in collaboration with Merilin S. and Serena D.

## Features

- **File System Management**: Create, format, and manage virtual file systems.
- **File Operations**: Add, remove, list, and extract files.
- **Defragmentation**: Optimize file system storage.
- **Cross-Language Implementation**: Available in both Python and Java for comparison and learning.
- **Binary Format**: Uses structured binary formats for headers and file entries.

## Project Structure

- `zvfs.py`: Python implementation of the ZVFS file system.
- `zvfs.java`: Java implementation of the ZVFS file system.
- `filesystem1.zvfs`, `filesystem2.zvfs`: Example virtual file systems.
- `test_file1.txt`, `test_file2.txt`: Sample files for testing.

## Installation

### Prerequisites

- Python 3.x for the Python version.
- Java 11+ for the Java version.

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/zvfs-filesystem.git
   cd zvfs-filesystem
   ```

2. For Python:
   
   - No additional dependencies required (uses built-in `struct` module).

3. For Java:
   
    - Compile the Java files:
     ```bash
     javac zvfs.java
     ```

## Usage

### Python Version

Run the Python script to interact with the file system:

```bash
python zvfs.py
```

Available commands:
- `mkfs <filesystem>`: Create a new file system.
- `addfs <filesystem> <file>`: Add a file to the file system.
- `rmfs <filesystem> <filename>`: Remove a file from the file system.
- `lsfs <filesystem>`: List files in the file system.
- `extractfs <filesystem> <filename>`: Extract a file from the file system.
- `defragfs <filesystem>`: Defragment the file system.

### Java Version

Compile and run the Java version:

```bash
javac zvfs.java
java zvfs
```

Similar commands as Python version.

## Binary Format

- **Header**: 64 bytes containing magic number, version, file count, etc.
- **File Entries**: 64 bytes each, up to 32 files, with name, offsets, timestamps.
- **Data Area**: Stores file contents with alignment.

The initial `<` character specifies the Little-Endian byte order.

- The subsequent characters define the data type and size:
    - `s`: Fixed-length byte string.
    - `B`: Unsigned 1-byte integer (0-255).
    - `H`: Unsigned 2-byte integer (0-65,535).
    - `I`: Unsigned 4-byte integer.
    - `Q`: Unsigned 8-byte integer.

- `HEADER_FORMAT = "<8s B B H H H H H I I I I H 26s"`

**The Header is structured this way:**

- `magic`, `8s`= 8 bytes: identifier for the `Magic Number`, which has a fixed string: `b"ZVFSDSK1"`.
- `version`, `B`= 1 byte: used to memorize the File system version. It can represnt values from 0 to 255
- `flags`, `B`= 1 byte: it represents flag status
- `reserved0`, `H`= 2 bytes: zero padding
- `file_count`, `H`= 2 bytes: it representes the `file_count`: Current number of active files stored in the system
- `file_capacity`,`H`= 2 bytes: it represents `file_capacity`: the maximum number of files supported, which in our case is 32
- `file_entry_size`, `H`= 2 bytes: tells the size of a file (`ENTRY_SIZE`, so 64)
- `reserved1` `H`= 2 bytes: zero padding
- `file_table_offset`, `I`= 4 bytes: represents the offset of the beginning of the file table (`FILE_TABLE_OFFSET`), which is 64.
- `data_start_offset`, `I`= 4 bytes: represents the offset of the beginning of the data (`DATA_START_OFFSET`), which is 2112.
- `next_free_offset`,`I`= 4 bytes: it points where the the next free place is, when adding a new file.
- `free_entry_offset`,`I`= 4 bytes: it points at the next free slot
- `deleted_files`, `H`= 2 bytes: it counts the files that have been removed.
- `reserved2`,`26s`= 26 bytes: a long string of bytes used as padding, to make sure that the Header size stays at exactly 64 bytes.

- `FILE_ENTRY_FORMAT = "<32s I I B B H Q 12s"`

**The file entry is structured this way:**

- `name`,`32s`= 32 bytes: file name (padded with null bytes to 32 bytes).
- `start`,`I` = 4 bytes: the starting offset of the file's content in the data
- `length`,`I`= 4 bytes: the size of the file's content in bytes.
- `type`,`B`= 1 byte: type of file
- `flag`,`B`= 1 byte: status flag (set to 1 if the file has been deleted).
- `reserved0`,`H`= 2 bytes: reserved.
- `created`,`Q`= 8 bytes: 8-byte timestamp for file creation.
- `reserved1`,`12s`= 12 bytes: final padding to make sure the file entry is 64 bytes in total

