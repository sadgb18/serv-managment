package org.example.servmanagment;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {
    @FXML private TextField hostField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField portField;
    @FXML private Button connectButton;

    private Session sshSession;

    @FXML
    protected void onConnectButtonClick() {
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Ошибка", "Пожалуйста, заполните все поля");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException("Порт должен быть от 1 до 65535");
            }
        } catch (NumberFormatException e) {
            showError("Ошибка", "Неверный формат порта. " + e.getMessage());
            return;
        }

        // Отключаем кнопку на время подключения
        connectButton.setDisable(true);

        // Выполняем подключение в отдельном потоке
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                sshSession = jsch.getSession(username, host, port);
                
                // Создаем и устанавливаем UserInfo
                UserInfo userInfo = new UserInfo() {
                    public String getPassword() { return password; }
                    public boolean promptYesNo(String str) { return true; }
                    public String getPassphrase() { return null; }
                    public boolean promptPassphrase(String message) { return true; }
                    public boolean promptPassword(String message) { return true; }
                    public void showMessage(String message) { }
                };
                
                sshSession.setUserInfo(userInfo);
                sshSession.setPassword(password);
                sshSession.setConfig("StrictHostKeyChecking", "no");
                sshSession.connect(5000); // таймаут 5 секунд

                Platform.runLater(() -> {
                    try {
                        openMainWindow();
                    } catch (Exception e) {
                        showError("Ошибка", "Не удалось открыть главное окно: " + e.getMessage());
                        enableConnectButton();
                    }
                });
            } catch (JSchException e) {
                Platform.runLater(() -> {
                    String message = e.getMessage();
                    if (message.contains("Auth fail") || message.contains("Authentication failed")) {
                        showError("Ошибка", "Неверное имя пользователя или пароль");
                    } else if (message.contains("timeout") || message.contains("Connection refused")) {
                        showError("Ошибка", "Не удалось подключиться к серверу. Проверьте адрес и порт");
                    } else {
                        showError("Ошибка подключения", message);
                    }
                    enableConnectButton();
                });
            }
        }).start();
    }

    private void openMainWindow() throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        MainController mainController = new MainController(sshSession);
        fxmlLoader.setController(mainController);
        Scene scene = new Scene(fxmlLoader.load(), 1024, 768);
        Stage stage = new Stage();
        stage.setTitle("Управление сервером");
        stage.setScene(scene);
        stage.show();

        // Закрываем окно входа
        ((Stage) hostField.getScene().getWindow()).close();
    }

    private void enableConnectButton() {
        connectButton.setDisable(false);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
    }
} 