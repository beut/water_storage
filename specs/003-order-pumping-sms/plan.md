# Implementation Plan: Zamawianie wywozu ścieków SMS-em

**Branch**: `003-order-pumping-sms` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-order-pumping-sms/spec.md`

## Summary

Przycisk „Zamów wywóz” na ekranie głównym otwiera okno z listą 5 dni roboczych (najwcześniej pojutrze) i podglądem SMS-a. Po potwierdzeniu aplikacja sama wysyła przez `SmsManager` SMS „Poproszę o wywóz {w/we dzień}” na numer firmy asenizacyjnej zapisany w Ustawieniach, a potem pokazuje wynik wysyłki (sukces albo błąd z „Ponów”).

Podejście techniczne:
- czysta logika domenowa (`domain/order`): lista dni, treść z polską odmianą, walidacja numeru;
- cienki adapter `data/sms/SmsSender`, który zwraca wynik z `sentIntent` jako `suspend`;
- nowa kolumna `pumpingCompanyPhone` w `tank_configuration` z niedestrukcyjną migracją 2 → 3;
- runtime permission `SEND_SMS`.

## Technical Context

**Language/Version**: Kotlin, JVM target 17 (bez zmian)

**Primary Dependencies**: bez nowych bibliotek. Wykorzystane: Jetpack Compose + Material3 (istniejące), `androidx.core` (`ContextCompat.registerReceiver`), `androidx.activity` (`rememberLauncherForActivityResult` dla uprawnienia), platformowe `android.telephony.SmsManager` i `java.time` (natywnie od API 26)

**Storage**: Room/SQLite: `tank_configuration` + kolumna `pumpingCompanyPhone TEXT NULL`; wersja bazy 2 → 3 przez `MIGRATION_2_3` (`ALTER TABLE ... ADD COLUMN`), bez utraty danych

**Testing**: JUnit4 (`app/src/test/kotlin`) dla `domain/order` (lista dni, treść, walidacja numeru). Wysyłka i UI są sprawdzane ręcznie według [quickstart.md](./quickstart.md), bo prawdziwy SMS wymaga urządzenia z SIM

**Target Platform**: Android API 26+ (telefon z obsługą SMS); `compileSdk`/`targetSdk` 34 bez zmian

**Project Type**: mobile-app (pojedynczy moduł `android/app`)

**Performance Goals**: Zamówienie w < 15 s od otwarcia aplikacji (SC-001), czyli 3 dotknięcia: „Zamów wywóz” → (opcjonalnie inny dzień) → „Wyślij”. Wynik wysyłki pokazany zaraz po odpowiedzi sieci, najpóźniej po 60 s (timeout)

**Constraints**:
- Nowe uprawnienie `SEND_SMS`: aplikacja jest dystrybuowana jako APK (sideload), więc polityka Google Play dotycząca SMS jej nie dotyczy (research.md §1).
- Poza SMS-em wysyłanym przez sieć komórkową aplikacja pozostaje offline (FR-014 z 001).
- Numer telefonu jest przechowywany tylko lokalnie.

**Scale/Scope**: 1 nowy pakiet domenowy (3 małe obiekty), 1 adapter SMS, 1 dialog, zmiany w `HomeScreen`/`HomeViewModel`, `SettingsScreen`/`SettingsViewModel`, encji, repozytorium, bazie i manifeście

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` nadal zawiera tylko niewypełnione placeholdery szablonu, więc nie ma sformalizowanych zasad do sprawdzenia. Plan trzyma się konwencji projektu z 001/002:
- logika biznesowa jako czysty Kotlin w `domain/*`, testowalna jednostkowo (wzorzec `UsageCalculator`, `HistoryTrendCalculator`);
- persystencja przez `data/db` (Entity + Repository), migracje niedestrukcyjne (commit e986fbc);
- `ui/<feature>` jako ViewModel (StateFlow) + Composable;
- ręczny `AppContainer` zamiast frameworka DI;
- brak nowych zależności.

Brak naruszeń, więc sekcja Complexity Tracking nie jest potrzebna.

**Post-Design re-check (po Fazie 1)**: Projekt ([data-model.md](./data-model.md), [contracts/sms-message.md](./contracts/sms-message.md)) dodaje jedną nullable kolumnę z migracją `ADD COLUMN`, jedno uprawnienie systemowe i jeden adapter platformowy za interfejsem. Nie ma nowych bibliotek, modułów ani komunikacji sieciowej poza SMS-em. Zgodność potwierdzona.

## Project Structure

### Documentation (this feature)

```text
specs/003-order-pumping-sms/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── sms-message.md   # Phase 1 output: treść SMS + kontrakt UI
└── tasks.md             # Phase 2 output (/speckit-tasks - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
android/app/src/main/
├── AndroidManifest.xml                         # ZMIENIONY: <uses-permission SEND_SMS>, <uses-feature android.hardware.telephony required="false">
└── kotlin/pl/watershed/septictank/
    ├── AppContainer.kt                         # ZMIENIONY: + smsSender
    ├── domain/order/
    │   ├── OrderDays.kt                        # NOWY: availableOrderDays(today: LocalDate): List<LocalDate>
    │   ├── PumpingOrderMessage.kt              # NOWY: forDay(DayOfWeek): String (tabela odmiany)
    │   └── PhoneNumberValidator.kt             # NOWY: normalize(input): Result (Valid(normalized) | Empty | Invalid(reason))
    ├── data/
    │   ├── db/
    │   │   ├── AppDatabase.kt                  # ZMIENIONY: version = 3, addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    │   │   ├── Migrations.kt                   # ZMIENIONY: + MIGRATION_2_3
    │   │   ├── TankConfigurationRepository.kt  # ZMIENIONY: + updatePumpingCompanyPhone(String?)
    │   │   └── entities/TankConfigurationEntity.kt  # ZMIENIONY: + pumpingCompanyPhone: String? = null
    │   └── sms/
    │       └── SmsSender.kt                    # NOWY: interface SmsSender { suspend fun send(phone, text): SmsSendResult } + AndroidSmsSender (SmsManager + sentIntent, timeout 60 s)
    └── ui/
        ├── home/
        │   ├── HomeScreen.kt                   # ZMIENIONY: przycisk „Zamów wywóz” + PumpingOrderDialog + launcher uprawnienia SEND_SMS
        │   ├── HomeViewModel.kt                # ZMIENIONY: stan zamówienia (maszyna stanów z data-model.md)
        │   └── PumpingOrderDialog.kt           # NOWY: lista dni, podgląd SMS, stany Sending/Sent/Failed
        └── settings/
            ├── SettingsScreen.kt               # ZMIENIONY: pole „Numer firmy asenizacyjnej” + „Zapisz numer”
            └── SettingsViewModel.kt            # ZMIENIONY: savePumpingCompanyPhone(text): Boolean

android/app/src/test/kotlin/pl/watershed/septictank/domain/order/
├── OrderDaysTest.kt                            # NOWY
├── PumpingOrderMessageTest.kt                  # NOWY
└── PhoneNumberValidatorTest.kt                 # NOWY
```

**Structure Decision**: Zostaje istniejąca struktura pojedynczego modułu `android/app` z podziałem `data` / `domain` / `ui`. Logika zamówienia (dni, treść, walidacja) trafia do nowego, czystego pakietu `domain/order`, testowanego bez Androida. Wysyłka SMS jest w `data/sms` za interfejsem `SmsSender`: ViewModel da się testować z atrapą, a ewentualna zamiana na `ACTION_SENDTO` (research.md §1) dotyczy jednego pliku. Okno zamówienia jest osobnym plikiem w `ui/home`, bo `HomeScreen` już teraz obsługuje kilka przepływów.

## Complexity Tracking

Nie dotyczy: brak naruszeń.
