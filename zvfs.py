##### Imports
import sys
from struct import pack, unpack
import time
import os
from pathlib import Path
#############

############################
# Global constants for sizes
############################

# We followed the standard of making constants upper case 
# The whole header is 64 bytes and each file entry is also 64 bytes
HEADER_SIZE = 64 
ENTRY_SIZE = 64
# Constant used for padding
DATA_ALIGNMENT = 64 # alignment means that the data size is a multiple of 64 bytes
# In the file system a maximum of 32 files are supported
MAX_FILES = 32

# We decided to make these global constants in case that we need to update these values to their original states while doing a defragment etc.
# If we start with the first file entry, it will start at byte 64, since it lies directly after the header
FILE_TABLE_OFFSET = HEADER_SIZE

# Now for the data_start_offset it should be 2112, so we do the header + the amount of files * the file entry sizes, to point to where the file data starts
# So, it would be 64 + 32 * 64 = 2112
DATA_START_OFFSET = HEADER_SIZE + MAX_FILES * ENTRY_SIZE

# To ensure that the HEADER and the File entries follow a specific byte format, we used the function struct from the chapter 17. Binary Data
# We also had to consult the documentation of the function, to understand which symbols to use in the strings: https://docs.python.org/3/library/struct.html
# We decided to use little endian '<', since we saw that often one or the other was used and that "Intel x86, AMD64 (x86-64), and Apple M1 are little-endian". This was used for the header and the file entries
# s: fixed-length byte string (we use 8 byte string for MAGIC)
# B: unsigned 1-byte integer (0 - 255)
# H: unsigned 2-byte integer (0 - 65535)
# I: unsigned 4-byte integer (0 - 4294967295)
# We used unsigned types, since the fields are things such as counts (file amounts), sizes (bytes) or offsets (file positions) -> all these are usually non-negative and thus this version fits better
HEADER_FORMAT = "<8s B B H H H H H I I I I H 26s"
FILE_ENTRY_FORMAT = "<32s I I B B H Q 12s"

# We also went for a object oriented approach, because we thought it would be easier to translate to java afterwards, since Java uses classes for everything

###########################
# Header and FileEntry class
###########################

class Header:
    # We define the variables above, in case one wishes to add a specific special value as an argument
    # here we set the default values as per the specification 
    def __init__(
            self,
            magic = b"ZVFSDSK1", # 8s
            version = 1,  # B
            flags = 0, # flags B
            reserved0 = 0, # reserved0 H
            file_count = 0, # file_count H
            file_capacity = MAX_FILES, # H
            file_entry_size = ENTRY_SIZE, # H
            reserved1 = 0, # reserved1 H
            file_table_offset = FILE_TABLE_OFFSET,# I
            data_start_offset = DATA_START_OFFSET, # I
            next_free_offset = DATA_START_OFFSET, # I
            free_entry_offset = FILE_TABLE_OFFSET, # I
            deleted_files = 0, # H
            reserved2 = b"\x00" * 26 # reserved2 26s
    ):
    # We assign the arguments to the class variables -> these will be used when packing and unpacking the header
        self.magic = magic
        self.version = version
        self.flags = flags
        self.reserved0 = reserved0
        self.file_count = file_count
        self.file_capacity = file_capacity
        self.file_entry_size = file_entry_size
        self.reserved1 = reserved1
        self.file_table_offset = file_table_offset
        self.data_start_offset = data_start_offset
        self.next_free_offset = next_free_offset
        self.free_entry_offset = free_entry_offset
        self.deleted_files = deleted_files
        self.reserved2 = reserved2
    
    def pack(self):
        '''
        This function converts the header fields into bytes, for that we use the pack function from struct
        '''
        return pack(
            HEADER_FORMAT,
            self.magic,
            self.version,
            self.flags,
            self.reserved0,
            self.file_count,
            self.file_capacity,
            self.file_entry_size,
            self.reserved1,
            self.file_table_offset,
            self.data_start_offset,
            self.next_free_offset,
            self.free_entry_offset,
            self.deleted_files,
            self.reserved2
        )

    # we use a class method here, since we want to create a new Header object from the unpacked data
    @classmethod
    def unpack(cls, data):
        '''
        Creates header object from 64 bytes, for that we use the unpack function from struct
        '''
        unpacked = unpack(HEADER_FORMAT, data) # input is a bytes object of length 64, returns a tuple of the unpacked values
        return cls(*unpacked) # The * operator unpacks the tuple into individual arguments for the class constructor
    
    # we use static methods here, since they do not depend on the instance of the class
    @staticmethod
    def read_header(file):
        '''
        Reads the 64 byte header at offset 0 and returns the Header object
        '''
        # The seek() method sets the current file position in a file stream.
        file.seek(0)
        # We read 64 bytes from the start
        raw = file.read(HEADER_SIZE)
        # We unpack the raw bytes into a Header object
        return Header.unpack(raw)

    # static method to write the header back to the file, use static since it does not depend on the instance
    @staticmethod
    def write_header(header_instance, file):
        # The header starts at byte 0
        file.seek(0)
        # We write the packed header bytes into the file
        file.write(header_instance.pack())

# Here we define the structure of the FileEntry
class FileEntry:
    def __init__(
            self,
            name = b"\x00" * 32,
            start = 0,
            length = 0,
            type = 0,
            flag = 0,
            reserved0 = 0,
            created = 0,
            reserved1 = b"\x00" * 12
    ):
        self.name = name
        self.start = start
        self.length = length
        self.type = type
        self.flag = flag # is 1 if deleted
        self.reserved0 = reserved0
        self.created = created
        self.reserved1 =reserved1

    def pack(self):
        '''
        creates FileEntry (= pack file entry fields into bytes), using the pack function from struct
        '''
        return pack(
            FILE_ENTRY_FORMAT,
            self.name,
            self.start,
            self.length,
            self.type,
            self.flag,
            self.reserved0,
            self.created,
            self.reserved1,
        )

    # we use a class method here, since we want to create a new FileEntry object from the unpacked data
    @classmethod
    def unpack(cls, raw):
        '''
        Convert 64 bytes -> FileENtry object.
        '''
        return cls(*unpack(FILE_ENTRY_FORMAT, raw))
    
    def is_empty(self):
        '''
        Returns True if the name is zero bytes
        '''
        # An empty file entry has a name of 32 zero bytes
        return self.name == b"\x00" * 32
    
    def filename(self):
        '''
        Decodes UTF-8 file name
        '''
        # We split the name at the first null byte and decode the rest
        return self.name.split(b"\x00", 1)[0].decode("utf-8")
    
    @staticmethod
    def read_entries(file):
        '''
        This function reads all the 32 FileEntry objects seqeuntially
        and returns a list of FileEntry objects
        '''
        # create empty list to hold entries
        entries = []
        # set file position to start of file table
        file.seek(FILE_TABLE_OFFSET)

        # iterate over max files and read each entry
        for i in range(MAX_FILES):
            # read 64 bytes for each entry
            raw = file.read(ENTRY_SIZE)
            # unpack into FileEntry object
            entry = FileEntry.unpack(raw)
            # append to list
            entries.append(entry)
        # return list of FileEntry objects
        return entries 
    
    @staticmethod
    def write_entry(entry_instance, file, position):
        '''
        Writes a single FileEntry object at the specified position in the file
        '''
        file.seek(position)
        file.write(entry_instance.pack())
        
#####################
# File System 
#####################

class FileSystem:
    def __init__(self, fs_path):
        # We only check if the file system exists in the methods, because in mkfs, the system does not exist at first
        self.fs_path = Path(fs_path) # Path to the file system
        self.header = Header() # Create a default header
        self.empty_file = FileEntry() # Create a default empty file entry
    
    @staticmethod
    def encode_filename(file):
        '''
        This function makes sure that the filename is encoded correctly, 
        such as if it is 31 + null terminator chars long and it utf-8 encodes it.
        It then returns the filename.
        '''
        if len(file) > 31:
            raise ValueError("File name can be max 31 characters long")
        encoded_file = file.encode("utf-8")
        filename = encoded_file + b"\x00" * (32 - len(encoded_file))

        return filename
    
    @staticmethod
    def compare_filenames(file_entries, filename):
        '''
        This function compares the provided filename with the file entries in the file system.
        (We use the same loop to as in addfs, but to find the file with the same name)
        '''
            
        ### ALTERNATIVE APPROACH ######
        # file_index = None
        # for index, entry in enumerate(file_entries):
        #     if entry.name == filename:
        #         file_index = index
        #         break
        
        # generator expression to find the index of the file entry with the matching name
        # look until we find it, else None
        file_index = next((idx for idx, entry in enumerate(file_entries) if entry.name == filename), None)
        
        # We check if the entry was found
        assert file_index is not None, "The file you want to extract/remove is not in the filesystem"
        return file_index

    
    def mkfs(self):
        '''
        This function create a new file <file system name>.zvfs that will represent a new filesys-
        tem. Populate the header with the appropriate values, fill out the file entry region with zeroes for all 32
        file entries. Make sure to include the correct values for the header metadata.
        '''
        # First, we need to check if the file system already exists, if so, we ask the user to tell us, what they want to do:
        # abort the action or overwrite the system
        filesys_path = Path(self.fs_path)

        if filesys_path.exists():
            next_step = input(f"The file system {self.fs_path} already exists, do you wish to abort the action or overwrite the file system. Please write 'OVERWRITE' or 'ABORT': ")
            assert next_step in ['OVERWRITE', 'ABORT'], "Your response needs to be 'OVERWRITE' or 'ABORT'. Action was aborted."

            if next_step == 'ABORT':
                sys.exit("Action mkfs aborted, since file system already exists.")
            else:
                # If we wish to overwrite it, we decided to just remove it on disk before writing it again: https://www.datacamp.com/tutorial/python-delete-file
                os.remove(self.fs_path)

        # First we open the filesystem path for writing in binary 
        with open(self.fs_path, "wb") as f:
            # We write the header into the filesystem using the pack method
            f.write(self.header.pack())
            # Now we need to write the 32 empty file entries after the header
            # We first pack one empty entry
            entry = self.empty_file.pack()
            # Now we populate 32 of them
            for i in range(MAX_FILES):
                f.write(entry)

        print(f"File system {self.fs_path} was created")
    def gifs(self):
        '''
        This function gets information for a specified filesystem file. The information printed out
        should be file name, number of files present (non deleted), remaining free entries for new files (excluding
        deleted files), and the number of files marked as deleted. Moreover, print out the total size of the file.
        '''

        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # Now we read the file system in binary, we only need to read the header, since it contains all the information needed
        with open(self.fs_path, "rb") as f:
            header_fs = self.header.read_header(f)
            # The amount of non-deleted files is in the attribute file_count
            files = header_fs.file_count

            # Then we get the deleted files count
            deleted_files = header_fs.deleted_files

            # Here we get the free file spaces amount
            free_entries = header_fs.file_capacity - files - deleted_files # We also remove the deleted files, since they are still in teh file system until defragment

            # Then we get the OS file system size: https://www.educative.io/answers/what-is-the-ospathgetsize-method-in-python
            fs_size = os.path.getsize(self.fs_path) # Should be 2112

            print(f"File System: {self.fs_path},\nNon-deleted files: {files},\nFree entries: {free_entries},\nDeleted files:{deleted_files},\nTotal system size: {fs_size} bytes")
        
    def addfs(self, new_file):
        '''
        This function adds a file from your disk to the filesystem file. In order
        to do that, you need to create a new file entry, with the appropriate data, and then append the file data
        at the end of the file (with the proper byte alignment). Do not forget to update the filesystem metadata
        in the header as well.
        ''' 

        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        file_path_new = Path(new_file)
        
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # We need to check if the file exists on disk
        assert file_path_new.exists(), f"The file {new_file} does not exist."

        # We open the file system for read and write in binary mode
        with open(self.fs_path, "r+b") as f:
            # We need to read the header to later update it and to entries to add one
            header_fs =  self.header.read_header(f)
            entries = self.empty_file.read_entries(f)

            free_position = next((idx for idx, entry in enumerate(entries) if entry.is_empty()), None)

            # We encode the file name to be able to compare it with the file entries in the file system
            filename = FileSystem.encode_filename(new_file)
            # We need to add a check here, where we avoid the addition of the same file to the file system
            try:
                # If the file already exists the code in the line bellow will run with no exceptions, then we abort the run and tell the user to first remove the duplicate file before adding it again (the same or with different content)
                file_index = FileSystem.compare_filenames(entries, filename)
                sys.exit(f"The file {new_file} already exists in the file system {self.fs_path}. Please remove it first, before adding it once more to the file system.")
                
            # If an AssertionError is raised, then the file does not exist in the file system and we can continue
            except AssertionError:
                pass

            # Now we need to read the new files content to write it into the file system.
            with open(new_file, "rb") as file:
                # read the whole file content
                file_content = file.read()
                
                # We counted each character as a byte and thus the length defined the content byte size 
                # Here, we were unsure if this is the correct method, but we use len() to get the size in bytes, since we read the file in binary mode 
                file_size = len(file_content) 

                # Since we have filename restrictions, we need to check for them, so it is allowed to have 31 characters and a null termination and must be encoded in utf-8
                # Alternatively, one could additionally use basename() to get the tail of the provided file, in case that a path to the file is provided: https://www.geeksforgeeks.org/python/python-os-path-basename-method/
                name_encoded = new_file.encode("utf-8")
                assert len(name_encoded) <= 31, "File name is too long, can only be 31 utf-8 characters long"
                # The rest of the name is filled with zero bytes for padding, if the filename is under 32 characters
                name_encoded += b"\x00" * (32 - len(name_encoded))

                # To be able to write the file data into the filesystem, we need to check from where on there is space and that is saved in a header attribute
                current_data_offset = header_fs.next_free_offset

                # Since the file data needs to be padded, we need to calculate the how much padding need to be added (through zero bytes)
                # Here, we calculate the multiple that gets to when padded
                # we use data alignment constant defined above -> this is 64 bytes
                padded = (file_size + (DATA_ALIGNMENT - 1)) // DATA_ALIGNMENT * DATA_ALIGNMENT
                # And now we find out how much of that padded version is already filled by the file content itself
                padding_size = padded - file_size if padded > file_size else 0 # if no padding is needed, then it is zero, else the difference

                # Now, we need to set the position in the filesystem to where we want to write the file data
                f.seek(current_data_offset)
                f.write(file_content)
                # And we pad the rest of the data space so it is a multiple of 64
                f.write(b"\x00" * padding_size)

                # We create another file entry and this time with the current timestamp
                new_entry = FileEntry(
                                name = name_encoded,
                                start = current_data_offset,
                                length = file_size,
                                type = 0,
                                flag= 0, # not deleted
                                reserved0 = 0,
                                created = int(time.time()) # UNIX timestamp
                            )

                # This new entry is written into the file table section of the file system 
                # and for that we need to calculate starting from which byte, it needs to be written
                entry_offset = FILE_TABLE_OFFSET + free_position * ENTRY_SIZE
                # We set the writing at said positions and write the new entry there
                FileEntry.write_entry(new_entry, f, entry_offset)

                # Now we need to update the header
                # We realized that if we do not rewrite the header, it will not correctly update, so it always needs to be rewritten
                header_fs.file_count += 1
                header_fs.next_free_offset = current_data_offset + padded

                # We use a static method in Header to rewrite it
                Header.write_header(header_fs, f)
            
            print(f"File {new_file} was added to the file system {self.fs_path}")

    def getfs(self, extraction_file):
        '''
        Extracts a file from the filesystem to your disk. For
        this action, make no modifications to the .zvfs file, simply produce the requested file as an output.
        '''

        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # We encode the file name to be able to compare it with the file entries in the file system
        filename = FileSystem.encode_filename(extraction_file)

        with open(self.fs_path, "r+b") as f:
            # We read entries, to be able to get the file information
            entries = self.empty_file.read_entries(f)

            # We utilize the same loop to as in addfs to find the file with the same name
            file_index = FileSystem.compare_filenames(entries, filename)

            # We specifically get the entry we are looking for
            entry = entries[file_index]

            # We need to handle the case, where the file is already on disk
            # In this case, we decided to prompt the user to decide, whether they wish to change the filename or if they want to overwrite it
            extraction_file_path = Path(extraction_file)
            if extraction_file_path.exists():
                next_step = input(f"The file {extraction_file} is already/still on your disk. If you wish to overwrite, then write 'OVERWRITE' or if you want to save it under a different name, then write the new filename with the correct file appendix (e.g. .txt). Write your answer here: ")

                if next_step != 'OVERWRITE':
                    extraction_file = next_step
            # To restore the file, we open the file for writing again
            with open(extraction_file, "wb") as restore_file:
                # We get the start byte where the file data is stored from the entry and read the data
                f.seek(entry.start)
                data = f.read(entry.length)
                # This data is then rewritten into the file
                restore_file.write(data)     
        
        print(f"File {extraction_file} was retrieved from the file system {self.fs_path} to the disk")

    def rmfs(self, remove_file):
        ''' 
        Marks a file in the filesystem as deleted. To do that,
        set the respective entry's flag byte to 1 from 0. Do not delete the file data, or move them around in this
        command. Make sure to update the deleted files entry in the header as well.
        '''

        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        file_path_new = Path(remove_file)
        
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # We need to check if the file exists
        assert file_path_new.exists(), f"The file {remove_file} does not exist."

        # Now we encode the filename again
        filename = FileSystem.encode_filename(remove_file)

        # Since we need to change things in the header and a file entry, both are read
        with open(self.fs_path, "r+b") as f:
            fs_header = self.header.read_header(f)
            fs_entries = self.empty_file.read_entries(f)

            # We again loop through the entries and compare the names
            file_index = FileSystem.compare_filenames(fs_entries, filename)

            # We get the entry again, that we want to remove now
            entry = fs_entries[file_index]

            # For deleted files, we set the flag to 1
            entry.flag = 1
            # And we update the header information
            fs_header.deleted_files += 1
            fs_header.file_count -= 1

            # Both the updated file entry and the header are rewritten into the file system
            entry_start_position = FILE_TABLE_OFFSET + file_index * ENTRY_SIZE
            FileEntry.write_entry(entry, f, entry_start_position)

            Header.write_header(fs_header, f)
        
        print(f"File {remove_file} was removed from the file system {self.fs_path}")
    
    def lsfs(self):
        '''
        Lists all the file in the provided filesystem. For every file, print its name, size
        (in bytes) and creation time. Make sure to not print files marked as deleted.
        ''' 

        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"
    
        # We only wish to list file entries, so only those are opened for reading in binary
        with open(self.fs_path, "rb") as f:
            fs_entries = self.empty_file.read_entries(f)

            found_entry = False
            # We need to loop through the entries, ignore the empty ones (zero byte names) and the deleted ones
            for entry in fs_entries:
                if entry.is_empty() or entry.flag == 1:
                    continue

                # If the code continues here, then we have found an entry
                found_entry = True

                # We decode the filename
                file_name = entry.filename()

                # We get the file's size
                size = entry.length

                # We get the files UNIX timestamp
                timestamp = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(entry.created))

                # We print the current entry's information
                print(f"File Name: {file_name},\nFile Size: {size} bytes,\nCreated: {timestamp}\n")
            
            # Here we make sure that at least one file was found
            assert found_entry, "No files in the filesystem"
    
    def catfs(self, file):
        '''
        Prints out the file contents of a specified file from
        the filesystem to the console.
        '''
        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        #file_path_new = Path(file)
        
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # We need to check if the file exists
        #assert file_path_new.exists(), f"The file {file} does not exist."
        # -> method already checks for existence via compare_filenames
        # -> no need to check on disk, since we read from the file system
        
        # We once again check the file name and encode it for comparison
        filename = FileSystem.encode_filename(file)

        # To get a file's content we only need it's file entry
        with open(self.fs_path, "rb") as f:
            fs_entries = self.empty_file.read_entries(f)

            # We loop through the entries and get the desired file
            file_index = FileSystem.compare_filenames(fs_entries, filename)

            # We get the entry we want
            entry = fs_entries[file_index]

            # We get the start byte where the file data is stored from the entry and read the data
            f.seek(entry.start)
            data = f.read(entry.length)
            # The data also needs to be decoded and then we print it to the terminal
            print(f"Content of file {file}: {data.decode("utf-8")}")

    def dfrgfs(self):
        '''
        Defragments the file system. This operation removes all files marked from
        deletion from the system, along with their respective file entries. Afterwards, it compacts the file entries
        and the file data (moves everything up to fill up the available space, so that no 64 byte block gaps exist).
        When running this command, you should print out how many files were defragmented and how many bytes
        of file data were freed.
        '''
        # Use path to check if it exists
        fs_path_new = Path(self.fs_path)
        # First we make sure that the file system exists
        assert fs_path_new.exists(), f"File system {self.fs_path} does not exist"

        # Both changes in the file entries and the header happen, so both are read and written in binary
        with open(self.fs_path, "r+b") as f:
            fs_header = self.header.read_header(f)
            fs_entries = self.empty_file.read_entries(f)

            # We now need to check which files we will keep and which we will remove 
            # this means we keep non-empty and non-deleted files
            files_to_keep = []
            files_to_remove = []
            # -> we could've also used list comprehensions here, or could use filter() with lambda functions:
            
            # LIST COMPREHENSION ALTERNATIVE:
            # files_to_keep = [entry for entry in fs_entries if not entry.is_empty() and entry.flag == 0]
            # files_to_remove = [entry for entry in fs_entries if not entry.is_empty() and entry.flag == 1] 
            
            # FILTER + LAMBDA ALTERNATIVE:
            # files_to_keep = list(filter(lambda entry: not entry.is_empty() and entry.flag == 0, fs_entries))
            # files_to_remove = list(filter(lambda entry: not entry.is_empty() and entry.flag == 1, fs_entries))    

            # CURRENT APPROACH, we went for the explicit loop for clarity:
            # iterate through entries
            for entry in fs_entries:
                # check if entry is empty or marked for deletion, if so, skip it
                if entry.is_empty():
                    continue
                # if not deleted, keep it by adding to keep list
                elif entry.flag == 0:
                    files_to_keep.append(entry)
                # if deleted, add to remove list
                else:
                    files_to_remove.append(entry)
                
            # Now we document the amount of files that are to be removed and how much bytes they compose:
            removed_count = len(files_to_remove) # length of the remove list -> amount of files removed
            removed_bytes = 0 # total bytes "freed"
            # we loop through the files to remove and sum their lengths
            for entry in files_to_remove:
                removed_bytes += entry.length
            
            ### The next step is to compact the file data
            # We loop through the entries that we keep and go to where the entry data is and read it to the write it at the data offset
            # This data offset is at the start just the constant and is then in each iteration updated to shift
            current_data_offset = DATA_START_OFFSET

            for entry in files_to_keep:
                # locate entry data location in fs
                f.seek(entry.start)
                # Read entry data for the length that is documented
                data = f.read(entry.length)

                # we write it at the new position
                f.seek(current_data_offset)
                f.write(data)

                # we recompute the padding, and reuse the old formula in mkfs
                padded = ((entry.length + (DATA_ALIGNMENT - 1)) // DATA_ALIGNMENT) * DATA_ALIGNMENT
                # if padding is needed, we add as much as is left
                if padded > entry.length: 
                    # we pad with zero bytes
                    f.write(b"\x00" * (padded - entry.length))
                
                # We update entry.start to the new compacted position
                entry.start = current_data_offset

                # and we advance in the offset by a multiple of 64 bytes
                current_data_offset += padded 
        
            # Next, we need to rebuild the file table:
            
            # we write the the non-deleted entries at the front
            f.seek(FILE_TABLE_OFFSET)

            for entry in files_to_keep:
                f.write(entry.pack())
            
            # here, we fill the remaining slots with empty entries
            # create on empty file entry
            empty = FileEntry().pack()
            # for the max - kept files, fill
            for i in range(MAX_FILES - len(files_to_keep)):
                f.write(empty)

            # finally, we need to rewrite the header
            fs_header.file_count = len(files_to_keep)
            fs_header.deleted_files = 0
            # The current data offset, is after the previous loop, the place where file data ends and we document that in the header
            fs_header.next_free_offset = current_data_offset

            # The free entry offset is where the first empty entry is
            fs_header.free_entry_offset = FILE_TABLE_OFFSET + len(files_to_keep) * ENTRY_SIZE

            # Header is at byte 0 so we start writing from 0
            Header.write_header(fs_header, f)
    
        print(f"Defragmented file system: {self.fs_path},\nRemoved file count: {removed_count},\nFreed bytes: {removed_bytes}")


#####################
# Main function
#####################


def main():
    # Make sure correct amount of arguments are used
    assert len(sys.argv) >= 3 and len(sys.argv) <= 4, "You have to specify the command you want to use and at least provide the file system path; at most the file system path and a file path. The format should be >>> python zvfs.py <command> <file_system> ..."

    # The argument at idx 1 is the command
    command = sys.argv[1]

    # The second argument is always the file_system
    fs_path = sys.argv[2]

    # Next, we create a FileSystem instance
    fs = FileSystem(fs_path)

    # This methodology was inspired by this website: https://stackoverflow.com/questions/54529405/call-python-function-based-on-command-line-argument
    file_system_commands = {
        "mkfs": fs.mkfs,
        "gifs": fs.gifs,
        "addfs": fs.addfs,
        "getfs": fs.getfs,
        "rmfs": fs.rmfs,
        "lsfs":fs.lsfs,
        "dfrgfs":fs.dfrgfs,
        "catfs":fs.catfs
    }

    # Either a method is called where only the file system path is given, and then we call it without a argument
    # or an extra file path is needed and then we call it with said file as an extra argument
    if sys.argv[3:] != []:
        file_system_commands[command](sys.argv[3])
    else:
        file_system_commands[command]()

    #### This was our old approach, but we automated the function calls
    
    # if command == "mkfs":
    #     assert len(sys.argv) == 3, "You need to provide the filesystem name"
    
    #     fs.mkfs()
    
    # elif command == "gifs":
    #     assert len(sys.argv) == 3, "You need to provide the filesystem name"
    
    #     fs.gifs()
    
    # elif command == "addfs":
    #     assert len(sys.argv) == 4, "You need to provide the filesystem name and new file"
    
    #     fs.addfs(sys.argv[3])
    
    # elif command == "lsfs":
    #     assert len(sys.argv) == 3, "You need to provide the filesystem name"
    
    #     fs.lsfs()
    
    # elif command == "rmfs":
    #     assert len(sys.argv) == 4, "You need to provide the filesystem name and the file to remove"
    
    #     fs.rmfs(sys.argv[3])

    # elif command == "getfs":
    #     assert len(sys.argv) == 4, "You need to provide the filesystem name and the file to extract"
    
    #     fs.getfs(sys.argv[3])
    
    # elif command == "catfs":
    #     assert len(sys.argv) == 4, "You need to provide the filesystem name and the file content file to extract"
    
    #     fs.catfs(sys.argv[3])

    # elif command == "dfrgfs":
    #     assert len(sys.argv) == 3, "You need to provide the filesystem name"
    
    #     fs.dfrgfs()

if __name__ == "__main__":
    main()