# Phase 1 Data Model: Graficzna historia wypełnienia zbiornika i wywozów

Ta funkcja **nie wprowadza żadnych nowych tabel Room ani zmian schematu**. Wykorzystuje wyłącznie istniejące encje z 001-septic-tank-monitor (`MeterReadingEntity`, `PumpingEventEntity`, `TankConfigurationEntity`) jako źródło danych. Poniżej opisano jedną nową, obliczaną (nie przechowywaną) strukturę domenową potrzebną do zasilenia wykresu, analogiczną do istniejącego `UsageState` (patrz `domain/usage/UsageState.kt`).

## Nowa encja obliczana: HistoryPoint

Reprezentuje pojedynczy punkt na wykresie liniowym — albo odczyt licznika, albo zdarzenie wywozu — z już wyliczoną wartością wypełnienia zbiornika w tym momencie. Tworzona w nowej funkcji domenowej `domain/history/HistoryTrendCalculator.kt`, nieprzechowywana w bazie danych.

| Pole | Typ | Opis |
|---|---|---|
| `timestampMillis` | Long | Data/godzina punktu (odczytu lub wywozu) — oś X wykresu |
| `type` | Enum(`READING`, `PUMPING`) | Rozróżnia odczyt licznika od zdarzenia wywozu (FR-002: wymagane osobne oznaczenie kształtem/ikoną) |
| `fillLiters` | Long | Wartość wypełnienia w danym momencie = `valueLiters` odczytu minus `valueLiters` aktywnego odczytu bazowego (patrz reguła niżej); dla punktu typu `PUMPING` zawsze `0` (moment zresetowania punktu bazowego) |
| `fillPercentOfCapacity` | Double? | `fillLiters / capacityLiters * 100`, `null` gdy `TankConfigurationEntity.capacityLiters` nieskonfigurowane (FR-003/FR-004) |
| `sourceReadingId` | Long? | Dla `type=READING`: `MeterReadingEntity.id` tego odczytu. Dla `type=PUMPING`: `PumpingEventEntity.baselineReadingId` (id odczytu bazowego wywozu) — potrzebne, aby widok szczegółowy punktu (User Story 3) mógł pokazać powiązany odczyt bazowy bez dodatkowego zapytania, skoro `HistoryUiState` przechowuje wyłącznie `chartPoints`, a nie surowe listy odczytów/wywozów |
| `pumpingEventId` | Long? | Dla `type=PUMPING`: `PumpingEventEntity.id`. Dla `type=READING`: `null` |
| `readingSource` | ReadingSource? | Kopiowane z `MeterReadingEntity.source` dla punktów typu `READING` — do ewentualnego rozróżnienia OCR / ręczny wpis w widoku szczegółowym (User Story 3) |
| `isAnomalous` | Boolean | Kopiowane z `MeterReadingEntity.isAnomalous` dla `type=READING`; zawsze `false` dla `type=PUMPING` — do wizualnego oznaczenia nietypowego punktu (Edge Case: odczyt-anomalia) |

### Reguła wyliczania `fillLiters` (rdzeń `HistoryTrendCalculator`)

Dla każdego odczytu (`type=READING`), aktywny punkt bazowy to `valueLiters` odczytu wskazanego przez `baselineReadingId` **najpóźniejszego** `PumpingEventEntity`, którego `timestampMillis` jest **wcześniejszy lub równy** `timestampMillis` danego odczytu; jeśli żaden taki wywóz nie istnieje (odczyt sprzed pierwszego wywozu lub brak wywozów w ogóle), punktem bazowym jest `valueLiters` **pierwszego** zarejestrowanego odczytu w całej historii (ta sama reguła co w `UsageCalculator.calculate()`, zastosowana punkt po punkcie zamiast tylko do najnowszego odczytu).

```
fillLiters(reading) = max(0, reading.valueLiters - activeBaseline(reading.timestampMillis).valueLiters)
```

Punkty typu `PUMPING` mają `fillLiters = 0` — reprezentują moment zresetowania (nowy punkt bazowy zaczyna się od zera), spójnie z FR-006 z 001-septic-tank-monitor („zeruje wyświetlane zużycie").

### Walidacja / niezmienniki

- Lista `HistoryPoint` jest posortowana rosnąco po `timestampMillis` (Edge Case: kolejność chronologiczna zachowana niezależnie od kolejności wstawiania do bazy).
- `fillLiters` MUST być `>= 0` (przycinane do 0, tak jak w `UsageCalculator`, na wypadek anomalii z niższym odczytem niż poprzedni).
- Gdy `capacityLiters` (z `TankConfigurationEntity`) jest `null` lub `<= 0`, `fillPercentOfCapacity` MUST być `null` — UI prezentuje wtedy `fillLiters` (m³) zamiast wartości procentowej (FR-004).
- Pusta historia (brak odczytów i wywozów) → pusta lista `HistoryPoint`; UI odpowiada stanem pustym (FR-007).

## Relacje do istniejących encji (bez zmian)

```
TankConfigurationEntity (capacityLiters, warningThresholdPercent)
        │ (używane do fillPercentOfCapacity oraz linii progu ostrzegawczego na wykresie)
        ▼
HistoryTrendCalculator.calculate(readings: List<MeterReadingEntity>,
                                  pumpingEvents: List<PumpingEventEntity>,
                                  configuration: TankConfigurationEntity)
        │
        ▼
List<HistoryPoint>  →  HistoryUiState.chartPoints  →  Vico CartesianChart (HistoryScreen)
```

- **`MeterReadingEntity`** (bez zmian) → źródło punktów `type=READING`.
- **`PumpingEventEntity`** (bez zmian) → źródło punktów `type=PUMPING` oraz punktów bazowych używanych do liczenia `fillLiters`.
- **`TankConfigurationEntity`** (bez zmian) → źródło `capacityLiters` (do `fillPercentOfCapacity`) i `warningThresholdPercent` (do linii progu na wykresie, FR-009). Uwaga: obecny `HistoryViewModel` (patrz `ui/history/HistoryViewModel.kt`) nie obserwuje jeszcze `TankConfigurationRepository` — ta funkcja dodaje tę zależność, analogicznie do `HomeViewModel`.

## Zmiana istniejącej struktury UI (nieprzechowywana, tylko stan ekranu)

`HistoryUiState` (obecnie w `ui/history/HistoryViewModel.kt`) zostaje przeprojektowane:

**Przed** (tekstowy widok, do zastąpienia):
```kotlin
data class HistoryUiState(
    val readings: List<MeterReadingEntity> = emptyList(),
    val pumpingEvents: List<PumpingEventEntity> = emptyList(),
)
```

**Po**:
```kotlin
data class HistoryUiState(
    val chartPoints: List<HistoryPoint> = emptyList(),
    val warningThresholdPercent: Int? = null,   // null gdy capacityLiters nieskonfigurowane (FR-009)
    val isCapacityConfigured: Boolean = false,   // steruje % vs m³ (FR-003/FR-004)
)
```
