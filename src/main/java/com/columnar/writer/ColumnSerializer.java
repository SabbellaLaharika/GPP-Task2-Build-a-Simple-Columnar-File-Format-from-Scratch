package com.columnar.writer;

import com.columnar.model.ColumnType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serializes column data to bytes according to the column type.
 * Each data type has its own serialization format as defined in SPEC.md.
 */
public class ColumnSerializer {

    /**
     * Serialize a column's data to a byte array.
     * 
     * @param values List of values in the column (as strings from CSV)
     * @param type The column's data type
     * @return Serialized byte array (uncompressed)
     * @throws IOException if serialization fails
     * @throws IllegalArgumentException if values or type is null, or data is invalid
     */
    public static byte[] serializeColumn(List<String> values, ColumnType type) throws IOException {
        if (values == null) {
            throw new IllegalArgumentException("Values list cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Column type cannot be null");
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);

        try {
            switch (type) {
                case INT32:
                    serializeInt32Column(values, dataStream);
                    break;
                case INT64:
                    serializeInt64Column(values, dataStream);
                    break;
                case FLOAT64:
                    serializeFloat64Column(values, dataStream);
                    break;
                case STRING:
                    serializeStringColumn(values, dataStream);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported column type: " + type);
            }

            dataStream.flush();
            return byteStream.toByteArray();

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
     * Serialize INT32 column (4 bytes per value).
     */
    private static void serializeInt32Column(List<String> values, DataOutputStream stream) 
            throws IOException {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            try {
                int intValue = Integer.parseInt(value.trim());
                stream.writeInt(intValue); // Big-endian, 4 bytes
            } catch (NumberFormatException e) {
                throw new IOException(
                    String.format("Invalid INT32 value at row %d: '%s'. Error: %s", 
                        i, value, e.getMessage()),
                    e
                );
            }
        }
    }

    /**
     * Serialize INT64 column (8 bytes per value).
     */
    private static void serializeInt64Column(List<String> values, DataOutputStream stream) 
            throws IOException {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            try {
                long longValue = Long.parseLong(value.trim());
                stream.writeLong(longValue); // Big-endian, 8 bytes
            } catch (NumberFormatException e) {
                throw new IOException(
                    String.format("Invalid INT64 value at row %d: '%s'. Error: %s", 
                        i, value, e.getMessage()),
                    e
                );
            }
        }
    }

    /**
     * Serialize FLOAT64 column (8 bytes per value).
     */
    private static void serializeFloat64Column(List<String> values, DataOutputStream stream) 
            throws IOException {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            try {
                double doubleValue = Double.parseDouble(value.trim());
                stream.writeDouble(doubleValue); // Big-endian, 8 bytes (IEEE 754)
            } catch (NumberFormatException e) {
                throw new IOException(
                    String.format("Invalid FLOAT64 value at row %d: '%s'. Error: %s", 
                        i, value, e.getMessage()),
                    e
                );
            }
        }
    }

    /**
     * Serialize STRING column (length-prefixed UTF-8).
     * Format: [2-byte length][UTF-8 bytes][2-byte length][UTF-8 bytes]...
     */
    private static void serializeStringColumn(List<String> values, DataOutputStream stream) 
            throws IOException {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                value = ""; // Treat null as empty string
            }

            byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);

            // Check if string is too long (max 65535 bytes)
            if (utf8Bytes.length > 65535) {
                throw new IOException(
                    String.format("String at row %d is too long: %d bytes (max 65535). " +
                        "Consider truncating or using a different encoding.", 
                        i, utf8Bytes.length)
                );
            }

            // Write length as unsigned short (2 bytes)
            stream.writeShort(utf8Bytes.length);
            
            // Write UTF-8 bytes
            stream.write(utf8Bytes);
        }
    }

    /**
     * Calculate the uncompressed size for a column.
     * 
     * @param values List of values
     * @param type Column type
     * @return Size in bytes
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static int calculateUncompressedSize(List<String> values, ColumnType type) {
        if (values == null) {
            throw new IllegalArgumentException("Values list cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Column type cannot be null");
        }

        switch (type) {
            case INT32:
                return values.size() * 4;
            case INT64:
            case FLOAT64:
                return values.size() * 8;
            case STRING:
                int totalSize = 0;
                for (String value : values) {
                    if (value == null) {
                        value = "";
                    }
                    byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
                    totalSize += 2 + utf8Bytes.length; // 2-byte length + actual bytes
                }
                return totalSize;
            default:
                throw new IllegalArgumentException("Unsupported column type: " + type);
        }
    }
}