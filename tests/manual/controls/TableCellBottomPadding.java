///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS org.openjfx:javafx-controls:${javafx.version:28-ea+12}:${os.detected.jfxname}

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/// TableCellSkinBase.layoutChildren subtracts the bottom padding a second time
/// (`layoutLabelInArea(x, y, w, h - getSkinnable().getPadding().getBottom())`).
///
/// Both views get the same cell CSS: 10px padding at top and bottom, CENTER_LEFT, rows of 50px.
/// Expected: "Text" is vertically centered in both, as in the ListView.
/// Actual: in the TableView the space above the text is 10px (= bottom padding) smaller than the space below.
public class TableCellBottomPadding extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ObservableList<String> items = FXCollections.observableArrayList("Text", "Text", "Text");

        ListView<String> list = new ListView<>(items);
        list.setFixedCellSize(50);
        list.setPrefSize(170, 200);

        TableColumn<String, String> column = new TableColumn<>("TableView");
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        column.setPrefWidth(160);
        TableView<String> table = new TableView<>(items);
        table.getColumns().add(column);
        table.setFixedCellSize(50);
        table.setPrefSize(170, 200);

        HBox root = new HBox(10, list, table);
        root.getStylesheets().add("data:text/css,.list-cell,.table-cell%7B-fx-padding:10px 5px;-fx-alignment:center-left;%7D");
        stage.setScene(new Scene(root));
        stage.setTitle("JavaFX " + System.getProperty("javafx.runtime.version"));
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(_ -> {
            report("ListView", list, ".list-cell");
            report("TableView", table, ".table-cell");
        });
        pause.play();
    }

    private static void report(String name, javafx.scene.Node view, String cellSelector) {
        IndexedCell<?> cell = view.lookupAll(cellSelector).stream()
                                  .map(IndexedCell.class::cast)
                                  .filter(c -> "Text".equals(c.getText()))
                                  .findFirst().orElseThrow();
        Bounds text = cell.lookup(".text").getBoundsInParent();
        System.out.printf("%-10s padding %s  space above text %4.1f, below %4.1f%n",
                name, cell.getPadding(), text.getMinY(), cell.getHeight() - text.getMaxY());
    }
}
