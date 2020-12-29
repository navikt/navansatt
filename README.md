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
  "displayName": "Darth Vader",
  "firstName": "Darth",
  "lastName": "Vader",
  "email": "darth.vader@hollywood.com"
}
```

### Hent fagområder for en ansatt
```
GET /navansatt/<ident>/fagomrader
```
(Ident er ansatt-koden; vanligvis en bokstav + seks siffer.)

Respons: (eksempel)
```
["PEN", "UFO", "GOS"]
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
  "displayName": "Darth Vader",
  "firstName": "Darth",
  "lastName": "Vader",
  "email": "darth.vader@hollywood.com"
}, {
  "ident": "lukesky",
  "displayName": "Luke Skywalker",
  "firstName": "Luke",
  "lastName": "Skywalker",
  "email": "luke.skywalker@hollywood.com"
}]
```
