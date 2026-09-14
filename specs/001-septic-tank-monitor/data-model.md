# Data Model: Monitor poziomu zbiornika szamba na podstawie licznika wody

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Encje odpowiadają sekcji „Key Entities” w spec.md, uszczegółowionej o pola i reguły walidacji wynikające z wymagań funkcjonalnych (FR-001…FR-014) oraz decyzji z sesji `/speckit-clarify` (jednostka m³ z dokładnością do 0,001 m³, jeden zbiornik/licznik na instalację, przechowywanie lokalne w Room).

## MeterReading (Odczyt licznika)

Reprezentuje pojedynczy odczyt wartości licznika wody w danym momencie. Źródło: FR-001…FR-004, FR-011, FR-015.

| Pole | Typ | Reguły |
|---|---|---|
| `id` | Long (PK, auto) | Unikalny identyfikator odczytu |
| `timestamp` | Instant/epoch millis | Data i czas wykonania odczytu; wymagane |
| `valueM3` | Decimal(0,001) | Wartość odczytu w m³, dokładność do 0,001 m³ (FR-002); MUST być ≥ 0 |
| `photoPath` | String, nullable | Ścieżka do pliku zdjęcia źródłowego w pamięci wewnętrznej aplikacji (FR-004); `null` dla odczytów wpisanych bezpośrednio ręcznie, bez zdjęcia (FR-015) |
| `source` | Enum(`AUTO_OCR`, `MANUAL_CORRECTED`, `MANUAL_ENTERED`) | Czy wartość pochodzi z automatycznego rozpoznania, ręcznej korekty wyniku OCR, czy pełnego ręcznego wpisu -- w tym wpisu bez zdjęcia (FR-003, FR-015) |
| `isAnomalous` | Boolean | Ustawiane, gdy `valueM3` < `valueM3` poprzedniego odczytu (FR-011); domyślnie `false` |
| `anomalyAcknowledged` | Boolean | Ustawiane na `true` po potwierdzeniu przez użytkownika anomalnego odczytu (FR-011) |

**Reguły walidacji**:

- `valueM3` nie może być ujemna.
- Jeśli nowy `valueM3` < `valueM3` najnowszego wcześniejszego odczytu → `isAnomalous = true`, zapis wymaga jawnego potwierdzenia użytkownika przed ustawieniem `anomalyAcknowledged = true` (Edge Case: licznik niższy niż poprzedni).
- Tylko jeden odczyt na `timestamp` nie jest wymagany — wiele zdjęć tego samego dnia jest dozwolonych (Edge Case); do obliczeń bieżącego zużycia liczy się odczyt o najpóźniejszym `timestamp`.

**Relacje**: Wiele `MeterReading` należy do jednej instalacji (v1: dokładnie jeden zbiornik/licznik, więc relacja jest niejawna — brak klucza obcego do encji zbiornika, gdyż istnieje tylko jedna konfiguracja).

## PumpingEvent (Zdarzenie wywozu ścieków)

Reprezentuje moment opróżnienia zbiornika przez firmę asenizacyjną. Źródło: FR-006, FR-007.

| Pole | Typ | Reguły |
|---|---|---|
| `id` | Long (PK, auto) | Unikalny identyfikator zdarzenia |
| `timestamp` | Instant/epoch millis | Data i czas wywozu; wymagane, ustawiane automatycznie na moment naciśnięcia przycisku |
| `baselineReadingId` | Long (FK → MeterReading.id) | Odczyt licznika w momencie wywozu, przyjmowany jako nowy punkt bazowy do liczenia zużycia (FR-006) |

**Reguły walidacji**:

- `baselineReadingId` MUST wskazywać na najnowszy dostępny `MeterReading` w momencie naciśnięcia przycisku „Wywóz ścieków”; jeśli żaden odczyt jeszcze nie istnieje, akcja resetu jest zablokowana (nie ma punktu bazowego do zapisania).

**Relacje**: Jeden `PumpingEvent` odwołuje się do jednego `MeterReading` jako punktu bazowego. Zużycie bieżące (`UsageState`, patrz niżej) liczone jest względem `baselineReadingId` najnowszego `PumpingEvent`, a jeśli żaden `PumpingEvent` nie istnieje — względem pierwszego zarejestrowanego `MeterReading` (FR-005, Edge Case).

## TankConfiguration (Konfiguracja zbiornika)

Reprezentuje właściwości zbiornika użytkownika. Źródło: FR-008, FR-009, FR-010; v1 ogranicza się do jednego rekordu (jeden zbiornik/licznik na instalację, decyzja z clarify).

| Pole | Typ | Reguły |
|---|---|---|
| `id` | Long (PK) | Zawsze pojedynczy rekord (singleton) w v1 |
| `capacityM3` | Decimal(0,001) | Pojemność zbiornika w m³ (FR-008); MUST być > 0; wymagane przed wyliczaniem ostrzeżeń |
| `warningThresholdPercent` | Int | Próg ostrzegawczy jako % pojemności; domyślnie `80` (Assumptions) |
| `reminderEnabled` | Boolean | Czy cykliczne przypomnienia o zdjęciu są włączone (FR-013) |
| `reminderIntervalDays` | Int nullable | Odstęp w dniach między przypomnieniami; wymagane, jeśli `reminderEnabled = true` |

**Reguły walidacji**:

- `capacityM3` musi być skonfigurowana (> 0), zanim `UsageState` może wyznaczyć poziom ostrzeżenia (Edge Case: brak skonfigurowanej pojemności → aplikacja nadal zapisuje odczyty, ale nie pokazuje ostrzeżeń).
- `warningThresholdPercent` MUST mieścić się w zakresie 1–99.

## UsageState (Bieżący stan zużycia) — encja wyliczana

Nie jest przechowywana bezpośrednio jako osobna tabela — wyliczana w warstwie domenowej z `MeterReading`, `PumpingEvent` i `TankConfiguration` na potrzeby UI. Źródło: FR-005, FR-009, FR-010.

| Pole | Typ | Reguła wyliczenia |
|---|---|---|
| `currentUsageM3` | Decimal | `najnowszy MeterReading.valueM3` − `valueM3 odczytu bazowego` (z ostatniego `PumpingEvent` lub pierwszego `MeterReading`, FR-005) |
| `usagePercentOfCapacity` | Decimal (%) | `currentUsageM3 / TankConfiguration.capacityM3 * 100`, `null`/nieokreślone jeśli `capacityM3` nieskonfigurowana |
| `warningLevel` | Enum(`NONE`, `APPROACHING`, `EXCEEDED`) | `EXCEEDED` gdy `usagePercentOfCapacity ≥ 100`; `APPROACHING` gdy `usagePercentOfCapacity ≥ warningThresholdPercent` i `< 100`; w przeciwnym razie `NONE` (FR-009, FR-010) |

## Diagram relacji (tekstowy)

```text
TankConfiguration (singleton, v1: jeden rekord)
        │ (capacityM3, warningThresholdPercent → używane do wyliczenia UsageState)
        │
PumpingEvent ──baselineReadingId──▶ MeterReading
     ▲                                   ▲
     │ (najnowszy PumpingEvent           │ (historia wszystkich odczytów,
     │  wyznacza punkt bazowy)           │  najnowszy = aktualny stan)
     │                                   │
     └──────────── UsageState (wyliczana, nieprzechowywana) ─────┘
```
