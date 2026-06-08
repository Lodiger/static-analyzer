package com.statanalyzer.controller;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.statanalyzer.config.AppConfig;
import com.statanalyzer.filter.*;
import com.statanalyzer.model.StatResult;
import com.statanalyzer.pipeline.ExportService;
import com.statanalyzer.pipeline.Pipeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.*;
import java.util.*;

public class StatController {

    // ── FXML-поля ─────────────────────────────────────────────────────────────

    @FXML private Label         fileInfoLabel;
    @FXML private Label         statusLabel;
    @FXML private ListView<String> columnListView;
    @FXML private Spinner<Integer> numBinsSpinner;
    @FXML private Spinner<Double>  iqrSpinner;
    @FXML private CheckBox      showNormalCurveCheck;
    @FXML private CheckBox      showOutliersCheck;
    @FXML private CheckBox      showGridCheck;
    @FXML private Button        runButton;
    @FXML private Canvas        histogramCanvas;
    @FXML private TableView<String[]> statsTable;
    @FXML private TableColumn<String[], String> metricColumn;
    @FXML private TableColumn<String[], String> valueColumn;
    @FXML private TextArea      pipelineLogArea;
    @FXML private ComboBox<String> columnSelector;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label         columnCountLabel;

    // ── Состояние ─────────────────────────────────────────────────────────────

    private Map<String, List<String>> fileData = new LinkedHashMap<>();
    private Map<String, StatResult>   results  = new LinkedHashMap<>();
    private AppConfig config = new AppConfig();
    private File currentFile;

    // CheckBox-состояния для выбора колонок
    private final Map<String, BooleanProperty> columnChecks = new LinkedHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Инициализация
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Таблица статистик
        metricColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[0]));
        valueColumn .setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[1]));
        statsTable.setPlaceholder(new Label("Запустите конвейер для отображения статистик"));

        // Спиннеры
        numBinsSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 200, config.getNumBins(), 1));
        iqrSpinner.setValueFactory(
            new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0, config.getIqrMultiplier(), 0.1));

        // CheckBox-состояния
        showNormalCurveCheck.setSelected(config.isShowNormalCurve());
        showOutliersCheck   .setSelected(config.isShowOutliers());
        showGridCheck       .setSelected(config.isShowGrid());

        // Список колонок с чекбоксами
        columnListView.setCellFactory(CheckBoxListCell.forListView(
            item -> columnChecks.computeIfAbsent(item,
                k -> new SimpleBooleanProperty(false))));

        // Выбор колонки для просмотра
        columnSelector.setOnAction(e -> showResultForColumn(columnSelector.getValue()));

        progressIndicator.setVisible(false);
        statusLabel.setText("Загрузите файл .csv или .xlsx");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Меню → Файл
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleOpenFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Открыть файл данных");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Файлы данных", "*.csv", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("CSV", "*.csv"),
            new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls")
        );
        if (currentFile != null) fc.setInitialDirectory(currentFile.getParentFile());
        else if (!config.getLastDirectory().isEmpty())
            fc.setInitialDirectory(new File(config.getLastDirectory()));

        File file = fc.showOpenDialog(getStage());
        if (file == null) return;

        currentFile = file;
        config.setLastDirectory(file.getParent());
        loadFile(file);
    }

    private void loadFile(File file) {
        setStatus("Загрузка: " + file.getName() + "…");
        progressIndicator.setVisible(true);
        fileData.clear();
        results.clear();
        columnChecks.clear();

        Task<Map<String, List<String>>> task = new Task<>() {
            @Override
            protected Map<String, List<String>> call() throws Exception {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".csv")) return loadCsv(file);
                else if (name.endsWith(".xlsx") || name.endsWith(".xls")) return loadExcel(file);
                else throw new IllegalArgumentException("Неподдерживаемый формат: " + file.getName());
            }
        };

        task.setOnSucceeded(e -> {
            fileData = task.getValue();
            progressIndicator.setVisible(false);
            populateColumns();
            fileInfoLabel.setText(file.getName() + " [" + fileData.keySet().stream().findFirst()
                .map(k -> fileData.get(k).size() + " строк").orElse("?") + "]");
            setStatus("Файл загружен. Выберите колонки и запустите анализ.");
            pipelineLogArea.clear();
        });

        task.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            showError("Ошибка загрузки файла", task.getException().getMessage());
            setStatus("Ошибка загрузки файла.");
        });

        new Thread(task, "file-loader").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Загрузка CSV / Excel
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, List<String>> loadCsv(File file) throws IOException, CsvException {
        Map<String, List<String>> data = new LinkedHashMap<>();
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) throw new IOException("CSV-файл пуст");

            // Определяем разделитель по первой строке
            String[] headers = rows.get(0);
            boolean useSemicolon = headers.length == 1 && headers[0].contains(";");
            if (useSemicolon) {
                headers = headers[0].split(";", -1);
            }

            for (String h : headers) data.put(h.trim(), new ArrayList<>());
            String[] keys = data.keySet().toArray(new String[0]);

            for (int r = 1; r < rows.size(); r++) {
                String[] row = rows.get(r);
                // Если файл с ';', каждая строка тоже придёт как один элемент — разбиваем
                if (useSemicolon && row.length == 1) {
                    row = row[0].split(";", -1);
                }
                for (int c = 0; c < keys.length && c < row.length; c++) {
                    data.get(keys[c]).add(row[c].trim());
                }
            }
        }
        return data;
    }

    private Map<String, List<String>> loadExcel(File file) throws IOException {
        Map<String, List<String>> data = new LinkedHashMap<>();
        // WorkbookFactory автоматически определяет формат (XLS/XLSX) и кидает только IOException
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            Iterator<Row> rowIt = sheet.iterator();
            if (!rowIt.hasNext()) throw new IOException("Файл Excel пуст");

            // Используем DataFormatter чтобы безопасно читать заголовки любого типа
            DataFormatter fmt = new DataFormatter();
            Row header = rowIt.next();
            List<String> headers = new ArrayList<>();
            for (Cell cell : header) {
                String name = fmt.formatCellValue(cell).trim();
                if (name.isEmpty()) name = "Колонка_" + cell.getColumnIndex();
                headers.add(name);
                data.put(name, new ArrayList<>());
            }

            while (rowIt.hasNext()) {
                Row row = rowIt.next();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = (cell == null) ? "" : fmt.formatCellValue(cell).trim();
                    data.get(headers.get(c)).add(val);
                }
            }
        }
        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Заполнение списка колонок
    // ─────────────────────────────────────────────────────────────────────────

    private void populateColumns() {
        columnChecks.clear();
        ObservableList<String> cols = FXCollections.observableArrayList(fileData.keySet());
        columnListView.setItems(cols);
        if (!cols.isEmpty()) {
            columnChecks.put(cols.get(0), new SimpleBooleanProperty(true));
        }
        columnCountLabel.setText(cols.size() + " колонок");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Запуск конвейера
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleRunPipeline() {
        List<String> selected = getSelectedColumns();
        if (selected.isEmpty()) {
            showWarning("Нет выбранных колонок", "Отметьте хотя бы одну колонку в списке.");
            return;
        }
        if (fileData.isEmpty()) {
            showWarning("Файл не загружен", "Сначала загрузите файл данных.");
            return;
        }

        applyConfigFromUI();

        runButton.setDisable(true);
        progressIndicator.setVisible(true);
        pipelineLogArea.clear();
        results.clear();

        setStatus("Выполняется анализ " + selected.size() + " колонок…");

        Task<Map<String, StatResult>> task = new Task<>() {
            @Override
            protected Map<String, StatResult> call() {
                Map<String, StatResult> res = new LinkedHashMap<>();
                for (String col : selected) {
                    StatResult input = new StatResult();
                    input.setColumnName(col);
                    input.setRawData(fileData.getOrDefault(col, List.of()));
                    input.setNumBins(config.getNumBins());
                    input.setShowNormalCurve(config.isShowNormalCurve());
                    input.setShowOutliers(config.isShowOutliers());
                    input.setIqrMultiplier(config.getIqrMultiplier());

                    Pipeline pipeline = new Pipeline()
                        .addFilter(new ValidationFilter())
                        .addFilter(new ParseFilter())
                        .addFilter(new SortFilter())
                        .addFilter(new StatCalcFilter())
                        .addFilter(new HistogramFilter());

                    StatResult output = pipeline.execute(input);
                    res.put(col, output);
                }
                return res;
            }
        };

        task.setOnSucceeded(e -> {
            results = task.getValue();
            runButton.setDisable(false);
            progressIndicator.setVisible(false);

            // Обновляем ComboBox с результатами
            columnSelector.setItems(FXCollections.observableArrayList(results.keySet()));
            if (!results.isEmpty()) {
                columnSelector.setValue(results.keySet().iterator().next());
                showResultForColumn(columnSelector.getValue());
            }

            // Лог конвейера (все колонки)
            StringBuilder log = new StringBuilder();
            for (StatResult r : results.values()) {
                for (String line : r.getPipelineLog()) log.append(line).append("\n");
                log.append("\n");
            }
            pipelineLogArea.setText(log.toString());
            pipelineLogArea.setScrollTop(Double.MAX_VALUE);

            long errCount = results.values().stream().filter(r -> !r.isValid()).count();
            if (errCount == 0) {
                setStatus("Анализ завершён: " + results.size() + " колонок обработано.");
            } else {
                setStatus("Анализ завершён с ошибками: " + errCount + " из " + results.size() + " колонок.");
            }
        });

        task.setOnFailed(e -> {
            runButton.setDisable(false);
            progressIndicator.setVisible(false);
            showError("Ошибка конвейера", task.getException().getMessage());
            setStatus("Конвейер завершился с ошибкой.");
        });

        new Thread(task, "pipeline-thread").start();
    }

    private List<String> getSelectedColumns() {
        List<String> selected = new ArrayList<>();
        for (String col : fileData.keySet()) {
            BooleanProperty prop = columnChecks.get(col);
            if (prop != null && prop.get()) selected.add(col);
        }
        return selected;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Отображение результатов
    // ─────────────────────────────────────────────────────────────────────────

    private void showResultForColumn(String columnName) {
        if (columnName == null || !results.containsKey(columnName)) return;
        StatResult r = results.get(columnName);

        if (!r.isValid()) {
            setStatus("Ошибка анализа «" + columnName + "»: " + r.getErrorMessage());
            clearChart();
            return;
        }

        drawHistogram(r);
        updateStatsTable(r);
        setStatus("Просмотр: «" + columnName + "» | n=" + r.getN()
            + " | μ=" + String.format("%.4f", r.getMean())
            + " | σ=" + String.format("%.4f", r.getStdDev()));
    }

    private void drawHistogram(StatResult result) {
        GraphicsContext gc = histogramCanvas.getGraphicsContext2D();
        double W = histogramCanvas.getWidth();
        double H = histogramCanvas.getHeight();

        gc.clearRect(0, 0, W, H);

        // Фон
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, W, H);

        int[] freq    = result.getFrequencies();
        double[] edges = result.getBinEdges();
        int numBins   = freq.length;
        if (numBins == 0) return;

        double margin = 60;
        double chartW = W - 2 * margin;
        double chartH = H - 2 * margin;

        double maxY = 0;
        for (int f : freq) if (f > maxY) maxY = f;
        if (result.isShowNormalCurve() && result.getNormalCurveY().length > 0)
            for (double y : result.getNormalCurveY()) if (y > maxY) maxY = y;
        if (maxY == 0) maxY = 1;

        double xMin = edges[0], xMax = edges[numBins];
        double xRange = xMax - xMin;
        if (xRange == 0) xRange = 1;

        double scaleX = chartW / xRange;
        double scaleY = chartH / maxY;

        // Сетка
        if (config.isShowGrid()) {
            gc.setStroke(Color.LIGHTGRAY);
            gc.setLineWidth(0.5);
            int yTicks = 5;
            for (int t = 1; t <= yTicks; t++) {
                double yPx = margin + chartH - (chartH * t / yTicks);
                gc.strokeLine(margin, yPx, margin + chartW, yPx);
            }
        }

        // Столбцы гистограммы
        for (int i = 0; i < numBins; i++) {
            double x  = margin + (edges[i] - xMin) * scaleX;
            double bw = (edges[i + 1] - edges[i]) * scaleX - 1;
            double h  = freq[i] * scaleY;
            double y  = margin + chartH - h;

            gc.setFill(Color.STEELBLUE.deriveColor(0, 1, 1, 0.75));
            gc.fillRect(x, y, Math.max(1, bw), h);
            gc.setStroke(Color.STEELBLUE.darker());
            gc.setLineWidth(0.8);
            gc.strokeRect(x, y, Math.max(1, bw), h);

            // Значения над столбцами
            if (config.isShowValues() && freq[i] > 0) {
                gc.setFill(Color.DARKSLATEGRAY);
                gc.fillText(String.valueOf(freq[i]), x + bw / 2 - 5, y - 3);
            }
        }

        // Кривая нормального распределения
        if (result.isShowNormalCurve() && result.getNormalCurveX().length > 1) {
            double[] cx = result.getNormalCurveX();
            double[] cy = result.getNormalCurveY();
            gc.setStroke(Color.RED);
            gc.setLineWidth(2.0);
            gc.beginPath();
            gc.moveTo(margin + (cx[0] - xMin) * scaleX,
                      margin + chartH - cy[0] * scaleY);
            for (int i = 1; i < cx.length; i++) {
                gc.lineTo(margin + (cx[i] - xMin) * scaleX,
                          Math.max(margin, margin + chartH - cy[i] * scaleY));
            }
            gc.stroke();
        }

        // Оси
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        gc.strokeLine(margin, margin, margin, margin + chartH);
        gc.strokeLine(margin, margin + chartH, margin + chartW, margin + chartH);

        // Метки Y
        gc.setFill(Color.DARKGRAY);
        int yTicks = 5;
        for (int t = 0; t <= yTicks; t++) {
            double yPx  = margin + chartH - (chartH * t / yTicks);
            double yVal = maxY * t / yTicks;
            gc.strokeLine(margin - 4, yPx, margin, yPx);
            String lbl = yVal < 10 ? String.format("%.1f", yVal) : String.format("%.0f", yVal);
            gc.fillText(lbl, margin - 40, yPx + 4);
        }

        // Метки X
        int step = Math.max(1, numBins / 8);
        for (int i = 0; i <= numBins; i += step) {
            int idx = Math.min(i, numBins);
            double xPx = margin + (edges[idx] - xMin) * scaleX;
            gc.strokeLine(xPx, margin + chartH, xPx, margin + chartH + 4);
            gc.fillText(String.format("%.2f", edges[idx]), xPx - 14, margin + chartH + 15);
        }

        // Подписи осей
        gc.setFill(Color.DIMGRAY);
        gc.fillText("Частота", 5, margin - 5);
        gc.fillText("Значение", margin + chartW / 2 - 20, H - 5);

        // Заголовок
        gc.setFill(Color.DARKSLATEGRAY);
        gc.setFont(javafx.scene.text.Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, 14));
        gc.fillText("Гистограмма: «" + result.getColumnName() + "»  "
            + "[n=" + result.getN() + ", μ=" + String.format("%.4f", result.getMean())
            + ", σ=" + String.format("%.4f", result.getStdDev()) + "]",
            margin, margin - 12);

        // Легенда
        gc.setFill(Color.STEELBLUE.deriveColor(0, 1, 1, 0.75));
        gc.fillRect(W - 160, margin + 10, 14, 14);
        gc.setFill(Color.DARKSLATEGRAY);
        gc.setFont(javafx.scene.text.Font.font("SansSerif", 11));
        gc.fillText("Гистограмма", W - 142, margin + 22);
        if (result.isShowNormalCurve()) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.strokeLine(W - 160, margin + 35, W - 146, margin + 35);
            gc.setFill(Color.DARKSLATEGRAY);
            gc.fillText("Норм. распред.", W - 142, margin + 39);
        }
    }

    private void clearChart() {
        GraphicsContext gc = histogramCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, histogramCanvas.getWidth(), histogramCanvas.getHeight());
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, histogramCanvas.getWidth(), histogramCanvas.getHeight());
    }

    private void updateStatsTable(StatResult result) {
        ObservableList<String[]> rows = FXCollections.observableArrayList();
        for (Map.Entry<String, String> e : result.getStatisticsMap().entrySet()) {
            rows.add(new String[]{e.getKey(), e.getValue()});
        }
        statsTable.setItems(rows);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сохранение графика
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleSaveChart() {
        StatResult result = getCurrentResult();
        if (result == null) { showWarning("Нет данных", "Сначала запустите анализ."); return; }

        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить график");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PNG",  "*.png"),
            new FileChooser.ExtensionFilter("SVG",  "*.svg"),
            new FileChooser.ExtensionFilter("PDF",  "*.pdf"),
            new FileChooser.ExtensionFilter("HTML", "*.html")
        );
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;

        String ext = getExtension(file.getName());
        progressIndicator.setVisible(true);
        setStatus("Сохранение графика → " + file.getName() + "…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Для всех форматов используем Java2D рендеринг (не требует FX-потока)
                ExportService svc = new ExportService();
                svc.export(result, file, ExportService.ExportType.CHART, ext);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            setStatus("График сохранён: " + file.getName());
        });
        task.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            showError("Ошибка экспорта", task.getException().getMessage());
        });

        new Thread(task, "export-chart").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сохранение таблицы
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleSaveTable() {
        StatResult result = getCurrentResult();
        if (result == null) { showWarning("Нет данных", "Сначала запустите анализ."); return; }

        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить таблицу статистик");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV",      "*.csv"),
            new FileChooser.ExtensionFilter("Excel",    "*.xlsx"),
            new FileChooser.ExtensionFilter("JSON",     "*.json"),
            new FileChooser.ExtensionFilter("Markdown", "*.md")
        );
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;

        String ext = getExtension(file.getName());
        progressIndicator.setVisible(true);
        setStatus("Сохранение таблицы → " + file.getName() + "…");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ExportService svc = new ExportService();
                svc.export(result, file, ExportService.ExportType.TABLE, ext);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            progressIndicator.setVisible(false);
            setStatus("Таблица сохранена: " + file.getName());
        });
        task.setOnFailed(e -> {
            progressIndicator.setVisible(false);
            showError("Ошибка экспорта", task.getException().getMessage());
        });

        new Thread(task, "export-table").start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Конфигурация
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleSaveConfig() {
        applyConfigFromUI();
        FileChooser fc = new FileChooser();
        fc.setTitle("Сохранить конфигурацию");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        fc.setInitialFileName("stat-config.json");
        File file = fc.showSaveDialog(getStage());
        if (file == null) return;
        try {
            config.save(file);
            setStatus("Конфигурация сохранена: " + file.getName());
        } catch (Exception e) {
            showError("Ошибка сохранения конфигурации", e.getMessage());
        }
    }

    @FXML
    public void handleLoadConfig() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Загрузить конфигурацию");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showOpenDialog(getStage());
        if (file == null) return;
        try {
            config = AppConfig.load(file);
            applyConfigToUI();
            setStatus("Конфигурация загружена: " + file.getName());
        } catch (Exception e) {
            showError("Ошибка загрузки конфигурации", e.getMessage());
        }
    }

    private void applyConfigFromUI() {
        config.setNumBins(numBinsSpinner.getValue());
        config.setIqrMultiplier(iqrSpinner.getValue());
        config.setShowNormalCurve(showNormalCurveCheck.isSelected());
        config.setShowOutliers(showOutliersCheck.isSelected());
        config.setShowGrid(showGridCheck.isSelected());
    }

    private void applyConfigToUI() {
        numBinsSpinner.getValueFactory().setValue(config.getNumBins());
        iqrSpinner.getValueFactory().setValue(config.getIqrMultiplier());
        showNormalCurveCheck.setSelected(config.isShowNormalCurve());
        showOutliersCheck.setSelected(config.isShowOutliers());
        showGridCheck.setSelected(config.isShowGrid());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Справка
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleShowHelp() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/statanalyzer/help-view.fxml"));
            Scene scene = new Scene(loader.load(), 700, 600);
            Stage helpStage = new Stage();
            helpStage.setTitle("Справка — Формулы и инструкция");
            helpStage.setScene(scene);
            helpStage.initModality(Modality.NONE);
            helpStage.show();
        } catch (Exception e) {
            showError("Ошибка", "Не удалось открыть справку: " + e.getMessage());
        }
    }

    @FXML
    public void handleExit() {
        Platform.exit();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Выбор всех / снятие всех колонок
    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    public void handleSelectAll() {
        for (String col : fileData.keySet()) {
            columnChecks.computeIfAbsent(col, k -> new SimpleBooleanProperty(false)).set(true);
        }
        columnListView.refresh();
    }

    @FXML
    public void handleDeselectAll() {
        columnChecks.values().forEach(p -> p.set(false));
        columnListView.refresh();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Утилиты
    // ─────────────────────────────────────────────────────────────────────────

    private StatResult getCurrentResult() {
        String col = columnSelector.getValue();
        if (col == null || !results.containsKey(col)) return null;
        StatResult r = results.get(col);
        return r.isValid() ? r : null;
    }

    private Stage getStage() {
        return (Stage) runButton.getScene().getWindow();
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
