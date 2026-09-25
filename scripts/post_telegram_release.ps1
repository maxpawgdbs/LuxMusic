param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^v\d+\.\d+\.\d+$')]
    [string]$Tag,
    [string]$DescriptionFile,
    [switch]$DryRun,
    [switch]$SkipCurrent
)

$ErrorActionPreference = 'Stop'

if ($DryRun -and $SkipCurrent) {
    throw 'Choose only one mode: DryRun or SkipCurrent.'
}

$scriptPath = Join-Path $PSScriptRoot 'telegram_release.py'
$pythonCommand = Get-Command python -ErrorAction Stop
$arguments = @('-X', 'utf8', $scriptPath, '--tag', $Tag)
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $OutputEncoding

if ($DescriptionFile) {
    $arguments += @('--description-file', (Resolve-Path -LiteralPath $DescriptionFile).Path)
}
if ($DryRun) {
    $arguments += '--dry-run'
} elseif ($SkipCurrent) {
    $arguments += '--skip-current'
}

& $pythonCommand.Source @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Publication failed (exit code $LASTEXITCODE)."
}
