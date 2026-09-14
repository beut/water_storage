---

description: "Task list template for feature implementation"
---

# Tasks: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Input**: Design documents from `/specs/001-septic-tank-monitor/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md (brak `contracts/` — aplikacja nie eksponuje zewnętrznego API, patrz plan.md)

**Tests**: Zadania testowe (pisanie testów jednostkowych/instrumentowanych) nie zostały tu wygenerowane jako osobne, wymagane kroki — spec.md nie żąda wprost podejścia TDD. `plan.md`/`research.md` ustalają zestaw narzędzi testowych (JUnit4, Espresso + Compose Testing); pokrycie testami można dodać w ramach każdej historyjki lub w Fazie Polish.

**Organization**: Zadania pogrupowane są według historyjek użytkownika (US1–US4) ze spec.md, aby każdą można było wdrożyć i przetestować niezależnie.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Można wykonać równolegle (różne pliki, brak zależności)
- **[Story]**: Do której historyjki należy zadanie (US1–US4)
- W opisach podano dokładne ścieżki plików

## Path Conventions

Struktura zgodna z `plan.md` → Project Structure: pojedynczy moduł Android w `android/app/src/main/kotlin/pl/watershed/septictank/`, testy w `android/app/src/test` i `android/app/src/androidTest`.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Inicjalizacja projektu Android i podstawowej struktury

- [X] T001 Utworzyć strukturę projektu Gradle Android w `android/` (moduł `android/app`, `android/settings.gradle.kts`, `android/build.gradle.kts`) zgodnie z `plan.md` → Project Structure
- [X] T002 Dodać zależności rdzeniowe w `android/app/build.gradle.kts`: Jetpack Compose, CameraX, ML Kit Text Recognition (model on-device), Room, WorkManager — zgodnie z decyzjami w `research.md`
- [X] T003 [P] Skonfigurować `compileSdk`/`minSdk=26` oraz Compose compiler w `android/app/build.gradle.kts`
- [X] T004 [P] Skonfigurować linting (ktlint/detekt) dla modułu `android/app`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wspólna infrastruktura danych i nawigacji wymagana przez wszystkie historyjki użytkownika

**⚠️ CRITICAL**: Żadna historyjka nie może być implementowana przed ukończeniem tej fazy

- [X] T005 Zdefiniować encje Room `MeterReading`, `PumpingEvent`, `TankConfiguration` w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/entities/` zgodnie z polami i regułami walidacji z `data-model.md` (m.in. `valueM3` ≥ 0 z dokładnością do 0,001 m³, `capacityM3` > 0, `warningThresholdPercent` w zakresie 1–99)
- [X] T006 [P] Utworzyć `MeterReadingDao`, `PumpingEventDao`, `TankConfigurationDao` w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/dao/` (zapytania: wstawienie rekordu, pobranie najnowszego odczytu, pobranie historii posortowanej po `timestamp`)
- [X] T007 Utworzyć klasę `AppDatabase` (Room) spinającą encje i DAO z T005/T006 w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/AppDatabase.kt`
- [X] T008 [P] Zaimplementować `PhotoStorage` (zapis/odczyt plików zdjęć licznika w pamięci wewnętrznej aplikacji) w `android/app/src/main/kotlin/pl/watershed/septictank/data/photo/PhotoStorage.kt`
- [X] T009 Utworzyć szkielet nawigacji Compose (ekrany: Home, History, Settings) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/AppNavHost.kt`
- [X] T010 [P] Skonfigurować proste, spójne logowanie błędów w `android/app/src/main/kotlin/pl/watershed/septictank/util/Logging.kt`

**Checkpoint**: Fundament gotowy — można rozpocząć pracę nad historyjkami użytkownika

---

## Phase 3: User Story 1 - Rejestrowanie odczytu licznika wody ze zdjęcia (Priority: P1) 🎯 MVP

**Goal**: Użytkownik robi zdjęcie licznika, aplikacja rozpoznaje odczyt lokalnie (offline) i zapisuje go, przeliczając bieżące zużycie.

**Independent Test**: Zrobić zdjęcie licznika i sprawdzić, że nowy odczyt oraz przeliczone zużycie pojawiają się poprawnie w aplikacji (patrz `quickstart.md` → US1).

### Implementation for User Story 1

- [X] T011 [P] [US1] Zaimplementować ekran przechwytywania zdjęcia licznika (CameraX) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/CameraCaptureScreen.kt` (FR-001)
- [X] T012 [P] [US1] Zaimplementować `MeterOcrReader` opakowujący ML Kit Text Recognition do wyodrębnienia wartości liczbowej ze zdjęcia w `android/app/src/main/kotlin/pl/watershed/septictank/data/ocr/MeterOcrReader.kt` — rozpoznawanie MUST działać w pełni lokalnie, bez wywołań sieciowych (FR-002, FR-014)
- [X] T013 [US1] Zaimplementować `MeterReadingRepository` (zapis odczytu, pobranie najnowszego odczytu, pobranie historii) w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/MeterReadingRepository.kt` (zależy od T005–T007)
- [X] T014 [US1] Zaimplementować `UsageCalculator`: `currentUsageM3 = wartość najnowszego odczytu − wartość odczytu bazowego` (na razie: pierwszy zarejestrowany odczyt, dopóki nie istnieje `PumpingEvent` — patrz US2) w `android/app/src/main/kotlin/pl/watershed/septictank/domain/usage/UsageCalculator.kt` (FR-005)
- [X] T015 [US1] Zaimplementować `AnomalyDetector`: oznaczać `isAnomalous = true`, gdy nowy `valueM3` jest niższy niż `valueM3` najnowszego wcześniejszego odczytu, w `android/app/src/main/kotlin/pl/watershed/septictank/domain/usage/AnomalyDetector.kt` (FR-011, reguła walidacji z `data-model.md`)
- [X] T016 [US1] Zaimplementować ekran potwierdzenia odczytu: pokazać wynik OCR, umożliwić ręczną korektę/ręczne wprowadzenie wartości gdy odczyt jest niepewny lub nieudany, wymagać jawnego potwierdzenia dla odczytów oznaczonych jako anomalne przed ustawieniem `anomalyAcknowledged = true`, w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/ReadingConfirmationScreen.kt` (FR-003, FR-011)
- [X] T017 [US1] Zaimplementować `HomeViewModel`: zapis potwierdzonego odczytu (`source` = `AUTO_OCR`/`MANUAL_CORRECTED`/`MANUAL_ENTERED`), zapis zdjęcia przez `PhotoStorage`, przeliczenie i wystawienie stanu zużycia do ekranu Home, w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/HomeViewModel.kt` (zależy od T013, T014, T015)
- [X] T018 [US1] Wyświetlić bieżące zużycie (`currentUsageM3`) na ekranie Home w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/HomeScreen.kt`

**Checkpoint**: Historyjka US1 w pełni funkcjonalna i testowalna niezależnie

---

## Phase 4: User Story 2 - Reset stanu po wywozie ścieków (Priority: P1)

**Goal**: Przycisk „Wywóz ścieków” zapisuje zdarzenie opróżnienia zbiornika i zeruje wyświetlane zużycie.

**Independent Test**: Nacisnąć przycisk resetu i sprawdzić, że zużycie wraca do zera, a kolejne odczyty liczone są od nowego punktu bazowego (patrz `quickstart.md` → US2).

### Implementation for User Story 2

- [X] T019 [P] [US2] Zaimplementować `PumpingEventRepository` (zapis zdarzenia wywozu z `baselineReadingId` wskazującym na najnowszy `MeterReading.id`, pobranie historii wywozów) w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/PumpingEventRepository.kt` (zależy od T005–T007; FR-006, FR-007)
- [X] T020 [US2] Dodać akcję „Wywóz ścieków” na ekranie Home i w `HomeViewModel`, tworzącą `PumpingEvent` i zerującą wyświetlane zużycie; akcja MUST być zablokowana, jeśli nie istnieje jeszcze żaden `MeterReading` (brak punktu bazowego do zapisania — reguła walidacji z `data-model.md`), w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/HomeScreen.kt` i `HomeViewModel.kt` (FR-006)
- [X] T021 [US2] Zaktualizować `UsageCalculator`, aby jako punkt bazowy używał `baselineReadingId` najnowszego `PumpingEvent`, gdy istnieje, w przeciwnym razie pierwszego zarejestrowanego `MeterReading` (FR-005) w `android/app/src/main/kotlin/pl/watershed/septictank/domain/usage/UsageCalculator.kt`
- [X] T022 [P] [US2] Zaimplementować ekran historii z listą odczytów licznika i wywozów ścieków (FR-012) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/history/HistoryScreen.kt` i `HistoryViewModel.kt`

**Checkpoint**: US1 i US2 działają w pełni, każda niezależnie testowalna

---

## Phase 5: User Story 3 - Ostrzeżenie o zbliżającym się zapełnieniu zbiornika (Priority: P2)

**Goal**: Aplikacja wyświetla ostrzeżenie o rosnącej pilności, gdy zużycie zbliża się do lub przekracza pojemność zbiornika.

**Independent Test**: Ustawić niską pojemność testową, rejestrować odczyty aż zużycie przekroczy próg ostrzegawczy i sprawdzić pojawienie się ostrzeżenia (patrz `quickstart.md` → US3).

### Implementation for User Story 3

- [X] T023 [P] [US3] Zaimplementować `TankConfigurationRepository` (odczyt/zapis `capacityM3`, `warningThresholdPercent`) w `android/app/src/main/kotlin/pl/watershed/septictank/data/db/TankConfigurationRepository.kt` (zależy od T005–T007; FR-008)
- [X] T024 [US3] Zaimplementować `WarningLevelCalculator` wyznaczający `NONE`/`APPROACHING`/`EXCEEDED` na podstawie procentu wykorzystania pojemności względem `warningThresholdPercent` (domyślnie 80%), w `android/app/src/main/kotlin/pl/watershed/septictank/domain/warning/WarningLevelCalculator.kt` (FR-009, FR-010, reguła z `data-model.md` → `UsageState.warningLevel`)
- [X] T025 [US3] Wyświetlić baner ostrzeżenia na ekranie Home, wizualnie odróżniający `APPROACHING` od `EXCEEDED` (FR-010) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/HomeScreen.kt`
- [X] T026 [US3] Obsłużyć brak skonfigurowanej `capacityM3`: odczyty i zużycie MUST być nadal zapisywane, ostrzeżenia MUST być wstrzymane z podpowiedzią o konieczności uzupełnienia pojemności (Edge Case ze spec.md) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/home/HomeViewModel.kt`

**Checkpoint**: US1, US2 i US3 działają, każda niezależnie testowalna

---

## Phase 6: User Story 4 - Konfiguracja zbiornika i przypomnień (Priority: P3)

**Goal**: Użytkownik konfiguruje pojemność zbiornika oraz cykliczne przypomnienia o zdjęciu licznika.

**Independent Test**: Wejść w ustawienia, wprowadzić pojemność zbiornika i sprawdzić, że wartość jest zapamiętana i używana do ostrzeżeń (patrz `quickstart.md` → US4).

### Implementation for User Story 4

- [X] T027 [P] [US4] Zaimplementować ekran Ustawień do konfiguracji `capacityM3` i `warningThresholdPercent` (domyślnie 80, walidacja zakresu 1–99 zgodnie z `data-model.md`) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/settings/SettingsScreen.kt` i `SettingsViewModel.kt` (FR-008)
- [X] T028 [US4] Zaimplementować przepływ wstępnej konfiguracji przy pierwszym uruchomieniu, wymagający podania pojemności zbiornika przed przejściem dalej (Acceptance Scenario 1, US4) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/settings/OnboardingScreen.kt`
- [X] T029 [P] [US4] Zaimplementować `PeriodicWorkRequest` (WorkManager) do przypomnień o zdjęciu licznika, z konfigurowalnym interwałem (`reminderEnabled`, `reminderIntervalDays`) w `android/app/src/main/kotlin/pl/watershed/septictank/reminders/MeterPhotoReminderWorker.kt` (FR-013)
- [X] T030 [US4] Zaimplementować wysyłanie powiadomień (przypomnienia i ostrzeżenia) przez `NotificationCompat` w `android/app/src/main/kotlin/pl/watershed/septictank/reminders/AppNotifications.kt`
- [X] T031 [US4] Podpiąć przełącznik w ekranie Ustawień do planowania/anulowania `MeterPhotoReminderWorker` (FR-013) w `android/app/src/main/kotlin/pl/watershed/septictank/ui/settings/SettingsViewModel.kt`

**Checkpoint**: Wszystkie historyjki użytkownika działają niezależnie

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Ulepszenia obejmujące wiele historyjek jednocześnie

- [X] T032 [P] Dodać ikonę aplikacji i zasoby tekstowe (polskie napisy UI) w `android/app/src/main/res/`
- [ ] T033 [P] Zweryfikować działanie w pełni offline (tryb samolotowy) zgodnie z `quickstart.md` → „Weryfikacja działania offline” (FR-014) — **wymaga fizycznego urządzenia/emulatora, niedostępnego w tym środowisku; do wykonania przez dewelopera**
- [ ] T034 Przeprowadzić ręczną walidację wszystkich historyjek (US1–US4) zgodnie z `quickstart.md` na fizycznym urządzeniu — **wymaga fizycznego urządzenia/emulatora, niedostępnego w tym środowisku; do wykonania przez dewelopera**
- [X] T035 [P] Przejrzeć i doprecyzować walidację danych wejściowych zgodnie z regułami z `data-model.md` we wszystkich repozytoriach (`valueM3` ≥ 0, `capacityM3` > 0, `warningThresholdPercent` w zakresie 1–99)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Brak zależności — można rozpocząć od razu
- **Foundational (Phase 2)**: Zależy od ukończenia Setup — BLOKUJE wszystkie historyjki użytkownika
- **User Stories (Phase 3–6)**: Wszystkie zależą od ukończenia fazy Foundational
  - Mogą być realizowane równolegle (przy odpowiedniej obsadzie) lub sekwencyjnie w kolejności priorytetów (P1 → P1 → P2 → P3)
- **Polish (Phase 7)**: Zależy od ukończenia pożądanych historyjek użytkownika

### User Story Dependencies

- **US1 (P1)**: Może wystartować po Foundational — brak zależności od innych historyjek
- **US2 (P1)**: Może wystartować po Foundational; T021 rozszerza `UsageCalculator` z US1 (ta sama funkcja, kolejny krok logiki), ale US2 pozostaje niezależnie testowalna po ukończeniu T019/T020
- **US3 (P2)**: Może wystartować po Foundational; korzysta z `UsageState` wyliczanego przez `UsageCalculator` (US1/US2), ale dodaje własną logikę progów bez modyfikowania działania US1/US2
- **US4 (P3)**: Może wystartować po Foundational; `TankConfiguration` z US4 jest odczytywana przez `WarningLevelCalculator` z US3, ale US4 jest niezależnie testowalna (konfiguracja i przypomnienia działają samodzielnie)

### Within Each User Story

- Modele/repozytoria przed logiką domenową
- Logika domenowa przed UI/ViewModel
- Rdzeń implementacji przed integracją z innymi historyjkami
- Historyjka ukończona przed przejściem do kolejnego priorytetu

### Parallel Opportunities

- Wszystkie zadania Setup oznaczone [P] mogą być wykonane równolegle
- Wszystkie zadania Foundational oznaczone [P] mogą być wykonane równolegle (w ramach Fazy 2)
- Po ukończeniu fazy Foundational wszystkie historyjki mogą być rozpoczęte równolegle (przy odpowiedniej obsadzie)
- Zadania oznaczone [P] w ramach jednej historyjki (różne pliki, brak zależności) mogą być wykonane równolegle

---

## Parallel Example: User Story 1

```bash
# Uruchom równolegle zadania dotykające różnych plików w ramach US1:
Task: "Zaimplementować ekran przechwytywania zdjęcia licznika (CameraX) w android/app/src/main/kotlin/pl/watershed/septictank/ui/home/CameraCaptureScreen.kt"
Task: "Zaimplementować MeterOcrReader (ML Kit Text Recognition, on-device) w android/app/src/main/kotlin/pl/watershed/septictank/data/ocr/MeterOcrReader.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Ukończyć Fazę 1: Setup
2. Ukończyć Fazę 2: Foundational (KRYTYCZNE — blokuje wszystkie historyjki)
3. Ukończyć Fazę 3: User Story 1
4. **ZATRZYMAJ SIĘ I ZWERYFIKUJ**: przetestować US1 niezależnie zgodnie z `quickstart.md`
5. Zaprezentować/wdrożyć, jeśli gotowe

### Incremental Delivery

1. Setup + Foundational → fundament gotowy
2. Dodaj US1 → przetestuj niezależnie → zademonstruj (MVP!)
3. Dodaj US2 → przetestuj niezależnie → zademonstruj
4. Dodaj US3 → przetestuj niezależnie → zademonstruj
5. Dodaj US4 → przetestuj niezależnie → zademonstruj
6. Każda historyjka dodaje wartość bez psucia poprzednich

---

## Notes

- [P] tasks = różne pliki, brak zależności
- Etykieta [Story] mapuje zadanie do konkretnej historyjki dla identyfikowalności
- Każda historyjka powinna być niezależnie ukończalna i testowalna
- Commitować po każdym zadaniu lub logicznej grupie zadań
- Zatrzymać się przy każdym checkpoint, aby zweryfikować historyjkę niezależnie
- Unikać: niejasnych zadań, konfliktów w tych samych plikach, zależności między historyjkami łamiących ich niezależność
