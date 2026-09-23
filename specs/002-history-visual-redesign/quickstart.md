# Quickstart: Graficzna historia wypełnienia zbiornika i wywozów

Ten przewodnik opisuje, jak uruchomić aplikację i ręcznie zweryfikować, że nowy graficzny widok zakładki Historia działa zgodnie ze scenariuszami akceptacyjnymi ze `spec.md`. Szczegóły reguł liczenia wartości — patrz `data-model.md`; szczegóły wyboru biblioteki wykresów — patrz `research.md`.

## Wymagania wstępne

- Android Studio (lub `gradlew` z linii poleceń) z zainstalowanym SDK API 34, min. emulator/urządzenie z API 26+.
- Repozytorium na branchu `002-history-visual-redesign`.
- Zależność `com.patrykandpatrick.vico:compose-m3` dodana w `android/app/build.gradle.kts` (Faza implementacji — patrz `tasks.md`).

## Uruchomienie aplikacji

```bash
cd android
./gradlew :app:installDebug
```

Otwórz aplikację na urządzeniu/emulatorze.

## Scenariusz 1 — Wykres trendu z co najmniej dwoma odczytami (User Story 1, P1)

1. Na ekranie głównym zarejestruj co najmniej dwa odczyty licznika w różnym czasie (np. „Wpisz odczyt ręcznie" dwukrotnie, z rosnącą wartością).
2. Otwórz zakładkę **Historia**.
3. **Oczekiwany wynik**: widoczny jest wykres liniowy (nie lista tekstowa) z punktami uporządkowanymi chronologicznie na osi X.
4. Jeśli pojemność zbiornika jest skonfigurowana w Ustawieniach → wartości na osi Y wyrażone są w %. Jeśli nie skonfigurowana → wartości w m³, wraz z komunikatem informującym o możliwości skonfigurowania pojemności.

Odpowiada: spec.md → User Story 1, Acceptance Scenarios 1–3; FR-001, FR-003, FR-004.

## Scenariusz 2 — Wywozy widoczne na tle trendu (User Story 2, P1)

1. Mając już co najmniej jeden odczyt, naciśnij „Wywóz ścieków" na ekranie głównym.
2. Zarejestruj kolejny odczyt po wywozie.
3. Otwórz zakładkę Historia.
4. **Oczekiwany wynik**: punkt wywozu jest oznaczony innym kształtem/ikoną niż punkty odczytów (nie tylko innym kolorem), widoczny bez dodatkowej interakcji, we właściwym miejscu chronologicznym (wartość wypełnienia spada do ~0 tuż po wywozie).

Odpowiada: spec.md → User Story 2, Acceptance Scenarios 1–2; FR-002, FR-010 (Clarifications: rozróżnienie kształtem, nie tylko kolorem).

## Scenariusz 3 — Szczegóły punktu po dotknięciu (User Story 3, P2)

1. Na wykresie w zakładce Historia dotknij dowolnego punktu odczytu.
2. **Oczekiwany wynik**: pojawia się znacznik/etykieta z dokładną datą, godziną i wartością tego odczytu.
3. Dotknij punktu oznaczającego wywóz.
4. **Oczekiwany wynik**: pojawia się znacznik z dokładną datą wywozu i powiązanym odczytem bazowym.

Odpowiada: spec.md → User Story 3, Acceptance Scenarios 1–2; FR-005.

## Scenariusz 4 — Stan pusty i duża historia (User Story 4, P3)

1. Na świeżej instalacji (bez żadnych odczytów) otwórz zakładkę Historia.
   **Oczekiwany wynik**: przyjazny komunikat zachęcający do zarejestrowania pierwszego odczytu, brak pustego/błędnego ekranu.
2. Zarejestruj ≥100 punktów historii (odczyty + wywozy łącznie — można to zrobić pomocniczym skryptem testowym lub wielokrotnym ręcznym wpisywaniem podczas testów manualnych).
3. Otwórz zakładkę Historia.
   **Oczekiwany wynik**: wykres pozostaje czytelny — punkty się nie nakładają nieczytelnie; przewijanie poziome (pan) i powiększanie gestem (pinch-to-zoom) działają płynnie.

Odpowiada: spec.md → User Story 4, Acceptance Scenarios 1–2; FR-006, FR-007, FR-008; SC-004.

## Scenariusz 5 — Próg ostrzegawczy na wykresie

1. Skonfiguruj pojemność zbiornika w Ustawieniach (np. niską wartość testową).
2. Zarejestruj odczyty zbliżające zużycie do progu ostrzegawczego (domyślnie 80%).
3. Otwórz zakładkę Historia.
   **Oczekiwany wynik**: pozioma linia referencyjna na poziomie progu ostrzegawczego jest widoczna na wykresie, w kolorze spójnym z ostrzeżeniem na ekranie głównym.

Odpowiada: spec.md → FR-009.

## Weryfikacja braku utraty danych (SC-005)

Dla każdego punktu widocznego wcześniej w starym widoku tekstowym (data, wartość, źródło odczytu dla odczytów; data i odczyt bazowy dla wywozów) sprawdź, że ta sama informacja jest dostępna w nowym widoku — poprzez sam wykres lub przez szczegóły punktu (Scenariusz 3).

## Testy jednostkowe (logika, bez UI)

```bash
cd android
./gradlew :app:testDebugUnitTest --tests "pl.watershed.septictank.domain.history.*"
```

Weryfikuje regułę wyliczania `fillLiters`/`fillPercentOfCapacity` z `data-model.md` (m.in.: brak wywozów → baseline to pierwszy odczyt; wielokrotne cykle wywóz→odczyty; brak skonfigurowanej pojemności → `fillPercentOfCapacity = null`).
