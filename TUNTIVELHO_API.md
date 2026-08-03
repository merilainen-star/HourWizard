# Tuntivelho API — Source of Truth

**Versio:** 2.0 (korjattu — johdettu suoraan `tuntiwelho_api.py`-lähdekoodista)
**Päivitetty:** 2026-07-30
**Status:** Tuotanto-dokumentaatio. Kaikki tässä dokumentissa oleva GraphQL-sisältö on
kopioitu **sanasta sanaan** toimivasta Python-skriptistä (`tuntiwelho_api.py`), joka on
todistetusti onnistuneesti leimannut sisään/ulos tuotannossa. Mitään ei ole arvattu.

> ⚠️ **Versio 1.0 tästä dokumentista oli virheellinen.** Se sisälsi keksityn
> `stamp(type: "in")`-mutaation ja kuvitteellisia REST-fallback-osoitteita, joita ei
> koskaan ole ollut olemassa missään toimivassa koodissa. Ne aiheuttivat Android-appiin
> 404/406/503-virheitä. Tämä versio korjaa sen — katso lopusta "Muutosloki".

---

## 📌 Yleiskatsaus

| Parametri | Arvo |
|-----------|------|
| **Päädomaini** | `https://app.tuntivelho.com` |
| **API tyyppi** | **VAIN GraphQL** — ei REST-fallbackia toimivassa toteutuksessa |
| **Pyynnön runko** | JSON-**array**, ei plain objekti: `[{"query": ..., "variables": ...}]` |
| **Vastauksen runko** | Myös array: `[{...}]` — käytä indeksiä `[0]` |
| **Autentikointi** | Token saadaan login-mutaatiosta, lähetetään `Authorization: Bearer <token>` |
| **Vanhentunut** | `https://www.tuntivelho.fi` (ei vastaa, älä käytä) |

---

## 🔗 Endpointit (tarkka lista, kokeillaan järjestyksessä)

```python
DEFAULT_API_URLS = [
    "https://app.tuntivelho.com/mobiili/backend/public/graphql",
    "https://app.tuntivelho.com/tvv-mobile/backend/public/graphql",
]
```

- Backend siirtyi `/tvv-mobile/...` → `/mobiili/...` maaliskuussa 2026.
- **`/mobiili/...` kokeillaan ensin**, `/tvv-mobile/...` on fallback.
- Ympäristömuuttuja `TW_API_URL` voi asettaa lisä-endpointin, joka kokeillaan **ensimmäisenä**
  (lisätään listan alkuun, duplikaatit poistetaan).
- **Ei mitään muuta osoitetta kokeilla.** Ei `/api/graphql`, ei `/login`, ei `/leimaus`,
  ei `www.tuntivelho.fi/mitään`. Näitä ei ole koskaan ollut Python-koodissa — ne olivat
  virheellisen v1.0-dokumentin keksintöä.

### Fallback-logiikka (tarkka, `graphql_request()`-funktiosta)

Kokeillaan URL:eja listan järjestyksessä. Seuraavaan URL:iin siirrytään **vain** näissä
tapauksissa:

| Virhe | Siirrytäänkö seuraavaan URL:iin? |
|-------|-----------------------------------|
| `HTTPError` koodi `404` | ✅ Kyllä, jos listassa on vielä URL:eja jäljellä |
| `HTTPError` muu koodi (esim. 401, 500, 503) | ❌ Ei — heitetään heti virhe `http_error_<koodi>` |
| `URLError` / `ConnectionError` / `TimeoutError` / `OSError` | ✅ Kyllä, jos URL:eja jäljellä |
| `JSONDecodeError` / `IndexError` (vastaus ei ole odotettu JSON-array) | ✅ Kyllä, jos URL:eja jäljellä |

**Tärkeä seuraus:** jos saat **406** tai **503** ensimmäiseltä endpointilta, koodi ei
automaattisesti kokeile toista — se on heti fataali virhe. Se tarkoittaa, että itse pyyntö
(headers/body/mutaatio) oli väärä sille endpointille, ei että endpoint olisi väärä.

---

## 📡 HTTP Pyynnön Rakenne (kriittinen — usein unohdettu kohta)

### Headers (tarkalleen nämä, ei muita)

```
Content-Type: application/json
Accept: application/json
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
Origin: https://app.tuntivelho.com
Authorization: Bearer <token>      ← VAIN jos token on olemassa
```

### Body — JSON-ARRAY, ei objekti!

```python
payload = [{"query": query, "variables": variables}]
data = json.dumps(payload).encode('utf-8')
```

Eli lähetettävä body on:

```json
[
  {
    "query": "mutation login(...) { ... }",
    "variables": { "kayttajatunnus": "...", "salasana": "..." }
  }
]
```

**Ei tätä (yleinen virhe):**
```json
{ "query": "...", "variables": {...} }
```

### Vastaus — myös array

```python
res_array = json.loads(response.read().decode('utf-8'))
return res_array[0]   # <-- vain ensimmäinen alkio otetaan
```

Palvelin palauttaa `[{"data": {...}, "errors": [...]}]` — pura array ja käytä indeksiä `0`.

---

## 🔄 Koko työnkulku (tarkka järjestys — mitään vaihetta ei voi ohittaa)

```
1. login(username, password)
     → palauttaa token + henkiloid
2. get_defaults(token)   [KELLOKORTTI_QUERY]
     → palauttaa talaatuid, tyopisteid (PAKOLLISET leimaukseen!)
     → palauttaa myös previousstamp (fallback-tietona)
3. do_punch(token, action, talaatuid, tyopisteid, prev_stamp)   [PUNCH_MUTATION / leimaTallenna]
     → tekee itse leimauksen
4. fetch_balance(token)   [BALANCE_QUERY]
     → haetaan saldo (Tase) punch-tuloksen jälkeen, non-fatal jos epäonnistuu
```

**Vaihe 2 on pakollinen ennen leimausta.** `talaatuid` ja `tyopisteid` eivät ole
vapaasti valittavia vakioita — ne pitää hakea käyttäjän omista `selectiondefaults`-tiedoista
joka kerta ennen leimausta.

---

## 1️⃣ LOGIN_MUTATION

```graphql
mutation login($kayttajatunnus: String!, $salasana: String!, $subuser: Boolean, $kieliid: Int) {
  login(kayttajatunnus: $kayttajatunnus, salasana: $salasana, subuser: $subuser, kieliid: $kieliid) {
    henkiloid
    success
    nimi
    token
    errors { message }
  }
}
```

**Variables (tarkalleen nämä avainten nimet — suomeksi, ei englanniksi!):**

```json
{
  "kayttajatunnus": "user@example.com",
  "salasana": "salasana123",
  "subuser": false,
  "kieliid": 1
}
```

> ⚠️ Kentät ovat `kayttajatunnus`/`salasana`, **ei** `username`/`password`. Tämä oli yksi
> v1.0-dokumentin virheistä.

**Onnistunut vastaus:**
```json
{
  "data": {
    "login": {
      "henkiloid": 12345,
      "success": true,
      "nimi": "Matti Meikäläinen",
      "token": "eyJhbGc...",
      "errors": null
    }
  }
}
```

**Python-logiikka:**
```python
data = res.get('data', {}).get('login', {})
if data.get('success'):
    return data.get('token'), data.get('henkiloid')
else:
    # data.get('errors') sisältää virheen
    emit_status("ERROR", "LOGIN", "login_failed")
```

---

## 2️⃣ KELLOKORTTI_QUERY (defaults — PAKOLLINEN ennen leimausta)

```graphql
query kellokortti($subuser: Int) {
  kellokortti(subuser: $subuser) {
    previousstamp {
      tv_leimaid
      aika
      suuntaid
    }
    selectiondefaults {
      talaatuid
      tyopisteid
    }
    talaadut {
      talaatuid
    }
  }
}
```

**Variables:**
```json
{ "subuser": null }
```

**Käyttö:** haetaan `selectiondefaults.talaatuid` ja `selectiondefaults.tyopisteid` —
näitä käytetään seuraavassa leimaus-mutaatiossa. `previousstamp` talletetaan
fallback-arvoksi (katso alempaa "Timestampin dekoodaus").

Kutsutaan tokenilla (`Authorization: Bearer <token>`).

---

## 3️⃣ PUNCH_MUTATION — `leimaTallenna` (EI "stamp"!)

Tämä on todellinen leimausmutaatio. Nimi on **`leimaTallenna`**, ei `stamp`, `leimaa`,
`leimaus`, `leima` tai `clockIn` — kaikki nuo olivat vääriä arvauksia joita on kokeiltu
Android-puolella ja jotka tuottavat 503:n koska mutaatiota ei ole olemassa.

```graphql
mutation leimaTallenna($input: LeimaInput!, $subuser: Int, $withStamps: Boolean = true) {
  leimaTallenna(input: $input, subuser: $subuser) {
    previousstamp {
      tv_leimaid
      aika
      suuntaid
      talaatuid
      tyopisteid
      tyolajiid
    }
    previousstamps @include(if: $withStamps) {
      henkiloid
      tv_leimaid
      aika
      suuntaid
    }
    selectiondefaults {
      talaatuid
      tyopisteid
      tyolajiid
    }
    errors {
      message
    }
  }
}
```

### `LeimaInput`-objekti (reverse-engineered JS-bundlesta — kaikki kentät pakollisia rakenteessa, arvo voi olla null)

```json
{
  "tietoja": "",
  "talaatuid": 1,
  "tyopisteid": 5,
  "tyolajiid": null,
  "polaatuid": null,
  "leimausaika": 1722357000,
  "tapahtuma": "sisaan"
}
```

| Kenttä | Tyyppi | Selitys |
|--------|--------|---------|
| `tietoja` | string | Vapaa teksti, tyhjä oletuksena |
| `talaatuid` | int | Työn laatu — **haettava kellokortti-kyselystä**, ei kovakoodata |
| `tyopisteid` | int | Työpiste — **haettava kellokortti-kyselystä**, ei kovakoodata |
| `tyolajiid` | int/null | Työn laji, yleensä `null` |
| `polaatuid` | int/null | Yleensä `null` |
| `leimausaika` | int (unix epoch, sekunteina) | `int(datetime.now().timestamp())` — nykyhetki |
| `tapahtuma` | string | `"sisaan"` \| `"ulos"` \| `"tauolle"` \| `"tauolta"` |

**Koko variables-objekti mutaatiolle:**
```json
{
  "input": { "tietoja": "", "talaatuid": 1, "tyopisteid": 5, "tyolajiid": null, "polaatuid": null, "leimausaika": 1722357000, "tapahtuma": "sisaan" },
  "subuser": null,
  "withStamps": true
}
```

### Virheiden käsittely (kaksi eri virhelähdettä!)

```python
errors = res.get("errors", [])                                            # top-level GraphQL errors
leima_errors = res.get("data", {}).get("leimaTallenna", {}).get("errors", [])  # mutaatiokohtaiset virheet
```

`leima_errors` on ensisijainen (esim. `"Suunta ei ole sallittu. Olet ehkä leimannut jo ulos."`)
— se on käyttäjälle näytettävä syy epäonnistumiseen. `errors` (top-level) liitetään perään jos
sekin on olemassa. Molemmat kootaan yhdeksi viestiksi `_extract_api_error_message()`-funktiolla.

---

## 4️⃣ BALANCE_QUERY — Saldo (kutsutaan leimauksen JÄLKEEN)

```graphql
query kellokortti($subuser: Int) {
  kellokortti(subuser: $subuser) {
    tase {
      tase
    }
  }
}
```

**Variables:** `{ "subuser": null }`

**Vastaus:** `tase` on **sekunteina**, voi olla negatiivinen.

```python
sign = "-" if tase_seconds < 0 else ""
hours = abs(int(tase_seconds)) // 3600
minutes = (abs(int(tase_seconds)) % 3600) // 60
balance_str = f"{sign}{hours}:{minutes:02d}"   # esim. "27:59" tai "-2:30"
```

Tämä kutsu on **non-fatal** — jos se epäonnistuu, jatketaan tyhjällä saldolla eikä
koko leimaus epäonnistu sen takia.

---

## ⏱️ Timestampin dekoodaus (kriittinen, helposti mokattava kohta)

API tallentaa `aika`-kentän **paikallisena kellonaikana koodattuna UTC-epochiksi**
(`aika = paikallinen_kellonaika_as_utc_epoch`). Suomessa (EET, UTC+2) tämä tarkoittaa,
että arvot ovat ~7200s edellä oikeasta UTC-ajasta.

**Väärin (lisää timezone-siirtymän toiseen kertaan):**
```python
datetime.fromtimestamp(aika)   # ❌ näyttää ajan ~2h tulevaisuudessa
```

**Oikein:**
```python
datetime.utcfromtimestamp(aika)   # ✅ palauttaa oikean paikallisen kellonajan
```

Kun kahta `aika`-arvoa vertaillaan keskenään (esim. löytääkseen oikean sisäänleimauksen
ulosleimauksen yhteydessä), vertaa **raakoja kokonaislukuja suoraan** — älä muunna
kumpaakaan ensin — koska molemmat jakavat saman enkoodauksen ja niiden erotus on oikea
kulunut aika sekunteina.

---

## 📐 Päivän saldon laskenta (ulosleimauksessa)

```python
TARGET_WORKDAY_MINUTES = 7 * 60 + 30   # 7h 30min
LUNCH_BREAK_MINUTES = 30

elapsed = (punch_out_dt - punch_in_dt).total_seconds() / 60.0
worked = elapsed - LUNCH_BREAK_MINUTES
delta_minutes = round(worked - TARGET_WORKDAY_MINUTES)
```

Sisäänleimaus haetaan `previousstamps`-listasta (mutaation vastauksesta). Jos lista on
tyhjä (API ei aina populoi sitä), käytetään fallbackina `kellokortti`-kyselystä (vaihe 2)
saatua `previousstamp`-arvoa, **mutta vain jos sen `suuntaid == 0`** (eli se oli
sisäänleimaus eikä ulos).

Kokonaissaldo-arvio = `fetch_balance()`:n palauttama edellinen saldo + `delta_minutes`.

---

## 🎯 Toimintalogiikka — Android-sovellukselle

### Sisäänkirjautuminen
```
1. login(kayttajatunnus, salasana) → token, henkiloid
2. Tallenna token turvallisesti (Keystore)
3. success == false → näytä data.login.errors[].message
```

### Sisäänleimaus
```
1. Varmista token voimassa, muuten re-login
2. kellokortti-kysely → talaatuid, tyopisteid, previousstamp
3. leimaTallenna(input: {tapahtuma: "sisaan", talaatuid, tyopisteid, leimausaika: now, ...})
4. Jos leima_errors tai errors → näytä viesti, älä merkitse clocked-in
5. Jos onnistui → tallenna clockInTimestamp, hae saldo (valinnainen)
```

### Ulosleimaus
```
1. Varmista token voimassa
2. kellokortti-kysely → talaatuid, tyopisteid (uudelleen — voi vaihtua)
3. leimaTallenna(input: {tapahtuma: "ulos", ...})
4. Laske päivän delta previousstamps-datasta (ks. yllä)
5. fetch_balance() kokonaissaldoon
6. Näytä "Saldo: +0:24" tms.
```

---

## ⚠️ HTTP-virheiden tulkinta

| Koodi | Todennäköisin syy tässä API:ssa |
|-------|-----------------------------------|
| `404` | Väärä endpoint-URL (ei ole listalla ollenkaan) |
| `401` | Token puuttuu/vanhentunut — **ei fallbackia**, korjaa auth |
| `406` | Väärät headerit (`Accept`/`Content-Type`) tai payload ei ole array |
| `503` | Mutaation/kentän nimi väärä (esim. keksitty `stamp`), tai palvelin oikeasti alhaalla |
| `400` | Variables-objektin muoto väärä (esim. `username` eikä `kayttajatunnus`) |

**Muista:** 401/406/503 eivät Pythonin logiikassa laukaise fallback-URL:iin siirtymistä —
ne ovat merkki siitä, että *pyyntö itse* on väärin muotoiltu, ei että endpoint olisi väärä.

---

## 🧾 Tasker-yhteensopiva ulostulo (vain Python-versiossa, ei relevantti Androidille)

```
TV_STATUS=OK|ERROR|DRYRUN
TV_RESULT=STATUS|ACTION|EPOCH|extra_key=value|extra_key2=value2
```

Tämä on Python-skriptin oma stdout-formaatti Taskeria varten. Android-sovellus ei
tarvitse tätä — se käsittelee GraphQL-vastauksen suoraan sovelluslogiikassa.

---

## ❌ Mitä EI ole olemassa (poistettu virheellisestä v1.0:sta)

Nämä olivat v1.0-dokumentin keksintöjä eivätkä perustu mihinkään toimivaan koodiin.
**Älä käytä näitä:**

- ~~`mutation { stamp(type: "in") }`~~ — oikea nimi on `leimaTallenna`
- ~~`https://app.tuntivelho.com/leimaus`~~ (REST) — ei käytössä missään toimivassa koodissa
- ~~`https://app.tuntivelho.com/api/graphql`~~ — väärä polku, ei ole listalla
- ~~`https://www.tuntivelho.fi/login`~~ — domain ei vastaa
- ~~`https://app.tuntivelho.com/tvv/login.php`~~ — tämä **on** oikeasti olemassa
  (AI-agentti löysi sen selaimen kautta, CSRF-suojattu HTML-lomake), mutta **mikään
  toimiva koodi ei käytä sitä** — se on selaimelle tarkoitettu lomake, ei API-client-flow.
  Älä rakenna sen varaan ilman erillistä CSRF-token-hakua ja HTML-parsingia.

---

## 📱 Android Implementation Checklist (korjattu)

- [ ] Käytä VAIN `DEFAULT_API_URLS`-listaa (2 GraphQL-osoitetta), poista kaikki REST-fallbackit
- [ ] Body = JSON-**array** `[{query, variables}]`, ei plain objekti
- [ ] Vastaus = JSON-array, parsitaan `[0]`
- [ ] Headers: `Content-Type`, `Accept` = `application/json` (ei `text/plain, */*`), `Origin: https://app.tuntivelho.com`, `User-Agent`
- [ ] Login: käytä `kayttajatunnus`/`salasana`-avaimia, ei `username`/`password`
- [ ] Login jälkeen: kutsu KELLOKORTTI_QUERY hakeaksesi `talaatuid`/`tyopisteid` — älä kovakoodaa
- [ ] Leimaus: käytä mutaatiota `leimaTallenna` + `LeimaInput`-objektia, ei `stamp`
- [ ] `tapahtuma`-arvo on `"sisaan"`/`"ulos"`, ei `"in"`/`"out"`
- [ ] Käsittele sekä `errors` (top-level) että `data.leimaTallenna.errors` (mutaatiokohtainen)
- [ ] Timestampit: dekoodaa `utcfromtimestamp()`-logiikalla, ei `fromtimestamp()`
- [ ] Fallback-URL:iin siirtyminen VAIN 404:llä tai yhteysvirheellä, ei muilla koodeilla

---

## 📚 Lähde

Tämä dokumentti on suoraan johdettu tiedostosta [`tuntiwelho_api.py`](tuntiwelho_api.py),
joka on ainoa todistetusti toimiva referenssi. Kun Python-koodia päivitetään, päivitä
tämä tiedosto samalla — älä koskaan lisää tähän mitään mitä ei ole ensin varmistettu
toimivasta koodista.

## 📝 Muutosloki

- **2.0** (2026-07-30): Täydellinen uudelleenkirjoitus suoraan `tuntiwelho_api.py`:stä.
  Korjattu: mutaation nimi (`leimaTallenna`, ei `stamp`), login-kentät (`kayttajatunnus`/`salasana`),
  array-wrapped payload, `Origin`-header, pakollinen kellokortti-defaults-vaihe, poistettu
  keksityt REST-fallbackit, lisätty tarkka fallback/retry-logiikka HTTP-koodeittain.
- **1.0** (2026-07-30): Alkuperäinen versio — sisälsi useita arvattuja/virheellisiä
  tietoja jotka eivät perustuneet Python-lähdekoodiin. **Ei enää käytössä.**
