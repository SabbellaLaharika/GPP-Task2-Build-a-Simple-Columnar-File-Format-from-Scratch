package com.columnar.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the file header of a CLMN file.
 * Contains metadata about the entire file and all column schemas.
 */
public class FileHeader {
    // Magic number "CLMN" = 0x434C4D4E
    public static final int MAGIC_NUMBER = 0x434C4D4E;
    public static final short VERSION = 1;
    
    // Fixed header size: magic(4) + version(2) + columnCount(4) + rowCount(8) = 18 bytes
    public static final int FIXED_HEADER_SIZE = 18;

    private final int columnCount;
    private final long rowCount;
    private final List<ColumnSchema> columnSchemas;

    /**
     * Create a file header.
     * 
     * @param rowCount Total number of rows in the file
     * @param columnSchemas List of column schemas (must not be empty)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public FileHeader(long rowCount, List<ColumnSchema> columnSchemas) {
        if (rowCount < 0) {
            throw new IllegalArgumentException(
                String.format("Row count cannot be negative: %d", rowCount)
            );
        }
        if (columnSchemas == null || columnSchemas.isEmpty()) {
            throw new IllegalArgumentException("Column schemas cannot be null or empty");
        }

        this.rowCount = rowCount;
        this.columnCount = columnSchemas.size();
        // Create immutable copy to prevent external modification
        this.columnSchemas = Collections.unmodifiableList(new ArrayList<>(columnSchemas));
    }

    // Getters
    public int getColumnCount() {
        return columnCount;
    }

    public long getRowCount() {
        return rowCount;
    }

    public List<ColumnSchema> getColumnSchemas() {
        return columnSchemas;
    }

    /**
     * Get a column schema by name.
     * 
     * @param columnName Name of the column to find
     * @return ColumnSchema if found, null otherwise
     */
    public ColumnSchema getColumnSchemaByName(String columnName) {
        if (columnName == null) {
            return null;
        }
        
        for (ColumnSchema schema : columnSchemas) {
            if (schema.getName().equals(columnName)) {
                return schema;
            }
        }
        return null;
    }

    /**
     * Get a column schema by index.
     * 
     * @param index Column index (0-based)
     * @return ColumnSchema at the given index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public ColumnSchema getColumnSchema(int index) {
        if (index < 0 || index >= columnSchemas.size()) {
            throw new IndexOutOfBoundsException(
                String.format("Column index %d is out of bounds (0-%d)", index, columnSchemas.size() - 1)
            );
        }
        return columnSchemas.get(index);
    }

    /**
     * Calculate the total size of the header when written to file.
     * This includes the fixed header (18 bytes) plus all column schema entries.
     * 
     * @return Total header size in bytes
     */
    public int getTotalHeaderSize() {
        int size = FIXED_HEADER_SIZE;
        for (ColumnSchema schema : columnSchemas) {
            size += schema.getSchemaEntrySize();
        }
        return size;
    }

    /**
     * Calculate total uncompressed data size (all columns).
     * 
     * @return Total uncompressed size in bytes
     */
    public long getTotalUncompressedSize() {
        long total = 0;
        for (ColumnSchema schema : columnSchemas) {
            total += schema.getUncompressedSize();
        }
        return total;
    }

    /**
     * Calculate total compressed data size (all columns).
     * 
     * @return Total compressed size in bytes
     */
    public long getTotalCompressedSize() {
        long total = 0;
        for (ColumnSchema schema : columnSchemas) {
            total += schema.getCompressedSize();
        }
        return total;
    }

    /**
     * Calculate overall compression ratio for the entire file.
     * 
     * @return Compression ratio (0-100), or 0 if uncompressed size is 0
     */
    public double getOverallCompressionRatio() {
        long uncompressed = getTotalUncompressedSize();
        if (uncompressed == 0) {
            return 0.0;
        }
        long compressed = getTotalCompressedSize();
        return (1.0 - ((double) compressed / uncompressed)) * 100.0;
    }

    /**
     * Get column names in order.
     * 
     * @return List of column names
     */
    public List<String> getColumnNames() {
        List<String> names = new ArrayList<>();
        for (ColumnSchema schema : columnSchemas) {
            names.add(schema.getName());
        }
        return names;
    }

    /**
     * Validate that the header is consistent with CLMN format specification.
     * 
     * @throws IllegalStateException if header is invalid
     */
    public void validate() {
        if (columnCount != columnSchemas.size()) {
            throw new IllegalStateException(
                String.format("Column count mismatch: expected %d, got %d schemas", 
                    columnCount, columnSchemas.size())
            );
        }

        // Check for duplicate column names
        List<String> names = new ArrayList<>();
        for (ColumnSchema schema : columnSchemas) {
            String name = schema.getName();
            if (names.contains(name)) {
                throw new IllegalStateException(
                    String.format("Duplicate column name found: '%s'", name)
                );
            }
            names.add(name);
        }

        // Verify offsets are in ascending order
        long lastOffset = getTotalHeaderSize();
        for (int i = 0; i < columnSchemas.size(); i++) {
            ColumnSchema schema = columnSchemas.get(i);
            if (schema.getOffset() < lastOffset) {
                throw new IllegalStateException(
                    String.format("Column %d ('%s') has invalid offset: %d (expected >= %d)", 
                        i, schema.getName(), schema.getOffset(), lastOffset)
                );
            }
            lastOffset = schema.getOffset() + schema.getCompressedSize();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "FileHeader{columns=%d, rows=%d, headerSize=%d bytes, " +
            "uncompressed=%d bytes, compressed=%d bytes, compression=%.1f%%}",
            columnCount, rowCount, getTotalHeaderSize(),
            getTotalUncompressedSize(), getTotalCompressedSize(), 
            getOverallCompressionRatio()
        );
    }

    /**
     * Get a detailed string representation including all column schemas.
     * 
     * @return Detailed string with all schemas
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toString()).append("\n");
        sb.append("Columns:\n");
        for (int i = 0; i < columnSchemas.size(); i++) {
            sb.append(String.format("  [%d] %s\n", i, columnSchemas.get(i)));
        }
        return sb.toString();
    }
}