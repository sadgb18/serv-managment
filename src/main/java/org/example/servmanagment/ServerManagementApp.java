package org.example.servmanagment;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

// главный класс приложения для управления сервером
// запускает окно логина и управляет жизненным циклом приложения
public class ServerManagementApp extends Application {
    // храним ссылку на контроллер окна логина
    // чтобы можно было корректно закрыть соединение при выходе
    private LoginController loginController;

    // метод при старте приложения
    @Override
    public void start(Stage stage) throws IOException {
        // грузим разметку окна логина из fxml файла
        FXMLLoader fxmlLoader = new FXMLLoader(ServerManagementApp.class.getResource("login-view.fxml"));
        // создаем окно размером 400x300
        Scene scene = new Scene(fxmlLoader.load(), 400, 300);
        // сохраняем ссылку на контроллер
        loginController = fxmlLoader.getController();
        
        // настраиваем и показываем окно
        stage.setTitle("Подключение к серверу");
        stage.setScene(scene);
        stage.show();
    }

    // метод при закрытии приложения
    // корректно закрываем SSH соединение если оно было
    @Override
    public void stop() {
        if (loginController != null) {
            loginController.shutdown();
        }
    }

    // точка входа в приложение
    public static void main(String[] args) {
        launch();
    }
} 
