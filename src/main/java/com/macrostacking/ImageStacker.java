package com.macrostacking;

import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageStacker {
    private final StackingAlgorithm algorithm;
    private boolean autoAlign = true;
    private ProgressCallback progressCallback;
    private int finalWidth;
    private int finalHeight;
    private int threadCount;

    @FunctionalInterface
    public interface ProgressCallback {
        void update(int progress, String status);
    }

    public ImageStacker(StackingAlgorithm algorithm) {
        this.algorithm = algorithm;
        this.threadCount = Runtime.getRuntime().availableProcessors();
    }

    public void setAutoAlign(boolean autoAlign) {
        this.autoAlign = autoAlign;
    }

    public void setThreadCount(int count) {
        this.threadCount = Math.max(1, count);
    }

    public BufferedImage stackImages(List<File> files, ProgressCallback callback) throws Exception {
        if (files.isEmpty()) {
            throw new Exception("Aucune image à traiter");
        }

        this.progressCallback = callback;

        callback.update(0, "Chargement de " + files.size() + " images (" + threadCount + " threads)...");
        BufferedImage[] images = loadImagesParallel(files);

        finalWidth = images[0].getWidth();
        finalHeight = images[0].getHeight();
        callback.update(30, "Résolution: " + finalWidth + "x" + finalHeight);

        if (autoAlign && images.length > 1) {
            callback.update(30, "Alignement automatique...");
            images = alignImages(images);
            callback.update(50, "Alignement terminé");
        }

        callback.update(50, "Stacking multi-threadé (" + threadCount + " threads)...");
        BufferedImage result = switch (algorithm) {
            case WEIGHTED_AVERAGE -> stackWeightedAverageParallel(images);
            case DEPTH_MAP -> stackDepthMapParallel(images);
            case PYRAMID -> stackPyramidParallel(images);
            case MAX_CONTRAST -> stackMaxContrastParallel(images);
            case LAPLACIAN -> stackLaplacianParallel(images);
        };

        if (result.getWidth() != finalWidth || result.getHeight() != finalHeight) {
            callback.update(95, "Redimensionnement final...");
            result = resizeToExact(result, finalWidth, finalHeight);
        }

        callback.update(100, "Terminé - " + result.getWidth() + "x" + result.getHeight());
        return result;
    }

    private BufferedImage[] loadImagesParallel(List<File> files) throws Exception {
        BufferedImage[] images = new BufferedImage[files.size()];
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ImageLoadResult>> futures = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            final int index = i;
            final File file = files.get(i);

            futures.add(executor.submit(() -> {
                try {
                    BufferedImage img = ImageLoader.loadImage(file);
                    return new ImageLoadResult(index, img, null);
                } catch (Exception e) {
                    return new ImageLoadResult(index, null, e);
                }
            }));
        }

        for (Future<ImageLoadResult> future : futures) {
            ImageLoadResult result = future.get();
            if (result.error != null) {
                executor.shutdown();
                throw result.error;
            }
            images[result.index] = result.image;
            progressCallback.update((result.index + 1) * 30 / files.size(),
                    "Chargée: " + files.get(result.index).getName());
        }

        executor.shutdown();
        return images;
    }

    private static class ImageLoadResult {
        int index;
        BufferedImage image;
        Exception error;

        ImageLoadResult(int index, BufferedImage image, Exception error) {
            this.index = index;
            this.image = image;
            this.error = error;
        }
    }

    private static class WorkBand {
        final int startY;
        final int endY;

        WorkBand(int startY, int endY) {
            this.startY = startY;
            this.endY = endY;
        }
    }

    private List<WorkBand> createWorkBands(int totalHeight, int numBands) {
        List<WorkBand> bands = new ArrayList<>();
        int linesPerBand = (int) Math.ceil((double) totalHeight / numBands);

        for (int i = 0; i < numBands; i++) {
            int startY = i * linesPerBand;
            int endY = Math.min(startY + linesPerBand, totalHeight);

            if (startY < totalHeight) {
                bands.add(new WorkBand(startY, endY));
                System.out.println("Thread " + i + ": lignes " + startY + " à " + (endY - 1) +
                        " (" + (endY - startY) + " lignes)");
            }
        }

        return bands;
    }

    private BufferedImage resizeToExact(BufferedImage img, int width, int height) {
        if (img.getWidth() == width && img.getHeight() == height) {
            return img;
        }

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(img, 0, 0, width, height, null);
        g2d.dispose();
        return result;
    }

    private BufferedImage[] alignImages(BufferedImage[] images) {
        BufferedImage reference = images[0];
        BufferedImage[] aligned = new BufferedImage[images.length];
        aligned[0] = reference;

        Point[] referencePoints = detectKeyPoints(reference);

        for (int i = 1; i < images.length; i++) {
            progressCallback.update(30 + (i * 20 / images.length), "Alignement " + (i+1) + "/" + images.length);
            Point[] currentPoints = detectKeyPoints(images[i]);
            Point offset = calculateBestOffset(reference, images[i], referencePoints, currentPoints);
            aligned[i] = translateImage(images[i], offset.x, offset.y, finalWidth, finalHeight);
        }

        return aligned;
    }

    private Point[] detectKeyPoints(BufferedImage img) {
        List<Point> points = new ArrayList<>();
        int width = img.getWidth();
        int height = img.getHeight();
        int step = Math.max(20, Math.min(width, height) / 40);

        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                double contrast = calculateLocalContrast(img, x, y, 10);
                if (contrast > 30) {
                    points.add(new Point(x, y));
                }
            }
        }

        return points.toArray(new Point[0]);
    }

    private Point calculateBestOffset(BufferedImage ref, BufferedImage img, Point[] refPoints, Point[] imgPoints) {
        int maxSearchRadius = Math.min(100, Math.min(ref.getWidth(), ref.getHeight()) / 10);
        double bestScore = Double.MAX_VALUE;
        Point bestOffset = new Point(0, 0);

        for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy += 5) {
            for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx += 5) {
                double score = calculateAlignmentScore(ref, img, dx, dy);
                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = new Point(dx, dy);
                }
            }
        }

        for (int dy = bestOffset.y - 4; dy <= bestOffset.y + 4; dy++) {
            for (int dx = bestOffset.x - 4; dx <= bestOffset.x + 4; dx++) {
                double score = calculateAlignmentScore(ref, img, dx, dy);
                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = new Point(dx, dy);
                }
            }
        }

        return bestOffset;
    }

    private double calculateAlignmentScore(BufferedImage ref, BufferedImage img, int offsetX, int offsetY) {
        int width = ref.getWidth();
        int height = ref.getHeight();
        double totalDiff = 0;
        int samples = 0;
        int step = Math.max(10, Math.min(width, height) / 80);

        for (int y = Math.max(0, -offsetY); y < Math.min(height, height - offsetY); y += step) {
            for (int x = Math.max(0, -offsetX); x < Math.min(width, width - offsetX); x += step) {
                int x2 = x + offsetX;
                int y2 = y + offsetY;

                if (x2 >= 0 && x2 < width && y2 >= 0 && y2 < height) {
                    int gray1 = getGray(ref, x, y);
                    int gray2 = getGray(img, x2, y2);
                    totalDiff += Math.abs(gray1 - gray2);
                    samples++;
                }
            }
        }

        return samples > 0 ? totalDiff / samples : Double.MAX_VALUE;
    }

    private BufferedImage translateImage(BufferedImage src, int dx, int dy, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        g2d.drawImage(src, dx, dy, null);
        g2d.dispose();
        return result;
    }

    private BufferedImage stackWeightedAverageParallel(BufferedImage[] images) throws Exception {
        BufferedImage result = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);

        List<WorkBand> bands = createWorkBands(finalHeight, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        AtomicInteger processedLines = new AtomicInteger(0);

        for (WorkBand band : bands) {
            futures.add(executor.submit(() -> {
                for (int y = band.startY; y < band.endY; y++) {
                    for (int x = 0; x < finalWidth; x++) {
                        double totalR = 0, totalG = 0, totalB = 0;
                        double totalWeight = 0;

                        for (BufferedImage img : images) {
                            int rgb = img.getRGB(x, y);
                            if (rgb == 0xFF000000 || rgb == 0) continue;

                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;

                            double contrast = calculateLocalContrast(img, x, y, 5);
                            double weight = Math.pow(contrast + 1, 2);

                            totalR += r * weight;
                            totalG += g * weight;
                            totalB += b * weight;
                            totalWeight += weight;
                        }

                        if (totalWeight > 0) {
                            int avgR = Math.min(255, (int) (totalR / totalWeight));
                            int avgG = Math.min(255, (int) (totalG / totalWeight));
                            int avgB = Math.min(255, (int) (totalB / totalWeight));
                            result.setRGB(x, y, (avgR << 16) | (avgG << 8) | avgB);
                        }
                    }

                    int completed = processedLines.incrementAndGet();
                    if (completed % 100 == 0) {
                        int progress = 50 + (completed * 50 / finalHeight);
                        progressCallback.update(progress, "Stacking: " + (completed * 100 / finalHeight) + "%");
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        return result;
    }

    private BufferedImage stackDepthMapParallel(BufferedImage[] images) throws Exception {
        BufferedImage result = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);

        progressCallback.update(50, "Calcul carte de profondeur optimisée...");
        int[][] bestImage = new int[finalHeight][finalWidth];
        double[][] sharpnessValues = new double[finalHeight][finalWidth];

        List<WorkBand> bands = createWorkBands(finalHeight, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        AtomicInteger processedLines = new AtomicInteger(0);

        for (WorkBand band : bands) {
            futures.add(executor.submit(() -> {
                try {
                    for (int y = band.startY; y < band.endY; y++) {
                        for (int x = 0; x < finalWidth; x++) {
                            double maxSharpness = -1;
                            int bestIdx = 0;

                            for (int i = 0; i < images.length; i++) {
                                int rgb = images[i].getRGB(x, y);
                                if (rgb == 0xFF000000 || rgb == 0) continue;

                                double contrast3 = calculateLocalContrast(images[i], x, y, 3);
                                double contrast7 = calculateLocalContrast(images[i], x, y, 7);
                                double laplacian = Math.abs(calculateLaplacian(images[i], x, y));

                                double sharpness = contrast3 * 0.5 + contrast7 * 0.3 + laplacian * 2.0;

                                if (sharpness > maxSharpness) {
                                    maxSharpness = sharpness;
                                    bestIdx = i;
                                }
                            }

                            bestImage[y][x] = bestIdx;
                            sharpnessValues[y][x] = maxSharpness;
                        }

                        int completed = processedLines.incrementAndGet();
                        if (completed % 50 == 0) {
                            int progress = 50 + (completed * 25 / finalHeight);
                            progressCallback.update(progress, "Profondeur: " + (completed * 100 / finalHeight) + "%");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        progressCallback.update(75, "Lissage médian...");
        int[][] smoothed = medianFilterDepthMapSimple(bestImage, 5);

        progressCallback.update(85, "Assemblage final...");
        futures.clear();
        processedLines.set(0);

        for (WorkBand band : bands) {
            futures.add(executor.submit(() -> {
                try {
                    for (int y = band.startY; y < band.endY; y++) {
                        for (int x = 0; x < finalWidth; x++) {
                            int selectedImage = smoothed[y][x];

                            if (isEdgePixel(smoothed, x, y)) {
                                result.setRGB(x, y, blendEdgePixelSimple(images, smoothed, x, y));
                            } else {
                                int rgb = images[selectedImage].getRGB(x, y);
                                if (rgb != 0xFF000000 && rgb != 0) {
                                    result.setRGB(x, y, rgb);
                                }
                            }
                        }

                        int completed = processedLines.incrementAndGet();
                        if (completed % 50 == 0) {
                            int progress = 85 + (completed * 15 / finalHeight);
                            progressCallback.update(progress, "Assemblage: " + (completed * 100 / finalHeight) + "%");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        return result;
    }

    private int[][] medianFilterDepthMapSimple(int[][] depthMap, int radius) {
        int height = depthMap.length;
        int width = depthMap[0].length;
        int[][] result = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                List<Integer> values = new ArrayList<>();

                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int ny = Math.max(0, Math.min(height - 1, y + dy));
                        int nx = Math.max(0, Math.min(width - 1, x + dx));
                        values.add(depthMap[ny][nx]);
                    }
                }

                values.sort(Integer::compareTo);
                result[y][x] = values.get(values.size() / 2);
            }

            if (y % 100 == 0) {
                progressCallback.update(75 + (y * 10 / height), "Lissage: " + (y * 100 / height) + "%");
            }
        }

        return result;
    }

    private boolean isEdgePixel(int[][] depthMap, int x, int y) {
        int height = depthMap.length;
        int width = depthMap[0].length;

        if (x == 0 || y == 0 || x >= width - 1 || y >= height - 1) {
            return false;
        }

        int center = depthMap[y][x];

        return depthMap[y-1][x] != center ||
                depthMap[y+1][x] != center ||
                depthMap[y][x-1] != center ||
                depthMap[y][x+1] != center;
    }

    private int blendEdgePixelSimple(BufferedImage[] images, int[][] depthMap, int x, int y) {
        int height = depthMap.length;
        int width = depthMap[0].length;

        int[] counts = new int[images.length];

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = Math.max(0, Math.min(height - 1, y + dy));
                int nx = Math.max(0, Math.min(width - 1, x + dx));
                counts[depthMap[ny][nx]]++;
            }
        }

        int first = 0, second = 0;
        int firstCount = 0, secondCount = 0;

        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > firstCount) {
                second = first;
                secondCount = firstCount;
                first = i;
                firstCount = counts[i];
            } else if (counts[i] > secondCount) {
                second = i;
                secondCount = counts[i];
            }
        }

        int rgb1 = images[first].getRGB(x, y);
        int rgb2 = images[second].getRGB(x, y);

        if (rgb1 == 0xFF000000 || rgb1 == 0) return rgb2;
        if (rgb2 == 0xFF000000 || rgb2 == 0) return rgb1;

        int r = ((rgb1 >> 16) & 0xFF + (rgb2 >> 16) & 0xFF) / 2;
        int g = ((rgb1 >> 8) & 0xFF + (rgb2 >> 8) & 0xFF) / 2;
        int b = ((rgb1 & 0xFF) + (rgb2 & 0xFF)) / 2;

        return (r << 16) | (g << 8) | b;
    }

    private BufferedImage stackPyramidParallel(BufferedImage[] images) throws Exception {
        progressCallback.update(50, "Pyramide parallèle...");
        return stackMaxContrastParallel(images);
    }

    private BufferedImage stackMaxContrastParallel(BufferedImage[] images) throws Exception {
        BufferedImage result = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);

        List<WorkBand> bands = createWorkBands(finalHeight, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        AtomicInteger processedLines = new AtomicInteger(0);

        for (WorkBand band : bands) {
            futures.add(executor.submit(() -> {
                for (int y = band.startY; y < band.endY; y++) {
                    for (int x = 0; x < finalWidth; x++) {
                        double maxContrast = -1;
                        int bestRgb = images[0].getRGB(x, y);

                        for (BufferedImage img : images) {
                            int rgb = img.getRGB(x, y);
                            if (rgb == 0xFF000000 || rgb == 0) continue;

                            double contrast = calculateLocalContrast(img, x, y, 5);
                            if (contrast > maxContrast) {
                                maxContrast = contrast;
                                bestRgb = rgb;
                            }
                        }

                        if (bestRgb != 0xFF000000 && bestRgb != 0) {
                            result.setRGB(x, y, bestRgb);
                        }
                    }

                    int completed = processedLines.incrementAndGet();
                    if (completed % 100 == 0) {
                        progressCallback.update(50 + (completed * 50 / finalHeight),
                                "Contraste: " + (completed * 100 / finalHeight) + "%");
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        return result;
    }

    private BufferedImage stackLaplacianParallel(BufferedImage[] images) throws Exception {
        BufferedImage result = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);

        List<WorkBand> bands = createWorkBands(finalHeight, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        AtomicInteger processedLines = new AtomicInteger(0);

        for (WorkBand band : bands) {
            futures.add(executor.submit(() -> {
                for (int y = band.startY; y < band.endY; y++) {
                    for (int x = 0; x < finalWidth; x++) {
                        double maxLaplacian = -1;
                        int bestRgb = images[0].getRGB(x, y);

                        for (BufferedImage img : images) {
                            int rgb = img.getRGB(x, y);
                            if (rgb == 0xFF000000 || rgb == 0) continue;

                            double laplacian = Math.abs(calculateLaplacian(img, x, y));
                            if (laplacian > maxLaplacian) {
                                maxLaplacian = laplacian;
                                bestRgb = rgb;
                            }
                        }

                        if (bestRgb != 0xFF000000 && bestRgb != 0) {
                            result.setRGB(x, y, bestRgb);
                        }
                    }

                    int completed = processedLines.incrementAndGet();
                    if (completed % 100 == 0) {
                        progressCallback.update(50 + (completed * 50 / finalHeight),
                                "Laplacien: " + (completed * 100 / finalHeight) + "%");
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();
        return result;
    }

    private double calculateLocalContrast(BufferedImage img, int x, int y, int radius) {
        int width = img.getWidth();
        int height = img.getHeight();

        int minGray = 255;
        int maxGray = 0;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = Math.max(0, Math.min(width - 1, x + dx));
                int ny = Math.max(0, Math.min(height - 1, y + dy));

                int rgb = img.getRGB(nx, ny);
                if (rgb == 0xFF000000 || rgb == 0) continue;

                int gray = ((rgb >> 16) & 0xFF + (rgb >> 8) & 0xFF + (rgb & 0xFF)) / 3;

                minGray = Math.min(minGray, gray);
                maxGray = Math.max(maxGray, gray);
            }
        }

        return maxGray - minGray;
    }

    private double calculateSharpness(BufferedImage img, int x, int y, int radius) {
        return calculateLocalContrast(img, x, y, radius);
    }

    private double calculateLaplacian(BufferedImage img, int x, int y) {
        int width = img.getWidth();
        int height = img.getHeight();

        if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
            return 0;
        }

        int center = getGray(img, x, y);
        int top = getGray(img, x, y - 1);
        int bottom = getGray(img, x, y + 1);
        int left = getGray(img, x - 1, y);
        int right = getGray(img, x + 1, y);

        return 4 * center - top - bottom - left - right;
    }

    private int getGray(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        if (rgb == 0xFF000000 || rgb == 0) return 0;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r + g + b) / 3;
    }
}