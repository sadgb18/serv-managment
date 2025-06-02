package org.example.servmanagment;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

// контроллер для тестового окна
// обрабатывает все действия пользователя в интерфейсе
public class HelloController {
    // инжектим Label из разметки в наш код
    @FXML
    private Label welcomeText;
    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}
