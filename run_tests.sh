#!/usr/bin/env bash
# Tetris agent tesztelo: a beadashoz hasonloan ASCII-re konvertalja az Agent.java-t,
# leforditja, majd tobb seeddel lefuttatja es osszesiti az eredmenyeket.
#
# Hasznalat:  ./run_tests.sh [seedek_szama=20] [agent_fajl=Agent.java]
# Pelda:      ./run_tests.sh 10

set -u
N=${1:-20}
SRC=${2:-Agent.java}
JAR="$(cd "$(dirname "$0")" && pwd)/game_engine.jar"
TIMEOUT_MS=1000
NEXT=1
PASS_SCORE=300

[ -f "$JAR" ] || { echo "Nem talalom: $JAR"; exit 1; }
[ -f "$SRC" ] || { echo "Nem talalom: $SRC"; exit 1; }

BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
# a jar a sajat konyvtarabol tolti be az Agent.class-t, ezert melle masoljuk
cp "$JAR" "$BUILD/game_engine.jar"

# 1) ASCII ellenorzes, ugyanugy mint a kiertekeles (iconv -f ascii -c)
iconv -f ascii -c "$SRC" > "$BUILD/Agent.java" 2>/dev/null
if ! cmp -s "$SRC" "$BUILD/Agent.java"; then
  echo "FIGYELEM: nem-ASCII karakterek vannak a fajlban, ezek ki lesznek vagva:"
  grep -n -P '[^\x00-\x7F]' "$SRC" | head -10
  echo
fi

# 2) Forditas
if ! javac -nowarn -cp "$JAR" -d "$BUILD" "$BUILD/Agent.java" 2> "$BUILD/javac.log"; then
  grep -v JAVA_TOOL_OPTIONS "$BUILD/javac.log"
  echo "Forditasi hiba."; exit 1
fi

# 3) Futtatas N seeddel (fix, reprodukalhato seedlista)
pass=0; total=0; min=""; max=0; sum=0; minleft=""
printf "%-12s %10s %12s\n" "seed" "pont" "maradek_ms"
for ((k = 1; k <= N; k++)); do
  seed=$(( (k * 2654435761 + 12345) % 2147483647 ))
  out=$(cd "$BUILD" && java -Xmx2G -jar game_engine.jar 0 game.tetris.TetrisGame \
          "$seed" "$TIMEOUT_MS" "$NEXT" Agent 2>&1 | grep -v JAVA_TOOL_OPTIONS)
  line=$(echo "$out" | grep -E '^0 Agent ' | tail -1)
  if [ -z "$line" ]; then
    echo "seed $seed: nincs eredmeny sor, kimenet:"; echo "$out" | tail -15
    score=0; left=0
  else
    score=$(echo "$line" | awk '{print $3}')
    left=$(echo "$line" | awk '{printf "%d", $4/1000000}')
  fi
  rm -f "$BUILD"/gameplay_*.data
  printf "%-12s %10s %12s\n" "$seed" "$score" "$left"
  s=${score%.*}
  total=$((total + 1)); sum=$((sum + s))
  [ "$s" -ge "$PASS_SCORE" ] && pass=$((pass + 1))
  [ -z "$min" ] || [ "$s" -lt "$min" ] && min=$s
  [ "$s" -gt "$max" ] && max=$s
  [ -z "$minleft" ] || [ "$left" -lt "$minleft" ] && minleft=$left
done

echo "-----------------------------------------"
echo "Futasok: $total   >=${PASS_SCORE} pont: $pass/$total   (kovetelmeny: 10-bol 8)"
echo "Atlag: $((sum / total))   Min: $min   Max: $max   Legkevesebb maradek ido: ${minleft} ms"
