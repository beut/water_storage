---

description: "Task list template for feature implementation"
---

# Tasks: Graficzna historia wypełnienia zbiornika i wywozów

**Input**: Design documents from `/specs/002-history-visual-redesign/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Foundational unit tests for `HistoryTrendCalculator` są uwzględnione, ponieważ `quickstart.md` (Faza 1) jawnie odwołuje się do ich uruchomienia jako sposobu weryfikacji reguły przeliczania wypełnienia; UI (Compose) nie ma dodatkowych testów instrumentalnych, bo nie były jawnie wymagane w spec.md.

**Organization**: Zadania pogrupowane wg User Story ze spec.md (US1–US4), aby każdą historię dało się zaimplementować i przetestować niezależnie.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Może być wykonane równolegle (inny plik, brak zależności od niedokończonych zadań)
- **[Story]**: Do której User Story należy zadanie (US1–US4)
- Ścieżki plików są bezwzględne względem korzenia repozytorium (`android/...`)

## Path Conventions

Pojedynczy moduł mobilny Android: `android/app/src/main/kotlin/pl/watershed/septictank/...` (implementacja), `android/app/src/test/kotlin/pl/watershed/septictank/...` (testy jednostkowe) — zgodnie ze strukturą z `plan.md`.

---

## Phase 1: Setup

**Purpose**: Dodanie nowej zależności potrzebnej do wykresu (research.md → decyzja 1)

- [X] T001 Dodać zależność `implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")` w `android/app/build.gradle.kts`, obok istniejącego bloku zależności Compose (research.md → Uwaga z implementacji: wersja 1.15.0 dobrana tak, by wymagać dokładnie tego samego `compose-bom 2024.06.00`, co już używany w projekcie — nowsze wersje Vico wymagają `compileSdk`/AGP wykraczających poza zakres tej funkcji; zweryfikowano `./gradlew :app:testDebugUnitTest` buduje się bez błędów)

**Checkpoint**: Projekt buduje się z nową zależnością (`./gradlew :app:assembleDebug`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wspólna logika domenowa i stan ekranu wymagane przez WSZYSTKIE User Stories — żadna historia nie może być zaimplementowana przed ukończeniem tej fazy

**⚠️ CRITICAL**: Żadna praca nad User Story nie może się zacząć, zanim ta faza nie zostanie ukończona

- [X] T002 [P] Utworzyć `HistoryPoint` (data class + `enum class HistoryPointType { READING, PUMPING }`) w `android/app/src/main/kotlin/pl/watershed/septictank/domain/history/HistoryPoint.kt` z polami dokładnie wg `data-model.md` → sekcja "Nowa encja obliczana: HistoryPoint": `timestampMillis: Long`, `type: HistoryPointType`, `fillLiters: Long`, `fillPercentOfCapacity: Double?`, `sourceReadingId: Long?`, `pumpingEventId: Long?`, `readingSource: ReadingSource?`, `isAnomalous: Boolean`
- [X] T003 Zaimplementować `HistoryTrendCalculator.calculate(readings: List<MeterReadingEntity>, pumpingEvents: List<PumpingEventEntity>, configuration: TankConfigurationEntity): List<HistoryPoint>` w `android/app/src/main/kotlin/pl/watershed/septictank/domain/history/HistoryTrendCalculator.kt` (zależy od T002), implementując regułę z `data-model.md` → "Reguła wyliczania `fillLiters`": dla każdego odczytu aktywny punkt bazowy to `valueLiters` odczytu wskazanego przez `baselineReadingId` najpóźniejszego `PumpingEventEntity` z `timestampMillis <= reading.timestampMillis`, a jeśli brak takiego wywozu — `valueLiters` pierwszego zarejestrowanego odczytu w całej historii; `fillLiters = max(0, reading.valueLiters - baseline.valueLiters)`; punkty typu `PUMPING` MUST mieć `fillLiters = 0`; `fillPercentOfCapacity` MUST być `null`, gdy `configuration.capacityLiters` jest `null` lub `<= 0`; wynikowa lista MUST być posortowana rosnąco po `timestampMillis`
- [X] T004 [P] Testy jednostkowe `HistoryTrendCalculator` w `android/app/src/test/kotlin/pl/watershed/septictank/domain/history/HistoryTrendCalculatorTest.kt` (zależy od T003), pokrywające: brak wywozów → baseline to pierwszy odczyt; jeden cykl wywóz→odczyty; wiele cykli wywozów pod rząd; odczyt niższy niż baseline → `fillLiters` przycięte do 0 (nie ujemne); `capacityLiters == null` → `fillPercentOfCapacity == null`; punkt `PUMPING` zawsze ma `fillLiters == 0`; wynik posortowany chronologicznie niezależnie od kolejności danych wejściowych (quickstart.md → "Testy jednostkowe")
- [X] T005 Przeprojektować `HistoryUiState` i `HistoryViewModel` w `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryViewModel.kt` (zależy od T003): wstrzyknąć dodatkowo `TankConfigurationRepository` (obok istniejących `MeterReadingRepository`, `PumpingEventRepository`), połączyć trzy `Flow` (`observeHistory()` x2 + `observe()` konfiguracji) przez `combine`, zmapować przez `HistoryTrendCalculator.calculate(...)` na nowy `HistoryUiState(chartPoints: List<HistoryPoint>, warningThresholdPercent: Int?, isCapacityConfigured: Boolean)` dokładnie wg `data-model.md` → sekcja "Zmiana istniejącej struktury UI" (usunąć stare pola `readings`/`pumpingEvents`); zaktualizować konstruktor `HistoryViewModel` w `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryScreen.kt` (fabryka `viewModelFactory`), przekazując `container.tankConfigurationRepository`

**Checkpoint**: Logika domenowa gotowa i przetestowana; `HistoryViewModel` udostępnia gotowe do wykresu `chartPoints` — implementacja User Stories może się zacząć

---

## Phase 3: User Story 1 - Wizualny trend wypełnienia zbiornika (Priority: P1) 🎯 MVP

**Goal**: Zakładka Historia pokazuje wykres liniowy trendu wypełnienia zbiornika (% pojemności lub m³) zamiast list tekstowych

**Independent Test**: Zarejestrować kilka odczytów licznika w różnym czasie i sprawdzić, że zakładka Historia prezentuje je jako wykres liniowy uporządkowany chronologicznie, z wartościami w % (gdy pojemność skonfigurowana) lub w m³ (gdy nie)

### Implementation for User Story 1

- [X] T006 [US1] Zastąpić dwie `LazyColumn` w `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryScreen.kt` jednym `Chart`/`lineChart` (Vico 1.15.0 — nazwy API skorygowane względem pierwotnego planu, patrz research.md → Uwaga z implementacji), renderującym `state.chartPoints` posortowane po `timestampMillis` (jako dni od pierwszego punktu, oś X) i `fillPercentOfCapacity` (gdy `isCapacityConfigured`) lub `fillLiters` (gdy nie) na osi Y (FR-001; Clarifications Session 2026-09-22: forma wykresu liniowego)
- [X] T007 [US1] Skonfigurować `AxisValueFormatter` osi Y (`rememberStartAxis`) w `HistoryScreen.kt`, przełączający etykiety między wartością procentową (`"%.0f%%"`) gdy `state.isCapacityConfigured == true`, a wartością w m³ (`"%.3f m³"`, dzieląc `fillLiters` przez 1000.0) gdy `false` (FR-003, FR-004)
- [X] T008 [US1] Dodać w `HistoryScreen.kt` komunikat informacyjny (`Text`, kolor `MaterialTheme.colorScheme.tertiary`, wzorem istniejącego komunikatu w `HomeScreen.kt`) widoczny nad wykresem, gdy `state.isCapacityConfigured == false`, informujący że wypełnienie procentowe wymaga skonfigurowania pojemności zbiornika w Ustawieniach (FR-004)
- [X] T009 [US1] Dodać w `HistoryScreen.kt` `ThresholdLine` (dekoracja Vico) na poziomie `state.warningThresholdPercent`, w kolorze `MaterialTheme.colorScheme.tertiary` (spójnie z `WarningBanner` w `HomeScreen.kt`), renderowaną tylko gdy `state.warningThresholdPercent != null` (FR-009; research.md → decyzja 3)

**Checkpoint**: Wykres trendu działa niezależnie — User Story 1 w pełni funkcjonalna i testowalna (MVP)

---

## Phase 4: User Story 2 - Terminy wywozów widoczne na tle trendu (Priority: P1)

**Goal**: Wywozy są oznaczone na wykresie odrębnym kształtem/ikoną, rozpoznawalne bez dodatkowej interakcji, także dla osób niedowidzących barw

**Independent Test**: Zarejestrować wywóz ścieków pomiędzy odczytami licznika i sprawdzić, że na wykresie jest on widoczny jako punkt o innym kształcie niż odczyty, we właściwym miejscu chronologicznym

### Implementation for User Story 2

- [X] T010 [US2] Zaimplementować w `HistoryScreen.kt` drugą serię `LineChart.LineSpec` (Vico 1.15.0: `lineChart(lines = listOf(readingLine, pumpingLine))`, jedna seria = odczyty z kołem (`Shapes.pillShape`), druga = wyłącznie wywozy z przezroczystą linią i rombem (`Shapes.cutCornerShape(50)`)) przypisując odrębny kształt markera punktom `HistoryPoint.type == HistoryPointType.PUMPING` względem `HistoryPointType.READING`, niezależnie od koloru (FR-002; Clarifications Session 2026-09-22)
- [X] T011 [US2] Dodać w `HistoryScreen.kt` tekstową legendę pod wykresem (kolorowy kwadrat + etykieta "Odczyt licznika" / "Wywóz ścieków") zamiast semantyki per-punkt — punkty wykresu Vico są rysowane na `Canvas`, więc nie są odrębnymi węzłami Compose z własną semantyką; legenda z tekstem czytelnym dla czytników ekranu realizuje intencję dostępności z FR-002/Clarifications w sposób faktycznie możliwy do zaimplementowania dla tej biblioteki

**Checkpoint**: Wywozy i odczyty są wizualnie i dostępnościowo rozróżnialne — User Story 1 i 2 działają razem niezależnie

---

## Phase 5: User Story 3 - Szczegóły pojedynczego punktu historii (Priority: P2)

**Goal**: Dotknięcie punktu na wykresie pokazuje dokładną datę, godzinę i wartość tego punktu

**Independent Test**: Dotknąć dowolnego punktu odczytu i punktu wywozu na wykresie, sprawdzić że pojawiają się poprawne, dokładne szczegóły

### Implementation for User Story 3

- [X] T012 [US3] Zaimplementować `MarkerComponent`/`MarkerLabelFormatter` (Vico 1.15.0: `Marker` pokazywany automatycznie po dotknięciu — parametr `marker` na `Chart(...)`) w `HistoryScreen.kt`, z własnym `labelFormatter` pokazującym dla punktu `READING`: sformatowaną datę/godzinę (`SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())`, wzorem usuniętego kodu) oraz wartość (`fillPercentOfCapacity` lub `fillLiters` zgodnie z T007), a dla punktu `PUMPING`: datę wywozu oraz wartość odczytu bazowego (odczyt znaleziony przez `sourceReadingId` w mapie odczytów) (FR-005; data-model.md → pola `sourceReadingId`, `pumpingEventId`)
- [X] T013 [US3] Uwzględnić w etykiecie markera z T012 informację o źródle odczytu (`readingSource`: `AUTO_OCR` / `MANUAL_CORRECTED` / `MANUAL_ENTERED`) dla punktów typu `READING`, tak aby żadna informacja dostępna w poprzednim widoku tekstowym nie została utracona (FR-010, SC-005)

**Checkpoint**: Szczegóły każdego punktu dostępne po dotknięciu — User Stories 1–3 działają razem niezależnie

---

## Phase 6: User Story 4 - Czytelny widok przy braku danych lub przy dużej historii (Priority: P3)

**Goal**: Przyjazny stan pusty przy braku danych; czytelny, przewijalny i powiększalny wykres przy dużej liczbie punktów

**Independent Test**: Otworzyć zakładkę Historia bez żadnych danych (oczekiwany czytelny stan pusty) oraz z ≥100 punktami historii (oczekiwany czytelny wykres, przewijalny i powiększalny)

### Implementation for User Story 4

- [X] T014 [US4] Dodać w `HistoryScreen.kt` widok stanu pustego (`Text` z przyjaznym komunikatem zachęcającym do zarejestrowania pierwszego odczytu), wyświetlany zamiast wykresu, gdy `state.chartPoints.isEmpty()` (FR-007)
- [X] T015 [US4] Skonfigurować `chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = true)` oraz `isZoomEnabled = true` na `Chart(...)` w `HistoryScreen.kt` (Vico 1.15.0 — te ustawienia są domyślne, ustawione tu jawnie dla udokumentowania wymagania), włączając poziome przewijanie (pan) po całej osi czasu i powiększanie gestem (pinch-to-zoom), tak aby wykres pozostawał czytelny przy ≥100 punktach historii (FR-006, FR-008; Clarifications Session 2026-09-22; SC-004)

**Checkpoint**: Wszystkie User Stories (1–4) działają niezależnie i razem — funkcja kompletna zgodnie ze spec.md

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Porządki po redesignie i końcowa walidacja end-to-end

- [X] T016 [P] Usunąć nieużywane już importy i kod (`LazyColumn`, `items`, `Divider`, stare `SimpleDateFormat` z jedną datą) z `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryScreen.kt` — plik napisany od nowa w ramach T006-T015, zweryfikowano brak nieużywanych importów (`compileDebugKotlin` bez ostrzeżeń)
- [X] T017 [P] Zaktualizować komentarz dokumentujący `HistoryUiState` w `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryViewModel.kt`, tak aby opisywał nowy kształt stanu (`chartPoints`, `warningThresholdPercent`, `isCapacityConfigured`) zamiast poprzednich pól `readings`/`pumpingEvents` — zrobione razem z T005
- [ ] T018 Przeprowadzić ręczną walidację wg `specs/002-history-visual-redesign/quickstart.md` (Scenariusze 1-5) na urządzeniu/emulatorze z API 26+

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Brak zależności — można zacząć od razu
- **Foundational (Phase 2)**: Zależy od Setup (T001) — BLOKUJE wszystkie User Stories
- **User Stories (Phase 3-6)**: Wszystkie zależą od ukończenia Foundational (T002-T005)
  - US1 (P1) i US2 (P1) mogą być realizowane równolegle (różne aspekty tego samego pliku `HistoryScreen.kt`, ale niezależne logicznie — US2 zakłada, że wykres z US1 już istnieje, więc sekwencyjnie: US1 → US2)
  - US3 (P2) wymaga istniejącego wykresu z US1 (marker działa na już wyrenderowanym `CartesianChartHost`) — sekwencyjnie po US1
  - US4 (P3) wymaga istniejącego wykresu z US1 (stan pusty zastępuje wykres, pan/zoom konfiguruje ten sam `CartesianChartHost`) — sekwencyjnie po US1, niezależnie od US2/US3
- **Polish (Phase 7)**: Zależy od ukończenia pożądanych User Stories

### Realistyczna kolejność (jeden plik `HistoryScreen.kt` = ograniczona równoległość)

Ponieważ US1-US4 modyfikują głównie ten sam plik (`HistoryScreen.kt`), praktyczna kolejność to: Setup → Foundational → US1 → US2 → US3 → US4 → Polish (sekwencyjnie, priorytet P1 → P1 → P2 → P3), a nie równoległa praca wielu deweloperów nad tym samym plikiem.

### Parallel Opportunities

- W ramach Foundational: T002 i T004 oznaczone [P] mogą być przygotowane równolegle z resztą (T004 pisane jako testy przed/równolegle z T003, o ile deweloper trzyma się kontraktu z data-model.md)
- W ramach Polish: T016 i T017 [P] mogą być wykonane równolegle (różne fragmenty tego samego pliku, ale niekolidujące zmiany — importy vs. komentarz)

---

## Parallel Example: Foundational

```bash
# T002 i T004 mogą być przygotowywane równolegle (T004 pisane wg kontraktu z data-model.md,
# nawet zanim T003 jest w pełni gotowe), T003 wymaga ukończonego T002:
Task: "Utworzyć HistoryPoint w android/app/src/main/kotlin/pl/watershed/septictank/domain/history/HistoryPoint.kt"
Task: "Testy jednostkowe HistoryTrendCalculator w android/app/src/test/kotlin/pl/watershed/septictank/domain/history/HistoryTrendCalculatorTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Ukończyć Phase 1: Setup (T001)
2. Ukończyć Phase 2: Foundational (T002-T005, KRYTYCZNE — blokuje wszystkie historie)
3. Ukończyć Phase 3: User Story 1 (T006-T009)
4. **STOP i ZWALIDUJ**: Przetestować User Story 1 niezależnie (Scenariusz 1 z quickstart.md) — wykres trendu widoczny zamiast list tekstowych
5. Zaprezentować/wdrożyć, jeśli gotowe

### Incremental Delivery

1. Setup + Foundational → fundament gotowy
2. Dodać US1 → przetestować niezależnie → MVP (wykres trendu)
3. Dodać US2 → przetestować niezależnie → wywozy rozróżnialne kształtem
4. Dodać US3 → przetestować niezależnie → szczegóły punktu po dotknięciu
5. Dodać US4 → przetestować niezależnie → stan pusty + czytelność przy dużej historii
6. Polish → sprzątanie i pełna walidacja quickstart.md

## Notes

- [P] = różne pliki lub w pełni niezależne fragmenty, bez zależności od niedokończonych zadań
- [Story] = mapowanie zadania na konkretną User Story ze spec.md
- Ta funkcja nie zmienia schematu bazy danych ani sposobu rejestrowania danych (patrz plan.md, data-model.md) — wszystkie zadania implementacyjne dotyczą wyłącznie `domain/history/` (nowe) i `ui/history/` (zmiana) plus jedna linia w `build.gradle.kts`
- Commit po każdym zadaniu lub logicznej grupie zadań
- Zatrzymaj się na każdym checkpoincie, aby zwalidować historię niezależnie
