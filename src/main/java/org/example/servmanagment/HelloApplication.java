package org.example.servmanagment;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // грузим наш fxml файл с разметкой интерфейса
        // путь берем относительно текущего класса
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        // создаем сцену размером 320 на 240 пикселей
        // контейнер для всех элементов интерфейса
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        // ставим заголовок окна, размер сцены и показываем окно
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
