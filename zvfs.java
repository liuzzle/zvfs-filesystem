import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.time.Instant;
import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.Date;

public class zvfs {
    // The whole header is 64 bytes and each file entry is also 64 bytes
    public static final int HEADER_SIZE = 64;
    public static final int ENTRY_SIZE = 64;
    // Constant used for padding
    public static final int DATA_ALIGNMENT = 64;
    // In the file system a maximum of 32 files are supported
    public static final int MAX_FILES = 32;
    // We decided to make these global constants in case that we need to update
    // these values to their original states while doing a defragment etc.
    // If we start with the first file entry, it will start at byte 64, since it
    // lies directly after the header
    public static final int FILE_TABLE_OFFSET = HEADER_SIZE;
    // Now for the data_start_offset it should be 2112, so we do the header + the
    // amount of files * the file entry sizes, to point to where the file data
    // starts
    // So, it would be 64 + 32 * 64 = 2112
    public static final int DATA_START_OFFSET = HEADER_SIZE + MAX_FILES * ENTRY_SIZE;

    // This was the python code, where the format needed to be specifically defined
    // in form of a string beforehand

    // To ensure that the HEADER and the File entries follow a specific byte format,
    // we used the function struct from the chapter 17. Binary Data
    // We also had to consult the documentation of the function, to understand which
    // symbols to use in the strings: https://docs.python.org/3/library/struct.html
    // We decided to use little endian '<', since we saw that often one or the other
    // was used and that "Intel x86, AMD64 (x86-64), and Apple M1 are
    // little-endian". This was used for the header and the file entries
    // s: fixed-length byte string (we use 8 byte string for MAGIC)
    // B: unsigned 1-byte integer (0 - 255)
    // H: unsigned 2-byte integer (0 - 65535)
    // I: unsigned 4-byte integer (0 - 4294967295)
    // We used unsigned types, since the fields are things such as counts (file
    // amounts), sizes (bytes) or offsets (file positions) -> all these are usually
    // non-negative and thus this version fits better
    // public static final String HEADER_FORMAT = "<8s B B H H H H H I I I I H 26s";
    // public static final String FILE_ENTRY_FORMAT = "<32s I I B B H Q 12s";

    /*
     * #####################
     * ###### Header and FileEntry class
     * #####################
     */

    static class Header {
        // We made the variables public, since we wanted to be able to access them
        // outside of the class
        public byte[] magic; // 8 bytes
        public byte version; // 1 byte
        public byte flags; // 1 byte
        public short reserved0; // 2 bytes
        public short fileCount; // 2 bytes
        public short fileCapacity; // 2 bytes
        public short fileEntrySize; // 2 bytes
        public short reserved1; // 2 bytes
        public int fileTableOffset; // 4 bytes
        public int dataStartOffset; // 4 bytes
        public int nextFreeOffset; // 4 bytes
        public int freeEntryOffset; // 4 bytes
        public short deletedFiles; // 2 xwbytes
        public byte[] reserved2; // 26 bytes

        // This is the version where the default arguments are used
        // We also consulted this website to understand what default constructors are
        // (same as default arguments in Python)
        // https://www.geeksforgeeks.org/java/constructors-in-java/
        public Header() {
            this.magic = new byte[] { 'Z', 'V', 'F', 'S', 'D', 'S', 'K', '1' }; // to convert a string into its bytes we
                                                                                // needed to use this:
            // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/String.html
            this.version = 1;
            this.flags = 0;
            this.reserved0 = 0;
            this.fileCount = 0;
            this.fileCapacity = MAX_FILES;
            this.fileEntrySize = ENTRY_SIZE;
            this.reserved1 = 0;
            this.fileTableOffset = FILE_TABLE_OFFSET;
            this.dataStartOffset = DATA_START_OFFSET;
            this.nextFreeOffset = DATA_START_OFFSET;
            this.freeEntryOffset = FILE_TABLE_OFFSET;
            this.deletedFiles = 0;
            this.reserved2 = new byte[26];
        }

        // here the values are inserted
        public Header(byte[] magic, byte version, byte flags, short reserved0, short fileCount, short fileCapacity,
                short fileEntrySize, short reserved1, int fileTableOffset, int dataStartOffset, int nextFreeOffset,
                int freeEntryOffset,
                short deletedFiles, byte[] reserved2) {
            this.magic = magic; // to convert a string into its bytes we
                                // needed to use this:
            // https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/String.html
            this.version = version;
            this.flags = flags;
            this.reserved0 = reserved0;
            this.fileCount = fileCount;
            this.fileCapacity = fileCapacity;
            this.fileEntrySize = fileEntrySize;
            this.reserved1 = reserved1;
            this.fileTableOffset = fileTableOffset;
            this.dataStartOffset = dataStartOffset;
            this.nextFreeOffset = nextFreeOffset;
            this.freeEntryOffset = freeEntryOffset;
            this.deletedFiles = deletedFiles;
            this.reserved2 = reserved2;
        }

        public byte[] pack() {
            // this function converts header fields into bytes
            // For the format string in Python there is no equivalent in Java, the
            // .allocate() method needs the direct size values according to:
            // https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html
            // we first have 8 string bytes, then 2 * 1-byte integer, 5 * 2-byte integer, 4
            // * 4-byte integer, 1 * 2-byte integer, 26 string bytes
            // this is the same as the the string format in python
            // "<8s B B H H H H H I I I I H 26s"
            // if we sum it up we get 8 + 2 + 10 + 16 + 2 + 26 = 64
            // https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // now we need to use the same ordering as in python, so little-endian.
            buffer.put(this.magic); // 8
            buffer.put(this.version); // 1
            buffer.put(this.flags); // 1
            buffer.putShort(this.reserved0); // 2
            buffer.putShort(this.fileCount); // 2
            buffer.putShort(this.fileCapacity); // 2
            buffer.putShort(this.fileEntrySize); // 2
            buffer.putShort(this.reserved1); // 2
            buffer.putInt(this.fileTableOffset); // 4
            buffer.putInt(this.dataStartOffset); // 4
            buffer.putInt(this.nextFreeOffset); // 4
            buffer.putInt(this.freeEntryOffset); // 4
            buffer.putShort(this.deletedFiles); // 2
            buffer.put(this.reserved2); // 26
            // array() Returns the byte array that backs this buffer (optional operation).
            byte[] packed = buffer.array();

            return packed;
        }

        // From our research class methods do not exist in the same way, thereby we used
        // a static method here
        public static Header unpack(byte[] data) {
            // wrap(byte[] array) Wraps a byte array into a buffer.
            // https://docs.oracle.com/javase/8/docs/api/java/nio/ByteBuffer.html
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            Header header = new Header();
            // here we unpack all the values in the byte data
            buffer.get(header.magic);
            header.version = buffer.get();
            header.flags = buffer.get();
            header.reserved0 = buffer.getShort();
            header.fileCount = buffer.getShort();
            header.fileCapacity = buffer.getShort();
            header.fileEntrySize = buffer.getShort();
            header.reserved1 = buffer.getShort();
            header.fileTableOffset = buffer.getInt();
            header.dataStartOffset = buffer.getInt();
            header.nextFreeOffset = buffer.getInt();
            header.freeEntryOffset = buffer.getInt();
            header.deletedFiles = buffer.getShort();
            buffer.get(header.reserved2);
            // write the buffer's byte array to the file
            return header;
        }

        public static Header readHeader(String file) throws IOException {
            // This helper function reads the 64 byte header at offset 0 and returns the
            // Header object
            /*
             * In Java, every file resource should be closed using:
             * - try-with-resources
             */
            try (RandomAccessFile newFile = new RandomAccessFile(file, "r")) {
                // The seek() method sets the current file position in a file stream.
                newFile.seek(0);
                // We read 64 bytes from the start
                byte[] headerBytes = new byte[HEADER_SIZE];
                newFile.readFully(headerBytes);
                // We unpack the raw bytes into a Header object
                return Header.unpack(headerBytes);
            }
        }

        public static void writeHeader(Header headerInstance, RandomAccessFile newFile) throws IOException {
            // try (RandomAccessFile newFile = new RandomAccessFile(file, "w")) {
            // The header starts at byte 0
            newFile.seek(0);
            // We write the packed header bytes into the file
            newFile.write(headerInstance.pack());
        }
    }

    static class FileEntry {
        public byte[] name; // 32 bytes
        public int start; // 4 bytes
        public int length; // 4 bytes
        public byte type; // 1 byte
        public byte flag; // 1 byte
        public short reserved0; // 2 bytes
        public long created; // 8 bytes
        public byte[] reserved1; // 12 bytes

        // Here the default arguments version
        public FileEntry() {
            this.name = new byte[32];
            this.start = 0;
            this.length = 0;
            this.type = 0;
            this.flag = 0;
            this.reserved0 = 0;
            this.created = 0;
            this.reserved1 = new byte[12];
        }

        // Here, the version, where arguments are passed
        public FileEntry(byte[] name, int start, int length, byte type, byte flag, short reserved0, long created,
                byte[] reserved1) {
            this.name = name;
            this.start = start;
            this.length = length;
            this.type = type;
            this.flag = flag;
            this.reserved0 = reserved0;
            this.created = created;
            this.reserved1 = reserved1;
        }

        public byte[] pack() {
            // here we again need to calculate the amount of bytes to input in .allocate()
            // the format is <32s I I B B H Q 12s. We have 32-byte string, 2 * 4-byte
            // integers, 2 * 1-byte integers, 1 * 2-byte integer, 1 * 8-byte integer and
            // 12-byte string.
            // This results in 32 + 8 + 2 + 2 + 8 + 12 = 64 bytes
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // we use little-endian again
            buffer.put(this.name); // 32 bytes
            buffer.putInt(this.start); // 4 bytes
            buffer.putInt(this.length); // 4 bytes
            buffer.put(this.type); // 1 byte
            buffer.put(this.flag); // 1 byte
            buffer.putShort(this.reserved0); // 2 bytes
            buffer.putLong(this.created); // 8 bytes
            buffer.put(this.reserved1); // 12 bytes

            byte[] packed = buffer.array();

            return packed;
        }

        public static FileEntry unpack(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            // create a new FileEntry object to hold the unpacked data
            FileEntry entry = new FileEntry();
            // get (read) fields from the buffer in order
            buffer.get(entry.name);
            // read int (4 bytes) from the buffer
            entry.start = buffer.getInt();
            entry.length = buffer.getInt();
            // read byte from the buffer
            entry.type = buffer.get();
            entry.flag = buffer.get();
            // read short (2 bytes) from the buffer
            entry.reserved0 = buffer.getShort();
            // read long (8 bytes) from the buffer
            entry.created = buffer.getLong();
            // read byte array from the buffer
            buffer.get(entry.reserved1);
            return entry;
        }

        public boolean isEmpty() {
            // Returns True if the name is zero bytes
            for (byte b : this.name) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        }

        public String filename() {
            // This function decodes the UTF-8 file name
            // https://www.geeksforgeeks.org/java/how-to-split-a-string-in-cc-python-and-java/
            int i = 0; // we start at index 0
            // then while we are bit at the end of the name and the name at index i is not 0
            // (zero byte), we increment the index by one and check the next character
            while (i < this.name.length && this.name[i] != 0)
                i++;
            // until the set index, we decode the filname in utf-8 format and return it as a
            // string
            return new String(this.name, 0, i, StandardCharsets.UTF_8);
        }

        public static List<FileEntry> readFileEntry(String file) throws IOException {
            // We consulted these sources to create the list below and find the possible
            // list operations that are available
            // https://www.w3schools.com/java/java_arraylist.asp
            // https://www.w3schools.com/java/java_list.asp
            List<FileEntry> entries = new ArrayList<>();
            try (RandomAccessFile fullFile = new RandomAccessFile(file, "r")) {
                fullFile.seek(FILE_TABLE_OFFSET);

                // To understand for loops, we used this source:
                // https://www.w3schools.com/java/java_for_loop.asp
                for (int i = 0; i < MAX_FILES; i++) {
                    byte[] entryBytes = new byte[ENTRY_SIZE];
                    fullFile.readFully(entryBytes);
                    FileEntry entry = FileEntry.unpack(entryBytes);
                    entries.add(entry);
                }

                return entries;
            }
        }

        public static void writeEntry(FileEntry entryInstance, RandomAccessFile newFile, int position)
                throws IOException {
            // try (RandomAccessFile newFile = new RandomAccessFile(file, "w")) {

            newFile.seek(position);

            newFile.write(entryInstance.pack());
        }

    }

    /*
     * #####################
     * ###### File System
     * #####################
     */

    static class FileSystem {
        private String fsPath;
        private Header header;
        private FileEntry fileEntry;

        public FileSystem(String fsPath) {
            // We only check if the file system exists in the methods, because in mkfs, the
            // system does not exist at first
            this.fsPath = fsPath; // Path to the file system
            this.header = new Header(); // Create a default header
            this.fileEntry = new FileEntry(); // Create a default empty file entry
        }

        private static byte[] encodeFilename(String file) {
            /*
             * This function makes sure that the filename is encoded correctly,
             * such as if it is 31 + null terminator chars long and it utf-8 encodes it.
             * It then returns the filename.
             */
            // Source for asserts: https://www.w3schools.com/java/ref_keyword_assert.asp
            assert file.length() <= 31 : "File name can be max 31 characters long";
            // https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#getBytes--
            byte[] bytes = file.getBytes(StandardCharsets.UTF_8);
            byte[] fileName = new byte[32];
            System.arraycopy(bytes, 0, fileName, 0, bytes.length);
            return fileName;
        }

        private static Integer findFilenameIndex(List<FileEntry> fileEntries, byte[] fileName) {
            // This function compares then names of the file system file entries and returns
            // null if none if found or the index where the file entry with the same name
            // lies
            for (int i = 0; i < fileEntries.size(); i++) {
                if (Arrays.equals(fileEntries.get(i).name, fileName)) {
                    return i;
                }
            }
            return null;
        }

        public void mkfs() throws IOException {
            /*
             * This function create a new file <file system name>.zvfs that will represent a
             * new filesys-
             * tem. Populate the header with the appropriate values, fill out the file entry
             * region with zeroes for all 32
             * file entries. Make sure to include the correct values for the header
             * metadata.
             */

            // First, we need to check if the file system already exists, if so, we ask the
            // user to tell us, what they want to do:
            // abort the action or overwrite the system
            Path filesysPath = Path.of(this.fsPath);

            if (Files.exists(filesysPath)) {
                // We used the following source to implement the input function:
                // https://www.w3schools.com/java/java_user_input.asp
                // first we create a Scanner object
                @SuppressWarnings("resource")
                Scanner myObj = new Scanner(System.in);
                System.out.println(
                        "The file system {self.fs_path} already exists, do you wish to abort the action or overwrite the file system. Please write 'OVERWRITE' or 'ABORT': ");
                String decision = myObj.nextLine(); // Read user input

                assert (decision.equals("OVERWRITE") || decision.equals("ABORT"))
                        : "Your response needs to be 'OVERWRITE' or 'ABORT'. Action was aborted.";

                if (decision.equals("ABORT")) {
                    System.err.println("Action mkfs aborted, since file system already exists");
                    System.exit(1);
                } else {
                    Files.delete(Path.of(this.fsPath));
                }
            }
            // First we open the filesystem path for writing in binary
            try (FileOutputStream f = new FileOutputStream(this.fsPath)) {
                // write bytes here using f.write(...)
                f.write(this.header.pack());

                // Now we need to write the 32 empty file entries after the header
                // We first pack one empty entry
                byte[] entry = this.fileEntry.pack();
                // Now we populate 32 of them
                for (int i = 0; i < MAX_FILES; ++i) {
                    f.write(entry);
                }
            }

            System.out.printf("File system %s was created", this.fsPath);

        }

        public void gifs() throws IOException {
            /*
             * This function gets information for a specified filesystem file. The
             * information printed out
             * should be file name, number of files present (non deleted), remaining free
             * entries for new files (excluding
             * deleted files), and the number of files marked as deleted. Moreover, print
             * out the total size of the file.
             */
            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);

            assert Files.exists(filesysPath) : "File system does not exist";

            // now we read the header for the file system information
            Header headerFs = Header.readHeader(this.fsPath);
            // The amount of non-deleted files is in the attribute file_count
            int files = headerFs.fileCount;
            // Then we get the deleted files count
            int deletedFiles = headerFs.deletedFiles;
            // Here we get the free file spaces amount
            int freeEntries = headerFs.fileCapacity - headerFs.fileCount;
            // Then we get the OS file system size:
            // https://stackoverflow.com/questions/8721262/how-to-get-file-size-in-java
            long fsSize = Files.size(filesysPath);

            System.out.println(String.format(
                    "File System: %s,\nNon-deleted files: %d,\nFree entries: %d,\nDeleted files: %d,\nTotal system size: %d bytes",
                    this.fsPath, files, freeEntries, deletedFiles, fsSize));

        }

        public void addfs(String newFile) throws IOException {
            /*
             * This function adds a file from your disk to the filesystem file. In order
             * to do that, you need to create a new file entry, with the appropriate data,
             * and then append the file data
             * at the end of the file (with the proper byte alignment). Do not forget to
             * update the filesystem metadata
             * in the header as well.
             */

            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);
            Path newFilePath = Path.of(newFile);

            assert Files.exists(filesysPath) : "File system does not exist";
            assert Files.exists(newFilePath) : "File does not exist";

            // We need to read the header to later update it and to entries to add one
            Header headerFs = Header.readHeader(this.fsPath);
            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);
            // Here, we get the position where there is no entry
            Object index = null;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).isEmpty()) {
                    index = i;
                    break;
                }
            }
            // If all positions are taken, we raise an assertion error
            assert index != null : "There are no free entry positions left";
            // We encode the file name to be able to compare it with the file entries in the
            // file system
            byte[] filename = FileSystem.encodeFilename(newFile);
            // We need to add a check here, where we avoid the addition of the same file to
            // the file system
            Integer existingIndex = findFilenameIndex(entries, filename);
            if (existingIndex != null) {
                System.err.println("The file " + newFile + " already exists in the file system " + this.fsPath +
                        ". Please remove it first before adding it again.");
                System.exit(1);
            }

            // read the new file content to save the data
            // Source that helped us:
            // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#readAllBytes(java.nio.file.Path)
            byte[] fileContent = Files.readAllBytes(newFilePath);

            // characters as bytes so the length will be the size of the file data
            int fileSize = fileContent.length;

            // check for incorrect file name argument
            byte[] encodedName = newFile.getBytes(StandardCharsets.UTF_8);
            assert encodedName.length <= 31 : "File name is too long, can only be 31 utf-8 characters long";

            // we fill the rest of the name with zero bytes
            byte[] paddedName = new byte[32];
            System.arraycopy(encodedName, 0, paddedName, 0, encodedName.length);

            // now we check where the starting point is for the data
            int currentDataOffset = headerFs.nextFreeOffset;

            /*
             * Since the file data needs to be padded, we need to calculate the how much
             * padding need to be added (through zero bytes)
             * Here, we calculate the multiple that gets to when padded
             */
            int firstPart = (fileSize + (DATA_ALIGNMENT - 1));
            // Math.floorDiv is the equivalent of // in Python
            int padded = Math.floorDiv(firstPart, DATA_ALIGNMENT) * DATA_ALIGNMENT;
            int paddingSize = padded - fileSize;

            // In java we need to again open the filesystem file for writing
            try (RandomAccessFile fs = new RandomAccessFile(this.fsPath, "rw")) {
                fs.seek(currentDataOffset);
                fs.write(fileContent);
                // in java we create a byte[] object of a specified size
                byte[] padding = new byte[paddingSize];
                fs.write(padding);
                // to get the UNIX timestamp of the entry, we found this source:
                // https://www.geeksforgeeks.org/java/instant-getepochsecond-method-in-java-with-examples/
                int currentTime = (int) Instant.now().getEpochSecond();
                // next we create a new file entry with the current timestamp
                FileEntry newEntry = new FileEntry(paddedName, currentDataOffset, fileSize, (byte) 0, (byte) 0,
                        (short) 0,
                        (long) currentTime, new byte[12]);

                // now we need to write the entry into thefile table in the filesystem
                // calculating where the to write the entry in
                int entryOffset = FILE_TABLE_OFFSET + (int) index * ENTRY_SIZE;
                FileEntry.writeEntry(newEntry, fs, entryOffset);

                // and now we update the header
                headerFs.fileCount += 1;
                headerFs.nextFreeOffset = currentDataOffset + padded;

                // and now we write it into the header
                Header.writeHeader(headerFs, fs);

            }
            System.out.printf("File %s was added to the file system %s", newFile, this.fsPath);
        }

        public void getfs(String extractionFile) throws IOException {
            /*
             * Extracts a file from the filesystem to your disk. For
             * this action, make no modifications to the .zvfs file, simply produce the
             * requested file as an output.
             */
            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);
            // First we make sure that the file system exists
            assert Files.exists(filesysPath) : "File system does not exist";
            // We encode the file name to be able to compare it with the file entries in the
            // file system
            byte[] fileName = FileSystem.encodeFilename(extractionFile);
            // We read entries, to be able to get the file information
            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);
            // We utilize the same loop to as in addfs to find the file with the same name
            int fileIndex = findFilenameIndex(entries, fileName);
            // We specifically get the entry we are looking for
            FileEntry entry = entries.get(fileIndex);

            /*
             * We need to handle the case, where the file is already on disk
             * In this case, we decided to prompt the user to decide, whether they wish to
             * change the filename or if they want to overwrite it
             */
            Path filePath = Path.of(extractionFile);
            if (Files.exists(filePath)) {
                @SuppressWarnings("resource")
                Scanner myObj = new Scanner(System.in);
                System.out.println(
                        "The file you want to extract is already/still on your disk. If you wish to overwrite, then write 'OVERWRITE' or if you want to save it under a different name, then write the new filename with the correct file appendix (e.g. .txt). Write your answer here: ");
                String decision = myObj.nextLine(); // Read user input
                // here we learned how to say !=
                // https://stackoverflow.com/questions/16995809/opposite-of-java-equals-method
                if (!decision.equals("OVERWRITE")) {
                    extractionFile = decision;
                }
            }
            // we need to open the file system for reading
            try (RandomAccessFile fs = new RandomAccessFile(this.fsPath, "rw")) {
                fs.seek(entry.start);
                byte[] data = new byte[entry.length];
                fs.readFully(data);
                // then imbedded, we open the path to where in the dist the data is written to
                // restore the file
                try (FileOutputStream restoreFile = new FileOutputStream(extractionFile)) {
                    restoreFile.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.printf("File %s was retrieved form the file system %s", extractionFile, this.fsPath);
        }

        public void rmfs(String removalFile) throws IOException {
            /*
             * Marks a file in the filesystem as deleted. To do that,
             * set the respective entry's flag byte to 1 from 0. Do not delete the file
             * data, or move them around in this
             * command. Make sure to update the deleted files entry in the header as well.
             */
            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);
            Path filePath = Path.of(removalFile);

            assert Files.exists(filesysPath) : "File system does not exist";
            assert Files.exists(filePath) : "File does not exist";
            // Now we encode the filename again
            byte[] fileName = FileSystem.encodeFilename(removalFile);
            // Since we need to change things in the header and a file entry, both are read
            Header fsHeader = Header.readHeader(this.fsPath);
            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);
            // We again loop through the entries and compare the names
            int fileIndex = findFilenameIndex(entries, fileName);
            // We get the entry again, that we want to remove now
            FileEntry entry = entries.get(fileIndex);

            // For deleted files, we set the flag to 1
            entry.flag = 1;
            // And we update the header information
            fsHeader.deletedFiles += 1;
            fsHeader.fileCount -= 1;

            // Both the updated file entry and the header are rewritten into the file system
            int entryStartPosition = FILE_TABLE_OFFSET + fileIndex * ENTRY_SIZE;
            try (RandomAccessFile fs = new RandomAccessFile(this.fsPath, "rw")) {
                FileEntry.writeEntry(entry, fs, entryStartPosition);
                Header.writeHeader(fsHeader, fs);
            }

            System.out.printf("File %s was removed from the file system %s", removalFile, this.fsPath);
        }

        public void lsfs() throws IOException {
            /*
             * Lists all the file in the provided filesystem. For every file, print its
             * name, size
             * (in bytes) and creation time. Make sure to not print files marked as deleted.
             */
            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);
            // First we make sure that the file system exists
            assert Files.exists(filesysPath) : "File system does not exist";

            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);

            Object foundEntry = false;

            for (var entry : entries) {
                // We need to loop through the entries, ignore the empty ones (zero byte names)
                // and the deleted ones
                if (entry.isEmpty() || entry.flag == 1) {
                    continue;
                }
                // If the code continues here, then we have found an entry
                foundEntry = true;
                // We decode the filename
                String fileName = entry.filename();
                // We get the file's size
                int size = entry.length;

                // We get the files UNIX timestamp
                // here we used this handy tool for the translation:
                // https://www.codeconvert.ai/python-to-java-converter?id=381c3066-0167-408a-9969-8e0356d26569
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(entry.created * 1000));

                // We print the current entry's information
                System.out.printf(
                        "File Name: %s,%nFile Size: %d bytes,%nCreated: %s%n%n",
                        fileName, size, timestamp);
            }

            // Here we make sure that at least one file was found
            assert (boolean) foundEntry == true : "No files in the filesystem";
        }

        public void catfs(String file) throws IOException {
            /*
             * Prints out the file contents of a specified file from
             * the filesystem to the console.
             */
            // Use path to check if it exists
            Path filesysPath = Path.of(this.fsPath);
            // Path filePath = Path.of(file);

            assert Files.exists(filesysPath) : "File system does not exist";
            // assert Files.exists(filePath) : "File does not exist";
            // -> method already checks for file existence in findFilenameIndex
            // -> ignoring local disk

            // We once again check the file name and encode it for comparison
            byte[] fileName = FileSystem.encodeFilename(file);
            // To get a file's content we only need its file entry
            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);
            // We loop through the entries and get the desired file
            int fileIndex = findFilenameIndex(entries, fileName);
            // We get the entry we want
            FileEntry entry = entries.get(fileIndex);
            // We get the start byte where the file data is stored from the entry and read
            // the data
            try (RandomAccessFile fs = new RandomAccessFile(this.fsPath, "rw")) {
                fs.seek(entry.start);
                byte[] data = new byte[entry.length];
                fs.readFully(data);
                // The data also needs to be decoded and then we print it to the terminal
                System.out.printf("Content of %s: %s", file, new String(data, StandardCharsets.UTF_8));
            }

        }

        public void dfrgfs() throws IOException {
            /*
             * Defragments the file system. This operation removes all files marked from
             * deletion from the system, along with their respective file entries.
             * Afterwards, it compacts the file entries
             * and the file data (moves everything up to fill up the available space, so
             * that no 64 byte block gaps exist).
             * When running this command, you should print out how many files were
             * defragmented and how many bytes
             * of file data were freed.
             */
            // check if path exists
            Path filesysPath = Path.of(this.fsPath);
            assert Files.exists(filesysPath) : "File system does not exist";

            // changes occur in the header and the entries, so both are read
            List<FileEntry> entries = FileEntry.readFileEntry(this.fsPath);
            Header fsHeader = Header.readHeader(this.fsPath);

            /*
             * We now need to check which files we will keep and which we will remove
             * this means we keep non-empty and non-deleted files
             */
            // Both changes in the file entries and the header happen, so both are read and
            // written in binary
            List<FileEntry> filesToKeep = new ArrayList<>();
            List<FileEntry> filesToRemove = new ArrayList<>();
            // We now need to check which files we will keep and which we will remove
            // this means we keep non-empty and non-deleted files
            for (FileEntry entry : entries) {
                // check if entry is empty or marked for deletion, if so, skip it
                if (entry.isEmpty()) {
                    continue;
                    // if not deleted, keep it by adding to keep list
                } else if (entry.flag == 0) {
                    filesToKeep.add(entry);
                    // if deleted, add to remove list
                } else {
                    filesToRemove.add(entry);
                }
            }
            // Now we document the amount of files that are to be removed and how much bytes
            // they compose
            int removedCount = filesToRemove.size();
            int removedBytes = 0; // # total bytes "freed"
            // we loop through the files to remove and sum their lengths
            for (FileEntry entry : filesToRemove) {
                removedBytes += entry.length;
            }

            // The next step is to compact the file data
            /*
             * We loop through the entries that we keep and go to where the entry data is
             * and read it to the write it at the data offset
             * This data offset is at the start just the constant and is then in each
             * iteration updated to shift
             */

            int currentDataOffset = DATA_START_OFFSET;

            try (RandomAccessFile fs = new RandomAccessFile(this.fsPath, "rw")) {
                for (FileEntry entry : filesToKeep) {
                    // locate entry data location in fs
                    fs.seek(entry.start);
                    // Read entry data for the length that is documented
                    byte[] data = new byte[entry.length];
                    fs.readFully(data);
                    // we write it at the new position
                    fs.seek(currentDataOffset);
                    fs.write(data);

                    // we recompute the padding, and reuse the old formula in addfs
                    int firstPart = (entry.length + (DATA_ALIGNMENT - 1));
                    // Math.floorDiv is the equivalent of // in Python
                    int padded = Math.floorDiv(firstPart, DATA_ALIGNMENT) * DATA_ALIGNMENT;
                    // if padding is needed, we add as much as is left
                    if (padded > entry.length) {
                        byte[] zeros = new byte[padded - entry.length];
                        fs.write(zeros);
                    }

                    // We update entry.start to the new compacted position
                    entry.start = currentDataOffset;
                    // and we advance in the offset by a multiple of 64 bytes
                    currentDataOffset += padded;
                }

                // Next, we need to rebuild the file table:

                // we write the the non-deleted entries at the front
                fs.seek(FILE_TABLE_OFFSET);
                for (FileEntry entry : filesToKeep) {
                    fs.write(entry.pack());
                }

                // here, we fill the remaining slots with empty entries
                // create on empty file entry
                byte[] emptyFileEntry = new FileEntry().pack();
                // for the max - kept files, fill
                for (int i = 0; i < MAX_FILES - filesToKeep.size(); i++) {
                    fs.write(emptyFileEntry);
                }

                // finally, we need to rewrite the header
                fsHeader.fileCount = (short) filesToKeep.size();
                fsHeader.deletedFiles = 0;

                // The current data offset, is after the previous loop, the place where file
                // data ends and we document that in the header
                fsHeader.nextFreeOffset = currentDataOffset;

                // The free entry offset is where the first empty entry is
                fsHeader.freeEntryOffset = FILE_TABLE_OFFSET + filesToKeep.size() * ENTRY_SIZE;

                // Header is at byte 0 so we start writing from 0
                Header.writeHeader(fsHeader, fs);
            }

            // Finally, we print the defragment results
            System.out.printf(
                    "Defragmented file system: %s,%nRemoved file count: %d,%nFreed bytes: %s bytes%n%n",
                    this.fsPath, removedCount, removedBytes);
        }
    }

    /*
     * The main function
     */

    public static void main(String[] args) {
        // check if the correct number of arguments is provided
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: java zvfs <command> <file_system> [file]");
            // exit with error code 1 when usage is incorrect
            System.exit(1);
        }
        // parse command-line arguments
        String command = args[0]; // command to execute
        String fsPath = args[1]; // path to the file system
        String extra = args.length == 3 ? args[2] : null; // optional extra argument

        // create an instance of ZestVirtualFilesSystem
        FileSystem fs = new FileSystem(fsPath);

        // execute the command based on user input
        try {
            // switch statement to handle different commands
            // instead of writing multiple if-else statements:
            // https://www.w3schools.com/java/java_switch.asp
            // it works by evaluating the expression once and comparing it to each case
            switch (command) {
                // if command matches a case, execute the corresponding block
                case "mkfs":
                    // check if extra argument is provided, if so, show usage
                    if (extra != null)
                        usage();
                    // call mkfs method to create a new file system
                    try {
                        fs.mkfs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // break to exit the switch statement after executing the case
                    break;
                case "gifs":
                    if (extra != null)
                        usage();
                    try {
                        fs.gifs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "addfs":
                    if (extra == null)
                        usage();
                    try {
                        fs.addfs(extra);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ;
                    break;
                case "getfs":
                    if (extra == null)
                        usage();
                    try {
                        fs.getfs(extra);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "rmfs":
                    if (extra == null)
                        usage();
                    try {
                        fs.rmfs(extra);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "lsfs":
                    if (extra != null)
                        usage();
                    try {
                        fs.lsfs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "catfs":
                    if (extra == null)
                        usage();
                    try {
                        fs.catfs(extra);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "dfrgfs":
                    if (extra != null)
                        usage();
                    try {
                        fs.dfrgfs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                // default case: if no cases match, show error message and exit
                default:
                    System.out.println("Unknown command: " + command);
                    System.exit(1);
            }
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    // usage: prints the usage message and exits the program
    private static void usage() {
        System.out.println("Usage: java zvfs <command> <file_system> [file]");
        System.exit(1);
    }
}
