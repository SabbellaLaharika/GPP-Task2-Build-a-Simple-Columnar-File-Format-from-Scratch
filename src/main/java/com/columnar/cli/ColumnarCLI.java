package com.columnar.cli;

import com.columnar.reader.ColumnarReader;
import com.columnar.writer.ColumnarWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Command-line interface for columnar format converter tools.
 * Provides csv_to_custom and custom_to_csv commands.
 */
public class ColumnarCLI {

    private static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        // Check if any arguments provided
        if (args.length == 0) {
            printHelp();
            System.exit(0);
        }

        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "csv_to_custom":
                    handleCsvToCustom(args);
                    break;
                case "custom_to_csv":
                    handleCustomToCsv(args);
                    break;
                case "read":
                    handleRead(args);
                    break;
                case "--help":
                case "-h":
                case "help":
                    printHelp();
                    break;
                case "--version":
                case "-v":
                case "version":
                    printVersion();
                    break;
                default:
                    System.err.println("Error: Unknown command '" + command + "'");
                    System.err.println();
                    printHelp();
                    System.exit(1);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printHelp();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Handle csv_to_custom command.
     */
    private static void handleCsvToCustom(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "csv_to_custom requires 2 arguments: <input.csv> <output.clmn>"
            );
        }

        String inputCsv = args[1];
        String outputClmn = args[2];

        System.out.println("========================================");
        System.out.println("CSV to Columnar Format Converter");
        System.out.println("========================================");
        System.out.println();

        long startTime = System.currentTimeMillis();

        ColumnarWriter writer = new ColumnarWriter(inputCsv, outputClmn);
        writer.write();

        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;

        System.out.println();
        System.out.println(String.format("Conversion completed in %.2f seconds", elapsedSeconds));
    }

    /**
     * Handle custom_to_csv command.
     */
    private static void handleCustomToCsv(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "custom_to_csv requires 2 arguments: <input.clmn> <output.csv>"
            );
        }

        String inputClmn = args[1];
        String outputCsv = args[2];

        System.out.println("========================================");
        System.out.println("Columnar Format to CSV Converter");
        System.out.println("========================================");
        System.out.println();

        long startTime = System.currentTimeMillis();

        ColumnarReader reader = new ColumnarReader(inputClmn);
        reader.readToCSV(outputCsv);

        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;

        System.out.println();
        System.out.println(String.format("Conversion completed in %.2f seconds", elapsedSeconds));
    }

    /**
     * Handle read command (selective column read).
     */
    private static void handleRead(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                "read requires at least 2 arguments: <input.clmn> --columns <col1,col2,...>"
            );
        }

        String inputClmn = args[1];
        String columnsArg = null;

        // Parse --columns argument
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--columns") && i + 1 < args.length) {
                columnsArg = args[i + 1];
                break;
            }
        }

        if (columnsArg == null) {
            throw new IllegalArgumentException(
                "read command requires --columns argument with comma-separated column names"
            );
        }

        // Parse column names
        String[] columnArray = columnsArg.split(",");
        List<String> columnNames = Arrays.asList(columnArray);

        System.out.println("========================================");
        System.out.println("Selective Column Read");
        System.out.println("========================================");
        System.out.println();

        long startTime = System.currentTimeMillis();

        ColumnarReader reader = new ColumnarReader(inputClmn);
        List<List<String>> columns = reader.readColumns(columnNames);

        long endTime = System.currentTimeMillis();
        double elapsedSeconds = (endTime - startTime) / 1000.0;

        // Print first 10 rows of selected columns
        System.out.println("\nFirst 10 rows:");
        System.out.println(String.join(" | ", columnNames));
        System.out.println("-".repeat(50));

        int rowsToPrint = Math.min(10, columns.get(0).size());
        for (int i = 0; i < rowsToPrint; i++) {
            List<String> row = new ArrayList<String>();
            for (List<String> column : columns) {
                row.add(column.get(i));
            }
            System.out.println(String.join(" | ", row));
        }

        if (columns.get(0).size() > 10) {
            System.out.println("... (" + (columns.get(0).size() - 10) + " more rows)");
        }

        System.out.println();
        System.out.println(String.format("Read completed in %.2f seconds", elapsedSeconds));
    }

    /**
     * Print help message.
     */
    private static void printHelp() {
        System.out.println("Columnar File Format Converter - Version " + VERSION);
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  java -jar columnar-format.jar <command> [arguments]");
        System.out.println();
        System.out.println("COMMANDS:");
        System.out.println("  csv_to_custom <input.csv> <output.clmn>");
        System.out.println("      Convert a CSV file to columnar format");
        System.out.println();
        System.out.println("  custom_to_csv <input.clmn> <output.csv>");
        System.out.println("      Convert a columnar format file back to CSV");
        System.out.println();
        System.out.println("  read <input.clmn> --columns <col1,col2,...>");
        System.out.println("      Read specific columns from a columnar file");
        System.out.println("      Example: read data.clmn --columns id,name,age");
        System.out.println();
        System.out.println("  --help, -h, help");
        System.out.println("      Show this help message");
        System.out.println();
        System.out.println("  --version, -v, version");
        System.out.println("      Show version information");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  # Convert CSV to columnar format");
        System.out.println("  java -jar columnar-format.jar csv_to_custom data.csv data.clmn");
        System.out.println();
        System.out.println("  # Convert back to CSV");
        System.out.println("  java -jar columnar-format.jar custom_to_csv data.clmn output.csv");
        System.out.println();
        System.out.println("  # Read only specific columns");
        System.out.println("  java -jar columnar-format.jar read data.clmn --columns id,name");
        System.out.println();
        System.out.println("For more information, see SPEC.md");
    }

    /**
     * Print version information.
     */
    private static void printVersion() {
        System.out.println("Columnar File Format Converter");
        System.out.println("Version: " + VERSION);
        System.out.println("Format Version: " + com.columnar.model.FileHeader.VERSION);
        System.out.println();
        System.out.println("Created for GPP Task 2");
    }
}