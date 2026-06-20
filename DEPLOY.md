# Automatisk deploy til Google Play Internal Testing

Hver push til `master` bygger en signert release (`.aab`) og publiserer den direkte til **Internal testing**-sporet i Google Play.

Workflow-fil: [`.github/workflows/play-internal-release.yml`](.github/workflows/play-internal-release.yml)

## Slik fungerer det

1. GitHub Actions sjekker ut koden
2. Gradle bygger `:app:bundleRelease` (inkl. Wear OS-modulen)
3. `versionCode` settes til `GITHUB_RUN_NUMBER + 16`
4. `versionName` settes til `2.13.<run-nummer>` (f.eks. `2.13.42`)
5. AAB lastes opp til **internal**-sporet med status **completed**

## Forutsetninger i Google Play Console

Appen må allerede være opprettet i Play Console med et aktivt **Internal testing**-spor (som du har i dag).

Service account må ha tilgang til å laste opp releases. Minimum:

- **Release to testing tracks** (Internal testing / Closed testing)

## 1. Opprett service account

Google har fjernet den gamle menyen **Setup → API access**. Oppsettet gjøres nå i **Google Cloud Console** + **Brukere og tillatelser** i Play Console.

### Steg A: Google Cloud Console

1. Gå til [Google Cloud Console](https://console.cloud.google.com/)
2. Opprett et nytt prosjekt (f.eks. `grefsenveien-play`) eller velg et eksisterende
3. Aktiver **Google Play Android Developer API**:
   - [Direktelenke til API-et](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com)
   - Klikk **Aktiver** / **Enable**
4. Opprett service account:
   - Gå til **IAM og administrasjon** → **Servicekontoer**
   - **Opprett servicekonto** → gi den et navn (f.eks. `github-play-upload`)
   - Hopp over roller (ikke nødvendig her) → **Ferdig**
5. Last ned JSON-nøkkel:
   - Klikk på servicekontoen → fanen **Nøkler** → **Legg til nøkkel** → **Opprett ny nøkkel** → **JSON**
   - Lagre JSON-filen — denne blir `PLAY_SERVICE_ACCOUNT_JSON` i GitHub
6. Kopier **e-postadressen** til servicekontoen (slutter på `@...gserviceaccount.com`)

### Steg B: Google Play Console

1. Gå til [Google Play Console](https://play.google.com/console) → **Startsiden** (konto-nivå, ikke inne i appen)
2. I venstremenyen: **Brukere og tillatelser**
3. Klikk **Inviter nye brukere** (eller tre prikker ved siden av «Administrer brukere»)
4. Lim inn servicekonto-e-posten fra steg A
5. Under **Apptilganger** → **Legg til app** → velg **Grefsenveien**
6. Gi disse tillatelsene (norsk/engelsk navn kan variere):
   - **Se appinformasjon** (View app information)
   - **Opprette, redigere og rulle ut apputgivelser til testspor** (Release to testing tracks)
7. Klikk **Inviter bruker** / **Send invitasjon**

Brukeren skal vise status **Aktiv** (grønn) etter noen sekunder.

> **Tips:** Noen konti har fortsatt **Utviklerkonto** i venstremenyen med API-relaterte innstillinger, men du trenger normalt ikke dette hvis servicekontoen er invitert under **Brukere og tillatelser**.

## 2. Legg inn GitHub Secrets

Gå til GitHub-repoet → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.

| Secret | Beskrivelse |
|--------|-------------|
| `PLAY_SERVICE_ACCOUNT_JSON` | Hele innholdet i JSON-filen fra service account |
| `KEYSTORE_BASE64` | Keystore kodet som base64 (se under) |
| `KEYSTORE_PASSWORD` | `storePassword` fra `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` fra `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` fra `keystore.properties` |
| `GARAGE_WEBHOOK_URL` | Samme som i `local.properties` |
| `GATE_WEBHOOK_URL` | Samme som i `local.properties` |
| `S3_IMAGE_URL` | Samme som i `local.properties` |
| `S3_MAILBOX_IMAGE_URL` | Samme som i `local.properties` |
| `WEATHER_CAMERA_URL` | Samme som i `local.properties` |
| `DOORBELL_TAKE_IMAGE_URL` | Samme som i `local.properties` |
| `HA_BASE_URL` | Samme som i `local.properties` |
| `HA_TOKEN` | Samme som i `local.properties` |

### Kod keystore som base64

Kjør lokalt (erstatt stien om nødvendig):

```bash
base64 -i app/pixelspore.keystore | pbcopy
```

Lim inn resultatet som verdien for `KEYSTORE_BASE64`.

## 3. Push til master

Når secrets er på plass:

```bash
git push origin master
```

Følg bygget under **Actions** i GitHub. Ved suksess finner du den nye versjonen i Play Console under **Internal testing**.

## Feilsøking

| Problem | Løsning |
|---------|---------|
| `Only releases with status draft may be created on draft app` | Fullfør alle obligatoriske skjemaer i Play Console, eller sett midlertidig `status: draft` i workflow-filen |
| `Version code X has already been used` | Øk grunnverdien `16` i workflow-filen, eller bump `versionCode` i `app/build.gradle` |
| Signering feiler | Sjekk at `KEYSTORE_BASE64` og passordene stemmer |
| Upload feiler med 403 | Service account mangler tilgang til appen eller testing track |

## Lokal bygging (uendret)

Lokal utvikling bruker fortsatt `local.properties` og `keystore.properties` som før. Se [README.md](README.md).
