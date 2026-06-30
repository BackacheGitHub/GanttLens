package com.ganttlens.gui;

import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.ProjectStats;
import com.ganttlens.parser.GanttFileParser;
import com.ganttlens.analyzer.AnalysisEngine;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Main controller for the GanttLens GUI.
 * Handles file loading and coordinates analysis.
 */
public class MainController {

    @FXML
    private BorderPane rootPane;

    @FXML
    private TextArea codeArea;

    @FXML
    private TreeView<String> fileTreeView;

    @FXML
    private TabPane analysisTabPane;

    @FXML
    private Label statusLabel;

    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine engine = new AnalysisEngine();
    private GanttSchedule currentSchedule;
    private ProjectStats currentStats;

    @FXML
    public void initialize() {
        // Initialize with sample content
        codeArea.setText("@startgantt\nproject starts 2026-07-01\n[Task1] lasts 5 days\n@endgantt");
    }

    @FXML
    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PlantUML Gantt File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PlantUML Files", "*.puml", "*.plantuml")
        );

        File file = fileChooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                codeArea.setText(content);
                parseAndAnalyze();
                statusLabel.setText("Loaded: " + file.getName());
            } catch (IOException e) {
                showError("Failed to read file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void parseAndAnalyze() {
        try {
            String content = codeArea.getText();
            currentSchedule = parser.parse(content);
            currentStats = engine.analyze(currentSchedule);

            statusLabel.setText(String.format("Parsed: %d tasks, Total man-days: %.1f",
                currentSchedule.tasks().size(), currentStats.totalManDays()));

            updateAnalysisDisplay();
        } catch (Exception e) {
            showError("Parse error: " + e.getMessage());
        }
    }

    private void updateAnalysisDisplay() {
        if (currentStats == null) return;

        // Update stats tab
        // TODO: Implement visualization tabs

        // For now, just show summary in status
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tasks: %d | Man-days: %.1f | Overloads: %d",
            currentSchedule.tasks().size(),
            currentStats.totalManDays(),
            currentStats.overloads().size()));

        if (currentStats.criticalPath() != null) {
            sb.append(String.format(" | Critical Path: %d days",
                currentStats.criticalPath().totalDurationDays()));
        }

        statusLabel.setText(sb.toString());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
