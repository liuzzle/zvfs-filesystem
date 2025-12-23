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

## File System Commands
### Python Commands
#### `mkfs()`: Create a new file

command:

```bash
python zvfs.py mkfs <filesystem>
```

**Functionality**:

This method creates a new empty `.zvfs` filesystem. Initializes the header with defaults (e.g., magic string, capacity=32, no files) and fills the file entry table with 32-zeroed entries. We handle existing files by prompting the client for overwrite or abort. Also populate header and zero out entries; data region starts empty at offset 2112.

**Implementation**:

- Checks if `self.fs_path` exists using `Path(self.fs_path).exists()`.
- If exists, prompts user: input `'OVERWRITE'` or `'ABORT'`; asserts valid input; aborts with `sys.exit` if `'ABORT'`, else deletes with `os.remove`.
- Opens file in write-binary (`"wb"`):
  - Writes default header: `f.write(self.header.pack())`. - Packs one empty entry: `entry = self.empty_file.pack()`. - Writes it 32 times: `for i in range(MAX_FILES): f.write(entry)`.
  - Prints that FS has been created (if successful)
  - No header updates needed post-write, as defaults are used.

**Alternative Approaches**:

Instead of prompting, we could always overwrite or raise an error, but we feel like our current interactive approach adds user safety / is nicer.

#### `gifs()`: Get info about a file

command:

```bash
python zvfs.py gifs <filesystem>
```

**Functionality**:

This method outputs filesystem info: filename, non-deleted files count, free entries, deleted files count, total file size (bytes). Excludes deleted files from "free". Validates file existence.

**Implementation**:

- Asserts file exists with `Path(self.fs_path).exists()`.
- Opens in read-binary (`"rb"`): Reads header with `Header.read_header(f)`.
- Extracts: `files = header_fs.file_count` (active files), `deleted_files = header_fs.deleted_files`.
- Computes free: `free_entries = header_fs.file_capacity - files - deleted_files` (excludes deleted, as they occupy slots until defrag).
- Gets total size: `fs_size = os.path.getsize(self.fs_path)`.
- Prints formatted string with all info.
- No writes, read-only.

#### `addfs()`: Add file

command:

```bash
python zvfs.py addfs <filesystem> <file_to_add>
```

**Functionality**:

This method adds a file from disk to the `.zvfs` file. Creates a new file entry, appends padded data to the end, updates header. Checks for duplicates/existence; validates name length and file existence. Appends data without moving others; updates metadata.

**Implementation**:

- Asserts filesystem and `new_file` exist using `Path`.
- Opens filesystem in read-write binary (`"r+b"`): Reads header and entries.
- Finds first free entry slot: `free_position = next((idx for idx, entry in enumerate(entries) if entry.is_empty()), None)`.
- Encodes filename: `filename = FileSystem.encode_filename(new_file)`.
- Checks for duplicates: Tries `FileSystem.compare_filenames`; if no error (file exists), aborts with `sys.exit`; catches `AssertionError` to proceed.
- Reads `new_file` content: `file_content = file.read()`, `file_size = len(file_content)`.
- Validates name: Re-encodes and asserts `len(name_encoded) <= 31` (redundant but safe).
- Gets append position: `current_data_offset = header_fs.next_free_offset`.
- Computes padding: `padded = (file_size + (DATA_ALIGNMENT - 1)) // DATA_ALIGNMENT * DATA_ALIGNMENT`, `padding_size = padded - file_size`.
- Writes data + padding: `f.seek(current_data_offset); f.write(file_content); f.write(b"\x00" * padding_size)`.
- Creates new `FileEntry`: With encoded name, start offset, length, type=0, flag=0, current UNIX timestamp (`int(time.time())`).
- Writes entry: Computes offset `FILE_TABLE_OFFSET + free_position * ENTRY_SIZE`, uses `FileEntry.write_entry`.
- Updates/writes header: Increments `file_count`, sets `next_free_offset = current_data_offset + padded`, uses `Header.write_header`.
- Prints that file was added to FS (if successful)

#### `getfs()`: Extract file from filesystem

command:

```bash
python getfs <file system file> <file to extract>
```

- Pure read operation, the `.zvfs` file is not modified.
- Finds the file entry by name.
- Reads exactly `entry.length` bytes starting at `entry.start`.
- Writes those bytes to a new file on disk.
- If the output file already exists, asks the user to overwrite or choose a new name.
- opens filesystem in read/write binary (`"r+b"`)
- locates correct entry via `compare_filename`
- uses `Header` / `FileEntry`pack/unpack helpers for all binary I/O

#### `rmfs()`: Mark file as deleted

command:

```bash
python rmfs <file system file> <file in filesystem>
```

- Marks the file as deleted without moving any data.
- Sets `entry.flag = 1`.
- Decrements `header.file_count`, increments `header.deleted_files`.
- Rewrites only the changed file entry and the header.
- Does not touch the file’s data blocks (they stay until `dfrgfs`).
- opens filesystem in read/write binary (`"r+b"`)
- locates correct entry via `compare_filename`
- uses `Header` / `FileEntry`pack/unpack helpers for all binary I/O

#### `lsfs()`: List files

command:

```bash
python lsfs <file system file>
```

This function lists all the file in the provided filesystem. For every file, print its name, size (in bytes) and creation time. Make sure to not print files marked as deleted.

- Here, we utilize the helper function in the class FileEntry to read all the entries and return a list of the ones in the file system.
- With a loop, we ignored the empty entry slots or the ones that are remove
- If the `if` statement above is not entered, then it means that a entry was found.
- We retrieve the file name, its length and use the time module to better display the timestamp
- The entries are then printed onto the terminal.

#### `catfs()`: Print file contents

command:

```bash
python catfs <file system file> <file in filesystem>
```

This function prints out the file contents of a specified file from the filesystem to the console.

- Here, we encode the filename and only read the file entries, since the header is not needed or is to not be changed.
- We use a helper function to find the entry with the same encoded file name and thus retrieve the file index.
- This index then retrieves the entry from a list.
- We then use `.seek()` to move the reader pointer to the start position of the entry data and read it to the print the utf-8 decoded data

#### `dfrgfs()`: Defragment file system

command:

```bash
python dfrgfs <file system file>
```

This function defragments the file system. This operation removes all files marked from deletion from the system, along with their respective file entries. Afterwards, it compacts the file entries and the file data (moves everything up to fill up the available space, so that no 64 byte block gaps exist). When running this command, you should print out how many files were defragmented and how many bytes of file data were freed.

##### Filtering

- This was definetly the most confusing function to write, since we tried different approaches and many did not work well, e.g., using a dictionary for documentation of which files to keep, but then we realized that we also need to keep track of the ones that need to be removed, since otherwise, we would need to do so later on. We then came to the decision that creating two lists for the tracking, would be the easiest. Thus, we loop through all entries, skip empty ones and filter them based on the `flag` variable.
- By creating a list with the entries that need to be removed, we can get the amount and also the bytes with the `length` variable.

##### File data compacting

- After the filtering, we need to write the data new, so that it has no gaps.
- We loop through the entries that we need to keep and start at the data offset, the read the data and write it there. Then we recalculate the padding and update the positions (shift positions by data).

##### Rewriting the FileEntry table

- We set the writer pointer, which overwrites the old entries and write all the entries into the file system that need to be kept.
- The rest of the free spaces in the system are filled with empty file entries.

##### Rewrite header

- The last step is to update the header, specifically the new file count without the deleted ones, set the deleted count back to zero, update the next free offset for entry data and compute the new free entry offset by adding to the file table offset the files that were kept times their size.

### The main function

- In the main function, we first check if the argument count is correct. Since the script counts as an argument in Python we should have at least the script, the command and a file system path. Then at max the previous arguments, plus a text file (so four).
- The next thing is to create an instance of the file system.
- For the algorithm responsible for calling the correct method, we first did a classic if, elif else approach, but that seemed to hard coded. We then tried intro-spection but it resultet always in errors. Thereby, we decided to create a dictionary that stores the command names and their callables. Like this, one can index the dictionary for the right method.
- We added a check that if there are more than 3 arguments, it means that it's a function that takes a text file and is thus called accordingly. Otherwise. it is called with no arguments. After the writing of the code, we also throught that \*args could be used for this, but we did not end up using it

### Java Commands
#### `mkfs()`: Create a new file
```bash
java zvfs mkfs <filesystem>
```

#### `gifs()`: Get info about a file
```bash
java zvfs gifs <filesystem>
```

#### `addfs()`: Add file

```bash
java zvfs addfs <filesystem> <file_to_add>
```

#### `getfs()`: Extract file from filesystem

```bash
java zvfs getfs <file system file> <file to extract>
```

#### `rmfs()`: Mark file as deleted

```bash
java zvfs rmfs <file system file> <file in filesystem>
```

#### `lsfs()`: List files

```bash
java zvfs lsfs <file system file>
```

#### `catfs()`: Print file contents

```bash
java zvfs catfs <file system file> <file in filesystem>
```

#### `dfrgfs()`: Defragment file system

```bash
java zvfs dfrgfs <file system file>
```
