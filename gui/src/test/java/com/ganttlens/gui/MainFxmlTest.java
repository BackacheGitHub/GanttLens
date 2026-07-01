package com.ganttlens.gui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
