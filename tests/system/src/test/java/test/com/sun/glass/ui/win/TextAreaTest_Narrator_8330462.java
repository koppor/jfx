package test.com.sun.glass.ui.win;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.Test;

public class TextAreaTest_Narrator_8330462 extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Test
    public void test() throws Exception {
        main(new String[0]);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setScene(new Scene(new VBox(new TextArea("javafx test"))));
        System.out.println("YESSZZZZZ");
        primaryStage.show();
    }
}