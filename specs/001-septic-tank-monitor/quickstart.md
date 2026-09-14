# Quickstart: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Feature**: [spec.md](./spec.md) | **Data model**: [data-model.md](./data-model.md) | **Research**: [research.md](./research.md)

Ten przewodnik opisuje, jak uruchomić aplikację i ręcznie zweryfikować, że każda historyjka użytkownika ze spec.md działa end-to-end. Nie zawiera pełnego kodu implementacji — szczegóły implementacyjne trafiają do `tasks.md` i fazy implementacji.

## Wymagania wstępne

- Android Studio (najnowsza stabilna wersja) z zainstalowanym Android SDK API 26+.
- Urządzenie fizyczne z Androidem 8.0+ i aparatem, lub emulator z obsługą kamery (dla realnego testu OCR zalecane urządzenie fizyczne — emulatorowa kamera daje słabą jakość obrazu do rozpoznawania tekstu).
- Repozytorium sklonowane lokalnie; moduł aplikacji Android znajduje się w katalogu `android/` (patrz `plan.md` → Project Structure).

## Uruchomienie aplikacji

```bash
cd android
./gradlew installDebug
```

lub otworzyć katalog `android/` w Android Studio i uruchomić konfigurację `app` na podłączonym urządzeniu/emulatorze.

## Uruchomienie testów

```bash
cd android
./gradlew test              # testy jednostkowe logiki domenowej (obliczanie zużycia, progi ostrzeżeń, anomalie)
./gradlew connectedAndroidTest   # testy instrumentowane UI (Espresso + Compose Testing) na podłączonym urządzeniu/emulatorze
```

## Walidacja historyjek użytkownika

### US1 — Rejestrowanie odczytu licznika wody ze zdjęcia (P1)

1. Otwórz aplikację, przejdź do ekranu głównego.
2. Naciśnij akcję „Zrób zdjęcie licznika”.
3. Zrób wyraźne zdjęcie licznika wody w dobrym oświetleniu.
4. **Oczekiwany rezultat**: aplikacja pokazuje rozpoznaną wartość odczytu i prosi o potwierdzenie; po potwierdzeniu nowy odczyt pojawia się w historii, a wyświetlane zużycie od ostatniego wywozu jest przeliczone (FR-002, FR-005).
5. Powtórz, robiąc celowo nieczytelne zdjęcie (np. rozmazane) — **oczekiwany rezultat**: aplikacja sygnalizuje niepewny/nieudany odczyt i prosi o ręczne wprowadzenie/poprawienie wartości (FR-003).

### US2 — Reset stanu po wywozie ścieków (P1)

1. Zarejestruj co najmniej jeden odczyt licznika (US1).
2. Na ekranie głównym naciśnij przycisk „Wywóz ścieków”.
3. **Oczekiwany rezultat**: wyświetlane zużycie wraca do 0 m³, a w historii wywozów pojawia się nowy wpis z bieżącą datą i odczytem licznika w momencie wywozu (FR-006, FR-007).
4. Zarejestruj kolejny odczyt licznika o wyższej wartości — **oczekiwany rezultat**: zużycie liczone jest od odczytu zapisanego przy wywozie, a nie od pierwszego historycznego odczytu (FR-005).

### US3 — Ostrzeżenie o zbliżającym się zapełnieniu zbiornika (P2)

1. W ustawieniach skonfiguruj małą pojemność testową zbiornika (np. 1 m³) i próg ostrzegawczy 80% (wartość domyślna).
2. Zarejestruj odczyty licznika, aż zużycie od ostatniego wywozu osiągnie ok. 0,8 m³ (80% pojemności testowej).
3. **Oczekiwany rezultat**: aplikacja wyświetla widoczne ostrzeżenie „zbliżające się zapełnienie” (FR-009, FR-010).
4. Kontynuuj rejestrowanie odczytów aż zużycie przekroczy 1 m³ (100% pojemności).
5. **Oczekiwany rezultat**: ostrzeżenie zmienia się na bardziej pilne, wyraźnie odróżnialne od poprzedniego stanu (FR-010).

### US4 — Konfiguracja zbiornika i przypomnień (P3)

1. Przy pierwszym uruchomieniu aplikacji przejdź proces wstępnej konfiguracji i wprowadź pojemność zbiornika.
2. **Oczekiwany rezultat**: bez podania pojemności aplikacja nie wylicza ostrzeżeń (US3 wymaga tego kroku).
3. W ustawieniach włącz cykliczne przypomnienia o zdjęciu licznika i ustaw krótki interwał testowy.
4. **Oczekiwany rezultat**: aplikacja dostarcza powiadomienie systemowe zgodnie z ustawionym cyklem (FR-013), nawet gdy urządzenie jest offline.

## Weryfikacja działania offline (FR-014)

1. Włącz tryb samolotowy na urządzeniu testowym.
2. Powtórz scenariusz US1 (zdjęcie → rozpoznanie odczytu → zapis).
3. **Oczekiwany rezultat**: rozpoznawanie odczytu (OCR) oraz zapis danych działają bez żadnego połączenia z internetem.

## Weryfikacja przypadków brzegowych

- Zarejestruj odczyt z wartością niższą niż poprzedni odczyt — aplikacja MUST oznaczyć go jako anomalię i wymagać potwierdzenia/korekty przed zapisaniem (data-model.md → `MeterReading.isAnomalous`).
- Zarejestruj dwa odczyty tego samego dnia — najnowszy MUST stać się aktualnym stanem, oba MUST pozostać widoczne w historii.
