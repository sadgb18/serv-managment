package org.example.servmanagment;

import com.jcraft.jsch.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;
import javafx.scene.chart.NumberAxis;

import java.io.*;
import java.util.*;

// главный контроллер приложения
// отвечает за мониторинг сервера и файловый менеджер
public class MainController {
    // SSH сессия для подключения к серверу
    private final Session sshSession;
    // таймер для обновления статистики
    private Timer monitoringTimer;
    // данные для графиков CPU и RAM
    private XYChart.Series<Number, Number> cpuSeries;
    private XYChart.Series<Number, Number> ramSeries;

    // части интерфейса, связываются через FXML
    @FXML private LineChart<Number, Number> cpuChart;    // график загрузки CPU
    @FXML private LineChart<Number, Number> ramChart;    // график использования RAM
    @FXML private Label cpuLabel;                        // текст с процентом CPU
    @FXML private Label ramLabel;                        // текст с процентом RAM
    @FXML private Label diskLabel;                       // текст с инфой о диске
    @FXML private TreeView<String> fileSystemTree;       // дерево файлов
    @FXML private VBox mainContainer;                    // главный контейнер окна

    // SVG иконки для файлов и папок
    private static final String FOLDER_ICON = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String FILE_ICON = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z";

    // конструктор, принимает SSH сессию из окна логина
    public MainController(Session sshSession) {
        this.sshSession = sshSession;
    }

    // этот метод вызывается автоматически после загрузки FXML
    @FXML
    public void initialize() {
        setupCharts();      // настраиваем графики
        setupMonitoring();  // запускаем мониторинг
        setupFileSystem();  // настраиваем файловый менеджер
    }

    // настройка графиков CPU и RAM
    private void setupCharts() {
        // создаем серии данных для графиков
        cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("CPU");
        cpuChart.getData().add(cpuSeries);
        styleChart(cpuChart, "#3498db");  // синий цвет для CPU

        ramSeries = new XYChart.Series<>();
        ramSeries.setName("RAM");
        ramChart.getData().add(ramSeries);
        styleChart(ramChart, "#2ecc71");  // зеленый цвет для RAM
    }

    private void styleChart(LineChart<Number, Number> chart, String color) {
        chart.setCreateSymbols(false);     // точки на линии не нужны
        chart.setAnimated(false);          // анимация тоже не нужна
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setAlternativeColumnFillVisible(false);
        chart.setAlternativeRowFillVisible(false);

        // настраиваем оси
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        
        xAxis.setTickLabelsVisible(false); // время не показываем
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);           // проценты от 0
        yAxis.setUpperBound(100);         // до 100
        yAxis.setTickUnit(20);            // деления каждые 20%
        
        // задаем CSS стили
        chart.setStyle("-fx-background-color: transparent;");
        String chartStyle = String.format(
            ".chart-series-line { -fx-stroke: %s; -fx-stroke-width: 2px; }" +
            ".chart-line-symbol { -fx-background-color: %s, white; }" +
            ".chart-vertical-grid-lines { -fx-stroke: #dddddd; }" +
            ".chart-horizontal-grid-lines { -fx-stroke: #dddddd; }" +
            ".chart-alternative-row-fill { -fx-fill: transparent; }" +
            ".chart-alternative-column-fill { -fx-fill: transparent; }" +
            ".chart-vertical-zero-line { -fx-stroke: transparent; }" +
            ".chart-horizontal-zero-line { -fx-stroke: transparent; }",
            color, color
        );
        chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
        chart.getStylesheets().clear();
        chart.setStyle(chartStyle);
    }

    // запускаем таймер для обновления статистики
    private void setupMonitoring() {
        monitoringTimer = new Timer(true);  // true = демон-поток, завершится вместе с программой
        monitoringTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    updateSystemStats();  // обновляем статистику каждые 2 секунды
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 2000);
    }

    // получаем статистику с сервера через SSH
    private void updateSystemStats() {
        try {
            // открываем канал для выполнения команд
            ChannelExec channel = (ChannelExec) sshSession.openChannel("exec");
            
            // команда для получения CPU, RAM и диска
            // top - загрузка CPU
            // free - использование RAM
            // df - информация о диске
            String command = 
                "top -bn1 | grep '%Cpu' | awk '{print $2}' && " +
                "free | grep Mem | awk '{print $3/$2 * 100}' && " +
                "df -h / | tail -1 | awk '{print $3,$2,$5}'";
            channel.setCommand(command);

            // читаем вывод команды
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            channel.setOutputStream(output);
            channel.connect();

            // ждем пока команда выполнится
            while (!channel.isClosed()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            // парсим результаты
            String[] stats = output.toString().split("\n");
            channel.disconnect();

            if (stats.length >= 3) {
                double cpu = Double.parseDouble(stats[0]);
                double ram = Double.parseDouble(stats[1]);
                String[] diskStats = stats[2].split("\\s+");

                // обновляем интерфейс в UI потоке
                Platform.runLater(() -> {
                    updateChart(cpuSeries, cpu);
                    updateChart(ramSeries, ram);
                    
                    cpuLabel.setText(String.format("CPU: %.1f%%", cpu));
                    ramLabel.setText(String.format("RAM: %.1f%%", ram));
                    
                    if (diskStats.length >= 3) {
                        String used = diskStats[0];   // использовано
                        String total = diskStats[1];  // всего
                        String percent = diskStats[2]; // процент использования
                        diskLabel.setText(String.format("Диск: %s/%s (%s)", used, total, percent));
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // обновляем график новыми данными
    private void updateChart(XYChart.Series<Number, Number> series, double value) {
        // храним только последние 50 значений
        if (series.getData().size() > 50) {
            series.getData().remove(0);
        }
        series.getData().add(new XYChart.Data<>(series.getData().size(), value));
    }

    // настраиваем файловый менеджер
    private void setupFileSystem() {
        refreshFileSystem();  // загружаем начальное состояние
        
        // обрабатываем двойной клик по папке
        fileSystemTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> item = fileSystemTree.getSelectionModel().getSelectedItem();
                if (item != null) {
                    expandTreeItem(item);  // раскрываем папку
                }
            }
        });
    }

    // создаем элемент дерева с иконкой
    private TreeItem<String> createTreeItem(String name, boolean isDirectory) {
        TreeItem<String> item = new TreeItem<>(name);
        
        // делаем иконку
        SVGPath icon = new SVGPath();
        icon.setContent(isDirectory ? FOLDER_ICON : FILE_ICON);
        icon.setFill(Color.valueOf(isDirectory ? "#f39c12" : "#7f8c8d")); // оранжевый для папок, серый для файлов
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);
        
        item.setGraphic(icon);
        return item;
    }

    // загружаем содержимое папки
    private void loadDirectoryContents(ChannelSftp channel, TreeItem<String> parent, String path) {
        try {
            // получаем список файлов через SFTP
            Vector<ChannelSftp.LsEntry> list = channel.ls(path);
            
            // сортируем: сначала папки, потом файлы
            List<ChannelSftp.LsEntry> sortedList = new ArrayList<>(list);
            sortedList.sort((a, b) -> {
                boolean aDir = a.getAttrs().isDir();
                boolean bDir = b.getAttrs().isDir();
                if (aDir && !bDir) return -1;
                if (!aDir && bDir) return 1;
                return a.getFilename().compareToIgnoreCase(b.getFilename());
            });

            // добавляем файлы в дерево
            for (ChannelSftp.LsEntry entry : sortedList) {
                String filename = entry.getFilename();
                // пропускаем . и .. (текущая и родительская папки)
                if (!filename.equals(".") && !filename.equals("..")) {
                    boolean isDirectory = entry.getAttrs().isDir();
                    TreeItem<String> item = createTreeItem(filename, isDirectory);
                    parent.getChildren().add(item);
                    
                    // для папок добавляем заглушку
                    // она нужна чтобы показать что папку можно раскрыть
                    if (isDirectory) {
                        item.getChildren().add(createTreeItem("", false));
                    }
                }
            }
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    // обновляем все дерево файлов
    @FXML
    private void refreshFileSystem() {
        new Thread(() -> {
            try {
                // открываем SFTP канал
                ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                channel.connect();

                // начинаем с домашней директории пользователя
                String homeDir = channel.getHome();
                TreeItem<String> root = createTreeItem(homeDir, true);
                
                // загружаем содержимое корневой директории
                loadDirectoryContents(channel, root, homeDir);

                // обновляем UI в основном потоке
                Platform.runLater(() -> {
                    fileSystemTree.setRoot(root);
                    root.setExpanded(true);
                });

                channel.disconnect();
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка файловой системы", e.getMessage()));
            }
        }).start();
    }

    // раскрываем папку при двойном клике
    private void expandTreeItem(TreeItem<String> item) {
        // если есть заглушка - значит папка еще не загружена
        if (item.getChildren().size() == 1 && item.getChildren().get(0).getValue().isEmpty()) {
            new Thread(() -> {
                try {
                    // открываем SFTP канал
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();

                    // получаем полный путь к папке
                    String path = buildPath(item);
                    // удаляем заглушку и загружаем содержимое
                    item.getChildren().clear();
                    loadDirectoryContents(channel, item, path);

                    channel.disconnect();
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        item.setExpanded(false);
                        showError("Ошибка", "Не удалось загрузить содержимое папки: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    // собираем полный путь к файлу/папке из дерева
    private String buildPath(TreeItem<String> item) {
        List<String> parts = new ArrayList<>();
        TreeItem<String> current = item;
        
        // идем от текущего элемента к корню
        while (current != null) {
            if (current.getValue() != null && !current.getValue().isEmpty()) {
                parts.add(0, current.getValue());
            }
            current = current.getParent();
        }
        
        // собираем путь через /
        String path = String.join("/", parts);
        return path.startsWith("/") ? path : "/" + path;
    }

    // загрузка файла на сервер
    @FXML
    private void uploadFile() {
        // проверяем что выбрана папка
        TreeItem<String> selected = fileSystemTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Ошибка", "Выберите папку для загрузки файла");
            return;
        }

        // открываем диалог выбора файла
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(mainContainer.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    // открываем SFTP канал
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();

                    // получаем путь к папке на сервере
                    String remotePath = buildPath(selected);
                    if (!remotePath.endsWith("/")) {
                        remotePath += "/";
                    }

                    // загружаем файл
                    channel.put(new FileInputStream(file), remotePath + file.getName());
                    
                    // обновляем содержимое папки
                    Platform.runLater(() -> {
                        expandTreeItem(selected);
                    });
                    
                    channel.disconnect();
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Ошибка загрузки", e.getMessage()));
                }
            }).start();
        }
    }

    // скачивание файла с сервера
    @FXML
    private void downloadFile() {
        // проверяем что выбран файл
        TreeItem<String> selected = fileSystemTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Ошибка", "Выберите файл для скачивания");
            return;
        }

        // открываем диалог сохранения файла
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(selected.getValue());
        File file = fileChooser.showSaveDialog(mainContainer.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    // открываем SFTP канал
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();
                    
                    // скачиваем файл
                    String remotePath = buildPath(selected);
                    channel.get(remotePath, file.getAbsolutePath());
                    
                    // показываем сообщение об успехе
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Успех");
                        alert.setContentText("Файл успешно скачан");
                        alert.showAndWait();
                    });
                    
                    channel.disconnect();
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Ошибка скачивания", e.getMessage()));
                }
            }).start();
        }
    }

    // открываем терминал с SSH подключением
    @FXML
    private void openTerminal() {
        try {
            // берем параметры подключения из текущей сессии
            String host = sshSession.getHost();
            String username = sshSession.getUserName();
            int port = sshSession.getPort();
            
            // запускаем cmd.exe с командой ssh
            ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start",
                "cmd.exe",
                "/k",
                String.format("ssh %s@%s -p %d", username, host, port)
            );
            
            processBuilder.start();
            
        } catch (Exception e) {
            showError("Ошибка", "Не удалось открыть терминал: " + e.getMessage());
        }
    }

    // показываем окно с ошибкой
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // этот метод вызывается при закрытии окна
    public void shutdown() {
        if (monitoringTimer != null) {
            monitoringTimer.cancel();  // останавливаем таймер обновления статистики
        }
    }
} 
