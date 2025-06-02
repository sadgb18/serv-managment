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

// контроллер окна логина
// вся логика для подключения к серверу по SSH
public class LoginController {
    // аннотация
    @FXML private TextField hostField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField portField;
    @FXML private Button connectButton;

    // сохраняем SSH сессию после подключения
    private Session sshSession;

    // метод вызывается когда юзер жмет на кнопку "Подключиться"
    @FXML
    protected void onConnectButtonClick() {
        // берем данные из полей ввода и убираем пробелы по краям
        String host = hostField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        // проверяем что все поля заполнены
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showError("Ошибка", "Пожалуйста, заполните все поля");
            return;
        }

        // парсим порт и проверяем что он валидный
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

        // блокируем кнопку чтобы юзер не спамил подключениями
        connectButton.setDisable(true);

        // подключение делаем в отдельном потоке
        // потому что оно может тормозить, а UI должен оставаться отзывчивым
        new Thread(() -> {
            try {
                // создаем SSH клиент и сессию
                JSch jsch = new JSch();
                sshSession = jsch.getSession(username, host, port);

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
                // отключаем проверку ключа сервера
                sshSession.setConfig("StrictHostKeyChecking", "no");
                // пробуем подключиться с таймаутом 5 сек
                sshSession.connect(5000);

                // если подключились - открываем главное окно
                Platform.runLater(() -> {
                    try {
                        openMainWindow();
                    } catch (Exception e) {
                        showError("Ошибка", "Не удалось открыть главное окно: " + e.getMessage());
                        enableConnectButton();
                    }
                });
            } catch (JSchException e) {
                // показываем ошибку
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

    // открываем главное окно приложения
    private void openMainWindow() throws Exception {
        // грузим разметку из fxml
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        // создаем контроллер и передаем ему SSH сессию
        MainController mainController = new MainController(sshSession);
        fxmlLoader.setController(mainController);
        // создаем окно и показываем его
        Scene scene = new Scene(fxmlLoader.load(), 1024, 768);
        Stage stage = new Stage();
        stage.setTitle("Управление сервером");
        stage.setScene(scene);
        stage.show();

        // закрываем окно логина
        ((Stage) hostField.getScene().getWindow()).close();
    }

    // разблокируем кнопку подключения
    private void enableConnectButton() {
        connectButton.setDisable(false);
    }

    // показываем окошко с ошибкой
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // этот метод вызывается при закрытии приложения
    // отключаемся от сервера если были подключены
    public void shutdown() {
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
    }
} 
