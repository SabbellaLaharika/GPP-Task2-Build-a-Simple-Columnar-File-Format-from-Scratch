package com.columnar.model;

/**
 * Represents the schema (metadata) for a single column in the CLMN format.
 * This information is stored in the file header.
 */
public class ColumnSchema {
    private final String name;
    private final ColumnType type;
    private final int uncompressedSize;
    private final int compressedSize;
    private final long offset;

    /**
     * Create a column schema.
     * 
     * @param name Column name
     * @param type Column data type
     * @param uncompressedSize Size of column data before compression
     * @param compressedSize Size of column data after compression
     * @param offset Byte offset where this column's data starts in the file
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ColumnSchema(String name, ColumnType type, int uncompressedSize, 
                       int compressedSize, long offset) {
        // Validate inputs
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Column type cannot be null");
        }
        if (uncompressedSize < 0) {
            throw new IllegalArgumentException(
                String.format("Uncompressed size cannot be negative: %d", uncompressedSize)
            );
        }
        if (compressedSize < 0) {
            throw new IllegalArgumentException(
                String.format("Compressed size cannot be negative: %d", compressedSize)
            );
        }
        if (offset < 0) {
            throw new IllegalArgumentException(
                String.format("Offset cannot be negative: %d", offset)
            );
        }

        this.name = name.trim();
        this.type = type;
        this.uncompressedSize = uncompressedSize;
        this.compressedSize = compressedSize;
        this.offset = offset;
    }

    // Getters
    public String getName() {
        return name;
    }

    public ColumnType getType() {
        return type;
    }

    public int getUncompressedSize() {
        return uncompressedSize;
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public long getOffset() {
        return offset;
    }

    /**
     * Calculate the compression ratio (percentage).
     * 
     * @return Compression ratio (0-100), or 0 if uncompressed size is 0
     */
    public double getCompressionRatio() {
        if (uncompressedSize == 0) {
            return 0.0;
        }
        return (1.0 - ((double) compressedSize / uncompressedSize)) * 100.0;
    }

    /**
     * Get the size of this schema entry when written to the file header.
     * Format: nameLength(2) + nameBytes(N) + type(1) + uncompressed(4) + compressed(4) + offset(8)
     * 
     * @return Size in bytes
     */
    public int getSchemaEntrySize() {
        try {
            byte[] nameBytes = name.getBytes("UTF-8");
            return 2 + nameBytes.length + 1 + 4 + 4 + 8; // Total: 19 + name length
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported, but we need to handle the exception
            throw new RuntimeException("UTF-8 encoding not supported (this should never happen)", e);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "ColumnSchema{name='%s', type=%s, uncompressedSize=%d, compressedSize=%d, offset=%d, compression=%.1f%%}",
            name, type, uncompressedSize, compressedSize, offset, getCompressionRatio()
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ColumnSchema other = (ColumnSchema) obj;
        return name.equals(other.name) && 
               type == other.type &&
               uncompressedSize == other.uncompressedSize &&
               compressedSize == other.compressedSize &&
               offset == other.offset;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + uncompressedSize;
        result = 31 * result + compressedSize;
        result = 31 * result + (int) (offset ^ (offset >>> 32));
        return result;
    }
}