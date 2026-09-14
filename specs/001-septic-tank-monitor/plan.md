# Implementation Plan: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Branch**: `001-septic-tank-monitor` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-septic-tank-monitor/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Natywna aplikacja na Androida umożliwiająca śledzenie poziomu zbiornika bezodpływowego (szamba) na podstawie zdjęć licznika wody. Użytkownik robi zdjęcie licznika, aplikacja lokalnie (on-device, offline) rozpoznaje odczyt (ML Kit Text Recognition), zapisuje go w historii i przelicza bieżące zużycie od ostatniego wywozu ścieków. Przycisk „Wywóz ścieków” zapisuje zdarzenie opróżnienia zbiornika i zeruje wyświetlane zużycie. Gdy zużycie zbliża się do (lub przekracza) skonfigurowaną pojemność zbiornika, aplikacja wyświetla ostrzeżenie o rosnącej pilności. Dane przechowywane są lokalnie (Room + pliki zdjęć), v1 obsługuje jeden zbiornik/licznik na instalację, bez backendu i bez synchronizacji w chmurze.

## Technical Context

**Language/Version**: Kotlin, Android API 26+ (Android 8.0+)

**Primary Dependencies**: Jetpack Compose (UI), CameraX (przechwytywanie zdjęcia), ML Kit Text Recognition — model on-device (OCR), Room (lokalna baza danych), WorkManager (cykliczne przypomnienia i powiadomienia)

**Storage**: Room (SQLite) dla danych strukturalnych (`MeterReading`, `PumpingEvent`, `TankConfiguration`); zdjęcia liczników jako pliki w pamięci wewnętrznej aplikacji, referencjonowane ścieżką z Room

**Testing**: JUnit4 dla testów jednostkowych logiki domenowej (`src/test`); Espresso + Compose Testing API dla testów instrumentowanych kluczowych przepływów UI (`src/androidTest`)

**Target Platform**: Android (telefony), API 26+

**Project Type**: mobile-app (pojedynczy moduł Android, bez osobnego backendu — patrz FR-014: przetwarzanie w pełni lokalne)

**Performance Goals**: Zgodnie z SC-001…SC-005 w spec.md — m.in. rejestracja nowego odczytu (zdjęcie → zaktualizowane zużycie) w < 30 s; sprawdzenie procentu wykorzystania pojemności zbiornika w ≤ 2 dotknięciach ekranu od otwarcia aplikacji

**Constraints**: Aplikacja MUST działać w pełni offline, bez wymogu połączenia z internetem (FR-014); v1 obsługuje wyłącznie jeden zbiornik i jeden licznik na instalację (decyzja z sesji `/speckit-clarify`)

**Scale/Scope**: Pojedynczy użytkownik/gospodarstwo domowe, pojedynczy zbiornik, rzędu setek odczytów licznika i pojedynczych/kilkunastu zdarzeń wywozu rocznie — trywialna skala dla lokalnej bazy SQLite

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` zawiera wyłącznie nieuzupełniony szablon (placeholdery `[PRINCIPLE_1_NAME]` itd., brak ratyfikowanej wersji) — projekt nie ma jeszcze zdefiniowanych, wiążących zasad konstytucyjnych. W związku z tym brak jest specyficznych dla projektu bramek do wymuszenia na tym etapie; plan kieruje się ogólnymi dobrymi praktykami (prostota architektury — patrz research.md → „Architektura aplikacji”, niezależna testowalność historyjek użytkownika, brak zbędnych zależności).

**Status**: PASS (brak zdefiniowanych zasad do naruszenia). Rekomendacja: uruchomić `/speckit-constitution`, gdy pojawią się pierwsze konkretne zasady projektu (np. wymagania dot. testowania, przechowywania danych, prywatności), i ponownie zweryfikować ten plan względem nich.

**Post-Phase 1 re-check**: Projekt (data-model.md, research.md) nie wprowadza elementów wymagających uzasadnienia w Complexity Tracking — architektura pozostaje jednomodułowa, bez dodatkowych warstw ponad to, co wynika wprost z wymagań. Status: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-septic-tank-monitor/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify + /speckit-clarify)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

Brak katalogu `contracts/`: aplikacja nie eksponuje żadnego zewnętrznego interfejsu (API, protokołu) dla innych systemów ani użytkowników poza własnym UI — całe przetwarzanie (OCR, przechowywanie danych) jest lokalne na urządzeniu (FR-014), więc krok „Define interface contracts” jest pomijany zgodnie z regułą „skip if project is purely internal”.

### Source Code (repository root)

```text
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/pl/watershed/septictank/
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/           # Room: encje, DAO, baza (MeterReading, PumpingEvent, TankConfiguration)
│   │   │   │   │   ├── photo/        # zapis/odczyt plików zdjęć w pamięci wewnętrznej
│   │   │   │   │   └── ocr/          # integracja z ML Kit Text Recognition
│   │   │   │   ├── domain/
│   │   │   │   │   ├── usage/        # obliczanie UsageState, wykrywanie anomalii
│   │   │   │   │   └── warning/      # logika progów ostrzegawczych (APPROACHING/EXCEEDED)
│   │   │   │   ├── reminders/        # WorkManager: cykliczne przypomnienia, powiadomienia
│   │   │   │   └── ui/
│   │   │   │       ├── home/         # ekran główny: bieżące zużycie, ostrzeżenie, akcja zdjęcia, przycisk wywozu
│   │   │   │       ├── history/      # historia odczytów i wywozów
│   │   │   │       └── settings/     # konfiguracja zbiornika i przypomnień
│   │   │   └── res/                  # zasoby Android (stringi, ikony, motyw)
│   │   ├── test/kotlin/...           # testy jednostkowe (domain, usage, warning)
│   │   └── androidTest/kotlin/...    # testy instrumentowane (Espresso + Compose Testing) dla US1–US4
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

**Structure Decision**: Pojedynczy moduł aplikacji Android (`android/app`) z wewnętrznym podziałem warstwowym `data` / `domain` / `ui` / `reminders`, bez wydzielonych modułów Gradle ani osobnego backendu — zgodnie z decyzją z research.md („Architektura aplikacji”), uzasadnioną ograniczonym zakresem v1 (jeden zbiornik/licznik, brak wielu użytkowników, przetwarzanie w pełni lokalne).

## Complexity Tracking

*Brak — Constitution Check nie zgłosił naruszeń wymagających uzasadnienia.*
