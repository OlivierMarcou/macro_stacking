package com.macrostacking;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class RawImageFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) return true;
        
        String name = f.getName().toLowerCase();
        return name.endsWith(".arw") || name.endsWith(".cr2") || 
               name.endsWith(".cr3") || name.endsWith(".nef") || 
               name.endsWith(".raw") || name.endsWith(".dng") ||
               name.endsWith(".orf") || name.endsWith(".raf") ||
               name.endsWith(".rw2") || name.endsWith(".pef") ||
               name.endsWith(".srw") || name.endsWith(".sr2") ||
               name.endsWith(".srf") || name.endsWith(".jpg") ||
               name.endsWith(".jpeg") || name.endsWith(".png") ||
               name.endsWith(".tif") || name.endsWith(".tiff");
    }

    @Override
    public String getDescription() {
        return "Images RAW et standards (ARW, CR2, CR3, NEF, RAW, DNG, ORF, RAF, JPG, PNG, TIF)";
    }
}
