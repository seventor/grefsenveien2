# Testing Grefsenveien (Android Auto, telefon og Wear OS)

Denne guiden beskriver hvordan du bygger, installerer og tester appen på telefon, med **Desktop Head Unit (DHU)**-emulatoren, og kort om Wear OS.

## Forutsetninger

| Krav | Merknad |
|------|---------|
| **Android Studio** | Nyeste stabile versjon anbefales |
| **Android SDK** | Platform-tools, build-tools, og emulator-pakker |
| **Fysisk Android-telefon** | Anbefalt for Android Auto / DHU (emulator alene er upålitelig) |
| **USB-kabel** | For `adb` og DHU-tilkobling |
| **Android Auto** (Play Store) | Må være installert på telefonen |
| **`local.properties`** | Med `sdk.dir` og hemmelige URL-er (se [README.md](README.md)) |

### Miljøvariabler og `adb` i PATH

Legg dette i `~/.zshrc` (eller tilsvarende for din shell):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
export PATH="$PATH:$ANDROID_HOME/emulator"
export PATH="$PATH:$ANDROID_HOME/extras/google/auto"
```

Last inn på nytt:

```bash
source ~/.zshrc
```

Verifiser:

```bash
which adb
adb version
```

### Installer DHU (Desktop Head Unit)

DHU er **ikke** en Play Store-app — den kommer med Android SDK.

1. Åpne **Android Studio** → **Settings** / **Preferences**
2. **Languages & Frameworks** → **Android SDK** → fanen **SDK Tools**
3. Huk av **Android Auto Desktop Head Unit emulator**
4. Klikk **Apply**

Binæren ligger typisk her:

```text
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

---

## Konfigurasjon før første kjøring

Opprett eller oppdater `local.properties` i prosjektroten. Android Studio oppretter ofte `sdk.dir` automatisk; du må legge til URL-ene selv:

```ini
sdk.dir=/Users/ditt-brukernavn/Library/Android/sdk

GARAGE_WEBHOOK_URL=https://your-home-assistant.url/api/webhook/secret_code_garage
GATE_WEBHOOK_URL=https://your-home-assistant.url/api/webhook/secret_code_gate
S3_IMAGE_URL=https://your-s3-bucket-url.com/latest.jpg
S3_MAILBOX_IMAGE_URL=https://your-mailbox-image-url.com/latest.jpg
DOORBELL_TAKE_IMAGE_URL=https://your-api-gateway-url/prod/
```

Gradle injiserer disse i `BuildConfig` ved bygging. **Bygg appen på nytt** etter at du endrer `local.properties`.

---

## Del 1: Teste på telefonen (uten bil / uten DHU)

Telefon-appen (`MainActivity`) er en **følgesvenn** for enkel testing av webhooks, kamerabilde og innlogging — den er **ikke** den samme UI-en som i Android Auto.

### Bygg og installer med Android Studio

1. Åpne prosjektet i Android Studio.
2. Koble telefonen med USB og aktiver **USB-feilsøking** (Utvikleralternativer på telefonen).
3. Velg modulen **`app`** og den fysiske enheten i enhetslisten.
4. Klikk **Run** (▶) eller trykk `Ctrl+R` / `⌃R`.

### Bygg og installer fra kommandolinje

Fra prosjektroten:

```bash
./gradlew :app:installDebug
```

Start appen manuelt på telefonen (ikonet **Grefsenveien**), eller:

```bash
adb shell am start -n com.pixelspore.grefsenveien/.MainActivity
```

### Hva du kan teste på telefonen

- Webhook-knapper for garasje og port
- Henting og visning av kamerabilde fra S3
- Eventuell Google-innlogging (hvis aktivert i `MainActivity`)

Dette verifiserer **ikke** tegning på `Surface`, ActionStrip eller oppsett i Android Auto — bruk DHU eller ekte bil for det.

---

## Del 2: Klargjøre telefonen for Android Auto-testing

DHU simulerer bilskjermen; **telefonen** kjører fortsatt Android Auto-appen og videreformidler Car App til DHU.

### 1. USB-feilsøking

På telefonen: **Innstillinger** → **Om telefonen** → trykk **Byggenummer** 7 ganger → **Utvikleralternativer** → slå på **USB-feilsøking**.

### 2. Utviklermodus i Android Auto

1. Installer / oppdater **Android Auto** fra Play Store.
2. Åpne **Android Auto** på telefonen.
3. Gå til **Innstillinger** (eller **Om** / **Version**).
4. Trykk på **versjonsnummeret** omtrent **10 ganger** til utviklermodus aktiveres.
5. Slå på valg som ligner på:
   - **Ukjente kilder** / **Add unrecognized apps**
   - Tillat appen **Grefsenveien** (`com.pixelspore.grefsenveien`)

Uten dette vises ikke debug-builds i DHU eller i bilen.

### 3. Verifiser `adb`-tilkobling

```bash
adb devices
```

Du skal se enheten som `device` (ikke `unauthorized`). Godta USB-feilsøking på telefonen ved behov.

---

## Del 3: Teste med Desktop Head Unit (DHU)

DHU er Googles offisielle emulator for Android Auto head unit-skjermen på Mac/PC.

### Steg-for-steg

#### 1. Installer debug-build på telefonen

```bash
cd /path/til/grefsenveien
./gradlew :app:installDebug
```

Eller **Run** `app` fra Android Studio mot telefonen.

#### 2. Sett opp port forwarding

DHU kommuniserer med telefonen over `localhost:5277`:

```bash
adb forward tcp:5277 tcp:5277
```

Kjør denne kommandoen på nytt hver gang du kobler fra USB eller starter en ny terminal-sesjon.

#### 3. Start DHU med skjermprofil

Prosjektet har to ferdige konfigurasjonsfiler i rotmappen:

| Fil | Skjerm | Bruk |
|-----|--------|------|
| `square_dhu.ini` | 1080×1080 (firkant) | Mange OEM head units |
| `mache_dhu.ini` | 1080×1920 (portrett) | Høy / smal skjerm |

**Firkant skjerm:**

```bash
cd /path/til/grefsenveien
desktop-head-unit -c square_dhu.ini
```

**Portrett / høy skjerm:**

```bash
desktop-head-unit -c mache_dhu.ini
```

Hvis `desktop-head-unit` ikke finnes i PATH, bruk full sti:

```bash
"$ANDROID_HOME/extras/google/auto/desktop-head-unit" -c square_dhu.ini
```

#### 4. Åpne appen i DHU

1. La DHU-vinduet stå åpent.
2. På telefonen skal **Android Auto** gjerne koble seg automatisk til DHU.
3. I DHU-menyen: finn appen under **Navigation** (appen er registrert med kategorien `NAVIGATION`).
4. Appen **Grefsenveien** lastes via `CarAppService` → `MainCarScreen`.

Du skal nå se kamerafeed, tidsstempel og knappene **Garasje**, **Port** og **Oppdater**.

### DHU-konfigurasjon (valgfritt)

Eksempel fra `square_dhu.ini`:

```ini
[general]
touch = true

[display]
resolution = 1080x1080
dpi = 240
framerate = 60
```

Du kan kopiere og tilpasse filene for andre oppløsninger.

### Nyttig rekkefølge (sjekkliste)

```text
[ ] local.properties er fylt ut
[ ] ./gradlew :app:installDebug
[ ] Utviklermodus i Android Auto + app tillatt
[ ] adb devices viser telefonen
[ ] adb forward tcp:5277 tcp:5277
[ ] desktop-head-unit -c square_dhu.ini (eller mache_dhu.ini)
[ ] Velg Grefsenveien under Navigation i DHU
```

---

## Del 4: Teste i ekte bil

1. Bygg og installer appen på telefonen (debug eller release fra Play Store).
2. For **debug** utenfor Play Store: bruk utviklermodus i Android Auto og tillat ukjente apper (som over).
3. Koble telefonen til bilen med USB, eller bruk trådløs Android Auto hvis bil og telefon støtter det.
4. Åpne appen fra bilens Android Auto-meny (Navigation).

---

## Del 5: Wear OS (kort)

1. Par en Wear OS-klokke eller start Wear-emulator i Android Studio.
2. Velg modulen **`wear`** og kjør mot enheten.
3. Test **Tile** på urskiven (sveip til widget) og eventuelt appen i applisten.

```bash
./gradlew :wear:installDebug
```

---

## Feilsøking

### `adb: command not found`

- Installer **Android SDK Platform-Tools** i Android Studio (SDK Tools).
- Legg `platform-tools` i `PATH` (se [Forutsetninger](#forutsetninger)).
- Kjør `source ~/.zshrc` eller åpne en ny terminal.

### `adb devices` viser ingen enhet

- Sjekk USB-kabel og **USB-feilsøking**.
- Prøv en annen USB-port; på Mac kan hub skape problemer.
- Godta «Tillat USB-feilsøking» på telefonen.

### DHU starter, men appen vises ikke

- Bekreft at **Grefsenveien** er tillatt under Android Auto utviklerinnstillinger.
- Installer appen på nytt: `./gradlew :app:installDebug`
- Kjør `adb forward tcp:5277 tcp:5277` på nytt.
- Start DHU på nytt etter at telefonen er koblet.
- Sjekk at Android Auto-appen kjører på telefonen.

### Tomt kamerabilde eller webhooks virker ikke

- Sjekk URL-ene i `local.properties`.
- Kjør **Rebuild** / `installDebug` etter endringer i properties.
- Test webhooks først fra telefon-appen (`MainActivity`).

### `desktop-head-unit: command not found`

- Installer **Android Auto Desktop Head Unit emulator** under SDK Tools.
- Bruk full sti: `"$ANDROID_HOME/extras/google/auto/desktop-head-unit"`

### DHU kobler ikke til telefonen

```bash
adb kill-server
adb start-server
adb devices
adb forward tcp:5277 tcp:5277
```

Start deretter DHU på nytt.

---

## Referanse

| Element | Verdi |
|---------|--------|
| Pakkenavn | `com.pixelspore.grefsenveien` |
| App-navn på enheten | Grefsenveien |
| Android Auto entry point | `CarAppService` |
| Hovedskjerm i bil | `MainCarScreen` |
| Telefon-aktivitet | `MainActivity` |
| Car App-kategori | Navigation |

Mer om prosjektstruktur og hemmeligheter: [README.md](README.md).
