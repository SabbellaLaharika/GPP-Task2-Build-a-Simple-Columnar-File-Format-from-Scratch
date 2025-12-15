package com.columnar.model;

/**
 * Enumeration of supported column data types in the CLMN format.
 * Each type has a unique byte code used in the binary format.
 */
public enum ColumnType {
    INT32((byte) 0x00, 4),      // For id, age
    INT64((byte) 0x01, 8),      // For very large integers
    FLOAT64((byte) 0x02, 8),    // For salary (decimals)
    STRING((byte) 0x03, -1);    // For name, email, city

    private final byte code;
    private final int fixedSize; // -1 means variable length

    ColumnType(byte code, int fixedSize) {
        this.code = code;
        this.fixedSize = fixedSize;
    }

    /**
     * @return The byte code representing this type in the binary format
     */
    public byte getCode() {
        return code;
    }

    /**
     * @return The fixed size in bytes, or -1 for variable-length types
     */
    public int getFixedSize() {
        return fixedSize;
    }

    /**
     * @return True if this is a fixed-size type
     */
    public boolean isFixedSize() {
        return fixedSize > 0;
    }

    /**
     * Convert byte code back to ColumnType (for reading files).
     * 
     * @param code The byte code from the file
     * @return The corresponding ColumnType
     * @throws IllegalArgumentException if code is invalid
     */
    public static ColumnType fromCode(byte code) {
        for (ColumnType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            String.format("Invalid column type code: 0x%02X. Valid codes are: 0x00 (INT32), 0x01 (INT64), 0x02 (FLOAT64), 0x03 (STRING)", 
            code)
        );
    }

    /**
     * Infer the most appropriate column type from a string value.
     * This is used when reading CSV files to automatically detect column types.
     * 
     * @param value The string value to analyze
     * @return The most appropriate ColumnType
     */
    public static ColumnType inferFromString(String value) {
        // Null or empty strings are treated as STRING type
        if (value == null || value.trim().isEmpty()) {
            return STRING;
        }

        String trimmedValue = value.trim();

        // Try parsing as integer first (most restrictive)
        try {
            long longValue = Long.parseLong(trimmedValue);
            
            // Check if it fits in INT32 range
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return INT32;
            } else {
                return INT64;
            }
        } catch (NumberFormatException e) {
            // Not a valid integer - this is expected for non-integer values
            // Continue to check if it's a floating point number
        }

        // Try parsing as floating point number
        try {
            Double.parseDouble(trimmedValue);
            // Successfully parsed as double, return FLOAT64
            return FLOAT64;
        } catch (NumberFormatException e) {
            // Not a valid number at all - this is expected for text values
            // Will be treated as STRING type
        }

        // Default to STRING type for any value that isn't a number
        return STRING;
    }

    /**
     * Infer column types from the first row of data values.
     * 
     * @param firstRowValues Array of string values from the first data row
     * @return Array of inferred ColumnTypes
     * @throws IllegalArgumentException if firstRowValues is null or empty
     */
    public static ColumnType[] inferTypes(String[] firstRowValues) {
        if (firstRowValues == null || firstRowValues.length == 0) {
            throw new IllegalArgumentException("Cannot infer types from null or empty data row");
        }

        ColumnType[] types = new ColumnType[firstRowValues.length];
        for (int i = 0; i < firstRowValues.length; i++) {
            types[i] = inferFromString(firstRowValues[i]);
        }
        return types;
    }

    /**
     * Get a human-readable description of this type.
     * 
     * @return Description string
     */
    public String getDescription() {
        switch (this) {
            case INT32:
                return "32-bit signed integer";
            case INT64:
                return "64-bit signed integer";
            case FLOAT64:
                return "64-bit IEEE 754 double-precision floating point";
            case STRING:
                return "Variable-length UTF-8 encoded string";
            default:
                return "Unknown type";
        }
    }

    @Override
    public String toString() {
        return name() + " (0x" + String.format("%02X", code) + ")";
    }
}