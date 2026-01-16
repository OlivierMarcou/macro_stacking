package com.macrostacking;

public enum OutputFormat {
    FITS("FITS", ".fits"),
    PNG("PNG", ".png"),
    JPEG("JPEG", ".jpg"),
    TIFF("TIFF", ".tif"),
    CR2("Canon RAW (CR2)", ".cr2");
    
    private final String displayName;
    private final String extension;
    
    OutputFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }
    
    public String getExtension() {
        return extension;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
