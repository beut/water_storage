# Research: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Feature**: [spec.md](./spec.md) | **Date**: 2026-09-14

Ten dokument rozstrzyga niewiadome techniczne (`NEEDS CLARIFICATION` z sekcji Technical Context planu) na podstawie wymagań specyfikacji, w szczególności decyzji z sekcji `## Clarifications` (odczyt OCR w pełni lokalny/offline, jednostka m³ z dokładnością do 0,001 m³, jeden zbiornik/licznik na instalację).

## Język i platforma

- **Decision**: Kotlin, natywna aplikacja Android, minimalny poziom API 26 (Android 8.0).
- **Rationale**: Kotlin jest oficjalnie rekomendowanym językiem dla nowych aplikacji Android; API 26 zapewnia dostęp do nowoczesnych bibliotek (CameraX, WorkManager, ML Kit) przy pokryciu zdecydowanej większości aktywnych urządzeń, bez konieczności utrzymywania kompatybilności wstecz z bardzo starymi wersjami systemu, co nie jest wymagane przez specyfikację.
- **Alternatives considered**: Flutter/React Native (cross-platform) — odrzucone, ponieważ spec wprost wymaga aplikacji na Androida, a natywny dostęp do aparatu i on-device OCR jest prostszy i lepiej wspierany natywnie; niższy minimalny poziom API (np. 21) — odrzucony, bo bez istotnej korzyści zwiększa nakład na zapewnienie kompatybilności.

## Interfejs użytkownika

- **Decision**: Jetpack Compose jako toolkit UI.
- **Rationale**: Deklaratywny, oficjalnie rekomendowany przez Google standard dla nowych aplikacji Android; upraszcza budowę ekranów (lista odczytów, ekran ostrzeżenia, ustawienia) i ich testowanie.
- **Alternatives considered**: Widoki XML (View system) — odrzucone jako starszy wzorzec wymagający więcej kodu bez dodatkowej korzyści dla tego zakresu.

## Rozpoznawanie odczytu licznika (OCR)

- **Decision**: Google ML Kit Text Recognition (model on-device, pakowany z aplikacją / pobierany lokalnie), uruchamiany wyłącznie na urządzeniu.
- **Rationale**: Bezpośrednio realizuje FR-014 (odczyt w pełni lokalny, offline) ustalone w sesji `/speckit-clarify`; ML Kit jest darmowy, dobrze udokumentowany, zoptymalizowany pod Android i nie wymaga wysyłania zdjęć poza urządzenie.
- **Alternatives considered**: Usługa OCR w chmurze (np. Cloud Vision API) — odrzucona, ponieważ wymagałaby stałego połączenia z internetem i wysyłania zdjęć na zewnątrz, co jest sprzeczne z decyzją z sesji clarify; Tesseract (tess-two) — odrzucony jako mniej dokładny i trudniejszy w utrzymaniu niż aktywnie rozwijany ML Kit.

## Przechwytywanie zdjęcia licznika

- **Decision**: CameraX (Jetpack).
- **Rationale**: Oficjalna, rekomendowana biblioteka Android do obsługi aparatu, upraszcza integrację z ML Kit (wspólne API `ImageAnalysis`) oraz obsługę różnych urządzeń/orientacji.
- **Alternatives considered**: Intencja systemowa `MediaStore.ACTION_IMAGE_CAPTURE` — odrzucona jako mniej elastyczna przy podglądzie na żywo i integracji z automatycznym odczytem.

## Przechowywanie danych

- **Decision**: Room (biblioteka SQLite) dla danych strukturalnych (odczyty, zdarzenia wywozu, konfiguracja zbiornika); zdjęcia liczników przechowywane jako pliki w pamięci wewnętrznej aplikacji (app-specific storage), z odwołaniem (ścieżką) zapisanym w rekordzie odczytu w Room.
- **Rationale**: Zgodne z założeniem lokalnego przechowywania danych bez synchronizacji w chmurze (sekcja Assumptions); skala danych (setki odczytów rocznie dla jednego zbiornika, zgodnie z decyzją „jeden zbiornik/licznik na instalację”) jest trywialna dla SQLite. Przechowywanie zdjęć jako plików (a nie BLOB w bazie) jest standardową praktyką na Androidzie i ułatwia zarządzanie pamięcią.
- **Alternatives considered**: DataStore/Preferences — odrzucone jako nieodpowiednie dla danych relacyjnych (historia odczytów, historia wywozów); zewnętrzna baza chmurowa — odrzucona jako niezgodna z wymogiem działania offline.

## Przypomnienia i powiadomienia

- **Decision**: WorkManager (cykliczne `PeriodicWorkRequest`) do wyzwalania przypomnień o zdjęciu licznika oraz `NotificationCompat` do wyświetlania przypomnień i ostrzeżeń o zbliżającym się/przekroczonym zapełnieniu zbiornika.
- **Rationale**: WorkManager to standardowe, zgodne z wytycznymi Android rozwiązanie do zadań cyklicznych odpornych na ograniczenia systemu (Doze, restart urządzenia); natywnie wspiera działanie offline.
- **Alternatives considered**: `AlarmManager` bezpośrednio — odrzucony jako niższopoziomowy i mniej odporny na optymalizacje baterii niż WorkManager.

## Architektura aplikacji

- **Decision**: Pojedynczy moduł aplikacji Android (bez wydzielonych modułów Gradle), warstwowa struktura wewnątrz modułu: `data` (Room, pliki zdjęć, ML Kit), `domain` (logika obliczania zużycia i progów ostrzeżeń), `ui` (ekrany Compose, ViewModel). Wstrzykiwanie zależności ręczne (proste fabryki/kontener), bez frameworka DI.
- **Rationale**: Zakres v1 (jeden zbiornik/licznik, brak backendu, brak wielu użytkowników) nie uzasadnia nakładu na framework DI (np. Hilt) ani podział na wiele modułów Gradle — zgodnie z zasadą prostoty (YAGNI); warstwowy podział wewnątrz jednego modułu wystarcza do testowalności logiki domenowej niezależnie od UI.
- **Alternatives considered**: Hilt/Dagger — odrzucone jako nadmiarowe dla obecnego zakresu, do rozważenia jeśli aplikacja urośnie (np. wsparcie wielu zbiorników w przyszłej wersji); podział na moduły Gradle (`:data`, `:domain`, `:ui`) — odrzucony jako przedwczesna optymalizacja dla pojedynczego, niewielkiego modułu aplikacji.

## Testowanie

- **Decision**: JUnit4 + lokalne testy jednostkowe (logika obliczania zużycia, progów ostrzeżeń, wykrywania anomalii) w `src/test`; Espresso + Compose Testing API dla testów instrumentowanych kluczowych przepływów UI (rejestrowanie odczytu, reset, ostrzeżenie) w `src/androidTest`.
- **Rationale**: Standardowy, w pełni wspierany przez Android Gradle Plugin zestaw narzędzi testowych; pozwala niezależnie testować logikę domenową (szybkie testy JVM) i przepływy UI (testy instrumentowane) zgodnie z niezależną testowalnością historyjek użytkownika w spec.md.
- **Alternatives considered**: Robolectric jako zamiennik testów instrumentowanych — pominięty w v1 dla uproszczenia zestawu narzędzi; można dodać później dla przyspieszenia CI.

## Cele wydajnościowe i ograniczenia

- **Decision**: Cały przepływ „zdjęcie → rozpoznany odczyt → zaktualizowane zużycie” (SC-001) musi zamknąć się w czasie zauważalnie krótszym niż limit 30 sekund określony w spec, z przetwarzaniem OCR wykonywanym asynchronicznie w tle, aby nie blokować UI. Aplikacja musi działać w pełni bez połączenia z internetem (FR-014).
- **Rationale**: Bezpośrednio z SC-001 i FR-014; on-device ML Kit Text Recognition typowo zwraca wynik dla pojedynczego zdjęcia w czasie rzędu pojedynczych sekund na współczesnym sprzęcie, co pozostawia zapas czasowy na interakcję użytkownika (potwierdzenie/korektę odczytu) w ramach 30-sekundowego budżetu.
- **Alternatives considered**: Brak — wartości wynikają wprost z zaakceptowanych kryteriów sukcesu w spec.md.

## Podsumowanie rozstrzygniętych niewiadomych (Technical Context)

| Niewiadoma | Rozstrzygnięcie |
|---|---|
| Language/Version | Kotlin, Android API 26+ |
| Primary Dependencies | Jetpack Compose, CameraX, ML Kit Text Recognition (on-device), Room, WorkManager |
| Storage | Room (SQLite) + pliki zdjęć w pamięci wewnętrznej aplikacji |
| Testing | JUnit4 (unit), Espresso + Compose Testing (instrumented) |
| Target Platform | Android (telefony), API 26+ |
| Project Type | mobile-app (pojedynczy moduł) |
| Performance Goals | Zgodne z SC-001…SC-005 z spec.md |
| Constraints | W pełni offline (FR-014); jeden zbiornik/licznik na instalację |
| Scale/Scope | Pojedynczy użytkownik, pojedynczy zbiornik, setki odczytów/rok |
