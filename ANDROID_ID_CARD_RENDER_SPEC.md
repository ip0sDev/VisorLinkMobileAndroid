# VisorLink Android — ID-карта: полная спецификация отображения

Спецификация для Android-клиента (**Kotlin**, **Jetpack Compose**) — как нарисовать ID-карту VisorLink так же,
как веб: лицо и оборот трёх режимов, все признаки выпуска, кастомные скины, **три вида подписи**,
голограммы, штампы, потёртость, наклон и переворот, анимация выдачи.

Заменяет раздел «4. Отрисовка карты» из `docs/ANDROID_ID_CARDS_SPEC.md` (он устарел: например, «Заменить документ» уже убран). Модель данных, callables,
выдача, инвентарь скинов и обмен — в `ANDROID_ID_CARDS_SPEC.md` и `ANDROID_ID_SKINS_AND_SESSIONS_SPEC.md`.

**Главный принцип:** признаки выпуска (`traits`) решает сервер; все мелкие детали (узор, место голограммы,
штамп, выражение визора, подпись-росчерк, царапины…) клиент **детерминированно выводит из `traits.seed`**.
Порт обязан давать **те же числа**, иначе одна и та же карта будет выглядеть по-разному на вебе и в Android.
Сверять по контрольным значениям в части 13.

Источники истины в вебе:

| Что | Файл |
|---|---|
| Сборка карты, все слои, подписи, ключ Protogen | `src/components/idcard/IdCard.jsx` |
| Генераторы: rng, признаки, серийник, детали, подпись, MRZ, штрихкод, царапины | `src/utils/idCardModel.js` (= `functions/idCardLogic.js` для серверной части) |
| Палитры, кастомная палитра, текстуры, маски голограмм и «скрытого изображения» | `src/components/idcard/cardStyle.js` |
| Графика: гильош, дорожки, окрас, LED-визор, штампы, эмблема голограммы | `src/components/idcard/cardArt.jsx` |
| Значок режима | `src/components/idcard/IdModeGlyph.jsx` |
| Раскладка, размеры, шрифты, эффекты, анимация выдачи | `src/styles/idcard.css` |
| Наклон, переворот, гироскоп, параллакс слоёв | `src/components/idcard/useCardTilt.js` |
| Нарисованная подпись (формат) | `src/components/idcard/SignaturePad.jsx`, `functions/publicCardLogic.js` (`normalizeSignature`) |
| Тексты | `src/locales/{ru,en}.json`, ключи `idcard.*` |

---

## Часть 1. Входные данные карты

Компонент (веб `IdCard`) получает:

| Параметр | Тип | Откуда |
|---|---|---|
| `card` | объект карты (1.1) | `idCards/{uid}`, превью скина, «ID группы», визитка |
| `person` | `{ displayName, username, avatarUrl }` | профиль владельца (или группы) |
| `accent` | `#RRGGBB` \| null | цвет профиля: `customization.accentHex`, иначе старый пресет (`purple #8C6BFF`, `blue #35C7E8`, `emerald #2DD4BF`, `crimson #FF4D6A`) |
| `emojis` | строка | `customization.emojis`, на карту идут **первые 2 символа** (по code points, не по UTF-16) |
| `signature` | SVG-путь \| null | **своя нарисованная подпись** (часть 2.2); null — росчерк из seed |
| `interactive` | bool | наклон и переворот (часть 11) |
| `issuing` | bool | анимация печати при выдаче (часть 12) |

### 1.1. Объект карты

```jsonc
{
  "mode": "standard" | "protogen" | "beast",
  "species": "Красная панда" | null,     // только protogen/beast; у protogen поле называется «Модель»
  "serial": "VL-7KQM-3XPA",
  "traits": { "seed": 123456789, "finish": "base", "laminated": true, "wear": 0..3,
              "foil": "none", "variant": 0..11, "tilt": -1.5..1.5 },
  "issuedAt": 1791200000000,             // мс, «Выдана» — дата печати надетого скина
  "registeredAt": 1741910400000,         // мс, «В VisorLink с»
  "version": 1,                          // Protogen печатает «FW {version}.{seed % 10}»
  "custom": null | {                     // кастомный скин (подарок админа), часть 5.4
    "base": "#RRGGBB", "primary": "#RRGGBB", "secondary": "#RRGGBB",
    "holo": "hex" | "shield" | "star" | "rosette" | null,
    "label": "до 32 символов" | null      // подпись скина на обороте, часть 2.3
  }
}
```

Отсутствующие поля `traits` по умолчанию: `seed 1, finish base, laminated false, wear 0, foil none, variant 0, tilt 0`.
Нет `serial` → `VL-0000-0000`. Нет имени → `username` → `—`. Нет ника → `user`.

### 1.2. Где какая карта

| Место | `card` | `person` | Особенности |
|---|---|---|---|
| Профиль (своя / чужая) | `idCards/{uid}` | профиль | чужая — только если у смотрящего особый режим; в Mask Mode не рисовать |
| Настройки «ID-карта» | своя | свой профиль | |
| Превью скина (инвентарь, обмен, барабан) | `skinAsCard(skin, { mode, species, registeredAt })` **смотрящего** | смотрящий | серийник, признаки, custom — от скина; `issuedAt = skin.mintedAt` |
| «ID группы» | `{ mode: режим владельца, species: null, serial, traits, issuedAt, registeredAt: issuedAt }` из `chats/{id}.groupId` | `{ name группы, tag ?: name, avatarUrl группы }` | без accent и эмодзи |
| Визитка `/card/{ник}` | `publicCards` → `card` | из снимка | **`signature` — своя подпись**, если есть |

---

## Часть 2. Подписи — три разных поля и ключ Protogen

На карте есть **три независимых** «подписи». Их легко спутать — ниже точно, что где.

### 2.1. Подпись-росчерк от сервиса (по умолчанию)

Генерируется из seed — у каждой карты свой «почерк», одинаковый на всех устройствах.

- Функция `signaturePath(seed)` (`idCardModel.js`) → строка SVG-пути `M x y L x y …` в координатах
  **0–100 × 0–30**. Портировать один в один (часть 4.6).
- Рисуется **линией без заливки**: толщина 1.2 (в единицах viewBox 100×30), скруглённые концы и стыки.
- Где:
  - **Лицо, только Standard** — блок «Подпись» (часть 6.1).
  - **Оборот, Standard и Beast** — полоса подписи (часть 7). У Protogen там вместо подписи ключ (2.4).

### 2.2. Своя (нарисованная) подпись

Пользователь рисует её пальцем на веб-странице `/card`. Если она есть, она **заменяет росчерк 2.1 во всех
местах, где он рисуется** (лицо Standard и полоса оборота Standard/Beast). У Protogen её не видно —
там ключ подлинности.

- Источник сейчас — **только визитка**: `publicCards/{slug}.signature` (через `GET /api/card/{slug}`).
  В `idCards/{uid}` её нет, поэтому карта в профиле внутри приложения пока рисует росчерк 2.1.
  Если подпись появится в профиле/карте — правило отображения то же.
- Формат (проверяет сервер, `normalizeSignature`): только отрезки `M x y` и `L x y`, один знак после точки,
  x 0–100, y 0–30, длина ≤ 6000 символов. Регулярка:
  `^(?:[ML]\d{1,3}(?:\.\d)? \d{1,2}(?:\.\d)?)+$` и строка начинается с `M`.
  Пример: `M12.4 20.1L14 19.3L16.2 17.9M30 22L41.5 18`.
- Каждое `M` — новый штрих. Рисовать так же, как 2.1 (линия 1.2, скруглённая). Невалидную строку
  **не рисовать** — показать росчерк 2.1.
- Парсить на Android: разбить по `M`/`L`, `Path.moveTo / lineTo` в координатах 100×30 и вписать в область
  подписи **с сохранением пропорций**: на лице — по центру (`xMidYMid meet`), на обороте — прижато влево
  (`xMinYMid meet`). Так же вписывается и росчерк 2.1.

### 2.3. Подпись кастомного скина (`custom.label`)

Текст, который админ задаёт кастомному скину (до 32 символов). Это **не** подпись владельца, а надпись
выпуска — «серия», «для …», и т. п.

- Только если у карты валидный `custom` (все три цвета — корректный `#RRGGBB`) и `label` — непустая строка;
  обрезать до 32 символов.
- Где: **оборот, блок «Тираж»**, под названием тиража (часть 7.3).
- Шрифт — бренд (Space Grotesk), 600, 0.62em, межбуквенный 0.04em, цвет `ink`, перенос по любым символам,
  ширина ≤ 68 % блока. Лёгкое свечение цветом `neon` (тень 0.6em, 45 %).

### 2.4. Ключ подлинности Protogen

У Protogen вместо подписи на обороте — «Ключ подлинности»: 4 группы по 6 hex-символов через двоеточие.

```kotlin
// seed — uint32 из traits.seed (0 → 1, как в вебе: authKey(traits.seed || 1))
fun authKey(seed: Long): String {
    var x = seed.toInt() xor 0x9E3779B9.toInt()
    return (0 until 4).joinToString(":") {
        x = (x xor (x ushr 15)) * 0x2C1B3C6D                           // Int * Int = Math.imul
        (x.toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0').take(6)
    }
}
```
Моноширинный шрифт, цвет `neon`, свечение. Контроль: seed 42 → `F33218:9A2E50:30F4DD:76BC25`.

### 2.5. Сводная таблица

| Режим | Лицо: блок «Подпись» | Оборот: полоса | Оборот: «Тираж» |
|---|---|---|---|
| Standard | своя (2.2) или росчерк (2.1) | своя или росчерк + подпись «Подпись» | `custom.label` (2.3), если есть |
| Beast | — | своя или росчерк + лапа справа + подпись «Подпись» | `custom.label`, если есть |
| Protogen | — | ключ подлинности (2.4) + подпись «Ключ подлинности» | `custom.label`, если есть |

---

## Часть 3. Геометрия

- Соотношение карты **85.6 : 54** (ID-1). Ширина по умолчанию `min(360px, 86vw)`, в профиле 296px.
- **Единица раскладки `em` = ширина карты / 36.** Все размеры ниже — в этих em. Пример: карта 360dp → 1em = 10dp.
- Графика фона (узоры) — в координатах **856 × 540**, растянута с запасом 2 % со всех сторон
  (`left/top −2%, width/height 104%`, `slice`) и сдвинута на «сбой печати» (часть 4.4 `misprint`, в em).
- Скругление сторон — **0.95em**. **Protogen** — без скругления, со срезанными углами:
  полигон `(3.04%,0) (100%,0) (100%,94.4%) (96.9%,100%) (0,100%) (0,5.2%)`.
- Контур стороны — внутренняя линия 1px цвета `edge` + блик сверху 1px `rgba(255,255,255,.22)`.
  Золото: линия `rgba(255,236,180,.85)` + внутренняя 0.2em `rgba(201,150,47,.35)`.
  Потёртость 3: + внутренняя тень 1.8em `rgba(0,0,0,.22)` (светлая карта — `rgba(110,90,60,.2)`).
- Тень под картой (на «столе»): прямоугольник 6 %–94 % по ширине, от 16 % высоты до −5 % снизу, скругление 1.4em
  (Protogen 0.3em), `rgba(0,0,0,.55)`, размытие 1.1em, прозрачность 0.75. Сдвигается при наклоне (часть 11).

Шрифты: **бренд** — Space Grotesk, **UI** — Inter, **моно** — JetBrains Mono.

---

## Часть 4. Детерминированные генераторы (портировать один в один)

Арифметика — **беззнаковая 32-битная**: JS `>>> 0` → `and 0xFFFFFFFF` (Long) или `UInt`;
`Math.imul(a, b)` → `(a.toInt() * b.toInt()).toLong() and 0xFFFFFFFF`. Порядок вызовов `random()` менять нельзя.

### 4.1. `rng(seed)` — mulberry32

```kotlin
private fun imul(a: Int, b: Int): Int = a * b      // Math.imul: Int-умножение в Kotlin и так 32-битное

class Rng(seed: Long) {
    private var a = seed and 0xFFFFFFFFL
    fun next(): Double {
        a = (a + 0x6D2B79F5L) and 0xFFFFFFFFL
        var t = a.toInt()
        t = imul(t xor (t ushr 15), t or 1)
        t = t xor (t + imul(t xor (t ushr 7), t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}
```
Контроль: `rng(42)` → `0.6011037519, 0.4482905590, 0.8524657935`.

**Округление и деление — как в JS:**
- `Math.round(x)` в JS — половина вверх (`−2.5 → −2`, `2.5 → 3`). В Kotlin — `Math.round(x)` / `x.roundToInt()`.
  **Не** `kotlin.math.round()`: он округляет половины к чётному и даст другие `stampRot`, `tilt`, `smudge`.
- `Math.floor(r() * n)` → `floor(r * n).toInt()`.
- `seed ^ CONST` в JS даёт знаковое 32-битное число, но `rng` сразу делает `>>> 0` — в Kotlin: `(seed xor CONST) and 0xFFFFFFFF`.

### 4.2. Признаки и тираж

`pickWeighted(random, table)`: `x = random() * сумма весов`; по таблице по порядку `x -= вес`, первое значение,
на котором `x < 0`, — результат; иначе последнее.

`generateTraits(seed)` (`rng(seed)`, порядок вызовов как в списке) — генерирует сервер, клиенту нужен для превью/барабана: `pickWeighted` по таблицам
finish `base 55, pearl 20, metallic 14, obsidian 8, gold 3`; `laminated = random() < 0.6`; wear `0:40, 1:30, 2:20, 3:10`;
foil `none 28, classic 32, prism 23, aurora 12, galaxy 5`; `variant = floor(random()*12)`;
`tilt = round((random()*2−1)*15)/10`.

**Тираж** (`editionOf`) не хранится: очки finish `base 0, pearl 1, metallic 2, obsidian 3, gold 5` +
foil `none 0, classic 0, prism 1, aurora 2, galaxy 4`. ≥7 legendary, ≥5 epic, ≥3 rare, ≥2 uncommon, иначе common.
**Кастомный скин — всегда `epic`.**

### 4.3. Серийник

`generateSerial(seed)`: `rng(seed xor 0x5EED)`, алфавит `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, `VL-XXXX-XXXX`.

### 4.4. Детали из seed — `deriveDetails(seed)`

`r = rng(seed xor 0xA11CE5)`; `pick(arr) = arr[floor(r()*len)]`; `between(a,b) = a + r()*(b−a)`. **Строго в этом порядке:**

| # | Поле | Значение |
|---|---|---|
| 1 | `guilloche.cx` | `round(between(560, 760))` |
| 2 | `guilloche.cy` | `round(between(260, 420))` |
| 3 | `guilloche.n` | `pick([12,14,16,18,20,24])` |
| 4 | `guilloche.m` | `pick([3,5,7])` |
| 5 | `guilloche.rings` | `11 + floor(r()*6)` |
| 6 | `guilloche.phase` | `between(0.12, 0.32)` |
| 7 | `guilloche.r0` | `round(between(70, 110))` |
| 8–10 | `waves.freq`, `.amp`, `.phase` | `between(1.6,3.4)`, `between(8,20)`, `between(0, 2π)` |
| 11 | `holoShape` | `pick(['hex','shield','star','rosette'])` |
| 12 | `holoSpot` | `pick(['corner-tilt','corner','corner'])` |
| 13 | `stampStyle` | `floor(r()*3)` (0 круглый, 1 прямоугольный, 2 овальный — только Standard) |
| 14 | `stampRot` | `round(between(−24, 10))` градусов |
| 15–16 | `stampDx`, `stampDy` | `between(−0.7,0.7)`, `between(−0.6,0.6)` em |
| 17 | `stampInk` | `between(0.66, 0.92)` — прозрачность краски |
| 18 | `stampPress` | `round(between(0, 360))` — угол градиента «нажима» |
| 19 | `secondStamp` | `r() < 0.45` |
| 20 | `secondRot` | `round(between(−12, 14))` |
| 21 | `led` | `floor(r()*8)` — выражение визора Protogen |
| 22 | `coat` | `floor(r()*5)` — окрас Beast |
| 23 | `chipCorner` | `floor(r()*4)` |
| 24–26 | `smudge.x`, `.y`, `.rot` | `round(between(15,80))`, `round(between(20,70))` %, `round(between(0,180))`° |
| 27 | `bubble` | `r() < 0.5 ? { x: round(between(10,88)), y: round(between(12,82)), size: between(0.6,1.2) } : null` |
| 28–29 | `misprint.x`, `.y` | `between(−0.25,0.25)`, `between(−0.2,0.2)` em |
| 30 | `artSeed` | `floor(r()*4294967296)` (uint32) — seed графики фона |

### 4.5. Голограмма

Форма — `custom.holo`, если он задан и допустим, иначе `details.holoShape`.

### 4.6. Подпись-росчерк `signaturePath(seed)`

```
r = rng(seed xor 0x51611); base = 23; pts = []; x0 = 0
letters = 5 + floor(r()*4)
для i в 0 until letters:
    capital = i == 0
    h   = capital ? 17 + r()*4 : (r() < 0.35 ? 11 + r()*5 : 5 + r()*3)
    adv = capital ? 9 + r()*3  : 5.5 + r()*3
    d   = capital ? 4.5 + r()*2 : 1.6 + r()*1.8
    для k в 0..22:  t = k/22 * 2π
        y = base − h*(1 − cos t)/2
        pts += [x0 + adv*t/(2π) − d*sin t + (base − y)*0.38, y]
    x0 += adv
ex = pts.last.x; tail = 0.35 + r()*0.4
для k в 1..16:  t = k/16
    pts += [ex + sin(tπ)*6 − t*x0*tail, base + 1 + sin(tπ)*4.5 − t*1.5]
minX = min(xs); scale = min(1, 94/(max(xs) − minX))
путь = "M" + pts.map { "${fmt1(3 + (x−minX)*scale)} ${fmt1(base − (base−y)*(0.6 + scale*0.4))}" }.join("L")
```
`fmt1` — JS `toFixed(1)`: **ровно один знак после точки** (`23.0`, а не `23`). Округление — как у `toFixed`:
от точного двоичного значения числа, половина — вверх. В Kotlin: `BigDecimal(x).setScale(1, RoundingMode.HALF_UP)`
(именно `BigDecimal(Double)`, не `BigDecimal.valueOf`). Проверять по контрольным значениям. Внимание на порядок `r()` в выражении `h`:
для не первой буквы сначала `r() < 0.35`, затем ещё один `r()` в выбранной ветке.

### 4.7. Остальное

- `scratches(seed, count)`: `rng(seed xor 0xC0FFEE)`; на каждую: `x=r*100, y=r*100, len=3+r*11, angle=r*π`,
  линия `(x,y)→(x+cos·len, y+sin·len·0.6)`, ширина `0.15+r*0.35`. Количество = `wear * 5`.
- `barcodeBars(serial)`: для каждого символа — 5 полос `[ ((code >> i) & 1) + 1 , i чётный ]` и разделитель `[1,false]`.
  Ширины складываются по X, `true` — чёрная полоса, высота 20, растянуть на всю ширину штрихкода.
- `mrzLines(...)` — три строки по 30 символов, заполнитель `<`:
  1. `ID` + (`S`|`P`|`B` по режиму) + `VLK` + MRZ(серийник)
  2. `YYMMDD(registeredAt)<YYMMDD(issuedAt)<VLK<` + MRZ(ник) — даты по **UTC**
  3. MRZ(имя, иначе ник)

  MRZ(текст): `uppercase()` → каждую букву `[А-ЯЁІЎЄЇҐ]` заменить по таблице (буквы вне таблицы этого диапазона — на пусто) →
  NFKD и удалить диакритику `[\u0300-\u036f]` → каждую серию символов вне `A–Z0–9` заменить одним `<`.
  Таблица (дословно из `idCardModel.js`):
  ```js
  const TRANSLIT = {
    А: 'A', Б: 'B', В: 'V', Г: 'G', Д: 'D', Е: 'E', Ё: 'E', Ж: 'ZH', З: 'Z', И: 'I', Й: 'I', К: 'K', Л: 'L', М: 'M',
    Н: 'N', О: 'O', П: 'P', Р: 'R', С: 'S', Т: 'T', У: 'U', Ф: 'F', Х: 'KH', Ц: 'TS', Ч: 'CH', Ш: 'SH', Щ: 'SHCH',
    Ъ: 'IE', Ы: 'Y', Ь: '', Э: 'E', Ю: 'IU', Я: 'IA', І: 'I', Ў: 'U', Є: 'IE', Ї: 'I', Ґ: 'G',
  };
  ```
  Строки дополняются `<` и обрезаются до 30 символов. Имя в 3-й строке — то же, что на лицевой стороне
  (`displayName`, иначе ник, иначе `—`); ник — `username`, иначе `user`.

---

## Часть 5. Палитры (материал карты)

Цвета карты — **материалы пластика и краски**, не тема приложения: одинаковы в светлой и тёмной теме.
Палитра даёт токены:

| Токен | Назначение |
|---|---|
| `base` | фон стороны (градиенты, см. ниже) |
| `ink` | основной текст |
| `muted` | подписи полей, второстепенный текст, микротекст |
| `line`, `line2` | линии узора (гильош / дорожки / окрас) |
| `band`, `bandInk` | шапка и её текст |
| `neon`, `neon2` | неон Protogen; у остальных — акцентная линия |
| `stamp` | краска штампа |
| `edge` | контур стороны |
| `photoBg` | фон фото без аватара |
| `core` | «сердцевина» пластика — цвет скола угла |
| `tone` | `light` / `dark` — от него зависят режим наложения краски, цвета царапин и тиража |

**Смешивание.** `mix(A, p, B)` = CSS `color-mix(in srgb, A p%, B)`: покомпонентно `A·p + B·(1−p)`.
`mix(A, p, transparent)` = **цвет A с альфой p** (премультиплицированное смешивание). Градиенты:
`linear-gradient(θ, …)` — угол CSS (0° вверх, по часовой); `radial-gradient(W% H% at X% Y%, c, transparent S%)`.

### 5.1. Standard

`h = finish == gold ? 40 : STD_HUES[variant]`, `STD_HUES = [212,196,174,150,96,40,18,352,320,270,234,204]`.

- **obsidian** (тон dark): base `radial(120% 100% at 100% 0%, hsl(h 40% 30% / .25) → transparent 60%) + linear(135°, #1A1C21, #0B0C0F)`;
  ink `#ECEFF3`; muted `hsl(h 12% 64%)`; line `hsl(h 50% 70% / .16)`; line2 `hsl(h+36 50% 70% / .12)`;
  band `hsl(h 30% 17%)`; bandInk `hsl(h 60% 80%)`; neon `hsl(h 60% 70%)`; stamp `hsl(h 70% 72%)`;
  edge `rgba(255,255,255,.14)`; photoBg `#22252B`; core `#3A3E46`.
- **остальные** (тон light): base —
  metallic `linear(125°, hsl(h 9% 92%), hsl(h 7% 74%) 46%, hsl(h 9% 89%) 70%, hsl(h 6% 79%))`,
  gold `linear(125°, #F6E7C1, #DCC07F 48%, #F1DDA6 72%, #CFAE68)`,
  base/pearl `linear(135°, hsl(h 32% 95%), hsl(h 24% 86%))`;
  ink gold `#2B2110` иначе `hsl(h 32% 14%)`; muted gold `#6E5A32` иначе `hsl(h 14% 37%)`;
  line gold `rgba(110,80,20,.28)` иначе `hsl(h 42% 36% / .26)`; line2 `hsl(h+36 40% 42% / .2)`;
  band gold `#5E4514` иначе `hsl(h 46% 30%)`; bandInk `#FFFFFF`; neon `hsl(h 46% 30%)`;
  stamp `STD_INKS[variant % 3]` = `hsl(224 68% 38%)`, `hsl(266 52% 40%)`, `hsl(354 62% 42%)`;
  edge `rgba(255,255,255,.7)`; photoBg `hsl(h 14% 80%)`; core `#FFFFFF`.

### 5.2. Protogen (тон dark)

`[p, s] = finish == gold ? [#FFC94D, #FF7A3D] : PROTO[variant]`:

```
#3DF2FF/#FF4FD8  #3DFFB8/#9B6BFF  #5B9BFF/#3DF2FF  #FF3DCB/#3DF2FF
#B6FF3D/#3DF2FF  #FFB23D/#FF4F6B  #A46BFF/#FF6BD1  #FF3D5A/#FFB23D
#6BFFE0/#5B9BFF  #FF7AC8/#B28CFF  #FF7A3D/#FFE13D  #C8F6FF/#3DF2FF
```
glow = `radial(120% 100% at 100% 0%, mix(p,16,transparent) → transparent 55%) + radial(90% 80% at 0% 100%, mix(s,11,transparent) → transparent 60%)`.
base: metallic `glow + linear(160°, #222831, #0E1116 55%, #1C2129)`; obsidian `linear(160°, #060708, #000000)`;
иначе `glow + linear(160°, #0A0D12, #05070A)`.
ink `mix(p,12,#F4FBFF)`; muted `mix(p,50,#7E8A99)`; line `mix(p,30,transparent)`; line2 `mix(s,26,transparent)`;
band = bandInk = neon = `p`; neon2 = stamp = `s`; edge `mix(p,42,transparent)`; photoBg `mix(p,10,#0B0E13)`; core `#C9D0D8`.

### 5.3. Beast (тон dark)

`[bg, a0] = BEAST[variant]`, `a = finish == gold ? #E9C46A : a0`:

```
#2A160C/#FF8A3D  #151A22/#A9BDD4  #1E2024/#E6DCCB  #26170A/#FFB03D
#150F1C/#B894FF  #24180F/#DDA36A  #0F1824/#7CC6FF  #22190F/#E8C27A
#0E1D1C/#5FD8BC  #2A120E/#FF6E52  #211C11/#E3C664  #24111A/#FF94BC
```
base: metallic `linear(150°, mix(a,12,#2A2C30), #141518 55%, mix(a,8,#24262A))`; obsidian `linear(150°, #0D0C0B, #030303)`;
иначе `radial(110% 90% at 100% 0%, mix(a,14,transparent) → transparent 60%) + linear(150°, mix(a,6,bg), bg 60%, mix(#000,30,bg))`.
ink `mix(a,10,#F6EEE4)`; muted `mix(a,45,#9C9286)`; line `mix(a,22,transparent)`; line2 `mix(a,12,transparent)`;
band = bandInk = neon = neon2 = stamp = `a`; edge `mix(a,30,transparent)`; photoBg `mix(a,12,#16130F)`; core `#D9CFC2`.

### 5.4. Кастомный скин (`custom`, подарок админа)

Если **все три** цвета `custom.base/primary/secondary` — валидный `#RRGGBB`, палитра режима **заменяется**
(режим при этом остаётся: раскладка, графика, штампы — как у режима). Иначе `custom` игнорировать.

```
plastic = finish == obsidian ? mix(base, 24, #050607) : base
dark    = finish == obsidian || luminance(base) < 0.18          // относительная яркость WCAG
shiny   = finish in (metallic, gold)
hi = mix(gold ? #FFE9A8 : #FFFFFF, shiny ? (dark ? 18 : 40) : (dark ? 8 : 30), plastic)
lo = mix(#000000, dark ? 32 : 14, plastic)
glow = radial(120% 100% at 100% 0%, mix(p, dark?18:12, transparent) → transparent 55%)
     + radial(90% 80% at 0% 100%, mix(s, dark?12:9, transparent) → transparent 60%)
base  = shiny ? linear(125°, hi, lo 46%, hi 70%, plastic) : glow + linear(150°, hi, plastic 55%, lo)
ink   = dark ? mix(p,10,#F4F7FA) : mix(p,14,#101418)
muted = dark ? mix(p,40,#8E98A4) : mix(p,22,#4F5964)
line  = mix(p,30,transparent); line2 = mix(s,26,transparent)
band  = p; bandInk = luminance(p) < 0.18 ? #FFFFFF : #101418
neon = p; neon2 = s; stamp = s
edge = dark ? mix(p,36,transparent) : rgba(255,255,255,.7)
photoBg = mix(p, dark?12:16, plastic); core = dark ? #C9D0D8 : #FFFFFF
tone = dark ? dark : light
```
Плюс: тираж всегда **epic**, голограмма — форма `custom.holo` (если задана), на обороте — `custom.label` (2.3).

---

## Часть 6. Лицевая сторона

Слои снизу вверх: фон `base` → печать (узор) → [Protogen: HUD-рамка] → шапка → [Standard: микротекст] → фото →
[Protogen: LED] → поля → [подпись / HUD / серийник / призрачное фото] → штамп → голограмма → наклейка →
акцентная линия → **слои отделки и износа** (часть 8).

### 6.1. Standard

| Элемент | Положение / размер (em) | Вид |
|---|---|---|
| Печать — гильош | на всю карту (856×540) | `artSeed` не нужен: кольца и волны из `details.guilloche/waves` (6.4) |
| Шапка | top 0, высота 2.7, отступы 0 1.2 | фон `linear(90°, band, mix(band,80,transparent))`, текст `bandInk` |
| «VISORLINK» | в шапке | бренд, 700, 1.15em, межбукв. 0.18em |
| «ID-карта» | в шапке, gap 0.6 | UI 600, 0.62em, межбукв. 0.14em, ВЕРХНИЙ РЕГИСТР, прозрачность 0.85 |
| Микротекст | top = 2.86em (по шрифту 0.3em), на всю ширину, обрезан | `VISORLINK · ID · ` ×14, моно 600 0.3em, межбукв. 0.3em, `muted`, 0.6 |
| Фото | left 1.2, top 3.8, ширина 9.8, 3:4, повёрнуто на `traits.tilt`° | скругление 0.35, фон `photoBg`, контур 1px `edge`; фото — cover, насыщенность 0.85, контраст 1.03; без аватара — первая буква имени, UI 700 3em, `muted` |
| Поля | left 12.4, right 1.2, top 3.7, сетка 2 колонки, зазор 0.6 / 1 | 6.5 |
| Подпись | left 12.4, top 13.4, ширина 10.5 | подпись «ПОДПИСЬ» (UI 600 0.5em, межбукв. 0.12em, `muted`), под ней SVG 100×30 высотой 3em: **своя (2.2) или росчерк (2.1)**, линия 1.2, цвет `hsl(226 62% 30%)`, на тёмном тоне `hsl(210 80% 80%)` |
| Призрачное фото | right 6.4, bottom 1.3, ширина 3.4, 3:4 | только при наличии аватара: ч/б, контраст 1.3, прозрачность 0.22, маска — радиальный градиент (видно до 55 % радиуса), наложение multiply (тёмный тон — screen) |
| Серийник | left 1.2, bottom 0.9 | моно 500, 0.95em, межбукв. 0.12em, `muted` |
| Штамп | 6.6 | стиль по `stampStyle` |

### 6.2. Protogen

| Элемент | Положение / размер (em) | Вид |
|---|---|---|
| Печать — дорожки | 856×540 | `Circuits(artSeed)` (6.4) |
| HUD-рамка | на всю карту, viewBox 856×540, растянута | путь `M26 1.5H855.5V510L829 538.5H1.5V28z` (линия 1.5px) и тонкая `M14 62H300l14-12H842` (1px, 0.5), цвет `neon`, прозрачность 0.75, толщина не масштабируется |
| Шапка | высота 2.5, отступы 0 1.2 0 1.6, цвет `neon` | «VISORLINK» — **моно**, межбукв. 0.24em, свечение 0.6em `mix(neon,70)`; «// Реестр юнитов» — моно, межбукв. 0.1em, `muted`; справа значок режима |
| Значок режима | справа в шапке | см. 6.6, у Protogen прямоугольный (скругл. 0.2em), фон `mix(neon,12,transparent)` |
| Фото | left 1.6, top 3.5, ширина 9.2, 3:4, повёрнуто на `tilt` | без скругления, срез углов `(0,0) (84%,0) (100%,9%) (100%,100%) (16%,100%) (0,91%)`, поверх — строки развёртки (полоски 0.05em через 0.24em, чёрный 10 %) и диагональный блик `mix(neon,20)` → прозрачно 55 %; уголки-скобки вокруг: путь `M0 14V0h14M86 0h14v14M100 86v14H86M14 100H0V86` в квадрате 100, рамка на 0.45em шире фото, линия 2px `neon` |
| LED-визор | left 1.6, top 16.5, 9.2 × 3.4, внутр. отступы 0.4/0.5 | 6.4 LED |
| Поля | left 12.2, top 3.5, зазор 0.5 / 1 | подписи полей — моно, межбукв. 0.08em, цвет `neon` 0.8, с префиксом `▸ `; поле «Серийный №» — `neon`, межбукв. 0.14em, свечение |
| HUD-строка | left 12.2, bottom 1 | `SYS.OK ▮▮▮▮▯ // VLK.REG // {serial без «VL-»}`, моно 500 0.5em, межбукв. 0.2em, `muted` 0.85 |
| Штамп | 6.6 | шестигранник QC |

### 6.3. Beast

| Элемент | Положение / размер (em) | Вид |
|---|---|---|
| Печать — окрас | 856×540 | `Coat(artSeed, details.coat)` (6.4) |
| Шапка | top 0, высота 2.7 | фон `linear(90°, mix(band,22,transparent), transparent 85%)`, текст `band`, нижняя линия 1px `mix(band,40)`; «VISORLINK», «ID-карта», справа значок режима |
| Фото | как Standard | арка: скругление 4.9 4.9 0.45 0.45; двойная обводка 0.14em `mix(band,60)` и 0.34em `mix(band,16)` |
| Поля | как Standard | поле «Вид» — цветом `band` |
| Серийник | left 1.2, bottom 0.9 | как Standard |
| Штамп | 6.6 | круглая печать с лапой |

### 6.4. Графика фона (viewBox 856 × 540, координаты — в `cardArt.jsx`)

- **Гильош (Standard).** Кольца `k = 0 until rings`: `base = r0 + 9k`, `amp = 12 + 0.5k`, `steps = n*12`;
  для `i = 0..steps`: `t = i/steps·2π`, `r = base·(1 + 0.05·sin(m·t)) + amp·sin(n·t + k·phase·2)`,
  точка `(cx + r cos t, cy + r sin t)`. Линия 0.9, цвет `line`.
  Волны внизу: 10 линий `j`, `x` от −8 до 864 шаг 8, `y = 436 + 7j + amp·sin(x/856·2π·freq + phase + 0.38j)`. Линия 1.1, `line2`.
- **Дорожки (Protogen)** — `Circuits(artSeed)`: 13 трасс по сетке 12 с поворотами на 45°, 1 или 3 параллельные
  дорожки, переходное отверстие на конце. Дорожки — линия 1.7 `line`, скруглённые концы и стыки
  (`.idc-art-a`); отверстия — заливка `#06080B`, обводка 1.7 `line` (`.idc-art-via`).
- **Окрас (Beast)** — `Coat(artSeed, coat)`: 0 мех (штрихи — линия 1.5 `line2`, скруглённые), 1 розетки леопарда,
  2 тигриные полосы, 3 крапины оленя, 4 цепочка следов; фигуры 1–4 — заливка `line2`.
  `PAW_PATH` (лапа, центр в 0,0) — в `cardStyle.js`.
- `f1(n)` = `(Math.round(n * 10) / 10).toString()` (без лишних нулей: `12`, `12.5`); `polyline(pts)` = `M x y L x y …`.
  Для отрисовки в Compose достаточно чисел — `f1` важен, только если сравнивать строки путей.

Эталон (дословно из `cardArt.jsx`, `W = 856`, `H = 540`, `TAU = 2π`):

```js
const ROT45 = { '1,0': [1, 1], '1,1': [0, 1], '0,1': [-1, 1], '-1,1': [-1, 0], '-1,0': [-1, -1], '-1,-1': [0, -1], '0,-1': [1, -1], '1,-1': [1, 0] };
const turn = (dir, cw) => {
  if (cw) return ROT45[dir.join(',')];
  const entry = Object.entries(ROT45).find(([, to]) => to[0] === dir[0] && to[1] === dir[1]);
  return entry[0].split(',').map(Number);
};
const turn = (dir, cw) => {
  if (cw) return ROT45[dir.join(',')];
  const entry = Object.entries(ROT45).find(([, to]) => to[0] === dir[0] && to[1] === dir[1]);
  return entry[0].split(',').map(Number);
};
export function Circuits({ seed, className = '' }) {
  const random = rng(seed ^ 0xC1C);
  const G = 12;
  const snap = (n) => Math.round(n / G) * G;
  const traces = [];
  const vias = [];
  for (let i = 0; i < 13; i += 1) {
    const side = ['right', 'right', 'bottom', 'bottom', 'top'][Math.floor(random() * 5)];
    let x; let y; let dir;
    if (side === 'right') { x = W; y = snap(60 + random() * (H - 120)); dir = [-1, 0]; }
    else if (side === 'bottom') { x = snap(W * 0.32 + random() * W * 0.62); y = H; dir = [0, -1]; }
    else { x = snap(W * 0.5 + random() * W * 0.45); y = 0; dir = [0, 1]; }
    const lanes = random() < 0.35 ? 3 : 1;
    const segs = 2 + Math.floor(random() * 3);
    const cw = random() < 0.5;
    const pts = [[x, y]];
    for (let s = 0; s < segs; s += 1) {
      const diag = dir[0] !== 0 && dir[1] !== 0;
      const len = G * (diag ? 2 + Math.floor(random() * 4) : 3 + Math.floor(random() * 9));
      x = Math.max(G, Math.min(W - G, x + dir[0] * len));
      y = Math.max(G, Math.min(H - G, y + dir[1] * len));
      pts.push([x, y]);
      dir = turn(dir, s % 2 === 0 ? cw : !cw);
    }
    const perp = side === 'right' ? [0, 1] : [1, 0];
    for (let l = 0; l < lanes; l += 1) {
      const off = (l - (lanes - 1) / 2) * 9;
      const moved = pts.map(([px, py]) => [px + perp[0] * off, py + perp[1] * off]);
      traces.push(polyline(moved));
      const [ex, ey] = moved[moved.length - 1];
      vias.push([ex, ey, l === 0 && lanes === 1 ? 5 : 3.4]);
    }
  }
  return (
    <svg className={`idc-art ${className}`} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <g className="idc-art-a">{traces.map((d, i) => <path key={i} d={d} pathLength="1" />)}</g>
      <g className="idc-art-via">{vias.map(([cx, cy, r], i) => <circle key={i} cx={f1(cx)} cy={f1(cy)} r={r} />)}</g>
    </svg>
  );
}
```

```js
function brush(points, width) {
  // Мазок с сужением к концу: контур слева + справа в обратном порядке
  const left = []; const right = [];
  points.forEach(([x, y], i) => {
    const [nx, ny] = points[Math.min(points.length - 1, i + 1)];
    const [px, py] = points[Math.max(0, i - 1)];
    const dx = nx - px; const dy = ny - py; const len = Math.hypot(dx, dy) || 1;
    const w = width * (1 - i / (points.length - 1)) ** 0.8;
    left.push([x - (dy / len) * w, y + (dx / len) * w]);
    right.push([x + (dy / len) * w, y - (dx / len) * w]);
  });
  return `${polyline([...left, ...right.reverse()])}Z`;
}
export function Coat({ seed, coat, className = '' }) {
  const random = rng(seed ^ 0xBEA57);
  const shapes = [];
  if (coat === 0) {
    // Мех: короткие штрихи по потоку
    const parts = [];
    for (let i = 0; i < 260; i += 1) {
      const x = W * 0.3 + random() * W * 0.72; const y = random() * H;
      const a = -0.6 + Math.sin(x / 140 + y / 210) * 0.5;
      const l = 10 + random() * 14;
      parts.push(`M${f1(x)} ${f1(y)}q${f1(Math.cos(a) * l * 0.5 + 3)} ${f1(Math.sin(a) * l * 0.5)} ${f1(Math.cos(a) * l)} ${f1(Math.sin(a) * l)}`);
    }
    shapes.push(<path key="fur" className="idc-art-stroke" d={parts.join('')} />);
  } else if (coat === 1) {
    // Розетки леопарда: неровные пятна-лепестки вокруг центра, между ними — сплошные крапины
    for (let i = 0; i < 24; i += 1) {
      const cx = W * 0.34 + random() * W * 0.7; const cy = random() * H; const r = 10 + random() * 8;
      const petals = 3 + Math.floor(random() * 3); const start = random() * TAU;
      for (let k = 0; k < petals; k += 1) {
        const a = start + (k / petals) * TAU + (random() - 0.5) * 0.5;
        const rr = r * (0.85 + random() * 0.3);
        const px = cx + Math.cos(a) * rr; const py = cy + Math.sin(a) * rr;
        shapes.push(<ellipse key={`s${i}-${k}`} className="idc-art-fill" cx={f1(px)} cy={f1(py)} rx={f1(4 + random() * 4)} ry={f1(2.4 + random() * 1.6)}
          transform={`rotate(${f1((a * 180) / Math.PI + 90)} ${f1(px)} ${f1(py)})`} />);
      }
    }
    for (let i = 0; i < 30; i += 1) {
      shapes.push(<circle key={`m${i}`} className="idc-art-fill" cx={f1(W * 0.32 + random() * W * 0.72)} cy={f1(random() * H)} r={f1(2 + random() * 3)} />);
    }
  } else if (coat === 2) {
    // Тигриные полосы: сужающиеся мазки от верхнего и нижнего края
    for (let i = 0; i < 11; i += 1) {
      const top = i % 2 === 0;
      const x0 = W * 0.36 + (i / 11) * W * 0.66 + random() * 20;
      const len = 90 + random() * 120; const bend = (random() - 0.5) * 70;
      const pts = Array.from({ length: 9 }, (_, k) => {
        const t = k / 8;
        return [x0 + Math.sin(t * Math.PI) * bend + t * 24, top ? t * len : H - t * len];
      });
      shapes.push(<path key={`t${i}`} className="idc-art-fill" d={brush(pts, 9 + random() * 7)} />);
    }
  } else if (coat === 3) {
    // Крапины (олень): ряды светлых пятен
    for (let row = 0; row < 3; row += 1) {
      for (let i = 0; i < 9; i += 1) {
        const cx = W * 0.4 + i * 58 + random() * 24 + row * 18; const cy = 110 + row * 130 + i * 9 + random() * 26;
        shapes.push(<ellipse key={`d${row}-${i}`} className="idc-art-fill" cx={f1(cx)} cy={f1(cy)} rx={f1(7 + random() * 6)} ry={f1(5 + random() * 4)} transform={`rotate(${f1(random() * 60 - 30)} ${f1(cx)} ${f1(cy)})`} />);
      }
    }
  } else {
    // Цепочка следов по диагонали
    const steps = 7; const a = -0.42 + random() * 0.2;
    for (let i = 0; i < steps; i += 1) {
      const t = i / (steps - 1);
      const side = i % 2 === 0 ? -1 : 1;
      const cx = W * 0.36 + t * W * 0.6 - Math.sin(a) * side * 22; const cy = H * 0.86 + Math.sin(a) * t * W * 0.6 + Math.cos(a) * side * 22;
      shapes.push(<path key={`p${i}`} className="idc-art-fill" d={PAW} transform={`translate(${f1(cx)} ${f1(cy)}) rotate(${f1((a * 180) / Math.PI + 90)}) scale(1.6)`} />);
    }
  }
  return (
    <svg className={`idc-art ${className}`} viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" aria-hidden="true">{shapes}</svg>
  );
}
```
- **Оборот** Protogen/Beast: та же графика с seed `artSeed xor 0xBAC`, прозрачность 0.45.

**LED-визор Protogen** (матрица 30 × 10, `details.led` 0–7):
- Фон: скругление `1.3 1.3 1.9 1.9 / 1 1 1.7 1.7`em, градиент `#0C0F14 → #020304`, сетка точек
  (радиальная точка `rgba(255,255,255,.08)` в каждой ячейке 1/30 × 1/10), внутр. контур 1px `mix(neon,40)`,
  внутр. тень, внешнее свечение 1.2em `mix(neon,16)`.
- Пиксель — квадрат 0.76 в ячейке со сдвигом 0.12, цвет `neon`, свечение 0.1em и 0.35em.
- Глаза — 8 × 6, рисуются **зеркально**: пиксель `(x+1, y)` и `(28−x, y)`:
```
0 обычный  .####... ######.. #######. ######## .####### ...####.
1 ^ ^      ...##... ..####.. .##..##. ##....## #......# ........
2 сердечки .##..##. ######## ######## .######. ..####.. ...##...
3 > <      ##...... .###.... ...###.. ...###.. .###.... ##......
4 o o      ..####.. .##..##. ##....## ##....## .##..##. ..####..
5 сонный   ........ ........ ######## .######. ..####.. ........
6 сердитый ##...... ####.... ######.. ######## .####### ..#####.
7 x x      ##....## .##..##. ..####.. ..####.. .##..##. ##....##
```
- Рот (строки 7–9, x от 8, строка + её зеркало): для `led ∈ {1,2,4}` — `w`: `#......`, `.#....#`, `..####.`;
  иначе `zig`: `#...#..`, `.#.#.#.`, `..#...#`.

### 6.5. Поля

`dl` в 2 колонки; поле на всю ширину, кроме «половинок». Подпись поля (`dt`): UI 600 0.56em, межбукв. 0.12em,
ВЕРХНИЙ РЕГИСТР, `muted`. Значение (`dd`): UI 600 1.05em, одна строка с многоточием, `ink`.

| Порядок | Поле | Значение | Ширина | Когда |
|---|---|---|---|---|
| 0 | Имя | `displayName` | вся, **бренд 700 1.32em** | всегда |
| 1 | Ник | `@username`, моно | вся | всегда |
| 2 | Вид / Модель (protogen) | `species` | вся | не standard и есть species |
| 3 | В VisorLink с | `registeredAt`, моно, 0.92em | половина | всегда |
| 4 | Выдана | `issuedAt`, моно, 0.92em | половина | всегда |
| 5 | Серийный № | `serial`, моно | вся | только protogen |

Даты — локализованный формат «день месяц год» (`formatDate(…, 'day')`): `16 марта 2026` / `March 16, 2026`.

### 6.6. Штамп, голограмма, значок, наклейка, акцент

**Штамп главный** (подпись/дата — даты регистрации):

| Режим | Форма | Размер (em) | Положение на лице | Тексты |
|---|---|---|---|---|
| Standard, `stampStyle 0` | круглый (viewBox 100) | 5.8 × 5.8 | left 7.5, top 12.9 | по кругу `VISORLINK ★ ЗАРЕГИСТРИРОВАН ★ `, в центре звезда и дата |
| Standard, `stampStyle 1` | прямоугольный (150×72) | 7.2 × 3.46 | left 6.6, top 14.4 | `VISORLINK` / «ЗАРЕГИСТРИРОВАН» / дата |
| Standard, `stampStyle 2` | овальный (140×92) | 7 × 4.6 | left 6.6, top 14.4 | кольцо как у круглого, «ЗАРЕГИСТРИРОВАН», дата |
| Protogen | шестигранник (120×104) | 6.6 × 5.7 | left 13.4, top 15.5 | `QC`, «ПРОВЕРЕНО», дата; + свечение 0.25em `mix(stamp,55)` |
| Beast | круглый с лапой | 5.8 × 5.8 | left 7.5, top 12.9 | по кругу `VISORLINK ✦ ЗАРЕГИСТРИРОВАН ✦ `, лапа и дата |

Геометрия и размеры шрифтов внутри — `RoundSeal / RectStamp / OvalStamp / HexStamp` в `cardArt.jsx`.
Дата в главном штампе — `registeredAt`, в том же формате, что в полях (`16 марта 2026`); у второго штампа — `issuedAt`.
У круглого штампа Standard нижней подписи нет (только звезда и дата).
Тексты ВЕРХНИМ РЕГИСТРОМ из `idcard.stamp` / `idcard.stampQc` / `idcard.stampVerified`.

Общее для штампов: цвет `stamp`, прозрачность `stampInk` (тёмный тон — +0.12 и наложение **screen**,
светлый — **multiply**), сдвиг `(stampDx, stampDy)` em и поворот `stampRot`°. Неровная краска — маска:
текстура «ink» (шум, плитка 7em) × линейный градиент под углом `stampPress`° от непрозрачного (30 %) к 45 %.
Текстуру допустимо упростить, но **нажим (градиент) оставить**.

**Голограмма** (если `foil != none`): 4.4 × 4.4em в правом нижнем углу — right 1.3, bottom 1.3
(Protogen: 1.5 / 1.9). `holoSpot = corner-tilt` → right 1.6, bottom 1.6 (Protogen 1.8 / 2.1) и поворот −9°.
**Никогда не поверх фото.** Устройство — часть 9.

**Значок режима** (`IdModeGlyph`, у Standard нет): в плашке `margin-left: auto`, отступы 0.25/0.6em, рамка 1px
`mix(currentColor,60)`, моно 700 0.6em межбукв. 0.12em ВЕРХНИМ: значок 13 + «PROTOGEN»/«BEAST».
Protogen — визор (`rect 2.5,6 19×12 rx5` + шевроны), Beast — лапа (5 эллипсов), viewBox 24.

**Наклейка-эмодзи** (если есть): right 1.2, top 3.3, белая подложка, скругление 0.5, тень 0.1/0.3em,
поворот 9°, размер 1.15em.

**Акцентная линия** (если `accent` — валидный HEX): Standard/Beast — на всю ширину под шапкой, top 2.7, высота 0.16em;
Protogen — left 1.6, top 2.2, ширина 5.5, высота 0.12em. Цвет — `accent`.

---

## Часть 7. Оборот

Порядок: фон `base` → [Protogen/Beast: графика, прозрачность 0.45] → магнитная полоса → блок штрихкода →
полоса подписи → блок «Тираж» → [второй штамп] → MRZ → слои отделки и износа.

### 7.1. Магнитная полоса
top 2, высота 3.6, на всю ширину. Standard: `linear(180°, #1C1E22, #0B0C0E 55%, #18191C)`.
Protogen: `linear(90°, #020304, mix(neon,24,#020304) 70%, #020304)` + линии 1px `mix(neon,45)` сверху и снизу.
Beast: `linear(180°, #0B0908, #1A1612 50%, #0B0908)` + нижняя линия `mix(band,40)`.

### 7.2. Штрихкод и примечание
Блок left 1.2, top 6.6, ширина 19.5, столбик с зазором 0.6:
- штрихкод `barcodeBars(serial)`, высота 2.6, отступы 0.3/0.5, белая подложка, скругл. 0.25, полосы `#111214`;
  Protogen — без подложки, полосы `neon`, рамка 1px `line`, без скругления;
- примечание `idcard.backNote`, 0.62em, межстрочный 1.45, `muted`.

### 7.3. Блок «Тираж»
right 1.2, top 6.6, ширина 13.4, мин. высота 4.4, отступы 0.7/0.8, рамка 1px `line`, скругл. 0.5 (Protogen 0).
Сверху вниз, зазор 0.3:
1. «ТИРАЖ» — UI 600 0.5em, межбукв. 0.16em, `muted`;
2. **название тиража** — бренд 700 0.92em, межбукв. 0.06em, ВЕРХНИЙ РЕГИСТР; цвет по тиражу и тону:

   | Тираж | светлый тон | тёмный тон |
   |---|---|---|
   | common | `ink` | `ink` |
   | uncommon | `hsl(150 60% 28%)` | `hsl(150 70% 66%)` |
   | rare | `hsl(212 70% 38%)` | `hsl(212 90% 72%)` |
   | epic | `hsl(276 55% 42%)` | `hsl(276 85% 78%)` |
   | legendary | золотой градиент по тексту `100°: #B8862A, #FFE9A8 40%, #C9962F 60%, #FFF1C4` | то же |

3. **подпись кастомного скина `custom.label`** (часть 2.3) — если есть;
4. признаки: `{отделка} · {фольга}` (+ у Protogen ` · FW {version}.{seed % 10}`), моно 500 0.56em, `muted`, ширина ≤ 68 %;
5. мини-голограмма 2.4 × 2.4em в правом нижнем углу блока (right 0.6, bottom 0.6) — если есть фольга.

### 7.4. Полоса подписи
left 1.2, top 12.4, ширина 19.5. Сверху подпись: «ПОДПИСЬ» или (Protogen) «КЛЮЧ ПОДЛИННОСТИ» —
UI 600 0.5em, межбукв. 0.14em, `muted`, отступ 0.25. Полоса высотой 3, отступы 0 0.7, скругл. 0.25:
- Standard: фон «бумага» — повторяющийся градиент −35°: `#F4F1EA` 0–0.35em, `#E9E4D8` 0.35–0.7em; **подпись 2.2 / 2.1**
  цветом `hsl(226 62% 30%)`, высота 2.6em, прижата влево (`xMinYMid meet`);
- Beast: то же, цвет подписи `#4A3018`, справа значок лапы 18, поворот −14°, прозрачность 0.85;
- Protogen: фон `mix(neon,7,#040506)`, рамка 1px `line`, без скругления; текст **ключа 2.4** моно 0.82em,
  межбукв. 0.08em, `neon`, свечение.

### 7.5. Второй штамп «Проверено»
Если `details.secondStamp`: прямоугольный (7.2 × 3.46em), right 9.5, top 12.2, поворот `secondRot`°,
тексты `VISORLINK` / «ПРОВЕРЕНО» / **дата выдачи** (`issuedAt`). Остальное — как у главного штампа.

### 7.6. MRZ
left 1.2, right 1.2, bottom 0.8; моно 0.78em, межстрочный 1.35, межбукв. 0.16em, `ink` 0.85
(Protogen — `neon` 0.75); три строки `mrzLines` (часть 4.7), без переносов, лишнее обрезать.

---

## Часть 8. Отделка и износ (поверх содержимого каждой стороны)

Порядок снизу вверх. «Параллакс» — слой двигается при наклоне (часть 11.3).

| # | Слой | Когда | Вид |
|---|---|---|---|
| 1 | Перламутр | `finish = pearl` | слой 200 % от −50 %, градиент 120°: прозрачно 22 % → розовый `rgba(255,190,230,.4)` 33 % → голубой `rgba(190,228,255,.4)` 44 % → мятный `rgba(200,255,225,.35)` 55 % → кремовый `rgba(255,240,200,.35)` 65 % → прозрачно 78 %; прозрачность 0.55 (тёмный тон 0.22). Параллакс `pearl` |
| 2 | Шлифовка | `finish = metallic` | вертикальные волоски: повтор 3px — 1px `rgba(255,255,255,.06)`, 1px `rgba(0,0,0,.04)`, 1px прозрачно |
| 3 | Скрытое изображение | `foil = aurora / galaxy` | 9.1 |
| 4 | Зерно | `wear ≥ 1` | шумовая текстура «grain» плиткой 6em, прозрачность 0.45 / 0.75 (wear 2) / 1 (wear 3) |
| 5 | Царапины | `wear ≥ 1` | `scratches(seed, wear*5)` в координатах 100×100, растянуто; линия `w·2.4`, не масштабируется; цвет `rgba(255,255,255,.38)`, светлый тон `rgba(40,50,60,.26)` |
| 6 | Отпечаток пальца | `wear ≥ 2` | эллипс 5.4 × 6.4em с центром в `smudge.x%, smudge.y%`, поворот `smudge.rot`; концентрические кольца (прозрачно 0–0.12em, `rgba(255,255,255,.07)` 0.12–0.19em; светлый тон — `rgba(70,55,40,.07)`), маска радиальная до 35 % |
| 7 | Скол угла | `wear = 3` | 1.7 × 1.2em в углу `chipCorner` (0 лв, 1 пв, 2 пн, 3 лн), цвет `core` 0.7, форма — полигоны из `idcard.css` (`.idc-chip.c0…c3`) |
| 8 | Плёнка | `laminated` | рамка с отступом 0.28em, скругл. 0.72 (Protogen 0), внутр. линия 1px `rgba(255,255,255,.3)`, внутр. свечение 0.7em `rgba(255,255,255,.06)`, сверху градиент `rgba(255,255,255,.07)` → прозрачно 35 % |
| 8a | Пузырь под плёнкой | `laminated` и `details.bubble` | эллипс 1.1 × 0.8 × `bubble.size`, центр в `bubble.x%, bubble.y%`, поворот −20°, блик и кольцо |
| 9 | Блик | всегда | слой 260 % от −80 %, градиент 105°: прозрачно 43 % → `rgba(255,255,255,.04)` 47 % → `.09` 50 % → `.04` 53 % → прозрачно 59 %; при плёнке, metallic, obsidian, gold — ярче: 39/46/50/54/61 % с `.06/.16/.06`. Параллакс `sheen` |

---

## Часть 9. Голограмма и «скрытое изображение»

### 9.1. Голограмма (`foil != none`)

Квадрат, обрезанный **маской формы** (viewBox 100, эталон — `HOLO_SHAPES`, `starPoints`, `rosettePoints` в `cardStyle.js`):
- `hex` — `50,2 92,26 92,74 50,98 8,74 8,26`;
- `shield` — `M50 3L91 15V47C91 73 73 90 50 97 27 90 9 73 9 47V15Z`;
- `star` — 8-лучевая звезда, внешний r 49, внутренний 36, первый луч вверх;
- `rosette` — 160 точек, `r = 44 + 5·|cos 8a|`.

Слои внутри:
1. фон: `linear(135°, #E9EEF4, #A9B5C3 45%, #F4F7FA 60%, #9DABBB)`; золото — `#FBE6A6, #C9962F 45%, #FFF1C4 60%, #B8862A`;
   галактика — `radial(at 32% 30%, #6A4CC0, #1E1650 58%, #0A0820)` + звёзды (белые точки в двух сетках 1.1 и 1.4em);
   **при отделке gold фон всегда золотой, даже у галактики** (правило золота в CSS идёт позже); звёзды и радуга галактики остаются;
2. **радуга** (параллакс `rainbow`): слой 300 % от −100 %, повторяющийся градиент 118°:
   classic `#FF6B9A 0, #FFD36B 3.5%, #7BFFB2 7%, #6BD7FF 10.5%, #B58CFF 14%, #FF6B9A 17.5%`, прозрачность 0.32;
   prism — то же, 0.85; aurora — `#3DFFB8 0, #3DD8FF 5%, #9B6BFF 10%, #FF6BD1 14%, #3DFFB8 19%`, 0.85;
   galaxy — `#FF4FD8 0, #6B8BFF 6%, #3DF2FF 11%, #B58CFF 16%, #FF4FD8 22%`, 0.5;
3. **отблеск** (параллакс `glint`): слой 300 %, 115°: прозрачно 44 % → `rgba(255,255,255,.8)` 50 % → прозрачно 56 %;
4. **эмблема с тиснением** (viewBox 100): два кольца r 44 и 38.5, розетка-гипотрохоида `hypotrochoid(96, 36, 30, 0.42, 50, 50)`
   (линия 0.6; эталон ниже) и знак режима: Standard — текст `VL` (бренд 800, 21, y 57),
   Protogen — визор (`rect −17,−10 34×20 rx8` + шевроны), Beast — лапа. Рисуется дважды: тень
   `rgba(0,0,0,.28)` со сдвигом (0.8, 0.9) и свет `rgba(255,255,255,.75)`. Линии 1.3.

```js
function hypotrochoid(R, r, d, scale, cx, cy) {
  const g = (a, b) => (b ? g(b, a % b) : a);
  const loops = r / g(R, r);
  const steps = Math.round((R / g(R, r)) * 22);
  const pts = [];
  for (let i = 0; i <= steps; i += 1) {
    const t = (i / steps) * TAU * loops;
    pts.push([cx + scale * ((R - r) * Math.cos(t) + d * Math.cos(((R - r) / r) * t)), cy + scale * ((R - r) * Math.sin(t) - d * Math.sin(((R - r) / r) * t))]);
  }
  return polyline(pts);
}
```

### 9.2. «Скрытое изображение» (`foil = aurora` или `galaxy`)

Слой на всю сторону, маска из `traits.seed` (0 → 1), координаты 856×540, растянута на сторону. Эталон (`cardStyle.js`):

```js
export function revealMask(kind, seed) {
  const key = `${kind}:${seed}`;
  if (revealMasks.has(key)) return revealMasks.get(key);
  const random = rng(seed ^ 0xA0A);
  let body = '';
  if (kind === 'galaxy') {
    for (let i = 0; i < 70; i += 1) {
      const x = random() * W; const y = random() * H; const s = random() < 0.12 ? 9 + random() * 8 : 2 + random() * 4;
      body += `<path transform="translate(${f1(x)} ${f1(y)})" d="M0 ${f1(-s)}Q${f1(s * 0.16)} ${f1(-s * 0.16)} ${f1(s)} 0Q${f1(s * 0.16)} ${f1(s * 0.16)} 0 ${f1(s)}Q${f1(-s * 0.16)} ${f1(s * 0.16)} ${f1(-s)} 0Q${f1(-s * 0.16)} ${f1(-s * 0.16)} 0 ${f1(-s)}Z"/>`;
    }
    for (let i = 0; i < 160; i += 1) body += `<circle cx="${f1(random() * W)}" cy="${f1(random() * H)}" r="${f1(0.8 + random() * 1.4)}"/>`;
  } else {
    const ph = random() * TAU; const fr = 1.2 + random() * 1.2;
    let d = '';
    for (let j = 0; j < 11; j += 1) {
      const pts = [];
      for (let x = -10; x <= W + 10; x += 12) pts.push([x, 50 + j * 42 + 26 * Math.sin((x / W) * TAU * fr + ph + j * 0.32)]);
      d += polyline(pts);
    }
    body = `<path d="${d}" fill="none" stroke="#000" stroke-width="2.2"/>`;
  }
  const url = svgUrl(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" fill="#000">${body}</svg>`);
  revealMasks.set(key, url);
  return url;
}
```
Внутри — радужная заливка 220 % (аврора — как радуга aurora, 0.5; тёмный тон 0.32; светлый 0.5;
галактика — `#FFFFFF, #FF9BE6 4%, #9BB4FF 8%, #7BFFF0 11%, #FFFFFF 14%`, 0.95), с параллаксом `rainbow`.
**Видимость слоя зависит от угла наклона** (параллакс `reveal`): почти не видно прямо, проступает под углом.
У неинтерактивной карты (превью, миниатюры, PNG) прозрачность слоя постоянная — **0.2**.

---

## Часть 10. Тексты (`idcard.*`)

| Ключ | ru | en |
|---|---|---|
| `title` | ID-карта | ID card |
| `protogen.doc` | Реестр юнитов | Unit registry |
| `mode.standard/protogen/beast` | Standard / Protogen / Beast | — |
| `field.name` | Имя | Name |
| `field.username` | Ник | Handle |
| `field.species` | Вид | Species |
| `field.model` | Модель | Model |
| `field.registered` | В VisorLink с | Member since |
| `field.issued` | Выдана | Issued |
| `field.serial` | Серийный № | Serial no. |
| `field.signature` | Подпись | Signature |
| `field.authKey` | Ключ подлинности | Auth key |
| `stamp` | Зарегистрирован | Registered |
| `stampQc` | Проверено | QC passed |
| `stampVerified` | Проверено | Verified |
| `backNote` | Выдано VisorLink. Карта подтверждает аккаунт в мессенджере и не является удостоверением личности. | Issued by VisorLink. Confirms a messenger account; not an identity document. |
| `edition.label` | Тираж | Edition |
| `edition.common…legendary` | Обычный, Необычный, Редкий, Эпический, Легендарный | Common, Uncommon, Rare, Epic, Legendary |
| `finish.*` | Стандартный пластик, Перламутр, Металлик, Обсидиан, Золото | Standard plastic, Pearl, Metallic, Obsidian, Gold |
| `foil.*` | Без голограммы, Классика, Призма, Аврора, Галактика | No hologram, Classic, Prism, Aurora, Galaxy |
| `showBack` / `showFront` | Оборот / Лицевая сторона | Back side / Front side |
| `aria` | ID-карта {name}, @{username}, режим {mode}, в VisorLink с {registered}, номер {serial} | ID card of {name}, @{username}, {mode} mode, member since {registered}, number {serial} |

`aria` — `contentDescription` всей карты (карта — одна картинка для TalkBack). Кнопка переворота отдельная:
«Оборот» / «Лицевая сторона».

---

## Часть 11. Наклон, переворот, гироскоп

### 11.1. Состояние и константы
`rx, ry` — текущие углы (°), `tx, ty` — цель, `vy` — скорость по Y, `face` 0 лицо / 1 оборот.
`MAX_TILT_X = 14`, `MAX_TILT_Y = 18`. Перспектива ≈ 1100px (Compose: `cameraDistance`).
Обратная сторона повёрнута на 180° по Y, задняя грань каждой стороны не рисуется.

Пружина каждый кадр (если не тянут): `vy += (ty − ry)·0.09; vy *= 0.76; ry += vy; rx += (tx − rx)·0.18`.
Остановка, когда все разности и скорость < 0.05.

`faceAngle(ry, face) = round((ry − face·180)/360)·360 + face·180` — ближайший угол, где видна сторона.

### 11.2. Жесты
- **Тап** (смещение < 6px) — переворот: `ty = faceAngle(ry, face) + 180` (всегда вперёд на пол-оборота).
- **Перетаскивание:** `ry = startRy + dx·0.55`, `rx = clamp(−dy·0.12, ±14)`, скорость `vy = Δx·0.55 / (dt/16)`.
  Отпустили: `projected = ry + vy·14`, `snapped = round(projected/180)·180`, сторона = `snapped/180 mod 2`, `ty = snapped`, `tx = 0`.
- **Гироскоп** (телефон, если не включено «уменьшение движения»): база `(beta, gamma)` подстраивается
  `base += (value − base)·0.02` каждое событие; `dx = clamp((gamma − base.gamma)/22, ±1)`,
  `dy = clamp((beta − base.beta)/22, ±1)`; `tx = −dy·14`, `ty = faceAngle + dx·18·(face ? −1 : 1)`.
  На Android: `TYPE_GAME_ROTATION_VECTOR` → `SensorManager.getOrientation`, pitch ≈ beta, roll ≈ gamma (в градусах).
- **Уменьшение движения** (`ANIMATOR_DURATION_SCALE = 0` / системная настройка): без гироскопа и пружин —
  переворот мгновенный.
- Кнопка «Оборот / Лицевая сторона» под картой — всегда (для TalkBack и reduced motion).

### 11.3. Параллакс слоёв (из углов, каждый кадр)
```
local = ((ry mod 360) + 540) mod 360 − 180                  // −180…180
faceY = |local| > 90 ? (local > 0 ? local − 180 : local + 180) : local
fx = clamp(faceY/24, ±1.6); fy = clamp(rx/14, ±1.2); mag = min(1, hypot(fx, fy)); cosY = |cos(ry)|
sheen   : translate(−fx·20 %, fy·16 %)      — от размера слоя
rainbow : translate(−fx·16 %, −fy·13 %)
glint   : translate(−fx·34 %, fy·26 %)
pearl   : translate(fx·12 %, fy·10 %)
reveal  : opacity = clamp(mag·1.5 − 0.15, 0, 1)
shadow  : translate(−faceY·0.3 px, −rx·0.5 px), scaleX(0.3 + 0.7·cosY)
```
**Производительность:** при вращении меняются только трансформации карты и этих слоёв. Всё статичное
(фон, узоры, тексты, штампы, износ) растеризовать один раз (`drawWithCache` / `GraphicsLayer` / `Picture`)
и не перерисовывать при наклоне.

---

## Часть 12. Анимация печати при выдаче

Только в церемонии выдачи (длительность `CEREMONY_MS = 6200` мс, затем экран «Карта выдана»; кнопка «Пропустить»). Задержки от старта:

| Время | Что |
|---|---|
| 0.2 с | печать (узор) проявляется 0.9 с; линии узора «прорисовываются» 1.8 с |
| 0.5 с | шапка, HUD-рамка, микротекст — появление 0.5 с |
| 0.9 с | фото «проявляется» 1.3 с: из размытия 0.5em, ч/б, яркость 1.7, прозрачность 0.2 |
| 1.5 + 0.3·i с | поля печатаются слева направо 0.5 с (16 шагов). `i` фиксирован: имя 0, ник 1, вид 2, «В VisorLink с» 3, «Выдана» 4, серийный № (Protogen) 5, блок подписи (Standard) 5 — номер не сдвигается, если поля нет |
| 1.6 с | уголки-скобки Protogen (мигание 2 шага) |
| 2.1 / 2.4 с | LED-визор появляется / «загружается» (мерцание) |
| 3.1 с | серийник и HUD-строка печатаются 0.6 с |
| 3.6 с | штамп «бьёт» 0.42 с: из масштаба 2.3 и прозрачности 0 |
| 3.85 с | карта вздрагивает от удара (0.3 с, отодвигается на 22px и наклоняется 2.5°) |
| 4.1 с | наклейка выскакивает 0.4 с |
| 4.4 с | при плёнке — проход ламинатора (блик слева направо 0.9 с) |
| 5 с | голограмма появляется 0.8 с (из масштаба 1.35), радуга проезжает 1.2 с |
| 5.1 с | «скрытое изображение» вспыхивает 1.3 с |

При уменьшении движения — без анимации.

---

## Часть 13. Контрольные значения (сверять порт)

```
rng(42) первые три: 0.6011037519, 0.4482905590, 0.8524657935

seed 1:          serial VL-44CX-3AKB, traits {pearl, laminated, wear 1, galaxy, variant 11, tilt −0.7}, тираж epic
                 guilloche {cx 702, cy 370, n 14, m 3, rings 16, r0 93}, holoShape star, holoSpot corner-tilt,
                 stampStyle 0, stampRot −14, stampPress 320, secondStamp false, led 5, coat 1, chipCorner 0,
                 smudge {34, 64, 16}, bubble null, artSeed 789935719
                 authKey 3B0BE4:CD1A49:DF3CF2:720FD8
                 signaturePath начинается "M3.9 23.0L3.3 22.6L3.0 21.5L3.2 19.6L3.9 17.3L5.", длина 1980

seed 42:         serial VL-ACS2-6WMF, traits {pearl, laminated, wear 2, prism, variant 2, tilt 0.1}, тираж uncommon
                 guilloche {cx 665, cy 389, n 16, m 7, rings 16, r0 81}, holoShape hex, holoSpot corner-tilt,
                 stampStyle 1, stampRot −7, stampPress 85, secondStamp true, secondRot −12, led 7, coat 4,
                 chipCorner 3, smudge {23, 54, 93}, artSeed 364178403
                 authKey F33218:9A2E50:30F4DD:76BC25
                 signaturePath начинается "M5.1 23.0L3.9 22.6L3.2 21.5L3.0 19.8L3.3 17.5L4.", длина 1750

seed 123456789:  serial VL-UHCN-QXX9, traits {base, без плёнки, wear 2, none, variant 3, tilt 0.7}, тираж common
                 guilloche {cx 654, cy 324, n 24, m 5, rings 15, r0 80}, holoShape hex, stampStyle 1, stampRot −4,
                 led 0, coat 1, artSeed 1256577108, authKey 497CAB:4E81B6:213EE7:6BAFD2, signaturePath длина 1293

seed 3735928559: serial VL-7FWD-ZDGE, traits {obsidian, laminated, wear 2, classic, variant 5, tilt 1}, тираж rare
                 holoShape rosette, holoSpot corner, stampStyle 0, stampRot −9, led 6, coat 2, artSeed 569453895,
                 authKey 1AA02C:3DFBF4:034E45:546E14, signaturePath длина 1518

mrzLines(serial VL-7KQM-3XPA, protogen, ник ipos_dev, имя «Иван Щукин», рег. 2026-03-16, выдана 2026-10-09):
  IDPVLKVL<7KQM<3XPA<<<<<<<<<<<<
  260316<261009<VLK<IPOS<DEV<<<<
  IVAN<SHCHUKIN<<<<<<<<<<<<<<<<<

barcodeBars("VL-7KQM-3XPA"): 72 элемента, первые 12:
  [1,т] [2,ф] [2,т] [1,ф] [2,т] [1,ф] [1,т] [1,ф] [2,т] [2,ф] [1,т] [1,ф]

scratches(42, 2): {89.603, 35.825 → 91.367, 38.761, w 0.26}, {91.494, 67.61 → 77.795, 67.932, w 0.378}
```

Для полной сверки удобно прогнать веб-модель в Node:
`node -e "import('./src/utils/idCardModel.js').then(m => console.log(m.deriveDetails(42)))"`.

---

## Чек-лист

- [ ] Порт генераторов (часть 4) — совпадение с контрольными значениями (часть 13), включая `signaturePath` целиком
- [ ] Палитры трёх режимов × 12 вариантов × 5 отделок + кастомная палитра (часть 5), `mix` с прозрачностью
- [ ] Лицо: Standard / Protogen / Beast (часть 6), графика фона, LED-визор, поля, штампы 5 видов, голограмма, значок, наклейка, акцент
- [ ] **Подписи:** росчерк из seed (2.1), своя нарисованная (2.2, с проверкой формата и запасным вариантом), `custom.label` (2.3), ключ Protogen (2.4) — по таблице 2.5
- [ ] Оборот: полоса, штрихкод, примечание, «Тираж» (цвета по тиражу), полоса подписи / ключа, второй штамп, MRZ (часть 7)
- [ ] Отделка и износ: перламутр, шлифовка, зерно, царапины, отпечаток, скол, плёнка, пузырь, блик (часть 8)
- [ ] Голограмма 4 формы × 4 фольги + золото, эмблема с тиснением, «скрытое изображение» (часть 9)
- [ ] Наклон, переворот, инерция, гироскоп, параллакс, reduced motion, кнопка переворота (часть 11)
- [ ] Анимация печати при выдаче (часть 12)
- [ ] TalkBack: `contentDescription` из `idcard.aria`
- [ ] Статичные слои кэшируются, при наклоне перерисовываются только трансформации
