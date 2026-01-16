package com.macrostacking;

public enum StackingAlgorithm {
    WEIGHTED_AVERAGE("Moyenne pondérée (Method A)"),
    DEPTH_MAP("Carte de profondeur (Method B)"),
    PYRAMID("Pyramide (Method C)"),
    MAX_CONTRAST("Contraste maximal"),
    LAPLACIAN("Laplacien");
    
    private final String displayName;
    
    StackingAlgorithm(String displayName) {
        this.displayName = displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
