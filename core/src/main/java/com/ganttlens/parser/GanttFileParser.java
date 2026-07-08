package com.ganttlens.parser;

import com.ganttlens.model.GanttSchedule;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses PlantUML Gantt chart files into GanttSchedule objects.
 */
public class GanttFileParser {

    /**
     * Parses a .puml file and returns a GanttSchedule.
     */
    public GanttSchedule parseFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return parse(content);
    }

    /**
     * Parses PlantUML Gantt content string and returns a GanttSchedule.
     */
    public GanttSchedule parse(String content) {
        // Preprocess: strip !define, <style>, today directives, date-color lines
        GanttPreprocessor.PreprocessResult prep = GanttPreprocessor.preprocess(content);

        PlantUMLGanttLexer lexer = new PlantUMLGanttLexer(CharStreams.fromString(prep.content()));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlantUMLGanttParser parser = new PlantUMLGanttParser(tokens);

        ParseTree tree = parser.ganttFile();

        GanttParseListener listener = new GanttParseListener(
            prep.closedDayColor(), prep.dateColors(), prep.todayDate()
        );
        listener.visit(tree);

        return listener.buildSchedule();
    }
}
