# all-engines-tournament.ps1
# Round-robin across all run-*.cmd engines, PGNs per pair, BayesElo summary for all.

[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding           = [Text.Encoding]::UTF8
$ErrorActionPreference    = 'Stop'

# --- paths (adjust if yours differ) ---
$cutechess = Join-Path $PSScriptRoot '..\..\cutechess\cutechess-cli.exe'
$bayes     = Join-Path $PSScriptRoot '..\..\bayeselo\bayeselo_static.exe'

# --- match settings (keep simple/stable for comparisons) ---
$tc            = '40/2'     # 40 moves / 2s; change if you like
$gamesPerPair  = 20        # total games per pairing (cutechess will split colors with -repeat)
$concurrency   = 1          # parallel games (engines should run Threads=1)

# --- discover engines ---
$engineScripts = Get-ChildItem -Path $PSScriptRoot -Filter 'run-*.cmd' | Where-Object { -not $_.PSIsContainer }
$engines = @()
foreach ($f in $engineScripts) {
    if ($f.BaseName -match '^run-(.+)$') { $engines += $Matches[1] }
}
$engines = $engines | Sort-Object -Unique
if ($engines.Count -lt 2) { Write-Error "Need at least two run-*.cmd files. Found: $($engines -join ', ')" }

# --- output roots ---
$tRoot     = Join-Path $PSScriptRoot 'results\all_engine_tournament'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$tFolder   = Join-Path $tRoot $timestamp
$null = New-Item -ItemType Directory -Path $tFolder -Force

Write-Host "Engines: $($engines -join ', ')"
Write-Host "Output:  $tFolder"

# --- run all pairings ---
$pairPgns = @()

for ($i = 0; $i -lt $engines.Count; $i++) {
    for ($j = $i + 1; $j -lt $engines.Count; $j++) {
        $A = $engines[$i]; $B = $engines[$j]
        $pairKey    = "$A--vs--$B"
        $pairFolder = Join-Path $tFolder $pairKey
        $pgnPath    = Join-Path $pairFolder 'match.pgn'

        $null = New-Item -ItemType Directory -Path $pairFolder -Force
        $pairPgns += $pgnPath

        Write-Host "Match: $A vs $B -> $pgnPath"

        & "$cutechess" `
      -engine name=$A cmd="cmd" arg="/c" arg="run-$A.cmd" dir="$PSScriptRoot" proto=uci `
      -engine name=$B cmd="cmd" arg="/c" arg="run-$B.cmd" dir="$PSScriptRoot" proto=uci `
      -each tc=$tc `
      -repeat `
      -games $gamesPerPair -concurrency $concurrency `
      -recover `
      -pgnout "$pgnPath"
        # add -debug above if you need to diagnose a pairing
    }
}

# --- collect per-pair PGNs and build a merged PGN with only finished games ---
$pairPgns = Get-ChildItem -Path $tFolder -Recurse -Filter 'match.pgn' | Select-Object -Expand FullName
if ($pairPgns.Count -eq 0) { Write-Error "No match.pgn files found under $tFolder"; return }

$mergedPgn = Join-Path $tFolder 'merged.pgn'
Remove-Item $mergedPgn -ErrorAction SilentlyContinue

$gamesKept = $pairPgns.Count
#
#foreach ($p in $pairPgns) {
#    $txt = Get-Content $p -Raw -ErrorAction SilentlyContinue
#    if ([string]::IsNullOrWhiteSpace($txt)) { Write-Warning "Empty PGN: $p"; continue }
#    Add-Content -Path $mergedPgn -Encoding Ascii -Value ($txt.Trim() + "`r`n`r`n")
#
##    # NON-capturing group → no separator fragments, no $nulls
##    $chunks = [regex]::Split($txt, '(?:\r?\n){2,}')
##
##    foreach ($g in $chunks) {
##        if ([string]::IsNullOrWhiteSpace($g)) { continue }  # skip empties just in case
##
##        # keep only finished games
##        if ($g -match '^\[White\s+".+"\].*^\[Black\s+".+"\].*^\[Result\s+"(1-0|0-1|1/2-1/2)"\]'s) {
##            $gamesKept++
##        }
##    }
#}

#Write-Host "Merged $gamesKept finished games into $mergedPgn"
#if ($gamesKept -eq 0) { throw "Merged PGN is empty; nothing for BayesElo to rate." }
foreach ($p in $pairPgns) {
    $txt = Get-Content $p -Raw -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($txt)) { Write-Warning "Empty PGN: $p"; continue }
    ($txt.Trim() + "`r`n`r`n") | Add-Content -Path $mergedPgn -Encoding ASCII

    # naive but effective splitter: blank lines separate games in cutechess PGN
#    $chunks = $txt -split '(\r?\n){2,}'
#    foreach ($g in $chunks) {
#        if ($g -match '^\[White\s+".+"\].*^\[Black\s+".+"\].*^\[Result\s+"(1-0|0-1|1/2-1/2)"\]') {
#            # normalize line endings; add a blank line after each game
#            ($g.Trim() + "`r`n`r`n") | Add-Content -Path $mergedPgn -Encoding ASCII
#            $gamesKept++
#        }
#    }
}

if ($gamesKept -eq 0) {
    Write-Error "Merged PGN contains 0 finished games. Open your per-pair PGNs and check the [Result] tags."
    return
}

Write-Host "Merged $gamesKept finished games into $mergedPgn"

# Paths
$pgn        = $mergedPgn
$stdinFile  = Join-Path $tFolder 'bayeselo.in'
$stdoutFile = Join-Path $tFolder 'bayeselo.txt'
$stderrFile = Join-Path $tFolder 'bayeselo.err.txt'

if (-not (Test-Path $bayes)) { Write-Error "BayesElo not found: $bayes" }
if (-not (Test-Path $pgn))     { Write-Error "PGN not found: $pgn" }
$null = New-Item -ItemType Directory -Path $tFolder -Force

# Prepare BayesElo commands
@"
readpgn $pgn
elo
mm
ratings
drawelo
x
"@ | Set-Content -Encoding Ascii -NoNewline -Path $stdinFile

# Run BayesElo (PowerShell-friendly redirection)
Start-Process -FilePath $bayes `
  -RedirectStandardInput  $stdinFile `
  -RedirectStandardOutput $stdoutFile `
  -RedirectStandardError  $stderrFile `
  -NoNewWindow -Wait | Out-Null

# Load output
$lines = Get-Content -Path $stdoutFile -Encoding ASCII

# set your path
$file = "$tFolder\bayeselo.txt"

$started = $false
$trimmed = @()

foreach ($line in Get-Content $file -Encoding UTF8) {
    if ($started -and $line -match '^ResultSet-EloRating>(.*)') { break }                 # stop before this marker
    if (-not $started) {
        if ($line -match '^ResultSet-EloRating>(.*)') {                       # keep header, minus prefix
            $trimmed += $Matches[1]
            $started = $true
        }
        continue
    }
    $trimmed += $line                                                         # keep everything after
}

if ($trimmed.Count) { $trimmed | Set-Content -Path $file -Encoding UTF8 }
