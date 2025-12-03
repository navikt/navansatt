# Henter secrets for navansatt og lagrer de under /private/tmp/ - her slettes alt ved restart av mac
# Sett opp 2run configuration" til å "Enable EnvFile" - og pek på rett fil
# Fungerer for mac.. (pga /private/tmp/)
currentDir=$(pwd)
cd /private/tmp/
env-fetch teampensjon navansatt azure navansatt.env
cd $currentDir
