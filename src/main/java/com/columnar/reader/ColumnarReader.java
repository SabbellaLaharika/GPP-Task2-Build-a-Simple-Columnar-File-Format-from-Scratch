package com.columnar.reader;

import com.columnar.compression.CompressionUtil;
import com.columnar.model.ColumnSchema;
import com.columnar.model.ColumnType;
import com.columnar.model.FileHeader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

/**
 * Reads CLMN (columnar) format files and converts them back to CSV.
 * Supports reading entire files or selective column reads.
 */
public class ColumnarReader {

    private final String inputClmnPath;

    /**
     * Create a columnar reader.
     *
     * @param inputClmnPath Path to input CLMN file
     * @throws IllegalArgumentException if path is null or empty
     */
    public ColumnarReader(String inputClmnPath) {
        if (inputClmnPath == null || inputClmnPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Input CLMN path cannot be null or empty");
        }
        this.inputClmnPath = inputClmnPath;
    }

    /**
     * Read CLMN file and convert to CSV.
     *
     * @param outputCsvPath Path to output CSV file
     * @throws IOException if reading/writing fails
     */
    public void readToCSV(String outputCsvPath) throws IOException {
        if (outputCsvPath == null || outputCsvPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Output CSV path cannot be null or empty");
        }

        System.out.println("Reading CLMN file: " + inputClmnPath);

        // Step 1: Read and validate file header
        FileHeader header = readFileHeader(inputClmnPath);
        System.out.println(header.toDetailedString());

        // Step 2: Read and decompress all columns
        List<List<String>> allColumns = readAllColumns(inputClmnPath, header);

        // Step 3: Write to CSV
        writeToCSV(outputCsvPath, header, allColumns);
        System.out.println("\nSuccessfully wrote CSV file: " + outputCsvPath);
    }

    /**
     * Read CLMN file header.
     */
    private FileHeader readFileHeader(String clmnPath) throws IOException {
        File clmnFile = new File(clmnPath);
        if (!clmnFile.exists()) {
            throw new FileNotFoundException("CLMN file not found: " + clmnPath);
        }
        if (!clmnFile.canRead()) {
            throw new IOException("Cannot read CLMN file: " + clmnPath);
        }

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(new FileInputStream(clmnFile)))) {

            // Read and validate magic number
            int magicNumber = input.readInt();
            if (magicNumber != FileHeader.MAGIC_NUMBER) {
                throw new IOException(
                    String.format("Invalid magic number: 0x%08X (expected 0x%08X). " +
                        "This is not a valid CLMN file.", magicNumber, FileHeader.MAGIC_NUMBER)
                );
            }

            // Read and validate version
            short version = input.readShort();
            if (version != FileHeader.VERSION) {
                throw new IOException(
                    String.format("Unsupported version: %d (expected %d). " +
                        "This file may require a newer version of the reader.", 
                        version, FileHeader.VERSION)
                );
            }

            // Read column and row counts
            int columnCount = input.readInt();
            long rowCount = input.readLong();

            if (columnCount <= 0) {
                throw new IOException(
                    String.format("Invalid column count: %d", columnCount)
                );
            }
            if (rowCount < 0) {
                throw new IOException(
                    String.format("Invalid row count: %d", rowCount)
                );
            }

            System.out.println(String.format("File contains %d columns, %d rows", 
                columnCount, rowCount));

            // Read column schemas
            List<ColumnSchema> columnSchemas = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                // Read column name
                int nameLength = input.readUnsignedShort();
                byte[] nameBytes = new byte[nameLength];
                int bytesRead = input.read(nameBytes);
                if (bytesRead != nameLength) {
                    throw new IOException(
                        String.format("Failed to read column name for column %d: " +
                            "expected %d bytes, got %d bytes", i, nameLength, bytesRead)
                    );
                }
                String columnName = new String(nameBytes, StandardCharsets.UTF_8);

                // Read column type
                byte typeCode = input.readByte();
                ColumnType columnType;
                try {
                    columnType = ColumnType.fromCode(typeCode);
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                        String.format("Invalid type code for column %d ('%s'): %s", 
                            i, columnName, e.getMessage()),
                        e
                    );
                }

                // Read sizes and offset
                int uncompressedSize = input.readInt();
                int compressedSize = input.readInt();
                long offset = input.readLong();

                // Validate values
                if (uncompressedSize < 0 || compressedSize < 0 || offset < 0) {
                    throw new IOException(
                        String.format("Invalid schema values for column %d ('%s'): " +
                            "uncompressed=%d, compressed=%d, offset=%d",
                            i, columnName, uncompressedSize, compressedSize, offset)
                    );
                }

                ColumnSchema schema = new ColumnSchema(
                    columnName, columnType, uncompressedSize, compressedSize, offset
                );
                columnSchemas.add(schema);

                System.out.println(String.format("  Column %d: %s", i, schema));
            }

            FileHeader header = new FileHeader(rowCount, columnSchemas);
            header.validate(); // Validate consistency

            return header;

        } catch (IOException e) {
            throw new IOException("Failed to read CLMN file header: " + e.getMessage(), e);
        }
    }

    /**
     * Read all columns from CLMN file.
     */
    private List<List<String>> readAllColumns(String clmnPath, FileHeader header) 
            throws IOException {
        List<List<String>> allColumns = new ArrayList<>();

        try (RandomAccessFile file = new RandomAccessFile(clmnPath, "r")) {
            for (int i = 0; i < header.getColumnCount(); i++) {
                ColumnSchema schema = header.getColumnSchema(i);
                System.out.println(String.format("\nReading column: %s", schema.getName()));

                // Seek to column offset
                file.seek(schema.getOffset());

                // Read compressed data
                byte[] compressedData = new byte[schema.getCompressedSize()];
                int bytesRead = file.read(compressedData);
                if (bytesRead != schema.getCompressedSize()) {
                    throw new IOException(
                        String.format("Failed to read compressed data for column '%s': " +
                            "expected %d bytes, got %d bytes",
                            schema.getName(), schema.getCompressedSize(), bytesRead)
                    );
                }
                System.out.println(String.format("  Read %d compressed bytes", bytesRead));

                // Decompress data
                byte[] uncompressedData;
                try {
                    uncompressedData = CompressionUtil.decompress(
                        compressedData, schema.getUncompressedSize()
                    );
                    System.out.println(String.format("  Decompressed to %d bytes", 
                        uncompressedData.length));
                } catch (DataFormatException e) {
                    throw new IOException(
                        String.format("Failed to decompress column '%s': %s",
                            schema.getName(), e.getMessage()),
                        e
                    );
                }

                // Deserialize data
                List<String> columnValues = ColumnDeserializer.deserializeColumn(
                    uncompressedData, schema.getType(), header.getRowCount()
                );
                System.out.println(String.format("  Deserialized %d values", 
                    columnValues.size()));

                allColumns.add(columnValues);
            }
        } catch (IOException e) {
            throw new IOException("Failed to read column data: " + e.getMessage(), e);
        }

        return allColumns;
    }

    /**
     * Read specific columns by name (selective column read).
     *
     * @param columnNames List of column names to read
     * @return List of columns (each column is a list of string values)
     * @throws IOException if reading fails
     */
    public List<List<String>> readColumns(List<String> columnNames) throws IOException {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("Column names list cannot be null or empty");
        }

        System.out.println("Reading CLMN file (selective read): " + inputClmnPath);
        System.out.println("Requested columns: " + String.join(", ", columnNames));

        // Step 1: Read file header
        FileHeader header = readFileHeader(inputClmnPath);

        // Step 2: Find requested columns in header
        List<ColumnSchema> requestedSchemas = new ArrayList<>();
        for (String columnName : columnNames) {
            ColumnSchema schema = header.getColumnSchemaByName(columnName);
            if (schema == null) {
                throw new IllegalArgumentException(
                    String.format("Column '%s' not found in file. Available columns: %s",
                        columnName, String.join(", ", header.getColumnNames()))
                );
            }
            requestedSchemas.add(schema);
        }

        // Step 3: Read only requested columns (using offsets to skip others)
        List<List<String>> selectedColumns = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(inputClmnPath, "r")) {
            for (ColumnSchema schema : requestedSchemas) {
                System.out.println(String.format("\nReading column: %s", schema.getName()));

                // Seek directly to this column's offset
                file.seek(schema.getOffset());

                // Read compressed data
                byte[] compressedData = new byte[schema.getCompressedSize()];
                int bytesRead = file.read(compressedData);
                if (bytesRead != schema.getCompressedSize()) {
                    throw new IOException(
                        String.format("Failed to read column '%s'", schema.getName())
                    );
                }

                // Decompress
                byte[] uncompressedData;
                try {
                    uncompressedData = CompressionUtil.decompress(
                        compressedData, schema.getUncompressedSize()
                    );
                } catch (DataFormatException e) {
                    throw new IOException(
                        String.format("Failed to decompress column '%s': %s",
                            schema.getName(), e.getMessage()),
                        e
                    );
                }

                // Deserialize
                List<String> columnValues = ColumnDeserializer.deserializeColumn(
                    uncompressedData, schema.getType(), header.getRowCount()
                );

                selectedColumns.add(columnValues);
                System.out.println(String.format("  Read %d values", columnValues.size()));
            }
        }

        return selectedColumns;
    }

    /**
     * Write columns to CSV file.
     */
    private void writeToCSV(String csvPath, FileHeader header, List<List<String>> allColumns) 
            throws IOException {

        try (Writer writer = new FileWriter(csvPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            // Write header row
            csvPrinter.printRecord(header.getColumnNames());

            // Write data rows
            long rowCount = header.getRowCount();
            for (long i = 0; i < rowCount; i++) {
                List<String> row = new ArrayList<>();
                for (List<String> column : allColumns) {
                    row.add(column.get((int) i));
                }
                csvPrinter.printRecord(row);
            }

            csvPrinter.flush();

        } catch (IOException e) {
            throw new IOException("Failed to write CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Get file header without reading column data.
     *
     * @return FileHeader
     * @throws IOException if reading fails
     */
    public FileHeader getFileHeader() throws IOException {
        return readFileHeader(inputClmnPath);
    }
}