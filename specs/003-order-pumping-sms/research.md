# Research: Zamawianie wywozu ścieków SMS-em

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-23

## 1. Mechanizm wysyłki SMS (FR-005)

**Decision**: `android.telephony.SmsManager.sendTextMessage(...)` z uprawnieniem `SEND_SMS` żądanym w czasie działania (runtime), dopiero przy pierwszym potwierdzeniu zamówienia. Instancja: `context.getSystemService(SmsManager::class.java)` na API 31+, `SmsManager.getDefault()` na API 26–30 (minSdk projektu = 26).

**Rationale**: Clarification z 2026-09-23 wybrał automatyczną wysyłkę po potwierdzeniu w oknie dialogowym, bez otwierania aplikacji SMS. Tylko `SmsManager` to umożliwia. Wiadomość ma ok. 35 znaków, więc nie trzeba jej dzielić (`divideMessage`/`sendMultipartTextMessage` nie jest potrzebne, patrz p. 4).

**Alternatives considered**:
- `Intent.ACTION_SENDTO` (`smsto:`) otwiera systemową aplikację SMS. Odrzucone w clarification (opcja A), bo użytkownik chce wysyłki z samej aplikacji.
- Bramka SMS przez sieć (np. zewnętrzne API). Odrzucone: aplikacja jest w pełni lokalna/offline (FR-014 z 001), a to wymagałoby konta, kosztów i sieci.

**Ryzyko (dystrybucja)**: Google Play dopuszcza uprawnienie `SEND_SMS` praktycznie tylko dla domyślnych aplikacji SMS. Aplikacja jest instalowana z APK (sideload), więc to jej nie dotyczy. Gdyby kiedyś miała trafić do Sklepu Play, trzeba wrócić do wariantu `ACTION_SENDTO`. Warto zostawić wysyłkę za jednym interfejsem (`SmsSender`), żeby taka zamiana dotyczyła jednego pliku.

## 2. Potwierdzenie wyniku wysyłki (FR-007, SC-003)

**Decision**: `sentIntent` jako `PendingIntent.getBroadcast` z jawnym intentem (`setPackage(context.packageName)`, unikalna akcja z identyfikatorem zamówienia), `FLAG_IMMUTABLE`. Odbiór przez dynamicznie rejestrowany `BroadcastReceiver` (`ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`), opakowany w `suspendCancellableCoroutine`. Wynik:
- `Activity.RESULT_OK` → sukces.
- `SmsManager.RESULT_ERROR_NO_SERVICE`, `RESULT_ERROR_RADIO_OFF` (tryb samolotowy), `RESULT_ERROR_GENERIC_FAILURE`, `RESULT_ERROR_NULL_PDU` i pozostałe kody → błąd z czytelnym komunikatem.
- Brak odpowiedzi w ciągu 60 s → błąd „nie udało się potwierdzić wysłania” (bez automatycznego ponawiania, żeby nie wysłać SMS-a dwa razy).
- `SecurityException` / brak `SmsManager` (urządzenie bez telefonii) → błąd.

**Rationale**: Bez `sentIntent` aplikacja nie wie, czy SMS wyszedł, a SC-003 zabrania cichych niepowodzeń. `deliveryIntent` (raport doręczenia) pomijamy, bo zależy od operatora i może nie przyjść wcale.

**Alternatives considered**: sama wysyłka bez sprawdzania wyniku. Odrzucona, bo narusza FR-007 i SC-003.

## 3. Lista dni do wyboru (FR-004)

**Decision**: Czysta funkcja domenowa `availableOrderDays(today: LocalDate): List<LocalDate>`. Zaczyna od `today + 2` dni, pomija `SATURDAY` i `SUNDAY` i zwraca pierwsze 5 dni roboczych. Domyślnie zaznaczony jest element `[0]`. `today` jest przekazywane z zewnątrz (`LocalDate.now()` w warstwie UI/ViewModel), żeby testy jednostkowe mogły ustawić dowolną datę. `java.time` jest dostępne natywnie od API 26, więc desugaring nie jest potrzebny.

**Rationale**: Wynika bezpośrednio z clarification. 5 dni roboczych od pojutrza zawsze mieści się w mniej niż 7 dniach kalendarzowych: najdłuższy przypadek to czwartek (pojutrze = sobota), dla którego lista to pon–pt, czyli 4–8 dni od dziś, zakres 5 dni. Nazwy dni na liście się więc nie powtarzają i sama nazwa dnia w SMS-ie jest jednoznaczna.

**Alternatives considered**: 7 dni kalendarzowych z wyszarzonym weekendem. Odrzucone, bo pokazuje opcje, których i tak nie można wybrać.

## 4. Treść wiadomości i odmiana (FR-003, SC-002)

**Decision**: Stała mapa `DayOfWeek → fraza z przyimkiem`, bez zależności od `Locale` urządzenia:

| DayOfWeek | Fraza |
|-----------|-------|
| MONDAY | w poniedziałek |
| TUESDAY | we wtorek |
| WEDNESDAY | w środę |
| THURSDAY | w czwartek |
| FRIDAY | w piątek |
| SATURDAY | w sobotę |
| SUNDAY | w niedzielę |

Treść: `"Poproszę o wywóz " + fraza`, np. „Poproszę o wywóz we wtorek”. Sobota i niedziela są w mapie dla kompletności (`when` wyczerpujący), mimo że nie pojawią się na liście dni.

**Rationale**: `DayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl"))` zwraca mianownik („środa”), a po „w” potrzebny jest biernik („środę”) i wyjątek „we wtorek”, więc i tak trzeba by ręcznie poprawiać. Tabela jest prostsza i daje się sprawdzić testem dla wszystkich 7 dni (SC-002).

**Kodowanie**: Polskie znaki (ę, ó, ś) nie należą do alfabetu GSM-7, więc SMS pójdzie w UCS-2 z limitem 70 znaków na jedną wiadomość. Najdłuższa treść („Poproszę o wywóz w poniedziałek”, 31 znaków) mieści się z zapasem, czyli zawsze 1 SMS.

## 5. Numer telefonu: przechowywanie i walidacja (FR-002)

**Decision**: Nowa kolumna `pumpingCompanyPhone TEXT` (nullable) w istniejącej tabeli `tank_configuration`. Wersja bazy 2 → 3, `MIGRATION_2_3` z `ALTER TABLE tank_configuration ADD COLUMN pumpingCompanyPhone TEXT`. Nie używamy destrukcyjnej migracji (patrz commit e986fbc).

Walidacja (czysta funkcja `PhoneNumberValidator`):
- wejście: cyfry, spacje, myślniki i opcjonalny `+` tylko na początku;
- po usunięciu spacji i myślników: `^\+?[0-9]{9,15}$` (9 cyfr to krajowy numer PL, 15 to maksimum E.164);
- zapisywana jest postać znormalizowana (bez spacji i myślników), np. `+48600123456` albo `600123456`;
- puste pole oznacza usunięcie numeru (`null`).

**Rationale**: `tank_configuration` to już singleton „ustawień instalacji” (pojemność, próg, przypomnienia), więc osobna tabela dla jednego pola byłaby nadmiarowa. `ADD COLUMN` z wartością `NULL` to najprostsza i bezpieczna migracja SQLite.

**Alternatives considered**: DataStore/SharedPreferences. Odrzucone, bo projekt trzyma całą konfigurację w Room, a drugi mechanizm persystencji dla jednego pola to niepotrzebna niespójność.

## 6. Ochrona przed podwójną wysyłką (Edge Case, SC-004)

**Decision**: Stan zamówienia w ViewModelu jako maszyna stanów `Hidden → Choosing → Sending → Sent | Failed`. Przycisk „Wyślij” jest aktywny tylko w `Choosing`. Przejście do `Sending` następuje synchronicznie przed wywołaniem wysyłki, więc drugie dotknięcie trafia już w stan `Sending` i jest ignorowane. „Ponów” z `Failed` wraca do `Choosing` z zachowanym wyborem dnia, więc każda wysyłka wymaga osobnego, świadomego potwierdzenia.

**Rationale**: Spełnia wymaganie „co najwyżej jeden SMS na jedno świadome zamówienie” i SC-004 (brak wysyłki bez potwierdzenia).
