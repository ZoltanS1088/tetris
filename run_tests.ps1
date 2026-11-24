# Tetris agent tesztelo (PowerShell): a beadashoz hasonloan ASCII-re szuri az
# Agent.java-t, leforditja, majd tobb seeddel lefuttatja es osszesiti az eredmenyt.
#
# Hasznalat (a jar mellett):
#   powershell -ExecutionPolicy Bypass -File .\run_tests.ps1 10
#   powershell -ExecutionPolicy Bypass -File .\run_tests.ps1 20 Agent.java

param(
  [int]$N = 20,
  [string]$Src = "Agent.java"
)

$TimeoutMs = 1000
$Next = 1
$PassScore = 300

$here = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$jar = Join-Path $here "game_engine.jar"
if (-not (Test-Path $jar)) { Write-Host "Nem talalom: $jar"; exit 1 }
if (-not (Test-Path $Src)) { Write-Host "Nem talalom: $Src"; exit 1 }
$srcPath = (Resolve-Path $Src).Path

$build = Join-Path ([IO.Path]::GetTempPath()) ("tetris_" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $build | Out-Null

try {
  # A jar a sajat konyvtarabol tolti be az Agent.class-t, ezert melle masoljuk.
  Copy-Item $jar (Join-Path $build "game_engine.jar")

  # 1) ASCII szures, mint az "iconv -f ascii -c": minden 127 feletti bajt kiesik.
  $bytes = [IO.File]::ReadAllBytes($srcPath)
  $ascii = New-Object System.Collections.Generic.List[byte]
  foreach ($b in $bytes) { if ($b -lt 128) { $ascii.Add($b) } }
  if ($ascii.Count -ne $bytes.Length) {
    Write-Host "FIGYELEM: nem-ASCII karakterek vannak a fajlban, ezek ki lesznek vagva:"
    $lines = [IO.File]::ReadAllLines($srcPath, [Text.Encoding]::UTF8)
    $shown = 0
    for ($i = 0; $i -lt $lines.Length -and $shown -lt 10; $i++) {
      if ($lines[$i] -match '[^\x00-\x7F]') {
        Write-Host ("{0}: {1}" -f ($i + 1), $lines[$i]); $shown++
      }
    }
    Write-Host ""
  }
  $agentSrc = Join-Path $build "Agent.java"
  [IO.File]::WriteAllBytes($agentSrc, $ascii.ToArray())

  # 2) Forditas
  $javacOut = & javac -nowarn -cp (Join-Path $build "game_engine.jar") -d $build $agentSrc 2>&1 |
    ForEach-Object { "$_" } | Where-Object { $_ -notmatch 'JAVA_TOOL_OPTIONS' }
  if ($LASTEXITCODE -ne 0) {
    $javacOut | ForEach-Object { Write-Host $_ }
    Write-Host "Forditasi hiba."
    exit 1
  }

  # 3) Futtatas N seeddel (fix, reprodukalhato seedlista, ugyanaz mint a bash scriptben)
  $pass = 0; $sum = 0.0; $min = $null; $max = $null; $minLeft = $null
  Write-Host ("{0,-12} {1,10} {2,12}" -f "seed", "pont", "maradek_ms")
  Push-Location $build
  try {
    for ($k = 1; $k -le $N; $k++) {
      $seed = ([long]$k * 2654435761 + 12345) % 2147483647
      $out = & java -Xmx2G -jar game_engine.jar 0 game.tetris.TetrisGame $seed $TimeoutMs $Next Agent 2>&1 |
        ForEach-Object { "$_" }
      $line = $out | Where-Object { $_ -match '^0 Agent ' } | Select-Object -Last 1
      if (-not $line) {
        Write-Host "seed ${seed}: nincs eredmeny sor, kimenet:"
        $out | Select-Object -Last 15 | ForEach-Object { Write-Host $_ }
        $score = 0.0; $left = 0
      } else {
        $parts = $line -split '\s+'
        $score = [double]::Parse($parts[2], [Globalization.CultureInfo]::InvariantCulture)
        $left = [math]::Floor([double]::Parse($parts[3], [Globalization.CultureInfo]::InvariantCulture) / 1000000)
      }
      Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $build "gameplay_*.data")
      Write-Host ("{0,-12} {1,10} {2,12}" -f $seed, $score, $left)

      $sum += $score
      if ($score -ge $PassScore) { $pass++ }
      if ($null -eq $min -or $score -lt $min) { $min = $score }
      if ($null -eq $max -or $score -gt $max) { $max = $score }
      if ($null -eq $minLeft -or $left -lt $minLeft) { $minLeft = $left }
    }
  } finally {
    Pop-Location
  }

  Write-Host "-----------------------------------------"
  Write-Host ("Futasok: {0}   >={1} pont: {2}/{0}   (kovetelmeny: 10-bol 8)" -f $N, $PassScore, $pass)
  Write-Host ("Atlag: {0:N0}   Min: {1}   Max: {2}   Legkevesebb maradek ido: {3} ms" -f ($sum / $N), $min, $max, $minLeft)
}
finally {
  Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $build
}
