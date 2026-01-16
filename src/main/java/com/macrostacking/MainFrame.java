// src/main/java/com/macrostacking/MainFrame.java - Ajout contrôle threads
package com.macrostacking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class MainFrame extends JFrame {
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private final DefaultListModel<File> imageListModel = new DefaultListModel<>();
    private final JList<File> imageList = new JList<>(imageListModel);
    private final JLabel previewLabel = new JLabel("", SwingConstants.CENTER);
    private final JComboBox<StackingAlgorithm> algorithmCombo = new JComboBox<>(StackingAlgorithm.values());
    private final JComboBox<OutputFormat> formatCombo = new JComboBox<>(OutputFormat.values());
    private final JCheckBox autoAlignCheck = new JCheckBox("Alignement automatique", true);
    private final JSpinner threadSpinner;
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton stackButton = new JButton("Stacker les images");
    private final JLabel statusLabel = new JLabel("Prêt");

    public MainFrame() {
        setTitle("Macro Focus Stacker - Multi-threadé");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        int cpuCount = Runtime.getRuntime().availableProcessors();
        threadSpinner = new JSpinner(new SpinnerNumberModel(cpuCount, 1, cpuCount * 2, 1));

        initComponents();
        setupDragAndDrop();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(300, 0));

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        JButton addFilesBtn = new JButton("Ajouter des images");
        JButton addFolderBtn = new JButton("Ajouter un dossier");
        JButton clearBtn = new JButton("Effacer la liste");

        addFilesBtn.addActionListener(e -> addFiles());
        addFolderBtn.addActionListener(e -> addFolder());
        clearBtn.addActionListener(e -> imageListModel.clear());

        buttonPanel.add(addFilesBtn);
        buttonPanel.add(addFolderBtn);
        buttonPanel.add(clearBtn);

        imageList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        imageList.setCellRenderer(new FileListCellRenderer());
        imageList.addListSelectionListener(e -> updatePreview());

        JScrollPane scrollPane = new JScrollPane(imageList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Images (glisser-déposer)"));

        leftPanel.add(buttonPanel, BorderLayout.NORTH);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        previewLabel.setPreferredSize(new Dimension(600, 600));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        centerPanel.add(new JScrollPane(previewLabel), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(250, 0));
        rightPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        rightPanel.add(new JLabel("Algorithme de stacking:"));
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(algorithmCombo);
        rightPanel.add(Box.createVerticalStrut(20));

        rightPanel.add(new JLabel("Format de sortie:"));
        rightPanel.add(Box.createVerticalStrut(5));
        rightPanel.add(formatCombo);
        rightPanel.add(Box.createVerticalStrut(20));

        rightPanel.add(autoAlignCheck);
        rightPanel.add(Box.createVerticalStrut(10));

        JPanel threadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        threadPanel.add(new JLabel("Threads CPU:"));
        threadPanel.add(threadSpinner);
        rightPanel.add(threadPanel);
        rightPanel.add(Box.createVerticalStrut(20));

        stackButton.addActionListener(e -> startStacking());
        rightPanel.add(stackButton);
        rightPanel.add(Box.createVerticalStrut(20));

        rightPanel.add(progressBar);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(statusLabel);
        rightPanel.add(Box.createVerticalGlue());

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        add(mainPanel);
    }

    private void setupDragAndDrop() {
        new DropTarget(imageList, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}

            @Override
            public void dragExit(DropTargetEvent dte) {}

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);

                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    for (File file : droppedFiles) {
                        if (file.isDirectory()) {
                            addFilesFromDirectory(file);
                        } else if (isImageFile(file)) {
                            if (!imageListModel.contains(file)) {
                                imageListModel.addElement(file);
                            }
                        }
                    }

                    dtde.dropComplete(true);
                    statusLabel.setText(imageListModel.size() + " image(s) chargée(s)");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        });
    }

    private void addFilesFromDirectory(File dir) {
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".arw") || lower.endsWith(".cr2") ||
                    lower.endsWith(".cr3") || lower.endsWith(".nef") ||
                    lower.endsWith(".raw") || lower.endsWith(".dng") ||
                    lower.endsWith(".orf") || lower.endsWith(".raf") ||
                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".tif") ||
                    lower.endsWith(".tiff");
        });

        if (files != null) {
            for (File file : files) {
                if (!imageListModel.contains(file)) {
                    imageListModel.addElement(file);
                }
            }
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".arw") || name.endsWith(".cr2") ||
                name.endsWith(".cr3") || name.endsWith(".nef") ||
                name.endsWith(".raw") || name.endsWith(".dng") ||
                name.endsWith(".orf") || name.endsWith(".raf") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".tif") ||
                name.endsWith(".tiff");
    }

    private void addFiles() {
        String lastDir = prefs.get("lastDirectory", System.getProperty("user.home"));
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new RawImageFileFilter());

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            for (File file : files) {
                if (!imageListModel.contains(file)) {
                    imageListModel.addElement(file);
                }
            }
            prefs.put("lastDirectory", chooser.getSelectedFile().getParent());
            statusLabel.setText(imageListModel.size() + " image(s) chargée(s)");
        }
    }

    private void addFolder() {
        String lastDir = prefs.get("lastDirectory", System.getProperty("user.home"));
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            addFilesFromDirectory(folder);
            prefs.put("lastDirectory", folder.getAbsolutePath());
            statusLabel.setText(imageListModel.size() + " image(s) chargée(s)");
        }
    }

    private void updatePreview() {
        if (imageList.getSelectedValue() != null) {
            File file = imageList.getSelectedValue();
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() {
                    try {
                        BufferedImage img = ImageLoader.loadImage(file);
                        if (img != null) {
                            BufferedImage scaled = scaleImage(img, 600, 600);
                            return new ImageIcon(scaled);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            previewLabel.setIcon(icon);
                            previewLabel.setText("");
                        }
                    } catch (Exception ex) {
                        previewLabel.setText("Erreur de chargement");
                    }
                }
            };
            worker.execute();
        }
    }

    private BufferedImage scaleImage(BufferedImage img, int maxWidth, int maxHeight) {
        int width = img.getWidth();
        int height = img.getHeight();

        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(img, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }

    private void startStacking() {
        if (imageListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Veuillez ajouter des images d'abord",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<File> files = new ArrayList<>();
        for (int i = 0; i < imageListModel.size(); i++) {
            files.add(imageListModel.getElementAt(i));
        }

        StackingAlgorithm algorithm = (StackingAlgorithm) algorithmCombo.getSelectedItem();
        OutputFormat format = (OutputFormat) formatCombo.getSelectedItem();
        boolean autoAlign = autoAlignCheck.isSelected();
        int threadCount = (Integer) threadSpinner.getValue();

        String lastDir = prefs.get("lastDirectory", System.getProperty("user.home"));
        JFileChooser chooser = new JFileChooser(lastDir);
        chooser.setDialogTitle("Enregistrer le résultat");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputFile = chooser.getSelectedFile();

            stackButton.setEnabled(false);
            progressBar.setIndeterminate(true);
            long startTime = System.currentTimeMillis();

            SwingWorker<Void, String> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    try {
                        ImageStacker stacker = new ImageStacker(algorithm);
                        stacker.setAutoAlign(autoAlign);
                        stacker.setThreadCount(threadCount);

                        BufferedImage result = stacker.stackImages(files, (progress, status) -> {
                            publish(status);
                            setProgress(progress);
                        });

                        publish("Sauvegarde (" + result.getWidth() + "x" + result.getHeight() + ")...");
                        ImageSaver.saveImage(result, outputFile, format);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(MainFrame.this,
                                        "Erreur: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE));
                    }
                    return null;
                }

                @Override
                protected void process(List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        statusLabel.setText(chunks.get(chunks.size() - 1));
                    }
                }

                @Override
                protected void done() {
                    stackButton.setEnabled(true);
                    progressBar.setValue(100);
                    progressBar.setIndeterminate(false);

                    long elapsed = System.currentTimeMillis() - startTime;
                    double seconds = elapsed / 1000.0;

                    statusLabel.setText("Terminé en " + String.format("%.1f", seconds) + "s!");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Stacking terminé avec succès en " + String.format("%.1f", seconds) + " secondes!",
                            "Succès", JOptionPane.INFORMATION_MESSAGE);

                    new Timer(3000, e -> {
                        progressBar.setValue(0);
                        statusLabel.setText("Prêt");
                        ((Timer)e.getSource()).stop();
                    }).start();
                }
            };

            worker.addPropertyChangeListener(evt -> {
                if ("progress".equals(evt.getPropertyName())) {
                    int progress = (Integer) evt.getNewValue();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(progress);
                }
            });

            worker.execute();
        }
    }
}