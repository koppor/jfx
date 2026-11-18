///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS org.openjfx:javafx-controls:${javafx.version:28-ea+12}:${os.detected.jfxname}

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/// TableRowSkinBase sets the alignment of every cell without a styled alignment to TOP_LEFT once the row is
/// taller than 24px (hard-coded, independent of font size and `-fx-cell-size`).
///
/// Expected: "Text" is vertically centered in all four tables (CENTER_LEFT is the default alignment of a Labeled).
/// Actual: rows of 25px and 40px get TOP_LEFT, the text sticks to the top; only an explicit `-fx-alignment` keeps it centered.
public class TallRowTopLeft extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        HBox root = new HBox(10,
                table(24, false),
                table(25, false),
                table(40, false),
                table(40, true));
        stage.setScene(new Scene(root));
        stage.setTitle("JavaFX " + System.getProperty("javafx.runtime.version"));
        stage.show();

        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(_ -> root.getChildren().forEach(node -> report((TableView<?>) node)));
        pause.play();
    }

    private static TableView<String> table(int fixedCellSize, boolean centerLeftCss) {
        TableColumn<String, String> column = new TableColumn<>(fixedCellSize + "px" + (centerLeftCss ? " + CSS" : ""));
        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        column.setPrefWidth(160);
        TableView<String> table = new TableView<>(FXCollections.observableArrayList("Text", "Text", "Text"));
        table.getColumns().add(column);
        table.setPrefSize(170, 200);
        table.setFixedCellSize(fixedCellSize);
        if (centerLeftCss) {
            table.getStylesheets().add("data:text/css,.table-cell%7B-fx-alignment:center-left;%7D");
        }
        return table;
    }

    private static void report(TableView<?> table) {
        TableCell<?, ?> cell = table.lookupAll(".table-cell").stream()
                                    .map(TableCell.class::cast)
                                    .filter(c -> "Text".equals(c.getText()))
                                    .findFirst().orElseThrow();
        Bounds text = cell.lookup(".text").getBoundsInParent();
        System.out.printf("%-22s row height %4.1f  alignment %-11s  space above text %4.1f, below %4.1f%n",
                table.getColumns().getFirst().getText(), cell.getHeight(), cell.getAlignment(),
                text.getMinY(), cell.getHeight() - text.getMaxY());
    }
}
