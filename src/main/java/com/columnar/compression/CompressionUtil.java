package com.columnar.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Utility class for compressing and decompressing data using zlib algorithm.
 * Used to compress each column independently in the CLMN format.
 */
public class CompressionUtil {
    
    // Buffer size for compression/decompression operations
    private static final int BUFFER_SIZE = 8192;
    
    // Compression level (0-9, where 6 is default, 9 is maximum compression)
    private static final int COMPRESSION_LEVEL = Deflater.DEFAULT_COMPRESSION;

    /**
     * Compress data using zlib algorithm.
     * 
     * @param data The uncompressed data
     * @return Compressed data
     * @throws IOException if compression fails
     * @throws IllegalArgumentException if data is null
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        
        // Handle empty data
        if (data.length == 0) {
            return new byte[0];
        }

        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        
        try {
            deflater.setInput(data);
            deflater.finish();
            
            byte[] buffer = new byte[BUFFER_SIZE];
            
            // Compress data in chunks
            while (!deflater.finished()) {
                int compressedSize = deflater.deflate(buffer);
                outputStream.write(buffer, 0, compressedSize);
            }
            
            return outputStream.toByteArray();
            
        } finally {
            // Always clean up resources
            deflater.end();
            try {
                outputStream.close();
            } catch (IOException e) {
                // Log but don't throw - we already have the compressed data
                System.err.println("Warning: Failed to close output stream: " + e.getMessage());
            }
        }
    }

    /**
     * Decompress data using zlib algorithm.
     * 
     * @param compressedData The compressed data
     * @param uncompressedSize Expected size of uncompressed data (for validation)
     * @return Decompressed data
     * @throws IOException if decompression fails
     * @throws IllegalArgumentException if data is null or uncompressedSize is negative
     * @throws DataFormatException if compressed data is corrupted
     */
    public static byte[] decompress(byte[] compressedData, int uncompressedSize) 
            throws IOException, DataFormatException {
        
        if (compressedData == null) {
            throw new IllegalArgumentException("Compressed data cannot be null");
        }
        if (uncompressedSize < 0) {
            throw new IllegalArgumentException(
                String.format("Uncompressed size cannot be negative: %d", uncompressedSize)
            );
        }
        
        // Handle empty data
        if (compressedData.length == 0) {
            if (uncompressedSize != 0) {
                throw new DataFormatException(
                    String.format("Compressed data is empty but expected %d bytes after decompression", 
                        uncompressedSize)
                );
            }
            return new byte[0];
        }

        Inflater inflater = new Inflater();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(uncompressedSize);
        
        try {
            inflater.setInput(compressedData);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int totalDecompressed = 0;
            
            // Decompress data in chunks
            while (!inflater.finished()) {
                int decompressedSize = inflater.inflate(buffer);
                
                if (decompressedSize == 0) {
                    // Check if we need more input or if we're done
                    if (inflater.needsInput()) {
                        throw new DataFormatException(
                            "Compressed data is incomplete - inflater needs more input"
                        );
                    }
                    break;
                }
                
                outputStream.write(buffer, 0, decompressedSize);
                totalDecompressed += decompressedSize;
            }
            
            byte[] result = outputStream.toByteArray();
            
            // Validate decompressed size matches expected size
            if (result.length != uncompressedSize) {
                throw new DataFormatException(
                    String.format("Decompressed size mismatch: expected %d bytes, got %d bytes", 
                        uncompressedSize, result.length)
                );
            }
            
            return result;
            
        } finally {
            // Always clean up resources
            inflater.end();
            try {
                outputStream.close();
            } catch (IOException e) {
                // Log but don't throw - we already have the decompressed data
                System.err.println("Warning: Failed to close output stream: " + e.getMessage());
            }
        }
    }

    /**
     * Calculate compression ratio.
     * 
     * @param originalSize Size before compression
     * @param compressedSize Size after compression
     * @return Compression ratio as percentage (0-100)
     * @throws IllegalArgumentException if sizes are negative
     */
    public static double calculateCompressionRatio(int originalSize, int compressedSize) {
        if (originalSize < 0) {
            throw new IllegalArgumentException(
                String.format("Original size cannot be negative: %d", originalSize)
            );
        }
        if (compressedSize < 0) {
            throw new IllegalArgumentException(
                String.format("Compressed size cannot be negative: %d", compressedSize)
            );
        }
        
        if (originalSize == 0) {
            return 0.0;
        }
        
        return (1.0 - ((double) compressedSize / originalSize)) * 100.0;
    }

    /**
     * Estimate compressed size before actually compressing.
     * This is a rough estimate - actual size may vary.
     * 
     * @param uncompressedSize Size of uncompressed data
     * @return Estimated compressed size
     */
    public static int estimateCompressedSize(int uncompressedSize) {
        if (uncompressedSize <= 0) {
            return 0;
        }
        
        // Conservative estimate: assume 50% compression ratio
        // Actual compression depends on data entropy
        // Add some overhead for zlib headers and safety margin
        return (int) (uncompressedSize * 0.6) + 100;
    }

    /**
     * Test if compression would be beneficial for given data.
     * 
     * @param data Data to test
     * @return true if compression is likely to reduce size, false otherwise
     */
    public static boolean shouldCompress(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        
        // Very small data might not compress well (overhead is too high)
        if (data.length < 100) {
            return false;
        }
        
        // For data >= 100 bytes, compression is usually beneficial
        return true;
    }
}