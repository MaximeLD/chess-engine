param (
    [string]$baseline = "random-baseline",
    [string]$candidate = "naive-position-eval"
)

# Use UTF-8 end to end
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding            = [System.Text.Encoding]::UTF8

$candidateName = $candidate
if($candidate -eq $baseline)
{
    $candidateName = "$candidate-bis"
}

$cutechess="$PSScriptRoot\..\..\cutechess\cutechess-cli.exe"
$bayes = "$PSScriptRoot\..\..\bayeselo\bayeselo_static.exe"

$epochSeconds = [Math]::Round((Get-Date).ToFileTime() / 10000000 - 11644473600)

$outputFolder = "$PSScriptRoot\results\$baseline\$candidate"
$bookPath = "$PSScriptRoot\books\openings-8ply.pgn"
$outputPgn = "$outputFolder\match-$epochSeconds.pgn"

md $outputFolder -ea 0

& "$cutechess" `
  -engine name=$baseline cmd="cmd" arg="/c" arg="run-$baseline.cmd" dir="$PSScriptRoot" proto=uci `
  -engine name=$candidateName cmd="cmd" arg="/c" arg="run-$candidate.cmd" dir="$PSScriptRoot" proto=uci `
  -each st=0.1 timemargin=150 `
  -openings file="$bookPath" format=pgn order=sequential plies=8 policy=round `
  -srand 123456 `
  -repeat `
  -games 50 -concurrency 4 `
  -recover `
  -ratinginterval 10 `
  -outcomeinterval 10 `
  -pgnout "$outputPgn"

#  -resign movecount=3 score=500 `
#  -draw movenumber=50 movecount=100 score=150 `

# Paths
$stdinFile  = Join-Path $outputFolder 'bayeselo.in'
$stdoutFile = Join-Path $outputFolder "bayeselo-$epochSeconds.txt"
$stderrFile = Join-Path $outputFolder 'bayeselo.err.txt'

if (-not (Test-Path $bayes)) { Write-Error "BayesElo not found: $bayes" }
if (-not (Test-Path $outputPgn))     { Write-Error "PGN not found: $outputPgn" }
$null = New-Item -ItemType Directory -Path $outputFolder -Force

# Prepare BayesElo commands
@"
readpgn $outputPgn
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

# Parse lines like: " 1  name   +12.3 ..."  (allow + or - and decimals)
$map = @{}
$rx = '^\s*\d+\s+(\S+)\s+([+-]?\d+(?:\.\d+)?)\b'
foreach ($line in $lines) {
    if ($line -match $rx) {
        $map[$Matches[1]] = [double]$Matches[2]
    }
}

# If names have spaces, change (\S+) to (.+?) and trim; but simplest is single-token names.
if ($map.ContainsKey($Baseline) -and $map.ContainsKey($candidateName)) {
    $delta = $map[$candidateName] - $map[$Baseline]
    '{0}: Delta Elo = {1:N1} (cand - base)  [{2} vs {3}]' -f (Get-Date -Format s), $delta, $candidateName, $Baseline
    "Full BayesElo output: $stdoutFile"
} else {
    Write-Warning ("Could not find both ratings. Parsed keys: {0}" -f ($map.Keys -join ', '))
    Write-Warning ("First lines of stdout:")
    $lines | Select-Object -First 40 | ForEach-Object { Write-Warning $_ }
    if ((Test-Path $stderrFile) -and (Get-Item $stderrFile).Length -gt 0) {
        Write-Warning ("stderr:")
        Get-Content $stderrFile | Write-Warning
    }
}

# set your path
$file = "$stdoutFile"

$started = $false
$trimmed = @()

foreach ($line in Get-Content $file -Encoding UTF8) {
    if ($line -match '^ResultSet-EloRating>1e\+02') { break }                 # stop before this marker
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

Remove-Item -Path "$outputFolder\bayeselo.in"
Remove-Item -Path "$outputFolder\bayeselo.err.txt"
