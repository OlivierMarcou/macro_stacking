// src/main/java/com/macrostacking/ImageLoader.java
package com.macrostacking;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ImageLoader {

    public static BufferedImage loadImage(File file) throws Exception {
        String filename = file.getName().toLowerCase();

        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") ||
                filename.endsWith(".png") || filename.endsWith(".tif") ||
                filename.endsWith(".tiff")) {
            return ImageIO.read(file);
        }

        return loadRawImage(file);
    }

    private static BufferedImage loadRawImage(File file) throws Exception {
        Exception lastException = null;

        try {
            return loadRawWithDcraw(file);
        } catch (Exception e1) {
            lastException = e1;
            System.err.println("dcraw failed: " + e1.getMessage());

            try {
                return loadRawWithImageMagick(file);
            } catch (Exception e2) {
                lastException = e2;
                System.err.println("ImageMagick failed: " + e2.getMessage());

                try {
                    return extractEmbeddedJpeg(file);
                } catch (Exception e3) {
                    lastException = e3;
                    System.err.println("Embedded JPEG failed: " + e3.getMessage());
                }
            }
        }

        throw new Exception("Impossible de charger le fichier RAW: " + file.getName() +
                "\nInstallez dcraw: sudo apt-get install dcraw\n" +
                "Erreur: " + (lastException != null ? lastException.getMessage() : "Unknown"));
    }

    private static BufferedImage loadRawWithDcraw(File file) throws Exception {
        File tempPpm = null;
        try {
            tempPpm = File.createTempFile("raw_dcraw_", ".ppm");

            ProcessBuilder pb = new ProcessBuilder(
                    "dcraw",
                    "-c",           // Output to stdout
                    "-w",           // Use camera white balance
                    "-q", "3",      // High quality
                    "-o", "1",      // sRGB color space
                    "-4",           // 16-bit linear output
                    file.getAbsolutePath()
            );

            pb.redirectOutput(tempPpm);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new Exception("dcraw exit code: " + exitCode);
            }

            if (!tempPpm.exists() || tempPpm.length() < 1000) {
                throw new Exception("dcraw output file too small or missing");
            }

            BufferedImage img = ImageIO.read(tempPpm);
            if (img == null) {
                throw new Exception("Failed to read dcraw output");
            }

            System.out.println("dcraw loaded: " + img.getWidth() + "x" + img.getHeight());
            return img;

        } finally {
            if (tempPpm != null && tempPpm.exists()) {
                tempPpm.delete();
            }
        }
    }

    private static BufferedImage loadRawWithImageMagick(File file) throws Exception {
        File tempPng = null;
        try {
            tempPng = File.createTempFile("raw_magick_", ".png");

            String[] commands = {
                    "magick", "convert",
                    file.getAbsolutePath(),
                    "-auto-orient",
                    tempPng.getAbsolutePath()
            };

            try {
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0 && tempPng.exists() && tempPng.length() > 1000) {
                    BufferedImage img = ImageIO.read(tempPng);
                    if (img != null) {
                        System.out.println("ImageMagick loaded: " + img.getWidth() + "x" + img.getHeight());
                        return img;
                    }
                }
            } catch (Exception e) {
                String[] commands2 = {
                        "convert",
                        file.getAbsolutePath(),
                        "-auto-orient",
                        tempPng.getAbsolutePath()
                };

                ProcessBuilder pb = new ProcessBuilder(commands2);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0 && tempPng.exists() && tempPng.length() > 1000) {
                    BufferedImage img = ImageIO.read(tempPng);
                    if (img != null) {
                        System.out.println("convert loaded: " + img.getWidth() + "x" + img.getHeight());
                        return img;
                    }
                }
            }

            throw new Exception("ImageMagick failed");

        } finally {
            if (tempPng != null && tempPng.exists()) {
                tempPng.delete();
            }
        }
    }

    private static BufferedImage extractEmbeddedJpeg(File file) throws Exception {
        System.err.println("WARNING: Using embedded JPEG preview (low resolution)");
        System.err.println("Install dcraw for full resolution: sudo apt-get install dcraw");

        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            if (fileSize > 100_000_000) {
                fileSize = 100_000_000;
            }

            byte[] buffer = new byte[(int) fileSize];
            int read = fis.read(buffer);

            BufferedImage bestImage = null;
            int bestSize = 0;

            for (int i = 0; i < read - 10; i++) {
                if (buffer[i] == (byte) 0xFF && buffer[i + 1] == (byte) 0xD8) {
                    boolean validJpeg = false;
                    if (i + 2 < read && buffer[i + 2] == (byte) 0xFF) {
                        byte marker = buffer[i + 3];
                        if (marker == (byte) 0xE0 || marker == (byte) 0xE1 ||
                                marker == (byte) 0xDB || marker == (byte) 0xC0 ||
                                marker == (byte) 0xC2 || marker == (byte) 0xC4) {
                            validJpeg = true;
                        }
                    }

                    if (validJpeg) {
                        for (int j = i + 10; j < read - 1; j++) {
                            if (buffer[j] == (byte) 0xFF && buffer[j + 1] == (byte) 0xD9) {
                                int jpegSize = j - i + 2;

                                if (jpegSize > 1024) {
                                    byte[] jpegData = new byte[jpegSize];
                                    System.arraycopy(buffer, i, jpegData, 0, jpegSize);

                                    try {
                                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegData));
                                        if (img != null) {
                                            int size = img.getWidth() * img.getHeight();
                                            if (size > bestSize) {
                                                bestSize = size;
                                                bestImage = img;
                                            }
                                        }
                                    } catch (Exception e) {
                                    }
                                }

                                break;
                            }
                        }
                    }
                }
            }

            if (bestImage != null) {
                System.err.println("Extracted JPEG: " + bestImage.getWidth() + "x" + bestImage.getHeight());
                return bestImage;
            }
        }

        throw new Exception("No valid embedded JPEG found");
    }
}