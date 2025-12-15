# Columnar File Format Specification (CLMN)

**Version:** 1.0  
**Author:** Custom Columnar Format Project  
**Date:** December 2025

---

## 1. Overview

This document specifies a binary columnar file format designed for efficient storage and retrieval of tabular data. The format organizes data by columns rather than rows, enabling selective column reads without scanning the entire file. Each column is independently compressed using the zlib algorithm, optimizing both storage space and I/O performance.

### 1.1 Design Goals

- **Columnar Storage:** Store data column-by-column for efficient analytical queries
- **Compression:** Reduce file size through per-column zlib compression
- **Selective Reads:** Enable reading specific columns without deserializing the entire file
- **Type Safety:** Support strongly-typed columns with explicit data type definitions
- **Simplicity:** Maintain a straightforward format that is easy to implement and debug

### 1.2 Use Cases

- Storage of analytical datasets where column-wise access is common
- Data interchange between systems requiring efficient serialization
- Educational purposes for understanding low-level data format design
- Performance optimization for wide tables with many columns

---

## 2. File Structure

A CLMN file consists of three main sections in sequence:

```
┌─────────────────────────────────────────┐
│         Magic Number (4 bytes)          │
├─────────────────────────────────────────┤
│              Header                     │
│  - Version                              │
│  - Row/Column counts                    │
│  - Column Schemas                       │
│  - Offsets to data blocks               │
├─────────────────────────────────────────┤
│      Column Block 0 (compressed)        │
├─────────────────────────────────────────┤
│      Column Block 1 (compressed)        │
├─────────────────────────────────────────┤
│               ...                       │
├─────────────────────────────────────────┤
│      Column Block N-1 (compressed)      │
└─────────────────────────────────────────┘
```

---

## 3. Data Types and Encoding

All multi-byte integers use **big-endian** byte order (network byte order).

### 3.1 Primitive Types

| Type Name | Type Code | Size (bytes) | Description | Range/Notes |
|-----------|-----------|--------------|-------------|-------------|
| INT32 | `0x00` | 4 | 32-bit signed integer | -2,147,483,648 to 2,147,483,647 |
| INT64 | `0x01` | 8 | 64-bit signed integer | -2^63 to 2^63-1 |
| FLOAT64 | `0x02` | 8 | 64-bit IEEE 754 double | IEEE 754 double-precision |
| STRING | `0x03` | variable | UTF-8 encoded string | See section 3.2 |

### 3.2 String Encoding

Strings are encoded using UTF-8 with a length-prefixed format:

```
┌────────────────┬─────────────────────────┐
│ Length (2 bytes) │  UTF-8 Bytes (N bytes) │
└────────────────┴─────────────────────────┘
```

- **Length:** Unsigned 16-bit integer representing the number of UTF-8 bytes (not characters)
- **Maximum string length:** 65,535 bytes
- **UTF-8 Bytes:** The actual UTF-8 encoded string data

**Example:**
- String "Hello" = `[0x00, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F]`
  - Length: 5 bytes
  - UTF-8: H(0x48) e(0x65) l(0x6C) l(0x6C) o(0x6F)

---

## 4. File Header

The header contains all metadata necessary to parse the file and locate column data blocks.

### 4.1 Header Structure

```
Offset   Size    Field                    Description
------   ----    -----                    -----------
0        4       Magic Number             File format identifier: "CLMN" (0x434C4D4E)
4        2       Version                  Format version (currently 0x0001)
6        4       Column Count             Number of columns (N)
10       8       Row Count                Number of rows
18       varies  Column Schemas           N column schema entries
```

### 4.2 Column Schema Entry

Each column has a schema entry in the header:

```
Offset   Size      Field                  Description
------   ----      -----                  -----------
0        2         Name Length            Length of column name in bytes (L)
2        L         Name Bytes             UTF-8 encoded column name
L+2      1         Data Type              Type code (0x00-0x03)
L+3      4         Uncompressed Size      Size of column data before compression
L+7      4         Compressed Size        Size of column data after compression
L+11     8         Offset                 Absolute byte offset to compressed data block
```

**Total size per schema entry:** 19 + L bytes (where L is the name length)

### 4.3 Complete Header Size Calculation

```
Header Size = 18 + Σ(19 + name_length[i]) for i = 0 to N-1
```

Where N is the number of columns.

---

## 5. Column Data Blocks

Each column's data is stored as a compressed block immediately following the header. The blocks appear in the same order as their schema entries.

### 5.1 Column Block Format

```
┌─────────────────────────────────────┐
│   Compressed Data (zlib format)    │
│   Size = Compressed Size from header│
└─────────────────────────────────────┘
```

### 5.2 Uncompressed Column Data Layout

Before compression, column data is laid out according to its type:

#### INT32 Column (Type 0x00)
```
┌────────┬────────┬─────┬────────┐
│ Value 0│ Value 1│ ... │ Value R│
│ (4B)   │ (4B)   │     │ (4B)   │
└────────┴────────┴─────┴────────┘
```
Total uncompressed size = R × 4 bytes (where R = row count)

#### INT64 Column (Type 0x01)
```
┌────────┬────────┬─────┬────────┐
│ Value 0│ Value 1│ ... │ Value R│
│ (8B)   │ (8B)   │     │ (8B)   │
└────────┴────────┴─────┴────────┘
```
Total uncompressed size = R × 8 bytes

#### FLOAT64 Column (Type 0x02)
```
┌────────┬────────┬─────┬────────┐
│ Value 0│ Value 1│ ... │ Value R│
│ (8B)   │ (8B)   │     │ (8B)   │
└────────┴────────┴─────┴────────┘
```
Total uncompressed size = R × 8 bytes

#### STRING Column (Type 0x03)
```
┌──────┬──────┬──────┬──────┬─────┬──────┬──────┐
│Len 0 │Str 0 │Len 1 │Str 1 │ ... │Len R │Str R │
│(2B)  │(var) │(2B)  │(var) │     │(2B)  │(var) │
└──────┴──────┴──────┴──────┴─────┴──────┴──────┘
```
Total uncompressed size = R × 2 + Σ(string_length[i]) for i = 0 to R-1

### 5.3 Compression Algorithm

- **Algorithm:** zlib (DEFLATE) compression
- **Compression Level:** Default (typically level 6)
- **Format:** Standard zlib format with header and checksum

Each column is compressed independently. The compressed bytes are written directly to the file at the offset specified in the column's schema entry.

---

## 6. Reading Algorithm

### 6.1 Full File Read

1. Read and verify magic number (bytes 0-3)
2. Read version number (bytes 4-5)
3. Read column count N (bytes 6-9)
4. Read row count R (bytes 10-17)
5. For each column (i = 0 to N-1):
   - Read column schema entry
   - Store offset, compressed size, uncompressed size, type, and name
6. For each column (i = 0 to N-1):
   - Seek to offset specified in schema
   - Read compressed bytes (compressed size)
   - Decompress using zlib
   - Parse according to data type
   - Reconstruct column values

### 6.2 Selective Column Read

To read only specific columns (column pruning):

1. Read header completely (steps 1-5 above)
2. For each requested column name:
   - Locate matching schema entry
   - Seek to offset specified in schema
   - Read and decompress only that column's data
3. Skip unrequested columns entirely

**Performance Benefit:** Reading K out of N columns requires reading only:
- The full header
- K compressed column blocks

This avoids reading (N - K) unnecessary column blocks.

---

## 7. Writing Algorithm

### 7.1 Two-Pass Write Strategy

**Pass 1: Data Collection and Compression**
1. Read input data (e.g., from CSV)
2. Organize data by columns
3. Compress each column independently
4. Record compressed and uncompressed sizes

**Pass 2: File Writing**
1. Write magic number
2. Write version number
3. Write column and row counts
4. Calculate offsets for each column block:
   - First column offset = header size
   - Subsequent offsets = previous offset + previous compressed size
5. Write all column schema entries with calculated offsets
6. Write compressed column data blocks in order

### 7.2 Offset Calculation

```
offset[0] = header_size
offset[i] = offset[i-1] + compressed_size[i-1]  (for i > 0)

where:
header_size = 18 + Σ(19 + name_length[j]) for j = 0 to N-1
```

---

## 8. Examples

### 8.1 Simple Example

**Input CSV:**
```csv
id,name,age
1,Alice,30
2,Bob,25
```

**Metadata:**
- 3 columns (id, name, age)
- 2 rows
- Types: INT32, STRING, INT32

**Uncompressed Column Data:**

*Column 0 (id):*
```
[0x00, 0x00, 0x00, 0x01,   // 1
 0x00, 0x00, 0x00, 0x02]   // 2
```
Size: 8 bytes

*Column 1 (name):*
```
[0x00, 0x05, 0x41, 0x6C, 0x69, 0x63, 0x65,  // "Alice" (length 5)
 0x00, 0x03, 0x42, 0x6F, 0x62]              // "Bob" (length 3)
```
Size: 14 bytes

*Column 2 (age):*
```
[0x00, 0x00, 0x00, 0x1E,   // 30
 0x00, 0x00, 0x00, 0x19]   // 25
```
Size: 8 bytes

**After Compression:**
Assume compression yields:
- Column 0: 6 bytes (compressed)
- Column 1: 12 bytes (compressed)
- Column 2: 6 bytes (compressed)

**File Layout:**

```
Offset  Content
------  -------
0-3     0x434C4D4E (Magic: "CLMN")
4-5     0x0001 (Version 1)
6-9     0x00000003 (3 columns)
10-17   0x0000000000000002 (2 rows)

18-19   0x0002 (name length: 2)
20-21   "id" (0x6964)
22      0x00 (type: INT32)
23-26   0x00000008 (uncompressed: 8)
27-30   0x00000006 (compressed: 6)
31-38   0x0000000000000059 (offset: 89)

39-40   0x0004 (name length: 4)
41-44   "name" (0x6E616D65)
45      0x03 (type: STRING)
46-49   0x0000000E (uncompressed: 14)
50-53   0x0000000C (compressed: 12)
54-61   0x000000000000005F (offset: 95)

62-63   0x0003 (name length: 3)
64-66   "age" (0x616765)
67      0x00 (type: INT32)
68-71   0x00000008 (uncompressed: 8)
72-75   0x00000006 (compressed: 6)
76-83   0x000000000000006B (offset: 107)

84-88   [padding to align to offset]
89-94   [compressed column 0 data: 6 bytes]
95-106  [compressed column 1 data: 12 bytes]
107-112 [compressed column 2 data: 6 bytes]
```

**Total file size:** 113 bytes (header + compressed data)

---

## 9. Implementation Notes

### 9.1 Error Handling

Implementations should validate:
- Magic number matches "CLMN"
- Version is supported (currently only 0x0001)
- Column count > 0
- Row count ≥ 0
- All offsets are within file bounds
- Compressed sizes match actual compressed data
- Decompressed sizes match expected sizes for types

### 9.2 Performance Considerations

- **Memory:** Columns can be processed individually, avoiding loading entire dataset
- **I/O:** Selective reads minimize disk access
- **Compression:** Homogeneous column data compresses more efficiently than row data
- **Seek Performance:** Random access to columns via offsets enables parallel reading

### 9.3 Limitations

- Maximum string length: 65,535 bytes (due to 2-byte length prefix)
- Maximum column name length: 65,535 bytes
- Maximum columns: 4,294,967,295 (limited by 4-byte column count)
- Maximum rows: 2^63-1 (limited by 8-byte row count)
- No null value support in version 1.0

### 9.4 Future Extensions

Potential enhancements for future versions:
- Null value bitmap support
- Additional data types (DATE, TIMESTAMP, BOOLEAN, etc.)
- Dictionary encoding for low-cardinality string columns
- Configurable compression algorithms
- Column statistics in header (min, max, null count)
- Index structures for faster filtering

---

## 10. Compliance and Validation

A compliant implementation must:

1. **Write files** that:
   - Begin with the correct magic number
   - Include all required header fields in the specified order
   - Compress column data using zlib
   - Calculate correct offsets for all column blocks
   - Use big-endian byte order for all multi-byte integers

2. **Read files** that:
   - Validate the magic number before proceeding
   - Parse the header completely before reading data
   - Support selective column reads using offset information
   - Correctly decompress and parse all supported data types
   - Handle files created by other compliant implementations

3. **Preserve data integrity**:
   - Round-trip conversions (CSV → CLMN → CSV) must produce identical data
   - No data loss during serialization/deserialization
   - Correct handling of special cases (empty strings, zero values, etc.)

---

## 11. Reference Implementation

This specification is accompanied by a reference implementation in Java that demonstrates:
- Writing CSV data to CLMN format
- Reading CLMN files back to CSV
- Selective column reading for performance optimization
- Proper error handling and validation

See the accompanying source code for implementation details.

---

## 12. Changelog

**Version 1.0 (December 2025)**
- Initial specification
- Support for INT32, INT64, FLOAT64, and STRING types
- Per-column zlib compression
- Selective column read support

---

## 13. Contact and Contributions

For questions, bug reports, or suggestions for improvements to this specification, please refer to the project repository.

---

**End of Specification**