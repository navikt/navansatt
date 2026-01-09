# NAV-ansatt

Dette er en tjeneste for å hente ut metadata om NAV-ansatte.

URL-er:
- dev: https://navansatt.intern.dev.nav.no
- prod: https://navansatt.intern.nav.no

## Bruksanvisning

### Hent info om en ansatt

```
GET /navansatt/<ident>
```
(Ident er ansatt-koden; vanligvis en bokstav + seks siffer.)

Respons: (eksempel)
```
{
  "ident": "darthvad",
  "navn": "Darth Vader",
  "fornavn": "Darth",
  "etternavn": "Vader",
  "epost": "darth.vader@hollywood.com",
  "enhet": "2970"
}
```

### Hent fagområder for en ansatt
```
GET /navansatt/<ident>/fagomrader
```
(Ident er ansatt-koden; vanligvis en bokstav + seks siffer.)

Respons: (eksempel)
```
[{
  "kode": "PEN"
},{
  "kode": UFO"
},{
 "kode": "GOS"
}]
```

### Hent enheter for en ansatt
```
GET /navansatt/<ident>/enheter
```
(Ident er ansatt-koden; vanligvis en bokstav + seks siffer.)

Respons: (eksempel)
```
[{
  "id: "1234",
  "navn: "NAV Kardemomme by"
}, {
  "id": "5678",
  "navn": "NAV Hakkebakkeskogen"
}]
```

### Hent alle ansatte som jobber i en enhet/et NAV-kontor

```
GET /enhet/<enhet-id>/navansatte
```
Respons: (eksempel)
```
[{
  "ident": "darthvad",
  "navn": "Darth Vader",
  "fornavn": "Darth",
  "etternavn": "Vader",
  "epost": "darth.vader@hollywood.com",
  "enhet": "2970"
}, {
  "ident": "lukesky",
  "navn": "Luke Skywalker",
  "fornavn": "Luke",
  "etternavn": "Skywalker",
  "epost": "luke.skywalker@hollywood.com",
  "enhet": "2970"
}]
```

### Lokal utvikling
#### Bygg
Applikasjonen bygges med Java 25.
Bygg med `mvn clean install`

#### Oppsett lokalt (IntelliJ)
- Velg `Run` -> `Edit Configurations...`
- Velg `Configuration` MainKt (no.nav.navansatt.MainKt)
- Kryss av for `Enable EnvFile`
- Legg til filen som inneholder miljøvariabler: `/private/tmp/navansatt.env` og `navansatt.env`
- Trykk OK

For å kjøre applikasjonen lokalt er du nødt til å tilgjengeligjøre noen miljøvariabler som ikke er sjekket inn.
Disse kan hentes ved å kjøre skriptet `./fetch-secrets.sh`.
For å kjøre skriptet må du:
  1. Ha installert [env-fetch](https://github.com/navikt/env-fetch)
  2. Være innlogget i GCP: `nais login` eller `gcloud auth login`
  3. Sett kontekst til dev gcp: `kubectl config use-context dev-gcp`

#### AzureAD tokens for test lokalt og testmiljø
1. [On Behalf of token](https://azure-token-generator.intern.dev.nav.no/api/obo?aud=dev-gcp:pensjon-saksbehandling:navansatt)
2. [Client Credentials token](https://azure-token-generator.intern.dev.nav.no/api/m2m?aud=dev-gcp:pensjon-saksbehandling:navansatt)


