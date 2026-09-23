---

description: "Task list for 003-order-pumping-sms"
---

# Tasks: Zamawianie wywozu ścieków SMS-em

**Input**: Design documents from `/specs/003-order-pumping-sms/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/sms-message.md, quickstart.md

**Tests**: Testy jednostkowe (JUnit4) dla `domain/order` są uwzględnione, bo `quickstart.md` wymienia je jawnie jako sposób weryfikacji listy dni, treści SMS (SC-002) i walidacji numeru. Wysyłka SMS i UI są weryfikowane ręcznie według scenariuszy z `quickstart.md`, bo prawdziwy SMS wymaga urządzenia z SIM.

**Organization**: Zadania pogrupowane wg User Story ze spec.md. Obie historie mają priorytet P1. **US2 (konfiguracja numeru) jest realizowana przed US1**, bo niezależny test US1 („konfigurując numer telefonu…”) wymaga możliwości wpisania numeru w aplikacji.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Może być wykonane równolegle (inny plik, brak zależności od niedokończonych zadań)
- **[Story]**: Do której User Story należy zadanie (US1, US2)
- Ścieżki względem korzenia repozytorium

## Path Conventions

Pojedynczy moduł Android. Skróty używane poniżej:
- `MAIN` = `android/app/src/main/kotlin/pl/watershed/septictank`
- `TEST` = `android/app/src/test/kotlin/pl/watershed/septictank`

---

## Phase 1: Setup

**Purpose**: Deklaracja uprawnienia i funkcji sprzętowej potrzebnych do wysyłki SMS (research.md §1)

- [X] T001 W `android/app/src/main/AndroidManifest.xml` dodać `<uses-permission android:name="android.permission.SEND_SMS" />` obok istniejących uprawnień CAMERA/POST_NOTIFICATIONS oraz `<uses-feature android:name="android.hardware.telephony" android:required="false" />`. `required="false"` jest potrzebne, bo samo `SEND_SMS` domyślnie wymusza telefonię, a aplikacja ma działać także na urządzeniach bez niej (wtedy zamówienie kończy się komunikatem błędu)

**Checkpoint**: `./gradlew :app:assembleDebug` przechodzi

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Trwałe pole numeru firmy asenizacyjnej z niedestrukcyjną migracją; wymagane przez US1 i US2

**⚠️ CRITICAL**: Żadna User Story nie może się zacząć przed ukończeniem tej fazy

- [X] T002 W `MAIN/data/db/entities/TankConfigurationEntity.kt` dodać pole `val pumpingCompanyPhone: String? = null` na końcu konstruktora (po `reminderIntervalDays`) z KDoc: numer w postaci znormalizowanej `+?[0-9]{9,15}`, `null` = nieskonfigurowany (data-model.md, FR-002, FR-008)
- [X] T003 W `MAIN/data/db/Migrations.kt` dodać `val MIGRATION_2_3 = object : Migration(2, 3)` wykonującą dokładnie `ALTER TABLE tank_configuration ADD COLUMN pumpingCompanyPhone TEXT`, z komentarzem w stylu `MIGRATION_1_2` (v2 → v3: nowe nullable pole numeru firmy asenizacyjnej, spec 003; zachowuje dane użytkownika)
- [X] T004 W `MAIN/data/db/AppDatabase.kt` zmienić `version = 2` na `version = 3` i `.addMigrations(MIGRATION_1_2)` na `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`; NIE dodawać `fallbackToDestructiveMigration` (depends on T002, T003)
- [X] T005 W `MAIN/data/db/TankConfigurationRepository.kt` dodać `suspend fun updatePumpingCompanyPhone(phone: String?)`, które zapisuje `dao.upsert(get().copy(pumpingCompanyPhone = phone))`, z `require(phone == null || Regex("^\\+?[0-9]{9,15}$").matches(phone))` i komunikatem w stylu istniejących `require` (depends on T002)

**Checkpoint**: Aplikacja buduje się; instalacja na wersję z danymi (baza v2) zachowuje odczyty, wywozy i pojemność (quickstart.md, scenariusz 1)

---

## Phase 3: User Story 2 - Konfiguracja numeru firmy asenizacyjnej (Priority: P1)

**Goal**: Użytkownik wpisuje w Ustawieniach numer firmy asenizacyjnej, numer jest walidowany i trwale zapamiętany

**Independent Test**: Wpisać numer w Ustawieniach, zapisać, zrestartować aplikację i sprawdzić, że numer jest zapamiętany; wpisać `12345` i sprawdzić, że zapis jest odrzucony z błędem (quickstart.md, scenariusz 3)

### Tests for User Story 2

- [X] T006 [P] [US2] Utworzyć `TEST/domain/order/PhoneNumberValidatorTest.kt` (JUnit4) z przypadkami z quickstart.md: `"600 123 456"` → `Valid("600123456")`; `"+48 600-123-456"` → `Valid("+48600123456")`; `"12345"` → `Invalid`; `"60a123456"` → `Invalid`; `"600+123456"` → `Invalid`; `""` i `"   "` → `Empty`; 16 cyfr → `Invalid`; dokładnie 9 i 15 cyfr → `Valid`

### Implementation for User Story 2

- [X] T007 [P] [US2] Utworzyć `MAIN/domain/order/PhoneNumberValidator.kt`: `object PhoneNumberValidator` z `sealed interface Result { data class Valid(val normalized: String); data object Empty; data class Invalid(val reason: String) }` i `fun validate(input: String): Result`. Reguły z data-model.md: dopuszczalne znaki wejściowe to cyfry, spacja, `-` i `+` tylko na pierwszej pozycji; po usunięciu spacji i `-` wynik MUSI pasować do `^\+?[0-9]{9,15}$`; pusty lub same białe znaki → `Empty`. `reason` po polsku, np. „Numer może zawierać tylko cyfry, spacje, myślniki i + na początku” / „Numer musi mieć od 9 do 15 cyfr”. Czysty Kotlin, bez importów Androida
- [X] T008 [US2] W `MAIN/ui/settings/SettingsViewModel.kt` dodać `pumpingCompanyPhoneText: String = ""` do `SettingsUiState`, wypełniane w `init` z `configuration.pumpingCompanyPhone ?: ""`, oraz `fun savePumpingCompanyPhone(text: String): String?`, która woła `PhoneNumberValidator.validate(text)`. `Valid` → `tankConfigurationRepository.updatePumpingCompanyPhone(normalized)`, aktualizacja stanu na postać znormalizowaną, zwrot `null`. `Empty` → `updatePumpingCompanyPhone(null)`, zwrot `null`. `Invalid` → zwrot `reason` bez zapisu (depends on T005, T007)
- [X] T009 [US2] W `MAIN/ui/settings/SettingsScreen.kt`, pod sekcją pojemności, dodać sekcję „Numer firmy asenizacyjnej”: `OutlinedTextField` z `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)`, `isError` + `supportingText` z komunikatem błędu, oraz `Button("Zapisz numer")` wołający `viewModel.savePumpingCompanyPhone(...)`. Wzorzec lokalnego stanu i `LaunchedEffect` jak przy `capacityText`. Po udanym zapisie krótki tekst „Zapisano” pod przyciskiem (depends on T008)

**Checkpoint**: US2 działa samodzielnie; `./gradlew :app:testDebugUnitTest` zielone

---

## Phase 4: User Story 1 - Zamówienie wywozu jednym przyciskiem (Priority: P1) 🎯 MVP

**Goal**: Przycisk „Zamów wywóz” → wybór jednego z 5 dni roboczych (od pojutrza) → potwierdzenie → aplikacja sama wysyła SMS „Poproszę o wywóz {w/we dzień}” i pokazuje wynik

**Independent Test**: Ze skonfigurowanym numerem testowego telefonu nacisnąć „Zamów wywóz”, wybrać dzień, „Wyślij”; na testowy telefon przychodzi dokładnie 1 SMS z poprawną treścią (contracts/sms-message.md), a aplikacja pokazuje potwierdzenie (quickstart.md, scenariusze 2, 4–10)

### Tests for User Story 1

- [X] T010 [P] [US1] Utworzyć `TEST/domain/order/OrderDaysTest.kt` (JUnit4) dla `availableOrderDays(today)`. Dla każdego z 7 dni tygodnia jako `today` (np. tydzień 2026-09-21 pon … 2026-09-27 niedz) sprawdzić: 5 elementów, wszystkie pon–pt, pierwszy `>= today.plusDays(2)`, ostatni − pierwszy `< 7` dni, rosnąco, bez powtórzeń `dayOfWeek`. Przypadki jawne: śr 2026-09-23 → pt 25, pon 28, wt 29, śr 30, czw 10-01; czw 2026-09-24 → pon 28 … pt 10-02; pt → pon…pt; sob → pon…pt; niedz 2026-09-27 → wt 29, śr 30, czw 10-01, pt 10-02, pon 10-05
- [X] T011 [P] [US1] Utworzyć `TEST/domain/order/PumpingOrderMessageTest.kt` (JUnit4) sprawdzający wszystkie 7 wartości `DayOfWeek` dokładnie wg tabeli z contracts/sms-message.md i research.md §4: `"Poproszę o wywóz w poniedziałek"`, `"Poproszę o wywóz we wtorek"`, `"Poproszę o wywóz w środę"`, `"Poproszę o wywóz w czwartek"`, `"Poproszę o wywóz w piątek"`, `"Poproszę o wywóz w sobotę"`, `"Poproszę o wywóz w niedzielę"`; oraz że każda treść ma ≤ 70 znaków (1 segment UCS-2)

### Implementation for User Story 1

- [X] T012 [P] [US1] Utworzyć `MAIN/domain/order/OrderDays.kt` z `fun availableOrderDays(today: LocalDate): List<LocalDate>`: generuje kolejne dni od `today.plusDays(2)`, pomija `DayOfWeek.SATURDAY` i `DayOfWeek.SUNDAY`, zwraca pierwsze 5 (FR-004, research.md §3). Stała `ORDER_DAY_COUNT = 5`, `MIN_DAYS_AHEAD = 2`. Czysty Kotlin + `java.time`
- [X] T013 [P] [US1] Utworzyć `MAIN/domain/order/PumpingOrderMessage.kt`: `object PumpingOrderMessage` z `fun dayPhrase(day: DayOfWeek): String` (wyczerpujący `when` wg tabeli research.md §4: „w poniedziałek”, „we wtorek”, „w środę”, „w czwartek”, „w piątek”, „w sobotę”, „w niedzielę”) i `fun forDay(day: DayOfWeek): String = "Poproszę o wywóz ${dayPhrase(day)}"`: bez kropki, bez daty, bez podpisu (contracts/sms-message.md)
- [X] T014 [P] [US1] Utworzyć `MAIN/data/sms/SmsSender.kt`: `sealed interface SmsSendResult { data object Sent; data class Failed(val reason: String) }`, `interface SmsSender { suspend fun send(phoneNumber: String, text: String): SmsSendResult }` oraz `class AndroidSmsSender(context: Context) : SmsSender` wg research.md §1–2:
  - `SmsManager` przez `context.getSystemService(SmsManager::class.java)` na API ≥ 31, inaczej `SmsManager.getDefault()`; `null` → `Failed("Ten telefon nie może wysyłać SMS-ów")`;
  - `sentIntent` = `PendingIntent.getBroadcast` z jawnym `Intent(uniqueAction).setPackage(context.packageName)`, `uniqueAction` zawiera UUID, `FLAG_IMMUTABLE or FLAG_ONE_SHOT`;
  - odbiór przez `ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_NOT_EXPORTED)` w `suspendCancellableCoroutine`, wyrejestrowanie w `finally`/`invokeOnCancellation`;
  - `withTimeoutOrNull(60_000)` → `Failed("Nie udało się potwierdzić wysłania SMS-a")`, bez automatycznego ponawiania;
  - mapowanie `resultCode`: `Activity.RESULT_OK` → `Sent`; `RESULT_ERROR_RADIO_OFF` → „Telefon jest w trybie samolotowym lub radio jest wyłączone”; `RESULT_ERROR_NO_SERVICE` → „Brak zasięgu sieci komórkowej”; pozostałe → „Nie udało się wysłać SMS-a (kod $resultCode)”;
  - `SecurityException` → `Failed("Brak uprawnienia do wysyłania SMS-ów")`;
  - jedno `sendTextMessage(phoneNumber, null, text, sentIntent, null)`, bez multipart i bez `deliveryIntent`;
  - logowanie przez istniejący `MAIN/util/Logging.kt`, bez treści SMS i numeru w logach.
- [X] T015 [US1] W `MAIN/AppContainer.kt` dodać `val smsSender: SmsSender = AndroidSmsSender(context)` (depends on T014)
- [X] T016 [US1] W `MAIN/ui/home/HomeViewModel.kt` dodać stan zamówienia wg maszyny stanów z data-model.md. `sealed interface PumpingOrderState` z wariantami:
  - `Hidden`, `MissingPhone`;
  - `Choosing(days: List<LocalDate>, selected: LocalDate, phoneNumber: String, message: String)`;
  - `PermissionDenied(choosing: Choosing)`;
  - `Sending(day: LocalDate, phoneNumber: String)`;
  - `Sent(day: LocalDate, phoneNumber: String)`;
  - `Failed(choosing: Choosing, reason: String)`.

  Pole `pumpingOrderState` w istniejącym UiState, domyślnie `Hidden`. Metody:
  - `onOrderPumpingClick()`: czyta `tankConfigurationRepository.get().pumpingCompanyPhone`; `null` → `MissingPhone`, inaczej `Choosing` z `availableOrderDays(LocalDate.now())` i `selected = days.first()`;
  - `onOrderDaySelected(day)`: aktualizuje `selected` i `message = PumpingOrderMessage.forDay(day.dayOfWeek)`;
  - `onOrderConfirm()`: działa TYLKO gdy stan to `Choosing`; synchronicznie ustawia `Sending` PRZED `viewModelScope.launch { smsSender.send(...) }` (ochrona przed podwójną wysyłką, research.md §6), a wynik mapuje na `Sent` / `Failed`;
  - `onOrderPermissionDenied()` → `PermissionDenied`;
  - `onOrderRetry()` → z `Failed` / `PermissionDenied` wraca do zachowanego `Choosing` (ten sam dzień);
  - `onOrderDismiss()` → `Hidden`.

  Zamówienie NIE może wywoływać `registerPumping` ani zmieniać `PumpingEventRepository` (FR-009). Dodać `smsSender` i `tankConfigurationRepository` do konstruktora/fabryki ViewModelu, jeśli ich brak (depends on T012, T013, T015)
- [X] T017 [P] [US1] Utworzyć `MAIN/ui/home/PumpingOrderDialog.kt`: `@Composable fun PumpingOrderDialog(state: PumpingOrderState, onDaySelected, onConfirm, onRetry, onDismiss, onOpenSettings)` renderujący `AlertDialog` per stan:
  - `MissingPhone`: „Nie ustawiono numeru firmy asenizacyjnej” + „Przejdź do ustawień” / „Anuluj” (FR-008);
  - `Choosing`: tytuł „Zamów wywóz”, „Na kiedy?”, lista `RadioButton` z etykietą `"${nazwa dnia} dd.MM"`; pierwszy element z dopiskiem „(najwcześniej)”. Pod listą „SMS do: {numer}” i treść w cudzysłowie „…”, przyciski „Anuluj” / „Wyślij” (FR-004, FR-006, contracts/sms-message.md → kontrakt UI);
  - `PermissionDenied`: „Aby zamówić wywóz, aplikacja potrzebuje uprawnienia do wysyłania SMS-ów. SMS wysyłany jest tylko po Twoim potwierdzeniu.” + „Spróbuj ponownie” / „Anuluj”;
  - `Sending`: „Wysyłanie…” z `CircularProgressIndicator`, bez przycisków potwierdzenia, `onDismissRequest` ignorowane;
  - `Sent`: „Zamówiono wywóz {fraza_dnia} (SMS do {numer})” + „OK”;
  - `Failed`: przyczyna + „Ponów” / „Zamknij” (FR-007).

  Nazwy dni w mianowniku z `DayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl"))`, frazy z `PumpingOrderMessage.dayPhrase` (depends on T013)
- [X] T018 [US1] W `MAIN/ui/home/HomeScreen.kt`:
  - dodać `Button("Zamów wywóz")` w wierszu obok / pod „Wywóz ścieków”, zawsze aktywny, wołający `viewModel::onOrderPumpingClick` (FR-001);
  - renderować `PumpingOrderDialog`, gdy `state.pumpingOrderState != Hidden`;
  - `onOpenSettings` z dialogu → `onOrderDismiss()` + istniejący callback `onOpenSettings`;
  - „Wyślij”: przez `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` sprawdzić `ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)` (wzorzec z `CameraCaptureScreen.kt`). Przyznane → `viewModel.onOrderConfirm()`, inaczej `launch(SEND_SMS)`, a w callbacku `granted` → `onOrderConfirm()`, odmowa → `onOrderPermissionDenied()`.

  (depends on T016, T017)

**Checkpoint**: MVP gotowe: zamówienie wysyła dokładnie 1 SMS z poprawną treścią; `./gradlew :app:testDebugUnitTest` zielone

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T019 Uruchomić `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug` i naprawić ewentualne błędy kompilacji/testów
- [X] T020 Przejść ręcznie wszystkie 10 scenariuszy z `specs/003-order-pumping-sms/quickstart.md` na telefonie z SIM (numer TESTOWY, nie firmy asenizacyjnej) i odhaczyć wyniki w tym pliku; szczególnie: migracja v2 → v3 bez utraty danych (1), podwójne dotknięcie = 1 SMS (8), tryb samolotowy → błąd + ponowienie (9), brak zmiany zużycia (10)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: brak zależności
- **Foundational (Phase 2)**: po Setup; blokuje wszystkie User Stories
- **US2 (Phase 3)**: po Foundational
- **US1 (Phase 4)**: po Foundational. Kod nie zależy od US2 (czyta numer z repozytorium), ale test end-to-end US1 wymaga numeru wpisanego przez UI z US2
- **Polish (Phase 5)**: po US1 i US2

### Within Each User Story

- Testy (T006, T010, T011) piszemy przed odpowiadającą implementacją i muszą najpierw nie przechodzić
- Domena (`domain/order`) → adapter (`data/sms`) → ViewModel → UI

### Parallel Opportunities

- T002 i T003 (różne pliki) równolegle; T004 i T005 po T002
- Phase 3: T006 ∥ T007; potem T008 → T009
- Phase 4: T010 ∥ T011 ∥ T012 ∥ T013 ∥ T014 (5 różnych plików); T017 równolegle z T015/T016 (zależy tylko od T013)
- US2 (Phase 3) i domena/adapter US1 (T010–T014) mogą iść równolegle po Phase 2

---

## Parallel Example: User Story 1

```text
# Najpierw razem (różne pliki, brak zależności):
T010 OrderDaysTest.kt
T011 PumpingOrderMessageTest.kt
T012 OrderDays.kt
T013 PumpingOrderMessage.kt
T014 SmsSender.kt

# Potem:
T015 AppContainer.kt  ∥  T017 PumpingOrderDialog.kt
T016 HomeViewModel.kt
T018 HomeScreen.kt
```

---

## Implementation Strategy

### MVP (obie historie P1)

1. Phase 1 + Phase 2 (uprawnienie, pole w bazie, migracja)
2. Phase 3 (US2): numer w Ustawieniach; **walidacja**: scenariusz 3 z quickstart
3. Phase 4 (US1): zamówienie SMS; **walidacja**: scenariusze 2, 4–10
4. Phase 5: pełny przebieg quickstart, APK do testów

### Incremental Delivery

Po Phase 3 można zbudować APK, w którym działa już tylko zapis numeru (bez ryzyka wysłania SMS). Po Phase 4 funkcja jest kompletna.
