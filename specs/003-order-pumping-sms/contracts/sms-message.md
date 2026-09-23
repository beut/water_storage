# Contract: wiadomość SMS z zamówieniem wywozu

Zewnętrzny interfejs tej funkcji to SMS odbierany przez firmę asenizacyjną (człowieka). Ten kontrakt ustala, co dokładnie wysyłamy.

## Odbiorca

- `TankConfiguration.pumpingCompanyPhone`, dokładnie w postaci zapisanej (znormalizowanej), np. `600123456` lub `+48600123456`.
- Zawsze jeden odbiorca, jedna wiadomość na jedno potwierdzone zamówienie.

## Treść

```text
Poproszę o wywóz {fraza_dnia}
```

| Wybrany dzień | Pełna treść SMS |
|---------------|-----------------|
| poniedziałek | `Poproszę o wywóz w poniedziałek` |
| wtorek | `Poproszę o wywóz we wtorek` |
| środa | `Poproszę o wywóz w środę` |
| czwartek | `Poproszę o wywóz w czwartek` |
| piątek | `Poproszę o wywóz w piątek` |

- Bez kropki na końcu, bez daty, bez podpisu, bez dodatkowych spacji.
- Kodowanie: UCS-2 (polskie znaki), zawsze 1 segment (≤ 31 znaków przy limicie 70).
- Sobota i niedziela nigdy nie trafiają do wiadomości (nie ma ich na liście dni, FR-004). Mapowanie dla nich (`w sobotę`, `w niedzielę`) istnieje tylko po to, żeby funkcja była totalna.

## Semantyka dnia

Nazwa dnia oznacza **najbliższy** taki dzień, liczony od pojutrza. Lista dni (5 dni roboczych od pojutrza) zawsze mieści się w mniej niż 7 dniach kalendarzowych, więc interpretacja jest jednoznaczna (research.md §3).

# Contract: UI (ekran główny i ustawienia)

| Element | Miejsce | Zachowanie |
|---------|---------|------------|
| Przycisk „Zamów wywóz” | `HomeScreen`, obok „Wywóz ścieków” | Zawsze aktywny, otwiera okno zamówienia albo komunikat o braku numeru (FR-001, FR-008) |
| Okno „Zamów wywóz” | dialog nad `HomeScreen` | Lista 5 dni (`nazwa dd.MM`), domyślnie pierwszy; podgląd „SMS do: {numer}” + treść; przyciski „Anuluj” / „Wyślij” (FR-004, FR-006) |
| Wynik | ten sam dialog | Sukces: „Zamówiono wywóz {fraza_dnia} (SMS do {numer})”. Błąd: przyczyna + „Ponów” / „Zamknij” (FR-007) |
| Pole „Numer firmy asenizacyjnej” | `SettingsScreen` | Pole tekstowe (klawiatura telefoniczna) + „Zapisz numer”; błąd walidacji pod polem (FR-002) |
