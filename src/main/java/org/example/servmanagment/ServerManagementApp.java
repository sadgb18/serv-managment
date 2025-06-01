package org.example.servmanagment;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ServerManagementApp extends Application {
    private LoginController loginController;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ServerManagementApp.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 400, 300);
        loginController = fxmlLoader.getController();
        
        stage.setTitle("Подключение к серверу");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (loginController != null) {
            loginController.shutdown();
        }
    }

    public static void main(String[] args) {
        launch();
    }
} 