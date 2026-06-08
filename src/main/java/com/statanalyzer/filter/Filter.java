package com.statanalyzer.filter;

/**
 * Единый интерфейс фильтра для архитектуры «Каналы и фильтры».
 * Каждый фильтр принимает данные типа T и возвращает данные типа R.
 */
public interface Filter<T, R> {

    /**
     * Выполняет преобразование данных.
     *
     * @param input входные данные
     * @return преобразованные данные
     * @throws FilterException при ошибке обработки
     */
    R execute(T input) throws FilterException;

    /** Возвращает человекочитаемое имя фильтра для журнала конвейера. */
    default String getName() {
        return getClass().getSimpleName();
    }
}
