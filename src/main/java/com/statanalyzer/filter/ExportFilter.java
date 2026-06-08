package com.statanalyzer.filter;

import com.statanalyzer.model.StatResult;
import com.statanalyzer.pipeline.ExportService;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Фильтр 6: Экспорт результатов.
 * Вызывается отдельно (не в основном конвейере анализа), когда пользователь
 * явно запрашивает сохранение. Делегирует сохранение ExportService.
 * Если exportFile == null — пропускает без действия.
 */
public class ExportFilter implements Filter<StatResult, StatResult> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final File exportFile;
    private final String format;
    private final ExportService.ExportType type;

    public ExportFilter(File exportFile, String format, ExportService.ExportType type) {
        this.exportFile = exportFile;
        this.format = format;
        this.type = type;
    }

    @Override
    public StatResult execute(StatResult input) throws FilterException {
        if (exportFile == null) {
            log(input, "Файл экспорта не указан — пропуск.");
            return input;
        }

        log(input, "Экспорт → " + format.toUpperCase() + " : " + exportFile.getAbsolutePath());

        try {
            ExportService service = new ExportService();
            service.export(input, exportFile, type, format);
            log(input, "Экспорт успешно завершён: " + exportFile.getName());
        } catch (Exception e) {
            String msg = "Ошибка экспорта: " + e.getMessage();
            log(input, "ОШИБКА: " + msg);
            throw new FilterException(msg, e);
        }

        return input;
    }

    @Override
    public String getName() { return "ExportFilter[" + format.toUpperCase() + "]"; }

    private void log(StatResult result, String message) {
        result.addLog("[" + LocalTime.now().format(TIME_FMT) + "] [ExportFilter] " + message);
    }
}
