# Feature Specification: Graficzna historia wypełnienia zbiornika i wywozów

**Feature Branch**: `002-history-visual-redesign`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "popraw zakladke historia. chcialbym zeby pokazywala odczyty (wypelnienie zbiornika) oraz terminy wywozu w elegancki graficzny sposob, zamiast obecnego"

## Clarifications

### Session 2026-09-22

- Q: Jaką formę wizualną powinien przyjąć główny wykres trendu wypełnienia zbiornika w zakładce Historia? → A: Wykres liniowy (line chart) z punktami na osi czasu, łączący kolejne odczyty linią trendu
- Q: Czy oznaczenia wywozów na wykresie muszą być rozróżnialne od odczytów w inny sposób niż tylko kolorem? → A: Odrębny kształt/ikona markera wywozu (niezależnie od koloru) — czytelne również dla osób niedowidzących barw
- Q: W jaki sposób użytkownik ma przeglądać wykres, gdy historia zawiera bardzo dużo punktów? → A: Poziome przewijanie (pan) + powiększanie gestem (pinch-to-zoom) po całej osi czasu

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Wizualny trend wypełnienia zbiornika (Priority: P1)

Użytkownik otwiera zakładkę Historia i od razu widzi, jak wypełnienie zbiornika zmieniało się w czasie, w formie czytelnego wykresu, zamiast listy surowych wartości tekstowych.

**Why this priority**: To jest podstawowy cel redesignu — obecny widok (dwie osobne listy tekstowe) wymaga od użytkownika samodzielnego zestawiania dat i wartości w głowie, aby zrozumieć trend. Wykres dostarcza tę wartość natychmiast, jednym spojrzeniem.

**Independent Test**: Można przetestować samodzielnie, zarejestrowawszy kilka odczytów licznika w różnym czasie i sprawdzając, że zakładka Historia prezentuje je jako wykres trendu wypełnienia zbiornika, a nie jako listę tekstową.

**Acceptance Scenarios**:

1. **Given** użytkownik ma zarejestrowane co najmniej dwa odczyty licznika, **When** otworzy zakładkę Historia, **Then** widzi graficzny wykres przedstawiający zmianę wypełnienia zbiornika w czasie, uporządkowany chronologicznie.
2. **Given** pojemność zbiornika jest skonfigurowana, **When** użytkownik przegląda wykres, **Then** wartości prezentowane są jako procent wypełnienia zbiornika.
3. **Given** pojemność zbiornika NIE jest skonfigurowana, **When** użytkownik przegląda wykres, **Then** wartości prezentowane są jako bezwzględne zużycie w m³, a aplikacja sygnalizuje, że wypełnienie procentowe wymaga skonfigurowania pojemności.

---

### User Story 2 - Terminy wywozów widoczne na tle trendu (Priority: P1)

Użytkownik widzi na tym samym widoku, kiedy miały miejsce wywozy ścieków, wyraźnie odróżnione od odczytów licznika, dzięki czemu od razu kojarzy spadki wypełnienia zbiornika z konkretnymi wywozami.

**Why this priority**: Terminy wywozów są drugą kluczową informacją, o którą prosi użytkownik, i mają sens tylko w zestawieniu z trendem wypełnienia (P1) — stanowią razem jedną spójną, MVP-ową funkcję.

**Independent Test**: Można przetestować samodzielnie, rejestrując wywóz ścieków pomiędzy odczytami licznika i sprawdzając, że jest on widoczny na wykresie/osi czasu jako odrębny, rozpoznawalny element (nie odczyt licznika), w prawidłowym miejscu chronologicznym.

**Acceptance Scenarios**:

1. **Given** użytkownik zarejestrował co najmniej jeden wywóz ścieków, **When** otworzy zakładkę Historia, **Then** wywóz jest oznaczony na osi czasu w sposób wizualnie odróżnialny od punktów odczytów licznika, bez potrzeby dodatkowej interakcji.
2. **Given** wykres zawiera zarówno odczyty, jak i wywozy, **When** użytkownik go przegląda, **Then** kolejność chronologiczna wszystkich elementów (odczytów i wywozów) jest zachowana i zgodna z rzeczywistymi datami.

---

### User Story 3 - Szczegóły pojedynczego punktu historii (Priority: P2)

Użytkownik dotyka wybranego punktu na wykresie (odczytu lub wywozu), aby zobaczyć jego dokładną datę, godzinę i wartość, gdy interesuje go konkretny szczegół, a nie tylko ogólny trend.

**Why this priority**: Wykres pokazuje trend, ale nie zawsze pozwala odczytać dokładne wartości liczbowe — ta funkcja uzupełnia P1/P2, zachowując precyzję dostępną w poprzednim widoku tekstowym, ale nie jest niezbędna do samego zrozumienia trendu.

**Independent Test**: Można przetestować samodzielnie, dotykając dowolnego punktu na wykresie i sprawdzając, że wyświetlone zostają dokładna data, godzina oraz wartość (i źródło odczytu, jeśli dotyczy) tego konkretnego punktu.

**Acceptance Scenarios**:

1. **Given** wykres zawiera punkt odczytu licznika, **When** użytkownik go dotknie, **Then** aplikacja pokazuje dokładną datę, godzinę i wartość odczytu (m³) tego punktu.
2. **Given** wykres zawiera oznaczenie wywozu, **When** użytkownik je dotknie, **Then** aplikacja pokazuje dokładną datę wywozu oraz powiązany odczyt licznika stanowiący punkt bazowy.

---

### User Story 4 - Czytelny widok przy braku danych lub przy dużej historii (Priority: P3)

Użytkownik, który dopiero zaczyna korzystać z aplikacji (brak danych) albo korzysta z niej od dawna (setki punktów historii), zawsze widzi czytelny, uporządkowany widok zamiast pustego ekranu lub nieczytelnego natłoku danych.

**Why this priority**: Poprawia jakość doświadczenia na skrajnych przypadkach, ale podstawowa wartość funkcji (P1, P2) działa niezależnie od tego, jak obsłużone są te przypadki brzegowe.

**Independent Test**: Można przetestować samodzielnie, otwierając zakładkę Historia na koncie bez żadnych danych (oczekiwany czytelny stan pusty) oraz na koncie z bardzo dużą liczbą zarejestrowanych odczytów (oczekiwany czytelny wykres bez nakładających się elementów).

**Acceptance Scenarios**:

1. **Given** użytkownik nie zarejestrował jeszcze żadnego odczytu ani wywozu, **When** otworzy zakładkę Historia, **Then** widzi przyjazny komunikat zachęcający do zarejestrowania pierwszego odczytu, zamiast pustego lub błędnego widoku.
2. **Given** użytkownik ma zarejestrowane co najmniej 100 punktów historii (odczytów i wywozów łącznie), **When** otworzy zakładkę Historia, **Then** wykres pozostaje czytelny — elementy się nie nakładają, a użytkownik może przewijać/przeglądać całą historię.

---

### Edge Cases

- Co się dzieje, gdy jest tylko jeden odczyt i żaden wywóz? Wykres pokazuje pojedynczy punkt bez linii trendu, bez błędu.
- Co się dzieje, gdy odczyt został oznaczony jako anomalia (np. wartość niższa niż poprzednia, wymagająca potwierdzenia użytkownika)? Punkt pozostaje widoczny na wykresie, ale jest wizualnie oznaczony jako nietypowy/skorygowany.
- Co się dzieje, gdy wywóz następuje bezpośrednio po sobie bez żadnego odczytu pomiędzy? Oba wywozy są widoczne na osi czasu we właściwej kolejności chronologicznej.
- Co się dzieje, gdy odczyt nie ma powiązanego zdjęcia (wpis ręczny, FR-015 z 001)? Punkt jest prezentowany normalnie na wykresie, a widok szczegółowy nie pokazuje miniatury zdjęcia.
- Co się dzieje przy bardzo długim okresie bez żadnej aktywności (np. przerwa kilkumiesięczna)? Oś czasu wykresu odzwierciedla rzeczywisty upływ czasu między punktami, zamiast rozkładać punkty równomiernie.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST zastąpić obecne dwie osobne listy tekstowe w zakładce Historia jednym wykresem liniowym (line chart) prezentującym trend wypełnienia zbiornika w czasie, z punktami na osi czasu połączonymi linią trendu, na podstawie historii odczytów licznika.
- **FR-002**: System MUST oznaczać zdarzenia wywozu ścieków na tej samej osi czasu co odczyty licznika za pomocą odrębnego kształtu/ikony markera (nie tylko koloru), tak aby były odróżnialne od punktów odczytów również dla użytkowników niedowidzących barw, bez konieczności dodatkowej interakcji użytkownika.
- **FR-003**: System MUST prezentować wypełnienie zbiornika jako wartość procentową pojemności, gdy pojemność zbiornika jest skonfigurowana (Tank Configuration z 001-septic-tank-monitor).
- **FR-004**: System MUST prezentować bezwzględne zużycie w m³ zamiast wartości procentowej, gdy pojemność zbiornika nie jest skonfigurowana, oraz informować użytkownika, że wypełnienie procentowe wymaga skonfigurowania pojemności.
- **FR-005**: System MUST umożliwiać użytkownikowi zobaczenie dokładnej daty, godziny i wartości dla dowolnego pojedynczego punktu historii (odczytu lub wywozu) poprzez interakcję z tym punktem na wykresie.
- **FR-006**: System MUST zachować dostęp do pełnej chronologicznej historii odczytów i wywozów (nie tylko do ostatnich N punktów) poprzez poziome przewijanie (pan) osi czasu wykresu.
- **FR-007**: System MUST wyświetlać przyjazny stan pusty, gdy nie istnieje jeszcze żaden zarejestrowany odczyt ani wywóz.
- **FR-008**: System MUST zachowywać czytelność widoku (brak nakładających się na siebie elementów graficznych) niezależnie od liczby zarejestrowanych punktów historii, umożliwiając użytkownikowi powiększenie wykresu gestem (pinch-to-zoom), aby rozdzielić gęsto rozmieszczone punkty.
- **FR-009**: System MUST wizualnie oznaczać próg ostrzegawczy pojemności zbiornika (Tank Configuration) na wykresie, gdy pojemność jest skonfigurowana, aby użytkownik widział odległość bieżącego wypełnienia od progu ostrzegawczego.
- **FR-010**: System MUST zachować wszystkie dane dostępne w poprzednim widoku tekstowym (data, wartość, źródło odczytu; data i odczyt bazowy wywozu) — redesign nie może powodować utraty informacji, jedynie zmianę sposobu ich prezentacji.

### Key Entities *(include if feature involves data)*

- **Odczyt licznika (Water Meter Reading)**: istniejąca encja z 001-septic-tank-monitor; w tej funkcji prezentowana jako punkt na wykresie trendu wypełnienia zbiornika.
- **Zdarzenie wywozu ścieków (Pumping Event)**: istniejąca encja z 001-septic-tank-monitor; w tej funkcji prezentowana jako odrębnie oznaczony punkt/marker na tej samej osi czasu co odczyty.
- **Konfiguracja zbiornika (Tank Configuration)**: istniejąca encja z 001-septic-tank-monitor; jej pojemność i próg ostrzegawczy determinują, czy wykres prezentuje wartości procentowe czy bezwzględne, oraz gdzie na wykresie znajduje się linia progu ostrzegawczego.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Użytkownik potrafi ocenić ogólny trend wypełnienia zbiornika (rosnący/malejący/stabilny) jednym spojrzeniem na zakładkę Historia, bez czytania pojedynczych wartości tekstowych.
- **SC-002**: Terminy wywozów są rozpoznawalne jako odrębne od odczytów licznika bez żadnej dodatkowej interakcji, w 100% przypadków posiadania takich danych.
- **SC-003**: Użytkownik może uzyskać dokładną wartość i datę dowolnego historycznego punktu w nie więcej niż jednym dotknięciu ekranu.
- **SC-004**: Widok pozostaje w pełni czytelny (brak przycinania lub nakładania się elementów) przy co najmniej 100 zarejestrowanych punktach historii.
- **SC-005**: Nowy widok historii udostępnia 100% informacji dostępnych w poprzednim widoku tekstowym — żadna informacja nie jest tracona podczas redesignu.

## Assumptions

- Wizualizacja przyjmuje formę wykresu liniowego (line chart) wypełnienia zbiornika w czasie (patrz Clarifications, Session 2026-09-22), na którym zdarzenia wywozu oznaczone są jako odrębne markery na tej samej osi czasu.
- Zakres czasowy wykresu obejmuje całą dostępną historię, z możliwością poziomego przewijania (pan) i powiększania gestem (pinch-to-zoom) — patrz Clarifications, Session 2026-09-22; ograniczanie się np. do "ostatnich 30 dni" jest poza zakresem tej iteracji.
- Miniatury zdjęć źródłowych odczytów (gdy dostępne z FR-004 w 001) mogą być pokazane w widoku szczegółowym pojedynczego punktu, ale nie są wymagane bezpośrednio na samym wykresie.
- Redesign dotyczy wyłącznie prezentacji wizualnej istniejących danych (odczytów i wywozów) w zakładce Historia — nie zmienia sposobu ich rejestrowania, struktury danych ani innych ekranów aplikacji.
- Funkcja jest kontynuacją istniejącej aplikacji na Androida (001-septic-tank-monitor) i korzysta z tych samych źródeł danych (historia odczytów licznika, historia wywozów, konfiguracja zbiornika).
