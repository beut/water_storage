# Data Model: Zamawianie wywozu ścieków SMS-em

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

## TankConfiguration (ZMIENIONA): tabela `tank_configuration`

Istniejący singleton (`id = 1`) dostaje jedno nowe pole:

| Pole | Typ | Null | Domyślnie | Opis |
|------|-----|------|-----------|------|
| `pumpingCompanyPhone` | TEXT | tak | `NULL` | Numer firmy asenizacyjnej w postaci znormalizowanej (`+?[0-9]{9,15}`), `NULL` = nieskonfigurowany (FR-002, FR-008) |

Pozostałe pola (`capacityLiters`, `warningThresholdPercent`, `reminderEnabled`, `reminderIntervalDays`) pozostają bez zmian.

**Reguły walidacji** (`PhoneNumberValidator`, research.md §5):
- Dopuszczalne znaki wejściowe: cyfry, spacja, `-`, `+` tylko na pierwszej pozycji.
- Po usunięciu spacji i `-` wynik musi pasować do `^\+?[0-9]{9,15}$`, inaczej zapis zostaje odrzucony z komunikatem błędu.
- Puste lub białe znaki: zapis `NULL` (usunięcie numeru).

**Migracja**: wersja bazy `2 → 3`, `MIGRATION_2_3`:

```sql
ALTER TABLE tank_configuration ADD COLUMN pumpingCompanyPhone TEXT
```

Rejestrowana obok `MIGRATION_1_2` w `AppDatabase` (`addMigrations(MIGRATION_1_2, MIGRATION_2_3)`). Istniejące dane zostają zachowane, a nowe pole ma wartość `NULL`.

## PumpingOrder (NOWY, nietrwały): obiekt domenowy, nie jest zapisywany w bazie

| Pole | Typ | Opis |
|------|-----|------|
| `day` | `LocalDate` | Wybrany dzień wywozu; zawsze element z `availableOrderDays(today)` |
| `phoneNumber` | `String` | Znormalizowany numer z `TankConfiguration.pumpingCompanyPhone` |
| `message` | `String` | `PumpingOrderMessage.forDay(day.dayOfWeek)`, np. „Poproszę o wywóz we wtorek” |

**Reguły**:
- `availableOrderDays(today)`: 5 kolejnych dni pon–pt, zaczynając najwcześniej od `today + 2` (FR-004, research.md §3).
- Zamówienie **nie** tworzy `PumpingEvent` i nie zmienia zużycia (FR-009).
- Historia zamówień nie jest przechowywana (spec → Key Entities).

## Stan UI zamówienia (maszyna stanów, research.md §6)

```text
Hidden ──[Zamów wywóz, numer ustawiony]──▶ Choosing(days, selected)
Hidden ──[Zamów wywóz, brak numeru]──────▶ MissingPhone ──[Przejdź do ustawień / Anuluj]──▶ Hidden
Choosing ──[Anuluj]──────────────────────▶ Hidden
Choosing ──[Wyślij, brak uprawnienia]────▶ (prośba o SEND_SMS) ──odmowa──▶ PermissionDenied ──[OK]──▶ Choosing
Choosing ──[Wyślij, uprawnienie OK]──────▶ Sending
Sending ──[RESULT_OK]────────────────────▶ Sent(day, phone) ──[OK]──▶ Hidden
Sending ──[błąd / timeout 60 s]──────────▶ Failed(reason) ──[Ponów]──▶ Choosing (ten sam dzień)
                                                         └─[Zamknij]──▶ Hidden
```

W stanie `Sending` każde kolejne „Wyślij” jest ignorowane, więc wychodzi co najwyżej 1 SMS na jedno potwierdzenie.
