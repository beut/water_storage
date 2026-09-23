# Phase 0 Research: Graficzna historia wypełnienia zbiornika i wywozów

## 1. Biblioteka wykresów dla Jetpack Compose

**Decision**: Użyć [Vico](https://github.com/patrykandpatrick/vico) (`com.patrykandpatrick.vico:compose-m3`, wersja **1.15.0** — nie najnowsza 3.x, patrz Uwaga niżej), natywnej biblioteki wykresów dla Jetpack Compose z motywem Material 3.

**Uwaga z implementacji (aktualizacja po próbie budowy, 2026-09-22)**: Najnowsza wersja Vico (3.3.1, dostępna w chwili planowania) wymaga `compose-bom` 2026.06.00, co wymusza `compileSdk` ≥ 37 i Android Gradle Plugin ≥ 9.1.0 (potwierdzone błędem `checkDebugAarMetadata` przy próbie budowy). Projekt używa AGP 8.5.2 i `compileSdk = 34` — podniesienie tych wersji tylko na potrzeby jednego ekranu byłoby zmianą wykraczającą poza zakres tej funkcji (ryzyko regresji w CameraX/ML Kit, które już są wrażliwe na wersje AGP/SDK) i sprzeczną z założeniem "redesign dotyczy wyłącznie prezentacji wizualnej" z `plan.md`. Sprawdzono historię wydań Vico (`compose-m3/maven-metadata.xml` + metadane Gradle modułów) i **Vico 1.15.0 wymaga dokładnie `compose-bom` 2024.06.00** — identycznego z już używanym w projekcie — i buduje się bez żadnych zmian w `compileSdk`/AGP (zweryfikowano: `./gradlew :app:testDebugUnitTest` przechodzi). Konsekwencja: Vico 1.15.0 używa **starszego API** (sprzed przemianowania na "Cartesian" w Vico 2.0) — `Chart`/`ChartEntryModelProducer`/`LineChart`/`Marker` zamiast `CartesianChartHost`/`CartesianChartModelProducer`/`LineCartesianLayer`/`CartesianMarker`. Poniższe sekcje badawcze i `data-model.md`/`tasks.md` pozostają aktualne co do **koncepcji** (linia trendu, markery kształtem, próg ostrzegawczy, pan/zoom), a dokładne nazwy API zostały zweryfikowane bezpośrednio w kodzie implementacji względem Vico 1.15.0.

**Rationale**:
- Renderuje w 100% lokalnie (Canvas w Compose) — brak wywołań sieciowych, zgodne z wymogiem pełnej offline'owości aplikacji (FR-014 z 001-septic-tank-monitor).
- Natywne wsparcie dla Jetpack Compose (nie wymaga mostkowania `AndroidView`/View system, jak starsze biblioteki np. MPAndroidChart).
- `compose-m3` integruje się z `MaterialTheme.colorScheme`, dzięki czemu wykres automatycznie dziedziczy kolory motywu aplikacji (spójne z istniejącym użyciem `MaterialTheme.colorScheme.tertiary`/`.error` w `HomeScreen.WarningBanner`).
- `LineCartesianLayer` pokrywa potrzebę wykresu liniowego (Clarifications, Session 2026-09-22).
- `CartesianMarker` obsługuje pokazywanie szczegółów punktu po dotknięciu (domyślnie „show on tap”) z możliwością własnego formatowania wartości i etykiety (FR-005, User Story 3).
- Wbudowana obsługa przewijania i powiększania osi (`rememberVicoScrollState`, `rememberVicoZoomState`, przekazywane do `CartesianChartHost`) pokrywa potrzebę poziomego przewijania (pan) i pinch-to-zoom bez pisania własnej obsługi gestów (FR-006, FR-008; Clarifications, Session 2026-09-22).
- `minSdk` wymagany przez Vico to 23 — projekt ma `minSdk = 26`, więc jest w pełni kompatybilny bez podnoszenia minimalnej wersji Androida.
- Aktywnie rozwijana (wydania do połowy 2026), wystarczająco dojrzała jak na potrzeby jednego ekranu prezentacyjnego.

**Alternatives considered**:
- **Ręczny wykres na `Canvas` w Compose (bez biblioteki)**: odrzucone — wymagałoby samodzielnej implementacji hit-testingu punktów, gestów pan/pinch-zoom oraz skalowania osi, co jest znacznym nakładem pracy i ryzykiem błędów w porównaniu do dojrzałej biblioteki, przy braku realnej korzyści (brak potrzeby unikania zależności — projekt już korzysta z kilku zewnętrznych bibliotek: CameraX, ML Kit, Room, WorkManager).
- **MPAndroidChart**: odrzucone — biblioteka oparta o klasyczny system Widoków (View), wymagałaby mostkowania przez `AndroidView` w drzewie Compose; mniej spójna integracja z motywem Material 3 i stanem Compose (`StateFlow`) niż biblioteka natywna dla Compose.
- **YCharts**: rozważane jako alternatywa natywna dla Compose, ale mniej aktywnie rozwijana i z węższym wsparciem dla własnych kształtów markerów niż Vico w chwili badania.

## 2. Reprezentacja wypełnienia zbiornika w historycznych punktach wykresu

**Decision**: Wartość „wypełnienia" każdego historycznego odczytu liczona jest jako różnica między wartością tego odczytu a wartością odczytu bazowego aktywnego w danym momencie (odczyt powiązany z najbliższym wcześniejszym zdarzeniem wywozu, albo pierwszy zarejestrowany odczyt w ogóle, jeśli żaden wywóz jeszcze nie miał miejsca przed tym punktem) — analogicznie do istniejącej logiki `UsageCalculator`, ale zastosowanej do każdego punktu historii z osobna, a nie tylko do najnowszego odczytu.

**Rationale**:
- Istniejący `UsageCalculator.calculate()` liczy to wyłącznie dla *bieżącego* stanu (najnowszy odczyt względem najnowszego wywozu). Zakładka Historia z definicji musi pokazać ten sam rodzaj wartości dla *każdego* historycznego punktu, aby wykres poprawnie odzwierciedlał cykle napełniania i opróżniania zbiornika (piłokształtny przebieg: wzrost między wywozami, spadek do ~0 tuż po wywozie) — zgodne z FR-003/FR-004 (prezentacja jako % pojemności lub m³ zużycia, nie jako surowa wartość licznika).
- Surowa wartość licznika (`MeterReadingEntity.valueLiters`) rośnie monotonicznie przez cały czas życia licznika i sama w sobie nie reprezentuje wypełnienia zbiornika — dopiero różnica względem aktywnego punktu bazowego to robi.
- Ponowne użycie tego samego sposobu liczenia co `UsageCalculator` zapewnia spójność między wartością widoczną na ekranie głównym (bieżące zużycie) a najnowszym punktem na wykresie historii.

**Alternatives considered**:
- **Wykres surowej wartości licznika**: odrzucone — nie odpowiada na pytanie użytkownika „jak wypełniony jest zbiornik", tylko pokazuje zawsze rosnącą wartość licznika wody, myląca wizualnie i niezgodna z FR-003/FR-004.
- **Przeliczanie w warstwie UI (ViewModel) bez osobnej funkcji domenowej**: odrzucone — logika iteracji po historii z dopasowywaniem aktywnego punktu bazowego dla każdego odczytu jest czystą logiką biznesową, łatwo testowalną w izolacji (jak `UsageCalculator`); umieszczenie jej w `domain/history` zamiast bezpośrednio w `ViewModel` jest zgodne z istniejącym podziałem warstw w projekcie i umożliwia test jednostkowy bez Androida.

## 3. Oznaczanie progu ostrzegawczego na wykresie

**Decision**: Gdy pojemność zbiornika jest skonfigurowana, na wykresie rysowana jest pozioma linia referencyjna na poziomie `warningThresholdPercent` (domyślnie 80%, `TankConfigurationEntity.warningThresholdPercent`) przy użyciu mechanizmu adnotacji/dekoracji warstwy w Vico (`CartesianLayer` decoration / horizontal line), w kolorze spójnym z `MaterialTheme.colorScheme.tertiary` (ten sam kolor co ostrzeżenie „APPROACHING" na ekranie głównym).

**Rationale**: FR-009 wymaga wizualnego oznaczenia progu; wykorzystanie tego samego koloru co istniejący `WarningBanner` w `HomeScreen` zachowuje spójność wizualną języka ostrzeżeń w całej aplikacji, bez wprowadzania nowej palety kolorów.

**Alternatives considered**: Osobny, nowy kolor dla linii progu — odrzucone jako niepotrzebna niespójność z już istniejącym językiem wizualnym ostrzeżeń w aplikacji.
