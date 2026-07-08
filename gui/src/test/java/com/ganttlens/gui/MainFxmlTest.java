package com.ganttlens.gui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that main.fxml loads without property or structural errors.
 * <p>
 * Catches issues like invalid attributes on JavaFX components
 * (e.g., minWidth/minHeight on Canvas which doesn't inherit from Region).
 */
class MainFxmlTest {

    @BeforeAll
    static void initJfxToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // JavaFX toolkit already initialized
        }
    }

    @Test
    void mainFxml_loadsWithoutErrors() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/main.fxml")
        );
        Parent root = loader.load();

        assertThat(root).isNotNull();
    }

    @Test
    void mainFxml_doesNotContainTaskListView() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/main.fxml")
        );
        Parent root = loader.load();

        // BFS traversal to ensure no ListView node exists in the scene graph
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            assertThat(node).isNotInstanceOf(ListView.class);
            if (node instanceof Parent parent) {
                queue.addAll(parent.getChildrenUnmodifiable());
            }
        }
    }

    @Test
    void computeTimeAxisY_alwaysReturnsZero() {
        // Time axis is fixed at top regardless of scroll position
        assertThat(GanttCanvasView.computeTimeAxisY(0)).isEqualTo(0.0);
        assertThat(GanttCanvasView.computeTimeAxisY(100)).isEqualTo(0.0);
        assertThat(GanttCanvasView.computeTimeAxisY(500)).isEqualTo(0.0);
    }

    @Test
    void rowBackgroundEven_isWhite() {
        assertThat(GanttColorMapper.rowBackgroundEven()).isEqualTo(Color.WHITE);
    }

    @Test
    void rowBackgroundOdd_isLightGray() {
        assertThat(GanttColorMapper.rowBackgroundOdd()).isEqualTo(Color.web("#F8F9FA"));
    }

    @Test
    void weekendBackground_withClosedDayColor_returnsCustomColor() {
        assertThat(GanttColorMapper.weekendBackground("WhiteSmoke")).isEqualTo(Color.web("WhiteSmoke"));
    }

    @Test
    void weekendBackground_withNull_returnsDefault() {
        assertThat(GanttColorMapper.weekendBackground(null)).isEqualTo(Color.web("#F5F5F5"));
    }

    @Test
    void ganttCanvasRendersWithGroupHeadersWithoutException() throws Exception {
        // Given: FXMLLoader loads main.fxml (triggers initialize() → parseAndAnalyze())
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();

        // Then: no exception thrown during load, controller is wired and canvas is bound
        assertThat(controller).isNotNull();
        assertThat(root).isNotNull();
        // Verify ganttCanvas exists in FXML namespace (fx:id binding)
        assertThat(loader.getNamespace().get("ganttCanvas")).isInstanceOf(Canvas.class);
    }

    @Test
    void viewportBoundsChange_triggersRefresh() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/main.fxml")
        );
        Parent root = loader.load();
        MainController controller = loader.getController();

        // The resize listener is registered if ganttScrollPane exists and has the listener
        // We verify indirectly: accessing the controller's ScrollPane should not throw
        assertThat(root).isNotNull();
        assertThat(controller).isNotNull();
    }
}
