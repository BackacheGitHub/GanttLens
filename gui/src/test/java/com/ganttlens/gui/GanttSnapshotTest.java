package com.ganttlens.gui;

import com.ganttlens.analyzer.AnalysisEngine;
import com.ganttlens.analyzer.GanttLayoutEngine;
import com.ganttlens.model.*;
import com.ganttlens.parser.GanttFileParser;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Visual snapshot test — renders each .puml test file to a GanttCanvasView
 * and saves the result as a PNG image for manual inspection.
 *
 * <p>Run this test, then browse the generated images in {@code gui/target/snapshots/}
 * to visually verify rendering correctness without launching the full GUI.</p>
 *
 * <p>Usage: {@code mvn test -pl gui -Dtest=GanttSnapshotTest}</p>
 */
class GanttSnapshotTest {

    private static final Path SNAPSHOT_OUTPUT_DIR = Path.of("target", "snapshots");

    private final GanttFileParser parser = new GanttFileParser();
    private final AnalysisEngine analysisEngine = new AnalysisEngine();
    private final GanttLayoutEngine layoutEngine = new GanttLayoutEngine();

    @BeforeAll
    static void initToolkit() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // JavaFX toolkit already initialized
        }
        // Ensure output directory exists
        Files.createDirectories(SNAPSHOT_OUTPUT_DIR);
    }

    // ========== Discover all .puml test files ==========

    static Stream<Arguments> pumlFiles() throws IOException {
        Path coreTestResources = Path.of("..", "core", "src", "test", "resources");

        Stream<Path> syntax = Files.list(coreTestResources.resolve("syntax"))
            .filter(p -> p.toString().endsWith(".puml"));
        Stream<Path> official = Files.list(coreTestResources.resolve("official-examples"))
            .filter(p -> p.toString().endsWith(".puml"));

        return Stream.concat(syntax, official)
            .map(p -> Arguments.of(p, p.getFileName().toString().replace(".puml", "")));
    }

    // ========== Snapshot test ==========

    @ParameterizedTest(name = "{1}")
    @MethodSource("pumlFiles")
    void renderSnapshot(Path pumlFile, String name) throws Exception {
        // Parse
        GanttSchedule schedule = parser.parseFile(pumlFile);
        assertThat(schedule).isNotNull();
        assertThat(schedule.tasks()).isNotEmpty();

        List<Task> tasks = schedule.tasks();

        // Compute date range from tasks
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

        minDate = minDate.minusDays(2);
        maxDate = maxDate.plusDays(2);

        // Analyze for critical path
        ProjectStats stats = analysisEngine.analyze(schedule);
        Set<String> criticalIds = stats.criticalPath() != null
            ? Set.copyOf(stats.criticalPath().criticalTaskIds())
            : Set.of();

        // Layout config
        LayoutConfig config = new LayoutConfig(
            minDate, maxDate,
            LayoutConfig.DEFAULT_PIXELS_PER_DAY,
            LayoutConfig.DEFAULT_ROW_HEIGHT,
            GanttCanvasView.TIME_AXIS_HEIGHT,
            LayoutConfig.DEFAULT_LABEL_COLUMN_WIDTH
        );

        List<TaskLayout> layouts = layoutEngine.computeLayout(tasks, criticalIds, config);

        // Compute canvas size
        double[] size = layoutEngine.computeCanvasSize(tasks, config);
        double canvasWidth = Math.max(size[0], 600);
        double canvasHeight = Math.max(size[1], 300);

        // Render on FX application thread and capture snapshot
        AtomicReference<WritableImage> snapshotRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                GanttCanvasView canvas = new GanttCanvasView(canvasWidth, canvasHeight);
                canvas.render(layouts, tasks, config);
                WritableImage snapshot = canvas.snapshot(null, null);
                snapshotRef.set(snapshot);
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(10, TimeUnit.SECONDS))
            .as("Rendering timed out for %s", name)
            .isTrue();

        WritableImage snapshot = snapshotRef.get();
        assertThat(snapshot).as("Snapshot is null for %s", name).isNotNull();

        // Save to PNG
        File outputFile = SNAPSHOT_OUTPUT_DIR.resolve(name + ".png").toFile();
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
        ImageIO.write(bufferedImage, "png", outputFile);

        System.out.printf("[Snapshot] %-30s -> %s (%.0fx%.0f)%n",
            name, outputFile.getPath(), canvasWidth, canvasHeight);

        assertThat(outputFile).exists();
    }
}
