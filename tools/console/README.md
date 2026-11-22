# Console

`mcc` runs console commands on a **test** server through the Wings API on node1 and prints the console
lines they produced, so most in-game checks need no screenshot. It refuses the live server.

```sh
tools/console/mcc "list" "mg arena list"
MCC_WAIT=3 tools/console/mcc "tp PackShooter 120 -60 0"
tools/console/mcc --log 40
```

Default target: server 5 (DBSDW Minigames). `MCC_SERVER=<uuid>` picks another test server. The Wings
token is read on node1 and never leaves it.
