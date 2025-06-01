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

public class MainController {
    private final Session sshSession;
    private Timer monitoringTimer;
    private XYChart.Series<Number, Number> cpuSeries;
    private XYChart.Series<Number, Number> ramSeries;

    @FXML private LineChart<Number, Number> cpuChart;
    @FXML private LineChart<Number, Number> ramChart;
    @FXML private Label cpuLabel;
    @FXML private Label ramLabel;
    @FXML private Label diskLabel;
    @FXML private TreeView<String> fileSystemTree;
    @FXML private VBox mainContainer;

    // SVG пути для иконок
    private static final String FOLDER_ICON = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String FILE_ICON = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z";

    public MainController(Session sshSession) {
        this.sshSession = sshSession;
    }

    @FXML
    public void initialize() {
        setupCharts();
        setupMonitoring();
        setupFileSystem();
    }

    private void setupCharts() {
        cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("CPU");
        cpuChart.getData().add(cpuSeries);
        styleChart(cpuChart, "#3498db");

        ramSeries = new XYChart.Series<>();
        ramSeries.setName("RAM");
        ramChart.getData().add(ramSeries);
        styleChart(ramChart, "#2ecc71");
    }

    private void styleChart(LineChart<Number, Number> chart, String color) {
        // Настройка внешнего вида графика
        chart.setCreateSymbols(false); // Убираем точки на линии
        chart.setAnimated(false); // Отключаем анимацию для более плавной работы
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setAlternativeColumnFillVisible(false);
        chart.setAlternativeRowFillVisible(false);

        // Настройка осей
        NumberAxis xAxis = (NumberAxis) chart.getXAxis();
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        
        xAxis.setTickLabelsVisible(false); // Скрываем метки времени
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(100);
        yAxis.setTickUnit(20);
        
        // Применяем CSS стили
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

    private void setupMonitoring() {
        monitoringTimer = new Timer(true);
        monitoringTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    updateSystemStats();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 2000);
    }

    private void updateSystemStats() {
        try {
            ChannelExec channel = (ChannelExec) sshSession.openChannel("exec");
            String command = 
                "top -bn1 | grep '%Cpu' | awk '{print $2}' && " +
                "free | grep Mem | awk '{print $3/$2 * 100}' && " +
                "df -h / | tail -1 | awk '{print $3,$2,$5}'";
            channel.setCommand(command);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            channel.setOutputStream(output);
            channel.connect();

            while (!channel.isClosed()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

            String[] stats = output.toString().split("\n");
            channel.disconnect();

            if (stats.length >= 3) {
                double cpu = Double.parseDouble(stats[0]);
                double ram = Double.parseDouble(stats[1]);
                String[] diskStats = stats[2].split("\\s+");

                Platform.runLater(() -> {
                    updateChart(cpuSeries, cpu);
                    updateChart(ramSeries, ram);
                    
                    cpuLabel.setText(String.format("CPU: %.1f%%", cpu));
                    ramLabel.setText(String.format("RAM: %.1f%%", ram));
                    
                    if (diskStats.length >= 3) {
                        String used = diskStats[0];
                        String total = diskStats[1];
                        String percent = diskStats[2];
                        diskLabel.setText(String.format("Диск: %s/%s (%s)", used, total, percent));
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateChart(XYChart.Series<Number, Number> series, double value) {
        if (series.getData().size() > 50) {
            series.getData().remove(0);
        }
        series.getData().add(new XYChart.Data<>(series.getData().size(), value));
    }

    private void setupFileSystem() {
        refreshFileSystem();
        
        fileSystemTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> item = fileSystemTree.getSelectionModel().getSelectedItem();
                if (item != null) {
                    expandTreeItem(item);
                }
            }
        });
    }

    private TreeItem<String> createTreeItem(String name, boolean isDirectory) {
        TreeItem<String> item = new TreeItem<>(name);
        
        // Создаем иконку
        SVGPath icon = new SVGPath();
        icon.setContent(isDirectory ? FOLDER_ICON : FILE_ICON);
        icon.setFill(Color.valueOf(isDirectory ? "#f39c12" : "#7f8c8d"));
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);
        
        item.setGraphic(icon);
        return item;
    }

    private void loadDirectoryContents(ChannelSftp channel, TreeItem<String> parent, String path) {
        try {
            Vector<ChannelSftp.LsEntry> list = channel.ls(path);
            
            // Сортируем файлы: сначала папки, потом файлы
            List<ChannelSftp.LsEntry> sortedList = new ArrayList<>(list);
            sortedList.sort((a, b) -> {
                boolean aDir = a.getAttrs().isDir();
                boolean bDir = b.getAttrs().isDir();
                if (aDir && !bDir) return -1;
                if (!aDir && bDir) return 1;
                return a.getFilename().compareToIgnoreCase(b.getFilename());
            });

            for (ChannelSftp.LsEntry entry : sortedList) {
                String filename = entry.getFilename();
                if (!filename.equals(".") && !filename.equals("..")) {
                    boolean isDirectory = entry.getAttrs().isDir();
                    TreeItem<String> item = createTreeItem(filename, isDirectory);
                    parent.getChildren().add(item);
                    
                    // Если это директория, добавляем заглушку
                    if (isDirectory) {
                        item.getChildren().add(createTreeItem("", false));
                    }
                }
            }
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void refreshFileSystem() {
        new Thread(() -> {
            try {
                ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                channel.connect();

                // Начинаем с домашней директории
                String homeDir = channel.getHome();
                TreeItem<String> root = createTreeItem(homeDir, true);
                
                // Загружаем содержимое корневой директории
                loadDirectoryContents(channel, root, homeDir);

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

    private void expandTreeItem(TreeItem<String> item) {
        if (item.getChildren().size() == 1 && item.getChildren().get(0).getValue().isEmpty()) {
            new Thread(() -> {
                try {
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();

                    String path = buildPath(item);
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

    private String buildPath(TreeItem<String> item) {
        List<String> parts = new ArrayList<>();
        TreeItem<String> current = item;
        
        while (current != null) {
            if (current.getValue() != null && !current.getValue().isEmpty()) {
                parts.add(0, current.getValue());
            }
            current = current.getParent();
        }
        
        String path = String.join("/", parts);
        return path.startsWith("/") ? path : "/" + path;
    }

    @FXML
    private void uploadFile() {
        TreeItem<String> selected = fileSystemTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Ошибка", "Выберите папку для загрузки файла");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(mainContainer.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();

                    String remotePath = buildPath(selected);
                    if (!remotePath.endsWith("/")) {
                        remotePath += "/";
                    }

                    channel.put(new FileInputStream(file), remotePath + file.getName());
                    
                    Platform.runLater(() -> {
                        expandTreeItem(selected); // Обновляем содержимое текущей папки
                    });
                    
                    channel.disconnect();
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Ошибка загрузки", e.getMessage()));
                }
            }).start();
        }
    }

    @FXML
    private void downloadFile() {
        TreeItem<String> selected = fileSystemTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Ошибка", "Выберите файл для скачивания");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(selected.getValue());
        File file = fileChooser.showSaveDialog(mainContainer.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    ChannelSftp channel = (ChannelSftp) sshSession.openChannel("sftp");
                    channel.connect();
                    
                    String remotePath = buildPath(selected);
                    channel.get(remotePath, file.getAbsolutePath());
                    
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

    @FXML
    private void openTerminal() {
        try {
            String host = sshSession.getHost();
            String username = sshSession.getUserName();
            int port = sshSession.getPort();
            
            // Создаем ProcessBuilder с командой для cmd
            ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "start",
                "cmd.exe",
                "/k",
                String.format("ssh %s@%s -p %d", username, host, port)
            );
            
            // Запускаем процесс
            processBuilder.start();
            
        } catch (Exception e) {
            showError("Ошибка", "Не удалось открыть терминал: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (monitoringTimer != null) {
            monitoringTimer.cancel();
        }
    }
} 