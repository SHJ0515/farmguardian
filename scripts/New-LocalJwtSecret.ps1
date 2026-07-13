param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $repositoryRoot "config"
$outputPath = Join-Path $outputDirectory "application-local-secret.yml"

if ((Test-Path -LiteralPath $outputPath) -and -not $Force) {
    throw "Local secret override already exists. Use -Force only when rotating the local JWT key."
}

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$bytes = New-Object byte[] 64
$randomNumberGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $randomNumberGenerator.GetBytes($bytes)
} finally {
    $randomNumberGenerator.Dispose()
}
$jwtSecret = [Convert]::ToBase64String($bytes)

$content = @"
# Local-only secret override. Do not commit or share this file.
jwt:
  secret: "$jwtSecret"
"@

$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outputPath, $content, $utf8WithoutBom)

Write-Output "Created local JWT secret override at config/application-local-secret.yml (value hidden)."
