# Feature Specification: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Feature Branch**: `001-septic-tank-monitor`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "Aplikacja na Androida do śledzenia poziomu wody w szambie. Aplikacja będzie brała dane z cyklicznych zdjęć licznika wody, odczytywała stan i aktualizowała zużycie wody. W momencie wywozu ścieków, będzie przycisk do resetu stanu. Przy zbliżaniu się zużycia do pojemności zbiornika, będzie pojawiało się ostrzeżenie."

## Clarifications

### Session 2026-09-14

- Q: Czy odczytywanie wartości z licznika (OCR) ma działać w pełni lokalnie na urządzeniu (offline), czy może korzystać z usługi w chmurze? → A: Odczyt w pełni lokalny (on-device OCR), aplikacja działa offline
- Q: Jaka ma być jednostka i precyzja odczytu licznika wody? → A: Metry sześcienne (m³) z dokładnością do 0,001 m³ (1 litr)
- Q: Czy aplikacja w pierwszej wersji ma obsługiwać tylko jeden zbiornik i jeden licznik wody na instalację, czy trzeba wspierać wiele zbiorników/nieruchomości? → A: Jeden zbiornik i jeden licznik na instalację aplikacji (poza zakresem v1: wiele nieruchomości)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rejestrowanie odczytu licznika wody ze zdjęcia (Priority: P1)

Użytkownik robi zdjęcie licznika wody aparatem telefonu. Aplikacja automatycznie odczytuje wskazywaną wartość i zapisuje ją jako nowy odczyt, aktualizując bieżące zużycie wody liczone od ostatniego wywozu ścieków.

**Why this priority**: To jest funkcja rdzeniowa aplikacji — bez rejestrowania odczytów nie ma żadnych danych do śledzenia poziomu zbiornika.

**Independent Test**: Można przetestować samodzielnie, robiąc zdjęcie licznika i sprawdzając, czy nowy odczyt oraz przeliczone zużycie pojawiają się poprawnie w aplikacji.

**Acceptance Scenarios**:

1. **Given** użytkownik ma skonfigurowany zbiornik i wcześniejszy odczyt bazowy, **When** zrobi zdjęcie licznika i odczyt zostanie jednoznacznie rozpoznany, **Then** aplikacja zapisuje nowy odczyt i przelicza bieżące zużycie wody od ostatniego resetu.
2. **Given** zdjęcie licznika jest nieczytelne lub odczyt automatyczny jest niepewny, **When** przetwarzanie zdjęcia się zakończy, **Then** aplikacja prosi użytkownika o ręczne potwierdzenie lub poprawienie odczytanej wartości przed zapisaniem.

---

### User Story 2 - Reset stanu po wywozie ścieków (Priority: P1)

Po opróżnieniu zbiornika przez firmę asenizacyjną użytkownik naciska przycisk „Wywóz ścieków”, co zapisuje zdarzenie wywozu i zeruje wyświetlane zużycie, ustawiając nowy punkt odniesienia.

**Why this priority**: Bez resetu zużycie byłoby liczone od początku korzystania z aplikacji, a nie od ostatniego opróżnienia zbiornika, co czyni śledzenie bezużytecznym.

**Independent Test**: Można przetestować samodzielnie, naciskając przycisk resetu i sprawdzając, że wyświetlane zużycie wraca do zera, a kolejne odczyty liczone są od nowego punktu bazowego.

**Acceptance Scenarios**:

1. **Given** użytkownik ma zarejestrowane zużycie wody od ostatniego wywozu, **When** naciśnie przycisk „Wywóz ścieków”, **Then** aplikacja zapisuje zdarzenie wywozu z datą i bieżącym odczytem licznika jako nowy punkt bazowy, a wyświetlane zużycie wraca do zera.
2. **Given** zarejestrowano co najmniej jeden wywóz ścieków, **When** użytkownik otworzy historię wywozów, **Then** widzi listę wcześniejszych wywozów wraz z datami i odczytami licznika w momencie wywozu.

---

### User Story 3 - Ostrzeżenie o zbliżającym się zapełnieniu zbiornika (Priority: P2)

Gdy obliczone zużycie wody od ostatniego wywozu zbliża się do skonfigurowanej pojemności zbiornika, aplikacja wyświetla użytkownikowi widoczne ostrzeżenie, aby mógł zaplanować kolejny wywóz z wyprzedzeniem.

**Why this priority**: Zwiększa wartość użytkową aplikacji poprzez proaktywne powiadamianie, ale podstawowe śledzenie zużycia (P1) działa i dostarcza wartość nawet bez tej funkcji.

**Independent Test**: Można przetestować, ustawiając niską pojemność zbiornika testowego i rejestrując kolejne odczyty aż zużycie przekroczy próg ostrzegawczy, sprawdzając czy ostrzeżenie się pojawia.

**Acceptance Scenarios**:

1. **Given** zużycie wody osiągnęło próg ostrzegawczy pojemności zbiornika, **When** użytkownik otworzy aplikację lub zostanie zarejestrowany nowy odczyt, **Then** aplikacja wyświetla widoczne ostrzeżenie o zbliżającym się zapełnieniu zbiornika.
2. **Given** zużycie wody przekroczyło skonfigurowaną pojemność zbiornika, **When** użytkownik otworzy aplikację, **Then** ostrzeżenie zmienia formę na bardziej pilną, wyraźnie odróżnialną od zwykłego ostrzeżenia zbliżania się do limitu.

---

### User Story 4 - Konfiguracja zbiornika i przypomnień (Priority: P3)

Użytkownik konfiguruje pojemność swojego zbiornika oraz opcjonalnie włącza cykliczne przypomnienia o wykonaniu zdjęcia licznika.

**Why this priority**: Niezbędne, aby ostrzeżenia (P2) miały sens, ale rejestrowanie odczytów i reset (P1) mogą działać nawet przy jednorazowym, ręcznym wprowadzeniu pojemności.

**Independent Test**: Można przetestować samodzielnie, wchodząc w ustawienia, wprowadzając pojemność zbiornika i sprawdzając, że wartość jest zapamiętana i używana do wyliczania ostrzeżeń.

**Acceptance Scenarios**:

1. **Given** nowy użytkownik uruchamia aplikację po raz pierwszy, **When** przechodzi proces wstępnej konfiguracji, **Then** musi podać pojemność zbiornika, zanim aplikacja zacznie wyliczać ostrzeżenia.
2. **Given** użytkownik chce otrzymywać przypomnienia o zdjęciu licznika, **When** włączy cykliczne przypomnienia w ustawieniach, **Then** aplikacja wysyła powiadomienie w wybranym przez użytkownika cyklu.

---

### Edge Cases

- Co się dzieje, gdy zdjęcie licznika jest nieczytelne (rozmazane, złe oświetlenie, licznik częściowo zasłonięty)? Aplikacja sygnalizuje błąd odczytu i prosi o nowe zdjęcie lub ręczne wprowadzenie wartości.
- Co się dzieje, gdy nowy odczyt jest niższy niż poprzedni (np. wymiana licznika, pomyłka użytkownika)? Aplikacja traktuje to jako anomalię, ostrzega użytkownika i prosi o potwierdzenie lub korektę zamiast automatycznie zapisać ujemne zużycie.
- Co się dzieje, gdy użytkownik nie skonfigurował jeszcze pojemności zbiornika? Aplikacja nadal zapisuje odczyty i zużycie, ale informuje, że ostrzeżenia o zapełnieniu wymagają uzupełnienia pojemności zbiornika.
- Co się dzieje, gdy użytkownik zrobi kilka zdjęć tego samego dnia? Najnowszy odczyt staje się aktualnym stanem, a poprzednie pozostają widoczne w historii.
- Co się dzieje przy pierwszym uruchomieniu, zanim doszło do jakiegokolwiek wywozu ścieków? Zużycie liczone jest od pierwszego zarejestrowanego odczytu, traktowanego jako wstępny punkt bazowy.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST umożliwiać użytkownikowi wykonanie zdjęcia licznika wody za pomocą aparatu urządzenia.
- **FR-002**: System MUST automatycznie odczytywać wartość wskazywaną przez licznik wody na podstawie wykonanego zdjęcia, w jednostce metrów sześciennych (m³) z dokładnością do 0,001 m³ (1 litr).
- **FR-003**: System MUST umożliwiać użytkownikowi ręczne potwierdzenie lub poprawienie odczytanej wartości przed jej zapisaniem, w szczególności gdy odczyt automatyczny jest niepewny lub nieudany.
- **FR-004**: System MUST przechowywać historię odczytów licznika wraz z datą, wartością odczytu oraz powiązanym zdjęciem źródłowym.
- **FR-005**: System MUST obliczać bieżące zużycie wody jako różnicę między najnowszym odczytem a odczytem zarejestrowanym przy ostatnim wywozie ścieków (lub pierwszym zarejestrowanym odczytem, jeśli żaden wywóz jeszcze nie miał miejsca).
- **FR-006**: System MUST udostępniać użytkownikowi akcję „Wywóz ścieków”, która rejestruje zdarzenie opróżnienia zbiornika wraz z datą i bieżącym odczytem licznika jako nowy punkt bazowy, oraz zeruje wyświetlane zużycie.
- **FR-007**: System MUST przechowywać historię zdarzeń wywozu ścieków, w tym datę oraz powiązany odczyt licznika.
- **FR-008**: System MUST umożliwiać użytkownikowi skonfigurowanie pojemności zbiornika w metrach sześciennych (m³).
- **FR-009**: System MUST wyświetlać użytkownikowi widoczne ostrzeżenie, gdy bieżące zużycie wody zbliża się do skonfigurowanej pojemności zbiornika.
- **FR-010**: System MUST rozróżniać co najmniej dwa poziomy pilności ostrzeżenia: zbliżanie się do limitu pojemności oraz jego przekroczenie.
- **FR-011**: System MUST wykrywać i sygnalizować anomalie odczytu (np. nowa wartość niższa niż poprzedni odczyt) i prosić użytkownika o potwierdzenie lub korektę zamiast cichego zapisania błędnej wartości.
- **FR-012**: System MUST umożliwiać użytkownikowi przeglądanie historii odczytów licznika oraz historii wywozów ścieków.
- **FR-013**: System MUST umożliwiać użytkownikowi ustawienie cyklicznych przypomnień o wykonaniu zdjęcia licznika wody.
- **FR-014**: System MUST rozpoznawać wartość odczytu licznika w pełni lokalnie na urządzeniu (on-device), bez wymogu połączenia z internetem do działania podstawowych funkcji aplikacji.

### Key Entities *(include if feature involves data)*

- **Odczyt licznika (Water Meter Reading)**: pojedynczy odczyt wartości licznika wody w danym momencie; obejmuje datę i czas, wartość odczytu, zdjęcie źródłowe oraz informację, czy wartość pochodzi z automatycznego rozpoznania czy z ręcznej korekty użytkownika.
- **Zdarzenie wywozu ścieków (Pumping Event)**: reprezentuje moment opróżnienia zbiornika; obejmuje datę wywozu oraz powiązany odczyt licznika stanowiący nowy punkt bazowy do liczenia zużycia.
- **Konfiguracja zbiornika (Tank Configuration)**: właściwości zbiornika użytkownika; obejmuje pojemność zbiornika oraz próg ostrzegawczy.
- **Bieżący stan zużycia (Usage State)**: wartość wyliczana jako różnica między najnowszym odczytem a odczytem bazowym z ostatniego wywozu; obejmuje wartość zużycia, procent wykorzystania pojemności zbiornika oraz aktualny poziom ostrzeżenia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Użytkownik może zarejestrować nowy odczyt licznika (zrobić zdjęcie i zobaczyć zaktualizowane zużycie) w mniej niż 30 sekund w typowych warunkach.
- **SC-002**: Automatyczne odczytanie wartości ze zdjęcia licznika jest poprawne bez potrzeby ręcznej korekty w co najmniej 80% przypadków przy dobrych warunkach oświetleniowych.
- **SC-003**: 100% zarejestrowanych wywozów ścieków poprawnie zeruje wyświetlane zużycie i tworzy nowy punkt odniesienia widoczny w historii.
- **SC-004**: Użytkownik otrzymuje ostrzeżenie o zbliżającym się zapełnieniu zbiornika, zanim zużycie przekroczy 100% skonfigurowanej pojemności, w co najmniej 95% przypadków.
- **SC-005**: Użytkownik może sprawdzić aktualny procent wykorzystania pojemności zbiornika w ciągu maksymalnie 2 dotknięć ekranu od otwarcia aplikacji.

## Assumptions

- Aplikacja jest przeznaczona dla pojedynczego użytkownika/gospodarstwa domowego na instalację, bez potrzeby współdzielenia danych między wieloma kontami w pierwszej wersji.
- Aplikacja w pierwszej wersji obsługuje jeden zbiornik i jeden licznik wody na instalację; obsługa wielu zbiorników/nieruchomości w ramach jednej instalacji jest poza zakresem v1.
- Zbiornik jest zbiornikiem bezodpływowym (szambo), do którego trafia cała zużyta woda wodociągowa, więc odczyt licznika wody jest przyjęty jako wiarygodne przybliżenie przyrostu poziomu ścieków w zbiorniku.
- Domyślny próg ostrzegawczy to 80% skonfigurowanej pojemności zbiornika, z możliwością zmiany przez użytkownika w ustawieniach.
- Dane aplikacji (odczyty, historia wywozów, zdjęcia) przechowywane są lokalnie na urządzeniu w pierwszej wersji, bez wymogu synchronizacji w chmurze.
- Zdjęcia licznika są wykonywane ręcznie przez użytkownika aparatem urządzenia, opcjonalnie na podstawie przypomnienia wysyłanego przez aplikację.
