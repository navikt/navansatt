# NAV-ansatt

Dette er en tjeneste for å hente ut metadata om NAV-ansatte.

URL-er:
- dev: https://navansatt.dev.adeo.no
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

1) Bygg med `mvn clean install`
2) Start [vtp-pensjon](https://github.com/navikt/vtp-pensjon)
3) Kjør main-metoden i `NAVAnsattApplication`.
