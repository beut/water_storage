# Quickstart: walidacja funkcji „Zamów wywóz”

**Feature**: [spec.md](./spec.md) | **Contracts**: [contracts/sms-message.md](./contracts/sms-message.md) | **Data model**: [data-model.md](./data-model.md)

## Wymagania wstępne

- Telefon z Androidem 8.0+ (API 26), z aktywną kartą SIM, która może wysyłać SMS-y. Emulator nie wyśle prawdziwego SMS-a, ale 2 emulatory mogą wymieniać SMS-y między sobą (numer = port, np. `5554`).
- Drugi telefon (albo numer znajomego), na który trafi testowy SMS. **Nie wpisuj numeru prawdziwej firmy asenizacyjnej podczas testów.**
- Zainstalowana poprzednia wersja aplikacji z danymi (do sprawdzenia migracji).

## Automatyczne testy

```bash
cd android
./gradlew :app:testDebugUnitTest
```

Oczekiwane testy jednostkowe (JUnit4, `app/src/test/kotlin/.../domain/order/`):
- `availableOrderDays`: dla każdego dnia tygodnia jako „dziś” zwraca 5 dni pon–pt, pierwszy ≥ dziś+2, bez weekendów, zakres < 7 dni. Przypadki: środa → pt…czw; czwartek → pon…pt; piątek → pon…pt; sobota → pon…pt; niedziela → wt…pon.
- `PumpingOrderMessage`: wszystkie 7 dni zgodne z tabelą w kontrakcie (SC-002).
- `PhoneNumberValidator`: `600 123 456` → `600123456`; `+48 600-123-456` → `+48600123456`; `12345` → błąd; `60a123456` → błąd; `600+123456` → błąd; puste → `null`.

## Scenariusze ręczne (na urządzeniu)

1. **Migracja bez utraty danych**: zainstaluj nowy APK na starą wersję z odczytami. Aplikacja startuje, a odczyty, wywozy i pojemność są zachowane.
2. **Brak numeru (FR-008)**: bez ustawionego numeru naciśnij „Zamów wywóz”. Pojawia się informacja o braku numeru z przejściem do Ustawień i żaden SMS nie wychodzi.
3. **Walidacja numeru (FR-002)**: w Ustawieniach wpisz `12345` i zapisz, pojawia się błąd. Wpisz numer testowego telefonu i zapisz, a po restarcie aplikacji numer jest nadal wpisany.
4. **Lista dni (FR-004)**: naciśnij „Zamów wywóz”. Na liście jest 5 dni roboczych, pierwszy to najbliższy dzień roboczy od pojutrza (zaznaczony), bez soboty i niedzieli.
5. **Anulowanie (SC-004)**: „Anuluj” zamyka okno i SMS nie wychodzi.
6. **Uprawnienie**: przy pierwszym „Wyślij” pojawia się prośba o uprawnienie do SMS. Odmów: pojawia się wyjaśnienie, SMS nie wychodzi. Ponów i zezwól.
7. **Wysyłka (US1)**: wybierz np. wtorek i „Wyślij”. Na testowy telefon przychodzi dokładnie jeden SMS „Poproszę o wywóz we wtorek”, a w aplikacji pojawia się potwierdzenie z dniem i numerem.
8. **Podwójne dotknięcie**: szybko dotknij „Wyślij” 2 razy. Wychodzi dokładnie jeden SMS.
9. **Błąd (FR-007, SC-003)**: włącz tryb samolotowy i wyślij. Pojawia się komunikat o błędzie z „Ponów”. Wyłącz tryb samolotowy, naciśnij „Ponów”, potem „Wyślij”, a SMS wychodzi.
10. **Brak wpływu na zużycie (FR-009)**: po zamówieniu bieżące zużycie na ekranie głównym i Historia są bez zmian (brak nowego wywozu).
