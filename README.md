# Статистический анализатор

**Паттерн:** Каналы и фильтры (Pipes and Filters)  
**Вариант:** Анализ распределения  
**Стек:** Java 17 · JavaFX 21 · Apache POI · OpenCSV · Jackson · PDFBox

---

## Содержание

1. [Описание](#описание)
2. [Паттерн «Каналы и фильтры»](#паттерн-каналы-и-фильтры)
3. [Функционал](#функционал)
4. [Архитектура](#архитектура)
5. [Диаграммы](#диаграммы)
6. [Запуск](#запуск)
7. [Тесты](#тесты)
8. [Git-ветки и коммиты](#git-ветки-и-коммиты)

---

## Описание

Приложение загружает данные из файлов `.csv` / `.xlsx`, позволяет выбрать числовые колонки и запускает конвейер обработки: валидация → парсинг → сортировка → расчёт статистик → построение гистограммы с кривой нормального распределения. Результаты сохраняются в форматах PNG/SVG/PDF/HTML (график) и CSV/XLSX/JSON/Markdown (таблица метрик).

---

## Паттерн «Каналы и фильтры»

```mermaid
flowchart LR
    I([Ввод rawData])
    subgraph pipe["Канал передачи: StatResult"]
        V["ValidationFilter\nпроверка null / длины"]
        P["ParseFilter\nString → Double"]
        S["SortFilter\nсортировка"]
        SC["StatCalcFilter\nμ, σ, медиана, мода,\nасимметрия, эксцесс,\nCV, Q1/Q3/IQR, bins"]
        H["HistogramFilter\nкривая N(μ,σ), 200 точек"]
    end
    I --> V --> P --> S --> SC --> H --> R([StatResult])
    R --> UI["UI\nCanvas + TableView"]
    R --> E["ExportService"]
    E --> G["PNG · SVG · PDF · HTML"]
    E --> T["CSV · XLSX · JSON · MD"]
```

**Единый интерфейс фильтра:**
```java
interface Filter<T, R> {
    R execute(T input) throws FilterException;
}
```

**Канал передачи** — объект `StatResult`, который накапливает данные по мере прохождения через фильтры. Каждый фильтр получает `StatResult` на вход и возвращает обогащённый `StatResult`.

---

## Функционал

| Действие | Описание |
|---|---|
| Открыть файл | `.csv` (запятая или точка с запятой) / `.xlsx` / `.xls` |
| Выбрать колонки | Список с чекбоксами; несколько колонок одновременно |
| Настроить параметры | Число бинов (2–200), IQR-множитель, флаги отображения |
| Запустить конвейер | Последовательный запуск 5 фильтров с журналом выполнения |
| Гистограмма | Столбчатая + кривая нормального распределения (PDF) |
| Статистики | n, min/max, μ, σ, медиана, мода, асимметрия, эксцесс, CV, Q1/Q3/IQR |
| Переключение колонок | ComboBox для выбора отображаемой колонки |
| Сохранить график | PNG, SVG, PDF, HTML (с Chart.js) |
| Сохранить таблицу | CSV, XLSX (с листом частот), JSON, Markdown |
| Конфигурация | Сохранение / загрузка JSON-конфига |
| Справка | Формулы, инструкция, о программе (F1) |
| Журнал конвейера | Временны́е метки и статус каждого фильтра в UI |

---

## Архитектура

```
src/main/java/com/statanalyzer/
├── StatApp.java                    ← точка входа (Application.launch)
├── filter/
│   ├── Filter.java                 ← интерфейс Filter<T,R>
│   ├── FilterException.java        ← исключение фильтра
│   ├── ValidationFilter.java       ← проверка: не пусто, ≥3 значений
│   ├── ParseFilter.java            ← String → List<Double>
│   ├── SortFilter.java             ← сортировка по возрастанию
│   ├── StatCalcFilter.java         ← все статистики + частоты бинов
│   ├── HistogramFilter.java        ← точки кривой нормального распред.
│   └── ExportFilter.java           ← делегирует ExportService
├── model/
│   └── StatResult.java             ← канал передачи данных
├── pipeline/
│   ├── Pipeline.java               ← запуск цепочки фильтров
│   └── ExportService.java          ← экспорт в PNG/SVG/PDF/HTML/CSV/XLSX/JSON/MD
├── controller/
│   └── StatController.java         ← FXML-контроллер главного окна
└── config/
    └── AppConfig.java              ← конфигурация (сериализация в JSON)

src/main/resources/com/statanalyzer/
├── main-view.fxml                  ← главное окно
├── help-view.fxml                  ← окно справки
└── styles.css                      ← стили JavaFX

src/test/java/com/statanalyzer/
├── filter/
│   ├── ValidationFilterTest.java
│   ├── ParseFilterTest.java
│   └── StatCalcFilterTest.java
└── pipeline/
    └── PipelineIntegrationTest.java
```

---

## Диаграммы

### Use-case

```mermaid
flowchart LR
    U(("Пользователь"))
    subgraph sys["Статистический анализатор"]
        UC1["UC1: Загрузить файл\n.csv / .xlsx / .xls"]
        UC2["UC2: Выбрать колонки"]
        UC3["UC3: Настроить параметры"]
        UC4["UC4: Запустить анализ"]
        UC5["UC5: Сохранить результаты"]
        UC6["UC6: Управление конфигурацией"]
        UC7["UC7: Просмотр справки"]
    end
    U --> UC1 & UC2 & UC3 & UC4 & UC5 & UC6 & UC7
    UC1 -.->|include| UC2
    UC3 -.->|extend| UC4
    UC4 -.->|include| UC5
```

### Диаграмма классов

```mermaid
classDiagram
    class Filter {
        <<interface>>
        +execute(StatResult) StatResult
        +getName() String
    }
    class ValidationFilter
    class ParseFilter
    class SortFilter
    class StatCalcFilter
    class HistogramFilter
    class ExportFilter

    Filter <|.. ValidationFilter
    Filter <|.. ParseFilter
    Filter <|.. SortFilter
    Filter <|.. StatCalcFilter
    Filter <|.. HistogramFilter
    Filter <|.. ExportFilter

    class StatResult {
        +columnName: String
        +rawData: List~String~
        +parsedValues: List~Double~
        +sortedValues: List~Double~
        +n: int
        +mean: double
        +stdDev: double
        +median: double
        +skewness: double
        +kurtosis: double
        +coefficientOfVariation: double
        +q1: double
        +q3: double
        +iqr: double
        +binEdges: double[]
        +frequencies: int[]
        +normalCurveX: double[]
        +normalCurveY: double[]
        +pipelineLog: List~String~
        +valid: boolean
        +errorMessage: String
        +getStatisticsMap() Map~String, String~
        +addLog(String) void
    }

    class Pipeline {
        -filters: List~Filter~
        +addFilter(Filter) Pipeline
        +execute(StatResult) StatResult
    }

    class ExportService {
        +export(StatResult, File, ExportType, String) void
        +renderHistogramToImage(StatResult, int, int) BufferedImage
    }

    class AppConfig {
        +numBins: int
        +iqrMultiplier: double
        +showNormalCurve: boolean
        +showGrid: boolean
        +showValues: boolean
        +colorScheme: String
        +save(File) void
        +load(File)$ AppConfig
    }

    class StatController {
        -fileData: Map
        -results: Map
        -config: AppConfig
        +handleOpenFile() void
        +handleRunPipeline() void
        +handleSaveChart() void
        +handleSaveTable() void
    }

    Pipeline --> Filter : содержит
    Pipeline --> StatResult : обрабатывает
    ExportFilter --> ExportService : делегирует
    StatController --> Pipeline : создаёт
    StatController --> AppConfig : использует
    StatController --> ExportService : вызывает
```

### Диаграмма последовательностей

```mermaid
sequenceDiagram
    actor U as Пользователь
    participant C as StatController
    participant P as Pipeline
    participant V as ValidationFilter
    participant Pa as ParseFilter
    participant S as SortFilter
    participant SC as StatCalcFilter
    participant H as HistogramFilter
    participant E as ExportService

    U->>C: handleRunPipeline()
    loop Для каждой выбранной колонки
        C->>P: execute(StatResult)
        P->>V: execute(StatResult)
        V-->>P: StatResult
        P->>Pa: execute(StatResult)
        Pa-->>P: StatResult
        P->>S: execute(StatResult)
        S-->>P: StatResult
        P->>SC: execute(StatResult)
        SC-->>P: StatResult
        P->>H: execute(StatResult)
        H-->>P: StatResult
        P-->>C: StatResult (готов)
    end
    C->>C: drawHistogram()
    C->>C: updateStatsTable()
    C-->>U: UI обновлён

    opt Сохранение результатов
        U->>C: handleSaveChart() / handleSaveTable()
        C->>E: export(result, file, type, format)
        E-->>C: файл сохранён
        C-->>U: статус обновлён
    end
```

### Диаграмма активности (конвейер)

```mermaid
flowchart TD
    Start([Нажата кнопка Запустить]) --> Check{Колонки\nвыбраны?}
    Check -->|нет| Warn["Предупреждение:\nвыберите колонку"] --> End([Конец])
    Check -->|да| Loop["Для каждой\nвыбранной колонки"]

    Loop --> VF["ValidationFilter\nпроверка rawData"]
    VF --> VErr{Ошибка?}
    VErr -->|да| ErrLog["Лог: invalid\nStatResult.valid = false"]
    VErr -->|нет| PF["ParseFilter\nString → Double"]

    PF --> PErr{Ошибка?}
    PErr -->|да| ErrLog
    PErr -->|нет| SF["SortFilter\nCollections.sort"]

    SF --> SCF["StatCalcFilter\nμ, σ, медиана, мода\nасимметрия, эксцесс, CV\nQ1, Q3, IQR, bins"]
    SCF --> HF["HistogramFilter\nкривая N(μ,σ), 200 точек"]
    HF --> OkLog["Лог: success"]

    ErrLog --> Next{Ещё\nколонки?}
    OkLog --> Next
    Next -->|да| Loop
    Next -->|нет| UI["Обновить UI\nГистограмма · Таблица · Журнал"]
    UI --> End
```

---

## Запуск

**Требования:** JDK 17+, Maven 3.8+

```bash
# Клонировать и перейти в проект
git clone <url> && cd statistical-analyzer

# Компиляция и запуск
mvn clean javafx:run

# Только компиляция
mvn clean compile

# Запуск тестов
mvn test
```

**IntelliJ IDEA:** File → Open → выбрать папку проекта → Run `StatApp`

---

## Тесты

| Класс теста | Группа | Что тестируется |
|---|---|---|
| `ValidationFilterTest` | Unit | null/пустые данные, граница ≥3, лог |
| `ParseFilterTest` | Unit | запятая/точка, пропуск NaN/"abc", пробелы |
| `StatCalcFilterTest` | Unit | μ, σ², σ, медиана, min/max, сумма частот = n, асимметрия симм. ≈ 0 |
| `PipelineIntegrationTest` | Integration | полный конвейер, смешанные данные, отрицательные, журнал |

```bash
# Запуск всех тестов
mvn test

# Отчёт сохраняется в target/surefire-reports/
```

---

## Git-ветки и коммиты

```
main   — стабильная версия
mvp    — минимально рабочая версия (без экспорта SVG/PDF/HTML)
```

**Рекомендуемые коммиты:**

```
feat: add Filter interface and FilterException
feat: implement ValidationFilter and ParseFilter
feat: implement SortFilter and StatCalcFilter  
feat: implement HistogramFilter (normal curve computation)
feat: implement Pipeline with step logging
feat: add ExportService (PNG, SVG, PDF, HTML, CSV, XLSX, JSON, MD)
feat: add ExportFilter delegating to ExportService
feat: add AppConfig with JSON serialization
feat: add StatController with file loading (CSV/XLSX)
feat: add main-view.fxml with histogram canvas and stats table
feat: add help-view.fxml with formulas and instructions
feat: add CSS styles
test: add unit tests for ValidationFilter, ParseFilter, StatCalcFilter
test: add integration test for full pipeline
docs: add README with diagrams and formulas
```
