package com.columnar.writer;

import com.columnar.compression.CompressionUtil;
import com.columnar.model.ColumnSchema;
import com.columnar.model.ColumnType;
import com.columnar.model.FileHeader;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes CSV data to CLMN (columnar) format.
 * Handles reading CSV, organizing by columns, compression, and binary output.
 */
public class ColumnarWriter {

    private final String inputCsvPath;
    private final String outputClmnPath;

    /**
     * Create a columnar writer.
     *
     * @param inputCsvPath Path to input CSV file
     * @param outputClmnPath Path to output CLMN file
     * @throws IllegalArgumentException if paths are null or empty
     */
    public ColumnarWriter(String inputCsvPath, String outputClmnPath) {
        if (inputCsvPath == null || inputCsvPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Input CSV path cannot be null or empty");
        }
        if (outputClmnPath == null || outputClmnPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Output CLMN path cannot be null or empty");
        }

        this.inputCsvPath = inputCsvPath;
        this.outputClmnPath = outputClmnPath;
    }

    /**
     * Convert CSV to CLMN format.
     *
     * @throws IOException if reading/writing fails
     */
    public void write() throws IOException {
        System.out.println("Reading CSV file: " + inputCsvPath);

        // Step 1: Read CSV and organize data by columns
        CsvData csvData = readCsvFile(inputCsvPath);
        System.out.println(String.format("Read %d rows, %d columns", 
            csvData.rowCount, csvData.columnCount));

        // Step 2: Infer column types from first data row
        ColumnType[] columnTypes = inferColumnTypes(csvData);
        System.out.println("Inferred column types:");
        for (int i = 0; i < csvData.columnCount; i++) {
            System.out.println(String.format("  %s: %s", 
                csvData.columnNames.get(i), columnTypes[i]));
        }

        // Step 3: Serialize and compress each column
        List<ColumnData> columnDataList = new ArrayList<>();
        for (int i = 0; i < csvData.columnCount; i++) {
            String columnName = csvData.columnNames.get(i);
            ColumnType columnType = columnTypes[i];
            List<String> columnValues = csvData.columns.get(i);

            System.out.println(String.format("Processing column: %s (%s)", 
                columnName, columnType));

            // Serialize column to bytes
            byte[] uncompressedBytes = ColumnSerializer.serializeColumn(columnValues, columnType);
            System.out.println(String.format("  Serialized: %d bytes", uncompressedBytes.length));

            // Compress column
            byte[] compressedBytes = CompressionUtil.compress(uncompressedBytes);
            System.out.println(String.format("  Compressed: %d bytes (%.1f%% saved)",
                compressedBytes.length,
                CompressionUtil.calculateCompressionRatio(
                    uncompressedBytes.length, compressedBytes.length)));

            columnDataList.add(new ColumnData(
                columnName,
                columnType,
                uncompressedBytes.length,
                compressedBytes
            ));
        }

        // Step 4: Calculate offsets for each column
        List<ColumnSchema> columnSchemas = calculateOffsets(columnDataList);

        // Step 5: Create file header
        FileHeader header = new FileHeader(csvData.rowCount, columnSchemas);
        header.validate(); // Validate before writing
        System.out.println("\n" + header.toDetailedString());

        // Step 6: Write CLMN file
        writeClmnFile(outputClmnPath, header, columnDataList);
        System.out.println("\nSuccessfully wrote CLMN file: " + outputClmnPath);

        // Step 7: Print summary
        printSummary(header, csvData.rowCount);
    }

    /**
     * Read CSV file and organize data by columns.
     */
    private CsvData readCsvFile(String csvPath) throws IOException {
        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            throw new FileNotFoundException("CSV file not found: " + csvPath);
        }
        if (!csvFile.canRead()) {
            throw new IOException("Cannot read CSV file: " + csvPath);
        }

        CsvData csvData = new CsvData();

        try (Reader reader = new FileReader(csvFile, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build())) {

            // Get column names from header
            csvData.columnNames = new ArrayList<>(parser.getHeaderNames());
            csvData.columnCount = csvData.columnNames.size();

            if (csvData.columnCount == 0) {
                throw new IOException("CSV file has no columns");
            }

            // Initialize column lists
            for (int i = 0; i < csvData.columnCount; i++) {
                csvData.columns.add(new ArrayList<>());
            }

            // Read data row by row and organize by columns
            long rowCount = 0;
            for (CSVRecord record : parser) {
                if (record.size() != csvData.columnCount) {
                    throw new IOException(
                        String.format("Row %d has %d columns, expected %d",
                            rowCount + 1, record.size(), csvData.columnCount)
                    );
                }

                for (int i = 0; i < csvData.columnCount; i++) {
                    csvData.columns.get(i).add(record.get(i));
                }
                rowCount++;
            }

            csvData.rowCount = rowCount;

            if (rowCount == 0) {
                throw new IOException("CSV file has no data rows");
            }

            return csvData;

        } catch (IOException e) {
            throw new IOException("Failed to read CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Infer column types from the first row of data.
     */
    private ColumnType[] inferColumnTypes(CsvData csvData) {
        ColumnType[] types = new ColumnType[csvData.columnCount];

        for (int i = 0; i < csvData.columnCount; i++) {
            // Use first non-empty value to infer type
            String firstValue = csvData.columns.get(i).get(0);
            types[i] = ColumnType.inferFromString(firstValue);
        }

        return types;
    }

    /**
     * Calculate byte offsets for each column in the file.
     */
    private List<ColumnSchema> calculateOffsets(List<ColumnData> columnDataList) throws IOException {
        List<ColumnSchema> schemas = new ArrayList<>();

        // Calculate header size first
        int headerSize = FileHeader.FIXED_HEADER_SIZE;
        for (ColumnData columnData : columnDataList) {
            try {
                byte[] nameBytes = columnData.name.getBytes(StandardCharsets.UTF_8);
                headerSize += 2 + nameBytes.length + 1 + 4 + 4 + 8; // As per SPEC.md
            } catch (Exception e) {
                throw new IOException("Failed to calculate header size: " + e.getMessage(), e);
            }
        }

        // Calculate offset for each column
        long currentOffset = headerSize;
        for (ColumnData columnData : columnDataList) {
            ColumnSchema schema = new ColumnSchema(
                columnData.name,
                columnData.type,
                columnData.uncompressedSize,
                columnData.compressedBytes.length,
                currentOffset
            );
            schemas.add(schema);
            currentOffset += columnData.compressedBytes.length;
        }

        return schemas;
    }

    /**
     * Write the CLMN file with header and compressed column data.
     */
    private void writeClmnFile(String clmnPath, FileHeader header, List<ColumnData> columnDataList)
            throws IOException {

        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(clmnPath)))) {

            // Write magic number (4 bytes)
            output.writeInt(FileHeader.MAGIC_NUMBER);

            // Write version (2 bytes)
            output.writeShort(FileHeader.VERSION);

            // Write column count (4 bytes)
            output.writeInt(header.getColumnCount());

            // Write row count (8 bytes)
            output.writeLong(header.getRowCount());

            // Write column schemas
            for (ColumnSchema schema : header.getColumnSchemas()) {
                // Write column name length and name
                byte[] nameBytes = schema.getName().getBytes(StandardCharsets.UTF_8);
                output.writeShort(nameBytes.length);
                output.write(nameBytes);

                // Write type (1 byte)
                output.writeByte(schema.getType().getCode());

                // Write uncompressed size (4 bytes)
                output.writeInt(schema.getUncompressedSize());

                // Write compressed size (4 bytes)
                output.writeInt(schema.getCompressedSize());

                // Write offset (8 bytes)
                output.writeLong(schema.getOffset());
            }

            // Write compressed column data
            for (ColumnData columnData : columnDataList) {
                output.write(columnData.compressedBytes);
            }

            output.flush();

        } catch (IOException e) {
            throw new IOException("Failed to write CLMN file: " + e.getMessage(), e);
        }
    }

    /**
     * Print summary statistics.
     */
    private void printSummary(FileHeader header, long rowCount) {
        System.out.println("\n========== Conversion Summary ==========");
        System.out.println("Rows: " + rowCount);
        System.out.println("Columns: " + header.getColumnCount());
        System.out.println("Header size: " + header.getTotalHeaderSize() + " bytes");
        System.out.println("Uncompressed data: " + header.getTotalUncompressedSize() + " bytes");
        System.out.println("Compressed data: " + header.getTotalCompressedSize() + " bytes");
        System.out.println(String.format("Overall compression: %.1f%%", 
            header.getOverallCompressionRatio()));
        System.out.println("========================================");
    }

    /**
     * Helper class to hold CSV data organized by columns.
     */
    private static class CsvData {
        List<String> columnNames = new ArrayList<>();
        List<List<String>> columns = new ArrayList<>();
        int columnCount = 0;
        long rowCount = 0;
    }

    /**
     * Helper class to hold column data with compression.
     */
    private static class ColumnData {
        String name;
        ColumnType type;
        int uncompressedSize;
        byte[] compressedBytes;

        ColumnData(String name, ColumnType type, int uncompressedSize, byte[] compressedBytes) {
            this.name = name;
            this.type = type;
            this.uncompressedSize = uncompressedSize;
            this.compressedBytes = compressedBytes;
        }
    }
}