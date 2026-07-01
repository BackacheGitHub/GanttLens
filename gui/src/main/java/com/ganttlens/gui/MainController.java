package com.ganttlens.gui;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.analyzer.GanttLayoutEngine;
import com.ganttlens.model.*;
import com.ganttlens.parser.GanttFileParser;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main controller for the GanttLens GUI.
 * Handles file loading, parsing, layout computation, canvas rendering, and analysis display.
 */
public class MainController {

    // ========== FXML bindings ==========
    @FXML private BorderPane rootPane;
    @FXML private TextArea codeArea;
    @FXML private TreeView<String> fileTreeView;
    @FXML private TabPane analysisTabPane;

    // Gantt Chart tab components
    @FXML private SplitPane ganttSplitPane;
    @FXML private ListView<String> taskListView;
    @FXML private Canvas ganttCanvas;
    @FXML private ScrollPane ganttScrollPane;

    // Property panel
    @FXML private VBox propertyPanel;
    @FXML private TextField propNameField;
    @FXML private DatePicker propStartDate;
    @FXML private DatePicker propEndDate;
    @FXML private ComboBox<String> propAssigneeCombo;
    @FXML private Slider propProgressSlider;
    @FXML private Label propProgressLabel;

    // Statistics tab
    @FXML private Label statTaskCount;
    @FXML private Label statManDays;
    @FXML private Label statDuration;
    @FXML private Label statCriticalPath;
    @FXML private VBox overloadBox;
    @FXML private VBox suggestionBox;
    @FXML private VBox resourceBox;

    // Status bar
    @FXML private Label statusLabel;

    // Menu items with shortcuts
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;
    @FXML private MenuItem parseMenuItem;

    // ========== Core components ==========
    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine engine = new AnalysisEngine();
    private final GanttLayoutEngine layoutEngine = new GanttLayoutEngine();
    private final TaskSelectionModel selectionModel = new TaskSelectionModel();
    private GanttInteractionHandler interactionHandler;
    private Tooltip hoverTooltip = new Tooltip();

    private GanttSchedule currentSchedule;
    private ProjectStats currentStats;
    private List<TaskLayout> currentLayouts;
    private double pixelsPerDay = LayoutConfig.DEFAULT_PIXELS_PER_DAY;
    private File currentFile;
    private boolean hasUnsavedChanges = false;
    private final ArrayList<Task> mutableTasks = new ArrayList<>();
    private final CommandStack commandStack = new CommandStack();
    private boolean updatingPropertyPanel = false; // guard against recursive listener fires

    // ========== Initialization ==========

    @FXML
    public void initialize() {
        // Initialize interaction handler
        interactionHandler = new GanttInteractionHandler(layoutEngine, selectionModel);

        // Set up selection change callback
        selectionModel.setOnSelectionChanged(this::onSelectionChanged);

        // Set up canvas mouse handlers
        ganttCanvas.setOnMouseClicked(this::onCanvasMouseClicked);
        ganttCanvas.setOnMouseMoved(this::onCanvasMouseMoved);
        ganttCanvas.setOnMouseExited(e -> {
            interactionHandler.handleHover(-1, -1);
            hoverTooltip.setText("");
        });

        // Set up task list selection sync
        taskListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null && currentSchedule != null && !updatingPropertyPanel) {
                    String taskName = newVal;
                    mutableTasks.stream()
                        .filter(t -> t.name().equals(taskName))
                        .findFirst()
                        .ifPresent(t -> selectionModel.selectTask(t.id()));
                }
            }
        );

        // Set up property panel change listeners
        setupPropertyPanelListeners();

        // Set up command stack state listener for undo/redo menu updates
        commandStack.setOnStateChanged(stack -> {
            // Could update menu item disable state here if needed
        });

        // Set keyboard shortcuts
        saveMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        parseMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN));

        // Set default content
        codeArea.setText("""
            @startgantt
            project starts 2026-07-01
            saturday are closed
            sunday are closed

            [Requirements] lasts 5 days
            [Design] starts at [Requirements]'s end lasts 3 days
            [Coding] starts at [Design]'s end lasts 7 days
            [Testing] starts at [Coding]'s end lasts 4 days

            Release happens at [Testing]'s end
            @endgantt
            """);

        // Auto-parse on load
        parseAndAnalyze();
    }

    // ========== File Operations ==========

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
                currentFile = file;
                hasUnsavedChanges = false;
                parseAndAnalyze();
                statusLabel.setText("Loaded: " + file.getName());
            } catch (IOException e) {
                showError("Failed to read file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void saveFile() {
        if (currentFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save PlantUML Gantt File");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PlantUML Files", "*.puml")
            );
            currentFile = fileChooser.showSaveDialog(rootPane.getScene().getWindow());
            if (currentFile == null) return;
        }

        try {
            Files.writeString(currentFile.toPath(), codeArea.getText());
            hasUnsavedChanges = false;
            updateStatusMark();
            statusLabel.setText("Saved: " + currentFile.getName());
        } catch (IOException e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    // ========== Parsing & Analysis ==========

    @FXML
    private void parseAndAnalyze() {
        try {
            String content = codeArea.getText();
            currentSchedule = parser.parse(content);
            currentStats = engine.analyze(currentSchedule);

            computeLayout();
            mutableTasks.clear();
            mutableTasks.addAll(currentSchedule.tasks());
            interactionHandler.setData(currentLayouts, mutableTasks);
            selectionModel.setTasks(mutableTasks);
            selectionModel.clearSelection();
            renderGantt();
            updateStatistics();
            updateTaskList();

            statusLabel.setText(String.format("Parsed: %d tasks, Total man-days: %.1f",
                currentSchedule.tasks().size(), currentStats.totalManDays()));
        } catch (Exception e) {
            showError("Parse error: " + e.getMessage());
        }
    }

    // ========== Layout & Rendering ==========

    private void computeLayout() {
        if (currentSchedule == null || currentSchedule.tasks().isEmpty()) {
            currentLayouts = List.of();
            return;
        }

        List<Task> tasks = mutableTasks;
        LocalDate minDate = tasks.stream()
            .map(Task::startDate)
            .filter(d -> d != null)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now());
        LocalDate maxDate = tasks.stream()
            .map(Task::endDate)
            .filter(d -> d != null)
            .max(LocalDate::compareTo)
            .orElse(minDate.plusDays(30));

        // Expand range by a few days on each side
        minDate = minDate.minusDays(2);
        maxDate = maxDate.plusDays(2);

        LayoutConfig config = new LayoutConfig(
            minDate, maxDate,
            pixelsPerDay,
            LayoutConfig.DEFAULT_ROW_HEIGHT,
            0, // startY (below time axis header)
            0  // labelColumnWidth (labels are in separate ListView)
        );

        Set<String> criticalIds = currentStats.criticalPath() != null
            ? Set.copyOf(currentStats.criticalPath().criticalTaskIds())
            : Set.of();

        currentLayouts = layoutEngine.computeLayout(tasks, criticalIds, config);

        // Resize canvas to fit content
        double[] size = layoutEngine.computeCanvasSize(tasks, config);
        double canvasWidth = Math.max(size[0], ganttScrollPane.getViewportBounds().getWidth());
        double canvasHeight = Math.max(size[1], ganttScrollPane.getViewportBounds().getHeight());
        ganttCanvas.setWidth(canvasWidth);
        ganttCanvas.setHeight(canvasHeight);
    }

    private void renderGantt() {
        if (currentLayouts == null || currentLayouts.isEmpty() || currentSchedule == null) {
            ganttCanvas.getGraphicsContext2D().clearRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
            return;
        }

        List<Task> tasks = mutableTasks;
        LocalDate minDate = tasks.stream()
            .map(Task::startDate)
            .filter(d -> d != null)
            .min(LocalDate::compareTo)
            .orElse(LocalDate.now())
            .minusDays(2);
        LocalDate maxDate = tasks.stream()
            .map(Task::endDate)
            .filter(d -> d != null)
            .max(LocalDate::compareTo)
            .orElse(minDate.plusDays(30))
            .plusDays(2);

        LayoutConfig config = new LayoutConfig(
            minDate, maxDate,
            pixelsPerDay,
            LayoutConfig.DEFAULT_ROW_HEIGHT,
            0, 0
        );

        GanttCanvasView canvasView = new GanttCanvasView(ganttCanvas.getWidth(), ganttCanvas.getHeight());
        canvasView.setSelectedIds(selectionModel.getSelectedIds());
        canvasView.render(currentLayouts, tasks, config);

        // Copy rendered content to the FXML canvas
        ganttCanvas.getGraphicsContext2D().drawImage(
            canvasView.snapshot(null, null),
            0, 0
        );
    }

    // ========== Task List ==========

    private void updateTaskList() {
        if (mutableTasks.isEmpty()) return;
        List<String> names = mutableTasks.stream()
            .map(Task::name)
            .collect(Collectors.toList());
        taskListView.setItems(FXCollections.observableArrayList(names));
    }

    // ========== Interaction Handlers ==========

    private void onCanvasMouseClicked(MouseEvent event) {
        if (currentLayouts == null || currentLayouts.isEmpty()) return;
        interactionHandler.handleClick(event.getX(), event.getY());
    }

    private void onCanvasMouseMoved(MouseEvent event) {
        if (currentLayouts == null || currentLayouts.isEmpty()) return;
        var tooltip = interactionHandler.handleHover(event.getX(), event.getY());
        if (tooltip.isPresent()) {
            hoverTooltip.setText(tooltip.get());
            Tooltip.install(ganttCanvas, hoverTooltip);
        } else {
            Tooltip.uninstall(ganttCanvas, hoverTooltip);
        }
    }

    private void onSelectionChanged(Task selectedTask) {
        // Update canvas highlights
        renderGantt();

        // Update task list selection
        if (selectedTask != null) {
            int index = mutableTasks.indexOf(selectedTask);
            if (index >= 0) {
                taskListView.getSelectionModel().select(index);
                taskListView.scrollTo(index);
            }
            // Show property panel
            propertyPanel.setVisible(true);
            propertyPanel.setManaged(true);
            updatePropertyPanel(selectedTask);
        } else {
            taskListView.getSelectionModel().clearSelection();
            propertyPanel.setVisible(false);
            propertyPanel.setManaged(false);
        }
    }

    private void updatePropertyPanel(Task task) {
        updatingPropertyPanel = true;
        try {
            propNameField.setText(task.name());
            propStartDate.setValue(task.startDate());
            propEndDate.setValue(task.endDate());
            propProgressSlider.setValue(
                task.status() == TaskStatus.COMPLETED ? 100 :
                task.status() == TaskStatus.IN_PROGRESS ? 50 : 0
            );
            propProgressLabel.setText(String.format("%.0f%%", propProgressSlider.getValue()));

            // Populate assignee combo
            List<String> assignees = task.assignments() != null
                ? task.assignments().stream().map(Assignment::person).collect(Collectors.toList())
                : List.of();
            propAssigneeCombo.setItems(FXCollections.observableArrayList(assignees));
            if (!assignees.isEmpty()) {
                propAssigneeCombo.getSelectionModel().select(0);
            }
        } finally {
            updatingPropertyPanel = false;
        }
    }

    // ========== Property Panel Editing ==========

    private void setupPropertyPanelListeners() {
        // Name change
        propNameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal && !updatingPropertyPanel) { // on focus lost
                applyNameChange();
            }
        });

        // Start date change
        propStartDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingPropertyPanel && newVal != null) {
                applyDateChange(newVal, true);
            }
        });

        // End date change
        propEndDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingPropertyPanel && newVal != null) {
                applyDateChange(newVal, false);
            }
        });

        // Progress slider change
        propProgressSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            propProgressLabel.setText(String.format("%.0f%%", propProgressSlider.getValue()));
            if (!updatingPropertyPanel) {
                applyProgressChange();
            }
        });
    }

    private void applyNameChange() {
        Task selected = selectionModel.getSelectedTask();
        if (selected == null) return;
        String newName = propNameField.getText().trim();
        if (newName.isEmpty() || newName.equals(selected.name())) return;

        replaceTask(selected, new Task(
            selected.id(), newName, selected.group(),
            selected.startDate(), selected.endDate(), selected.durationDays(),
            selected.assignments(), selected.dependencyIds(),
            selected.status(), selected.color()
        ), "Edit task name");
    }

    private void applyDateChange(LocalDate newDate, boolean isStart) {
        Task selected = selectionModel.getSelectedTask();
        if (selected == null) return;

        LocalDate newStart = isStart ? newDate : selected.startDate();
        LocalDate newEnd = isStart ? selected.endDate() : newDate;

        // Validate: start must be before end
        if (newStart != null && newEnd != null && newStart.isAfter(newEnd)) return;

        int newDuration = (newStart != null && newEnd != null)
            ? (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1
            : selected.durationDays();

        replaceTask(selected, new Task(
            selected.id(), selected.name(), selected.group(),
            newStart, newEnd, newDuration,
            selected.assignments(), selected.dependencyIds(),
            selected.status(), selected.color()
        ), "Edit task date");
    }

    private void applyProgressChange() {
        Task selected = selectionModel.getSelectedTask();
        if (selected == null) return;

        double value = propProgressSlider.getValue();
        TaskStatus newStatus;
        if (value >= 100) {
            newStatus = TaskStatus.COMPLETED;
        } else if (value > 0) {
            newStatus = TaskStatus.IN_PROGRESS;
        } else {
            newStatus = TaskStatus.PENDING;
        }

        if (newStatus == selected.status()) return;

        replaceTask(selected, new Task(
            selected.id(), selected.name(), selected.group(),
            selected.startDate(), selected.endDate(), selected.durationDays(),
            selected.assignments(), selected.dependencyIds(),
            newStatus, selected.color()
        ), "Edit task progress");
    }

    private void replaceTask(Task oldTask, Task newTask, String description) {
        EditTaskCommand cmd = new EditTaskCommand(mutableTasks, oldTask, newTask, description);
        commandStack.push(cmd);

        hasUnsavedChanges = true;

        // Update dependent data
        interactionHandler.setData(currentLayouts, mutableTasks);
        selectionModel.setTasks(mutableTasks);
        selectionModel.selectTask(newTask.id()); // re-select to refresh panel

        refreshGantt();
        updateTaskList();
        updateStatusMark();
    }

    // ========== Statistics Tab ==========

    private void updateStatistics() {
        if (currentStats == null) return;

        statTaskCount.setText(String.valueOf(mutableTasks.size()));
        statManDays.setText(String.format("%.1f", currentStats.totalManDays()));

        if (currentStats.criticalPath() != null) {
            statDuration.setText(String.valueOf(currentStats.criticalPath().totalDurationDays()));

            // Build critical path task names
            List<String> critNames = currentStats.criticalPath().criticalTaskIds().stream()
                .map(id -> mutableTasks.stream()
                    .filter(t -> t.id().equals(id))
                    .map(Task::name)
                    .findFirst().orElse(id))
                .collect(Collectors.toList());
            statCriticalPath.setText(String.format("%d days: %s",
                currentStats.criticalPath().totalDurationDays(),
                String.join(" -> ", critNames)));
        } else {
            statDuration.setText("--");
            statCriticalPath.setText("No critical path");
        }

        // Overload warnings
        overloadBox.getChildren().clear();
        if (currentStats.overloads() != null) {
            for (OverloadRecord overload : currentStats.overloads()) {
                Label lbl = new Label(overload.describe());
                lbl.setStyle("-fx-text-fill: #D32F2F;");
                overloadBox.getChildren().add(lbl);
            }
            if (currentStats.overloads().isEmpty()) {
                overloadBox.getChildren().add(new Label("No overload warnings"));
            }
        }

        // Balance suggestions
        suggestionBox.getChildren().clear();
        if (currentStats.balanceSuggestions() != null && !currentStats.balanceSuggestions().isEmpty()) {
            for (BalanceSuggestion suggestion : currentStats.balanceSuggestions()) {
                Label lbl = new Label(String.format("%s: move '%s' to %s (%s)",
                    suggestion.person(), suggestion.suggestTask(),
                    suggestion.suggestStart(), suggestion.reason()));
                lbl.setStyle("-fx-text-fill: #1565C0;");
                suggestionBox.getChildren().add(lbl);
            }
        } else {
            suggestionBox.getChildren().add(new Label("No suggestions"));
        }

        // Resource distribution
        resourceBox.getChildren().clear();
        if (currentStats.personManDays() != null && !currentStats.personManDays().isEmpty()) {
            double maxManDays = currentStats.personManDays().values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);

            for (var entry : currentStats.personManDays().entrySet()) {
                HBox bar = new HBox(6);
                bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label nameLabel = new Label(entry.getKey());
                nameLabel.setPrefWidth(80);

                double fraction = entry.getValue() / maxManDays;
                javafx.scene.shape.Rectangle barRect = new javafx.scene.shape.Rectangle(
                    fraction * 200, 16,
                    javafx.scene.paint.Color.web("#42A5F5")
                );
                barRect.setArcHeight(4);
                barRect.setArcWidth(4);

                Label valueLabel = new Label(String.format("%.1f days", entry.getValue()));
                bar.getChildren().addAll(nameLabel, barRect, valueLabel);
                resourceBox.getChildren().add(bar);
            }
        } else {
            resourceBox.getChildren().add(new Label("No resource data"));
        }
    }

    // ========== Zoom Controls ==========

    @FXML
    private void zoomIn() {
        pixelsPerDay = Math.min(pixelsPerDay * 1.3, 100.0);
        refreshGantt();
    }

    @FXML
    private void zoomOut() {
        pixelsPerDay = Math.max(pixelsPerDay / 1.3, 5.0);
        refreshGantt();
    }

    @FXML
    private void fitToWindow() {
        if (mutableTasks.isEmpty()) return;
        double viewportWidth = ganttScrollPane.getViewportBounds().getWidth() - 20;
        List<Task> tasks = mutableTasks;
        LocalDate minDate = tasks.stream().map(Task::startDate).filter(d -> d != null).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate maxDate = tasks.stream().map(Task::endDate).filter(d -> d != null).max(LocalDate::compareTo).orElse(minDate.plusDays(30));
        long totalDays = ChronoUnit.DAYS.between(minDate.minusDays(2), maxDate.plusDays(2));
        if (totalDays > 0) {
            pixelsPerDay = viewportWidth / totalDays;
        }
        refreshGantt();
    }

    private void refreshGantt() {
        computeLayout();
        renderGantt();
    }

    // ========== Undo / Redo (stubs for Task 6) ==========

    @FXML
    private void undo() {
        if (commandStack.undo()) {
            hasUnsavedChanges = true;
            interactionHandler.setData(currentLayouts, mutableTasks);
            selectionModel.setTasks(mutableTasks);
            Task selected = selectionModel.getSelectedTask();
            if (selected != null) {
                selectionModel.selectTask(selected.id());
            }
            refreshGantt();
            updateTaskList();
            updateStatusMark();
            statusLabel.setText("Undo: " + commandStack.peekRedoDescription());
        }
    }

    @FXML
    private void redo() {
        if (commandStack.redo()) {
            hasUnsavedChanges = true;
            interactionHandler.setData(currentLayouts, mutableTasks);
            selectionModel.setTasks(mutableTasks);
            Task selected = selectionModel.getSelectedTask();
            if (selected != null) {
                selectionModel.selectTask(selected.id());
            }
            refreshGantt();
            updateTaskList();
            updateStatusMark();
            statusLabel.setText("Redo: " + commandStack.peekUndoDescription());
        }
    }

    // ========== Help ==========

    @FXML
    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About GanttLens");
        alert.setHeaderText("GanttLens v1.0.0-SNAPSHOT");
        alert.setContentText("PlantUML Gantt chart analysis and insight tool.\n\nGUI Module - Iteration 3.2");
        alert.showAndWait();
    }

    // ========== Helpers ==========

    private void updateStatusMark() {
        String text = statusLabel.getText();
        if (hasUnsavedChanges && !text.contains("*")) {
            statusLabel.setText(text + " *");
        } else if (!hasUnsavedChanges) {
            statusLabel.setText(text.replace(" *", ""));
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
