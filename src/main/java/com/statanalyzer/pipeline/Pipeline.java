package com.statanalyzer.pipeline;

import com.statanalyzer.filter.Filter;
import com.statanalyzer.filter.FilterException;
import com.statanalyzer.model.StatResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Конвейер (Pipeline) — последовательно запускает список фильтров.
 * Выход каждого фильтра становится входом следующего.
 * При ошибке валидации (result.isValid() == false) конвейер прерывается.
 */
public class Pipeline {

    private static final Logger LOGGER = Logger.getLogger(Pipeline.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final List<Filter<StatResult, StatResult>> filters = new ArrayList<>();

    public Pipeline addFilter(Filter<StatResult, StatResult> filter) {
        filters.add(filter);
        return this;
    }

    /**
     * Запускает все фильтры последовательно и возвращает итоговый StatResult.
     * Каждый шаг логируется: имя фильтра, время выполнения, статус.
     */
    public StatResult execute(StatResult input) {
        pipelineLog(input, "═══ Запуск конвейера (" + filters.size() + " фильтров) ═══");
        StatResult current = input;

        for (int i = 0; i < filters.size(); i++) {
            Filter<StatResult, StatResult> filter = filters.get(i);
            String stepLabel = String.format("[%d/%d] %s", i + 1, filters.size(), filter.getName());
            pipelineLog(current, "▶ " + stepLabel);

            long startMs = System.currentTimeMillis();
            try {
                current = filter.execute(current);
                long elapsed = System.currentTimeMillis() - startMs;

                if (!current.isValid()) {
                    pipelineLog(current, "✖ " + stepLabel + " — прерывание: " + current.getErrorMessage()
                        + " (" + elapsed + " мс)");
                    break;
                }

                pipelineLog(current, "✔ " + stepLabel + " — выполнен за " + elapsed + " мс");

            } catch (FilterException e) {
                long elapsed = System.currentTimeMillis() - startMs;
                current.setValid(false);
                current.setErrorMessage(e.getMessage());
                pipelineLog(current, "✖ " + stepLabel + " — исключение: " + e.getMessage()
                    + " (" + elapsed + " мс)");
                LOGGER.severe("Pipeline error in " + filter.getName() + ": " + e.getMessage());
                break;
            }
        }

        pipelineLog(current, "═══ Конвейер завершён (статус: " +
            (current.isValid() ? "OK" : "ОШИБКА") + ") ═══");
        return current;
    }

    private void pipelineLog(StatResult result, String msg) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] [Pipeline] " + msg;
        result.addLog(line);
        LOGGER.info(line);
    }
}
