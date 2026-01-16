# Macro Focus Stacker

Logiciel de stacking macro professionnel en Java 21 avec support des formats RAW des grandes marques.

## Fonctionnalités

### Formats supportés en lecture
- **Sony**: ARW, SRF, SR2
- **Canon**: CR2, CR3, CRW
- **Nikon**: NEF, NRW
- **Fuji**: RAF
- **Olympus**: ORF
- **Panasonic**: RW2
- **Pentax**: PEF
- **Samsung**: SRW
- **Adobe**: DNG
- **Standards**: JPG, PNG, TIFF

### Formats de sortie
- FITS (pour l'astronomie)
- PNG (sans perte)
- JPEG (compression)
- TIFF (haute qualité)
- CR2 (Canon RAW - pseudo-format)

### Algorithmes de stacking
1. **Moyenne pondérée (Method A)** - Similaire à Helicon Focus Method A
   - Pondération basée sur le contraste local
   - Résultat lissé et naturel

2. **Carte de profondeur (Method B)** - Similaire à Helicon Focus Method B
   - Calcul de carte de netteté
   - Lissage spatial pour éviter les artefacts
   - Meilleur pour les sujets complexes

3. **Pyramide (Method C)** - Similaire à Helicon Focus Method C
   - Analyse multi-échelle
   - Fusion pyramidale
   - Excellent pour les détails fins

4. **Contraste maximal**
   - Sélection du pixel le plus contrasté
   - Rapide et efficace

5. **Laplacien**
   - Détection de netteté par filtre Laplacien
   - Précis pour les bords nets

## Installation

### Prérequis
- Java 21 ou supérieur
- Maven 3.6+
- dcraw (recommandé pour le support RAW optimal)
- ImageMagick (fallback)

### Installation de dcraw (Ubuntu/Debian)
```bash
sudo apt-get update
sudo apt-get install dcraw
```

### Installation de ImageMagick (Ubuntu/Debian)
```bash
sudo apt-get install imagemagick
```

### Compilation
```bash
mvn clean package
```

### Exécution
```bash
java -jar target/macro-stacker-1.0.0.jar
```

## Utilisation

1. **Ajouter des images**
   - Bouton "Ajouter des images" : sélection manuelle
   - Bouton "Ajouter un dossier" : chargement d'un dossier complet
   - Sélection multiple supportée

2. **Prévisualisation**
   - Cliquez sur une image dans la liste pour la prévisualiser
   - La dernière position de dossier est mémorisée

3. **Configuration**
   - Choisissez l'algorithme de stacking
   - Sélectionnez le format de sortie

4. **Stacking**
   - Cliquez sur "Stacker les images"
   - Choisissez l'emplacement de sauvegarde
   - La barre de progression affiche l'avancement

## Architecture technique

### Chargement des images RAW
Le logiciel utilise 3 méthodes de fallback :
1. **dcraw** - Décodage natif RAW (recommandé)
2. **ImageMagick** - Conversion via convert
3. **Extraction JPEG** - JPEG embarqué dans le RAW

### Algorithmes de stacking

#### Weighted Average (Moyenne pondérée)
```
Pour chaque pixel (x,y):
  - Calculer le contraste local de chaque image
  - Pondération = (contraste + 1)²
  - Pixel final = moyenne pondérée
```

#### Depth Map (Carte de profondeur)
```
1. Calculer la netteté locale pour chaque image
2. Créer une carte de profondeur (meilleure netteté)
3. Lisser la carte (filtre médian)
4. Assembler selon la carte lissée
```

#### Pyramid (Pyramide)
```
1. Construire pyramide gaussienne (6 niveaux)
2. Pour chaque niveau, sélectionner le meilleur contraste
3. Reconstruire l'image finale de bas en haut
```

## Structure du projet

```
macro-stacker/
├── pom.xml
├── README.md
└── src/main/java/com/macrostacking/
    ├── MacroStackerApp.java          # Point d'entrée
    ├── MainFrame.java                # Interface principale
    ├── ImageLoader.java              # Chargement RAW/standards
    ├── ImageStacker.java             # Algorithmes de stacking
    ├── ImageSaver.java               # Sauvegarde multi-format
    ├── StackingAlgorithm.java        # Enum des algorithmes
    ├── OutputFormat.java             # Enum des formats
    ├── RawImageFileFilter.java       # Filtre de fichiers
    └── FileListCellRenderer.java     # Rendu liste
```

## Dépendances Maven

- **FlatLaf 3.4.1** - Look & Feel moderne
- **Apache Commons Imaging 1.0-alpha5** - Manipulation images
- **TwelveMonkeys ImageIO 3.10.1** - Support formats étendus
- **nom-tam-fits 1.20.1** - Support FITS
- **Gson 2.10.1** - Sérialisation JSON

## Performance

- Traitement multi-thread possible
- Gestion mémoire optimisée
- Progression temps réel
- Support images haute résolution

## Limitations

- Toutes les images doivent avoir les mêmes dimensions
- Le format CR2 en sortie est un pseudo-format (TIFF)
- Les images RAW nécessitent dcraw ou ImageMagick

## Améliorations futures

- [ ] Alignement automatique des images
- [ ] Support GPU pour accélération
- [ ] Ajustement manuel de la zone de netteté
- [ ] Export 16 bits
- [ ] Correction aberrations chromatiques
- [ ] Batch processing
- [ ] Prévisualisation 3D de la profondeur

## Auteur

Développé pour le stacking macro professionnel avec support complet des formats RAW.

## Licence

Projet open source - Utilisation libre

## Support

Pour toute question ou problème :
- Vérifier que dcraw est installé
- Vérifier les permissions fichiers
- Consulter les logs de la console
