# Implementation Plan: Graficzna historia wypełnienia zbiornika i wywozów

**Branch**: `002-history-visual-redesign` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-history-visual-redesign/spec.md`

## Summary

Zastąpienie dwóch osobnych list tekstowych w zakładce Historia (Android, Jetpack Compose) jednym wykresem liniowym trendu wypełnienia zbiornika w czasie, z wywozami oznaczonymi jako odrębne markery (kształt, nie tylko kolor), linią progu ostrzegawczego, przewijaniem/pinch-to-zoom oraz szczegółami punktu po dotknięciu. Podejście techniczne: dodać bibliotekę wykresów Vico (Compose + Material3) do istniejącego modułu `android/app`, oraz nową, czystą funkcję domenową przeliczającą surową historię odczytów i wywozów na punkty wykresu (wartość wypełnienia liczona względem aktywnego w danym momencie punktu bazowego wywozu — analogicznie do `UsageCalculator`, ale dla każdego historycznego punktu, nie tylko najnowszego). Żadne zmiany schematu bazy danych ani sposobu rejestrowania danych nie są potrzebne — to redesign czysto prezentacyjny istniejącego ekranu `HistoryScreen`/`HistoryViewModel`.

## Technical Context

**Language/Version**: Kotlin, JVM target 17 (istniejący `android/app/build.gradle.kts`)

**Primary Dependencies**: Jetpack Compose (BOM `2024.06.00`) + Material3, `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2`, Room `2.6.1` (bez zmian schematu) — NOWA zależność: `com.patrykandpatrick.vico:compose-m3:1.15.0` (wykres liniowy, markery po dotknięciu, wbudowany pan/pinch-zoom; wersja dobrana tak, by dokładnie odpowiadać już używanemu `compose-bom 2024.06.00` — nowsze wersje Vico 2.x/3.x wymagają znacznie nowszego `compose-bom`, `compileSdk` i AGP, patrz `research.md` → Uwaga z implementacji)

**Storage**: Room/SQLite — istniejące tabele `meter_readings`, `pumping_events`, `tank_configuration`; bez migracji, funkcja czyta wyłącznie istniejące dane

**Testing**: JUnit4 (`app/src/test/kotlin`) dla logiki domenowej (nowa funkcja przeliczania punktów historii); Espresso + Compose UI test (`app/src/androidTest/kotlin`) dla ekranu, zgodnie z istniejącą konfiguracją `build.gradle.kts` (obecnie brak plików testowych w repozytorium — katalogi źródłowe są już skonfigurowane)

**Target Platform**: Android API 26+ (telefon)

**Project Type**: mobile-app (pojedynczy moduł `android/app`, kontynuacja 001-septic-tank-monitor)

**Performance Goals**: Wykres pozostaje płynny (bez zacinania) przy przewijaniu/zoomie dla co najmniej 100 punktów historii (SC-004); otwarcie zakładki Historia nie jest wolniejsze odczuwalnie niż obecny widok listowy

**Constraints**: Aplikacja MUST pozostać w pełni lokalna/offline (FR-014 z 001-septic-tank-monitor) — Vico renderuje lokalnie, bez wywołań sieciowych, więc ograniczenie jest zachowane; brak nowych uprawnień systemowych

**Scale/Scope**: Redesign jednego ekranu (`HistoryScreen` + `HistoryViewModel`), jedna nowa funkcja domenowa do przeliczania historii, bez nowych tabel/DAO; czytelność wymagana przy ≥100 połączonych punktach (odczyty + wywozy)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` zawiera wyłącznie niewypełnione placeholdery szablonu (`[PRINCIPLE_1_NAME]` itd.) — konstytucja projektu nie została jeszcze ratyfikowana. Brak sformalizowanych zasad do zweryfikowania jako gate.

W ich braku, plan trzyma się istniejących konwencji projektu widocznych w 001-septic-tank-monitor: warstwa `data/db` (Entity + Repository), czysta logika domenowa w `domain/*` niezależna od Androida (łatwa do testowania jednostkowego, jak `UsageCalculator`), `ui/<feature>` z `ViewModel` (StateFlow) + `@Composable` ekranem, brak zbędnych zależności (nowa zależność Vico jest uzasadniona: gesty pan/zoom i markery „z ręki” w Compose Canvas byłyby znacznie bardziej złożone i podatne na błędy niż w oparciu o dojrzałą, aktywnie rozwijaną bibliotekę). Brak naruszeń — sekcja Complexity Tracking nie jest wymagana.

**Post-Design re-check (po Fazie 1)**: Projekt danych i badania (patrz `research.md`, `data-model.md`) nie wprowadzają nowych tabel, migracji ani zależności sieciowych; jedyna nowa zależność (Vico) jest w pełni lokalna. Zgodność potwierdzona, bez zmian.

## Project Structure

### Documentation (this feature)

```text
specs/002-history-visual-redesign/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

Brak katalogu `contracts/` — funkcja nie eksponuje żadnego interfejsu zewnętrznego (API, CLI, schemat); to wewnętrzny redesign ekranu aplikacji mobilnej korzystającej wyłącznie z istniejących repozytoriów lokalnych.

### Source Code (repository root)

```text
android/app/src/main/kotlin/pl/watershed/septictank/
├── domain/
│   ├── history/
│   │   └── HistoryTrendCalculator.kt      # NOWY: czysta funkcja (readings, pumpingEvents, tankConfig) -> List<HistoryPoint>
│   ├── usage/
│   │   └── UsageCalculator.kt              # BEZ ZMIAN (wzorzec do naśladowania dla logiki baseline)
│   └── warning/                            # BEZ ZMIAN
├── data/db/                                # BEZ ZMIAN (repozytoria/DAO/encje już istnieją)
└── ui/history/
    ├── HistoryScreen.kt                    # ZMIENIONY: zastąpienie dwóch LazyColumn wykresem Vico
    └── HistoryViewModel.kt                 # ZMIENIONY: dołącza TankConfigurationRepository, mapuje przez HistoryTrendCalculator

android/app/src/test/kotlin/pl/watershed/septictank/
└── domain/history/
    └── HistoryTrendCalculatorTest.kt        # NOWY: testy jednostkowe logiki przeliczania (baseline per cykl, brak configu -> m3, itd.)

android/app/build.gradle.kts                 # ZMIENIONY: dodanie zależności com.patrykandpatrick.vico:compose-m3
```

**Structure Decision**: Istniejąca struktura pojedynczego modułu Android (`android/app`) z podziałem `data/db` (persystencja) / `domain/*` (logika biznesowa, czysty Kotlin) / `ui/<feature>` (Compose + ViewModel) zostaje zachowana bez zmian architektonicznych. Redesign dodaje jedną nową, czystą funkcję domenową (`domain/history`) oraz modyfikuje istniejące pliki ekranu Historia — zero nowych modułów, zero zmian w warstwie danych.

## Complexity Tracking

> Brak naruszeń Constitution Check — sekcja nie dotyczy tej funkcji.
