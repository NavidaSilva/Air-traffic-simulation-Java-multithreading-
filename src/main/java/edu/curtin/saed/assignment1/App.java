package edu.curtin.saed.assignment1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point. Wires the UI to the simulation controller.
 */
public class App extends Application
{
    // Defaults 
    private static final double W = 12.0;
    private static final double H = 12.0;
    private static final int NA = 10;
    private static final int NP = 10;
    private static final double S = 1.2; // units/second
    private SimulationController controller;
    
    public static void main(String[] args)
    {
        launch();
    }

    @Override
    public void start(Stage stage)
    {
        // Map area
        GridArea area = new GridArea(W, H);
        area.setGridLines(true);
        area.setStyle("-fx-background-color: #0b3d0b;");

        // Controls and outputs
        var startBtn = new Button("Start");
        var endBtn = new Button("End");
        var statusText = new Label("In-Flight: 0 | Servicing: 0 | Completed: 0");
        var textArea = new TextArea();
        textArea.setEditable(false);

        // Controller encapsulates the entire simulation
        controller = new SimulationController(W, H, NA, NP, S, area, statusText, textArea);

        startBtn.setOnAction(e -> controller.start());
        endBtn.setOnAction(e -> controller.end());
        stage.setOnCloseRequest(e -> {
            controller.shutdownAndExit();
        });

        var toolbar = new ToolBar();
        toolbar.getItems().addAll(startBtn, endBtn, new Separator(), statusText);

        var splitPane = new SplitPane();
        splitPane.getItems().addAll(area, textArea);
        splitPane.setDividerPositions(0.72);

        var content = new BorderPane();
        content.setTop(toolbar);
        content.setCenter(splitPane);

        var scene = new Scene(content, 1200, 900);
        stage.setTitle("Air Traffic Simulator");
        stage.setScene(scene);
        stage.show();
    }
}
