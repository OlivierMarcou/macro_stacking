package com.macrostacking;

import nom.tam.fits.*;
import nom.tam.util.BufferedFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageSaver {
    
    public static void saveImage(BufferedImage image, File file, OutputFormat format) throws Exception {
        String filename = file.getAbsolutePath();
        if (!filename.toLowerCase().endsWith(format.getExtension())) {
            filename += format.getExtension();
            file = new File(filename);
        }
        
        switch (format) {
            case FITS -> saveFits(image, file);
            case PNG -> ImageIO.write(image, "PNG", file);
            case JPEG -> ImageIO.write(image, "JPEG", file);
            case TIFF -> ImageIO.write(image, "TIFF", file);
            case CR2 -> saveCR2(image, file);
        }
    }
    
    private static void saveFits(BufferedImage image, File file) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Extract RGB channels
        float[][] rChannel = new float[height][width];
        float[][] gChannel = new float[height][width];
        float[][] bChannel = new float[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                rChannel[y][x] = ((rgb >> 16) & 0xFF) / 255.0f;
                gChannel[y][x] = ((rgb >> 8) & 0xFF) / 255.0f;
                bChannel[y][x] = (rgb & 0xFF) / 255.0f;
            }
        }
        
        Fits fits = new Fits();
        
        ImageHDU hduR = (ImageHDU) Fits.makeHDU(rChannel);
        hduR.addValue("CHANNEL", "RED", "Color channel");
        fits.addHDU(hduR);
        
        ImageHDU hduG = (ImageHDU) Fits.makeHDU(gChannel);
        hduG.addValue("CHANNEL", "GREEN", "Color channel");
        fits.addHDU(hduG);
        
        ImageHDU hduB = (ImageHDU) Fits.makeHDU(bChannel);
        hduB.addValue("CHANNEL", "BLUE", "Color channel");
        fits.addHDU(hduB);
        
        BufferedFile bf = new BufferedFile(file, "rw");
        fits.write(bf);
        bf.close();
    }
    
    private static void saveCR2(BufferedImage image, File file) throws Exception {
        // Save as TIFF with appropriate metadata for pseudo-RAW
        // True CR2 writing requires complex Canon-specific encoding
        File tempTiff = new File(file.getAbsolutePath().replace(".cr2", ".tif"));
        ImageIO.write(image, "TIFF", tempTiff);
        
        // Note: Full CR2 support would require libtiff or ExifTool
        System.out.println("Note: Sauvegardé en TIFF. CR2 natif nécessite des outils externes.");
    }
}
