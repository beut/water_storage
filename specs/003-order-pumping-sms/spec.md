# Feature Specification: Zamawianie wywozu ścieków SMS-em

**Feature Branch**: `003-order-pumping-sms`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "chcialbym dodac przycisk zamow wywóz, po wcisnięciu ktorego bedzie wyslany sms na skonfigurowany numer o tresci poprosze o wywoz w {nazwa dnia tygodnia}"

## Clarifications

### Session 2026-09-23

- Q: Czy aplikacja wysyła SMS sama, czy otwiera systemową aplikację SMS? → A: Aplikacja wysyła SMS sama, po potwierdzeniu w oknie dialogowym (wymaga uprawnienia do wysyłania SMS-ów)
- Q: Skąd bierze się dzień tygodnia w wiadomości? → A: Użytkownik wybiera go przy każdym zamówieniu z listy; w soboty i niedziele nie ma wywozów, a najbezpieczniej zamawiać na pojutrze lub później — lista zawiera więc tylko dni robocze, najwcześniej pojutrze

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Zamówienie wywozu jednym przyciskiem (Priority: P1)

Użytkownik widzi, że zbiornik się zapełnia, i naciska przycisk „Zamów wywóz”. Aplikacja wysyła do firmy asenizacyjnej SMS o treści „Poproszę o wywóz w {dzień tygodnia}” na wcześniej skonfigurowany numer, bez ręcznego wpisywania numeru i treści wiadomości.

**Why this priority**: To jest istota funkcji — skrócenie zamówienia wywozu do jednej akcji w aplikacji, w której użytkownik i tak śledzi zapełnienie zbiornika.

**Independent Test**: Można przetestować samodzielnie, konfigurując numer telefonu (np. własny drugi numer), naciskając „Zamów wywóz” i sprawdzając, że na ten numer dociera SMS z poprawną treścią i poprawną nazwą dnia tygodnia.

**Acceptance Scenarios**:

1. **Given** numer firmy asenizacyjnej jest skonfigurowany, **When** użytkownik naciśnie „Zamów wywóz” i wybierze dzień, **Then** na skonfigurowany numer zostaje wysłany SMS o treści „Poproszę o wywóz w {dzień tygodnia}” z nazwą wybranego dnia w poprawnej formie gramatycznej (np. „w poniedziałek”, „we wtorek”, „w środę”).
2. **Given** dziś jest środa, **When** użytkownik naciśnie „Zamów wywóz”, **Then** lista dni zaczyna się od piątku (pojutrze), pomija sobotę i niedzielę, zawiera kolejne dni robocze do czwartku następnego tygodnia, a piątek jest zaznaczony domyślnie.
3. **Given** dziś jest czwartek (pojutrze wypada w sobotę), **When** użytkownik naciśnie „Zamów wywóz”, **Then** pierwszym i domyślnie zaznaczonym dniem jest najbliższy poniedziałek.
4. **Given** SMS został wysłany, **When** operacja się zakończy, **Then** użytkownik widzi wyraźne potwierdzenie, na jaki dzień i na jaki numer zamówiono wywóz.
5. **Given** wysłanie SMS-a się nie powiodło (np. brak zasięgu), **When** operacja się zakończy, **Then** użytkownik widzi czytelny komunikat o błędzie i może ponowić próbę.

---

### User Story 2 - Konfiguracja numeru firmy asenizacyjnej (Priority: P1)

Użytkownik w ustawieniach wpisuje numer telefonu firmy asenizacyjnej, na który mają trafiać zamówienia wywozu.

**Why this priority**: Bez skonfigurowanego numeru przycisk „Zamów wywóz” nie ma dokąd wysłać wiadomości — obie historie razem tworzą MVP.

**Independent Test**: Można przetestować samodzielnie, wpisując numer w ustawieniach, zamykając i ponownie otwierając aplikację, i sprawdzając, że numer został zapamiętany.

**Acceptance Scenarios**:

1. **Given** użytkownik jest w ustawieniach, **When** wpisze poprawny numer telefonu i zapisze, **Then** numer jest zapamiętany i używany przy kolejnych zamówieniach wywozu.
2. **Given** użytkownik wpisze niepoprawny numer (np. litery, zbyt mało cyfr), **When** spróbuje zapisać, **Then** aplikacja nie zapisuje go i wskazuje, co jest nie tak.
3. **Given** numer NIE jest skonfigurowany, **When** użytkownik naciśnie „Zamów wywóz”, **Then** aplikacja nie wysyła SMS-a, tylko informuje o braku numeru i prowadzi do ustawień.

---

### Edge Cases

- Co się dzieje, gdy użytkownik nie udzielił aplikacji uprawnień potrzebnych do wysłania SMS-a? Aplikacja nie wysyła wiadomości, wyjaśnia, dlaczego uprawnienie jest potrzebne, i pozwala spróbować ponownie.
- Co się dzieje, gdy telefon nie może wysyłać SMS-ów (brak karty SIM, tryb samolotowy)? Użytkownik widzi komunikat o błędzie, a nie ciche niepowodzenie.
- Co się dzieje przy przypadkowym dwukrotnym naciśnięciu przycisku? Wysłany zostaje co najwyżej jeden SMS na jedno świadome zamówienie.
- Skąd firma wie, o który tydzień chodzi, skoro SMS zawiera tylko nazwę dnia? Lista obejmuje 5 kolejnych dni roboczych, czyli mniej niż 7 dni kalendarzowych, więc każda nazwa dnia wskazuje jednoznacznie najbliższy taki dzień.
- Jak odmieniana jest nazwa dnia? Zawsze zgodnie z polską gramatyką po przyimku: „w poniedziałek”, „we wtorek”, „w środę”, „w czwartek”, „w piątek”, „w sobotę”, „w niedzielę”.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST udostępniać na ekranie głównym przycisk „Zamów wywóz”.
- **FR-002**: System MUST umożliwiać skonfigurowanie w ustawieniach numeru telefonu firmy asenizacyjnej, walidować go (dopuszczalne cyfry, opcjonalny prefiks „+” i spacje; co najmniej 9 cyfr) i trwale go zapamiętać.
- **FR-003**: Po naciśnięciu „Zamów wywóz” system MUST wysłać na skonfigurowany numer wiadomość SMS o treści dokładnie „Poproszę o wywóz w {dzień tygodnia}”, z poprawną polską odmianą nazwy dnia i przyimkiem „we” przed „wtorek”.
- **FR-004**: Przy każdym zamówieniu system MUST pozwolić użytkownikowi wybrać dzień wywozu z listy 5 kolejnych dni roboczych (poniedziałek–piątek), zaczynając najwcześniej od pojutrza; soboty i niedziele MUST NOT być dostępne. Domyślnie zaznaczony MUST być pierwszy dzień z listy. Każda pozycja pokazuje nazwę dnia i datę (np. „piątek 25.09”), a SMS zawiera tylko nazwę dnia.
- **FR-005**: System MUST wysyłać SMS samodzielnie, bez otwierania osobnej aplikacji do wiadomości, dopiero po potwierdzeniu przez użytkownika w oknie dialogowym (FR-006); przy pierwszym użyciu system MUST poprosić o uprawnienie do wysyłania SMS-ów i wyjaśnić, do czego jest potrzebne.
- **FR-006**: Przed wysłaniem system MUST pokazać użytkownikowi podgląd treści wiadomości i numer docelowy, wymagając potwierdzenia, aby uniknąć przypadkowego zamówienia.
- **FR-007**: System MUST informować użytkownika o powodzeniu lub niepowodzeniu wysyłki oraz umożliwiać ponowienie próby po błędzie.
- **FR-008**: Gdy numer nie jest skonfigurowany, system MUST zamiast wysyłki wyświetlić informację o braku numeru z przejściem do ustawień.
- **FR-009**: Zamówienie wywozu MUST NOT rejestrować zdarzenia wywozu ścieków ani zerować zużycia — to nadal robi osobna akcja „Wywóz ścieków” po faktycznym opróżnieniu zbiornika.

### Key Entities *(include if feature involves data)*

- **Konfiguracja zbiornika (Tank Configuration)**: rozszerzona o numer telefonu firmy asenizacyjnej (opcjonalny).
- **Zamówienie wywozu (Pumping Order)**: jednorazowa akcja wysłania wiadomości — wybrany dzień roboczy, numer docelowy, treść. Nie jest przechowywane jako historia w tej wersji.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Użytkownik ze skonfigurowanym numerem zamawia wywóz w mniej niż 15 sekund od otwarcia aplikacji.
- **SC-002**: 100% wysłanych wiadomości ma treść zgodną z wzorcem „Poproszę o wywóz w {dzień tygodnia}” z poprawną odmianą dla każdego z 7 dni tygodnia.
- **SC-003**: Każda próba zamówienia kończy się widocznym dla użytkownika wynikiem (sukces albo komunikat błędu) — zero cichych niepowodzeń.
- **SC-004**: Przypadkowe naciśnięcie przycisku nie powoduje wysłania SMS-a bez potwierdzenia użytkownika.

## Assumptions

- Dni świąteczne przypadające w dni robocze nie są wykluczane z listy; użytkownik sam ich nie wybiera.
- Obsługiwany jest jeden numer firmy asenizacyjnej; wiele firm/numerów jest poza zakresem tej wersji.
- Treść wiadomości jest stała (poza nazwą dnia) i nie jest edytowalna przez użytkownika w tej wersji.
- Aplikacja nie odbiera ani nie interpretuje odpowiedzi firmy asenizacyjnej.
- Wysyłka SMS odbywa się z karty SIM telefonu użytkownika, na jego koszt, zgodnie z jego taryfą.
- Numer telefonu jest daną przechowywaną wyłącznie lokalnie na urządzeniu, jak pozostałe ustawienia aplikacji.
