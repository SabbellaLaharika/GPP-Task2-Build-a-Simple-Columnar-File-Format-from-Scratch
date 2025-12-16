package com.columnar.reader;

import com.columnar.model.ColumnType;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes column data from bytes back to values.
 * This is the reverse operation of ColumnSerializer.
 */
public class ColumnDeserializer {

    /**
     * Deserialize a column's data from bytes.
     * 
     * @param data Uncompressed column data (after decompression)
     * @param type The column's data type
     * @param rowCount Expected number of rows
     * @return List of values as strings
     * @throws IOException if deserialization fails
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static List<String> deserializeColumn(byte[] data, ColumnType type, long rowCount) 
            throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Column type cannot be null");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException(
                String.format("Row count cannot be negative: %d", rowCount)
            );
        }

        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        DataInputStream dataStream = new DataInputStream(byteStream);

        try {
            switch (type) {
                case INT32:
                    return deserializeInt32Column(dataStream, rowCount);
                case INT64:
                    return deserializeInt64Column(dataStream, rowCount);
                case FLOAT64:
                    return deserializeFloat64Column(dataStream, rowCount);
                case STRING:
                    return deserializeStringColumn(dataStream, rowCount);
                default:
                    throw new IllegalArgumentException("Unsupported column type: " + type);
            }
        } finally {
            // Always close streams
            try {
                dataStream.close();
            } catch (IOException e) {
                System.err.println("Warning: Failed to close data stream: " + e.getMessage());
            }
            try {
                byteStream.close();
            } catch (IOException e) {
                System.err.println("Warning: Failed to close byte stream: " + e.getMessage());
            }
        }
    }

    /**
     * Deserialize INT32 column (4 bytes per value).
     */
    private static List<String> deserializeInt32Column(DataInputStream stream, long rowCount) 
            throws IOException {
        List<String> values = new ArrayList<>((int) rowCount);

        for (long i = 0; i < rowCount; i++) {
            try {
                int value = stream.readInt(); // Big-endian, 4 bytes
                values.add(String.valueOf(value));
            } catch (IOException e) {
                throw new IOException(
                    String.format("Failed to read INT32 value at row %d: %s", i, e.getMessage()),
                    e
                );
            }
        }

        return values;
    }

    /**
     * Deserialize INT64 column (8 bytes per value).
     */
    private static List<String> deserializeInt64Column(DataInputStream stream, long rowCount) 
            throws IOException {
        List<String> values = new ArrayList<>((int) rowCount);

        for (long i = 0; i < rowCount; i++) {
            try {
                long value = stream.readLong(); // Big-endian, 8 bytes
                values.add(String.valueOf(value));
            } catch (IOException e) {
                throw new IOException(
                    String.format("Failed to read INT64 value at row %d: %s", i, e.getMessage()),
                    e
                );
            }
        }

        return values;
    }

    /**
     * Deserialize FLOAT64 column (8 bytes per value).
     */
    private static List<String> deserializeFloat64Column(DataInputStream stream, long rowCount) 
            throws IOException {
        List<String> values = new ArrayList<>((int) rowCount);

        for (long i = 0; i < rowCount; i++) {
            try {
                double value = stream.readDouble(); // Big-endian, 8 bytes (IEEE 754)
                values.add(String.valueOf(value));
            } catch (IOException e) {
                throw new IOException(
                    String.format("Failed to read FLOAT64 value at row %d: %s", i, e.getMessage()),
                    e
                );
            }
        }

        return values;
    }

    /**
     * Deserialize STRING column (length-prefixed UTF-8).
     * Format: [2-byte length][UTF-8 bytes][2-byte length][UTF-8 bytes]...
     */
    private static List<String> deserializeStringColumn(DataInputStream stream, long rowCount) 
            throws IOException {
        List<String> values = new ArrayList<>((int) rowCount);

        for (long i = 0; i < rowCount; i++) {
            try {
                // Read length (2 bytes, unsigned short)
                int length = stream.readUnsignedShort();

                // Read UTF-8 bytes
                byte[] utf8Bytes = new byte[length];
                int bytesRead = stream.read(utf8Bytes);

                if (bytesRead != length) {
                    throw new IOException(
                        String.format("Expected %d bytes but read %d bytes for string at row %d", 
                            length, bytesRead, i)
                    );
                }

                // Convert to string
                String value = new String(utf8Bytes, StandardCharsets.UTF_8);
                values.add(value);

            } catch (IOException e) {
                throw new IOException(
                    String.format("Failed to read STRING value at row %d: %s", i, e.getMessage()),
                    e
                );
            }
        }

        return values;
    }

    /**
     * Validate that we've read all expected data.
     * 
     * @param stream The input stream
     * @param expectedRows Number of rows we expected to read
     * @param actualRows Number of rows we actually read
     * @throws IOException if counts don't match or stream has remaining data
     */
    public static void validateDeserializedData(DataInputStream stream, long expectedRows, 
                                                 long actualRows) throws IOException {
        if (actualRows != expectedRows) {
            throw new IOException(
                String.format("Row count mismatch: expected %d rows, but deserialized %d rows", 
                    expectedRows, actualRows)
            );
        }

        // Check if there's unexpected data remaining in the stream
        if (stream.available() > 0) {
            throw new IOException(
                String.format("Unexpected data remaining in stream: %d bytes", stream.available())
            );
        }
    }
}