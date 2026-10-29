# WM04 MySQL 8 schema install. Preflight by default. No credentials stored in script.
[CmdletBinding()]
param(
  [switch]$Apply,
  [string]$Mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
  [string]$MySqlDump = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path -LiteralPath $Mysql -PathType Leaf)) { throw "mysql.exe missing: $Mysql" }
$schema = Join-Path $PSScriptRoot 'sql\001_wm04_invitation_mysql8.sql'
if (-not (Test-Path -LiteralPath $schema -PathType Leaf)) { throw 'WM04 migration SQL is missing' }
$database = 'sakai_framework_8082'
Write-Host "Target: 127.0.0.1:3306 / $database" -ForegroundColor Cyan
Write-Host 'PRECHECK: confirm the running framework points to the same database in its sakai.properties.'
if (-not $Apply) {
  Write-Host 'PREFLIGHT ONLY. No database changes. Use -Apply after tests pass and target is verified.' -ForegroundColor Yellow
  return
}
if (-not (Test-Path -LiteralPath $MySqlDump -PathType Leaf)) { throw 'mysqldump.exe missing' }
$dir = Join-Path $env:USERPROFILE ('Documents\SakaiBackups\WM04-schema-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$backup = Join-Path $dir 'sakai_framework_8082_before_wm04.sql'
& $MySqlDump '--host=127.0.0.1' '--port=3306' '--user=sakai_framework_8082' '--password' `
    '--single-transaction' '--no-tablespaces' '--set-gtid-purged=OFF' "--result-file=$backup" $database
if ($LASTEXITCODE -ne 0 -or !(Test-Path $backup) -or (Get-Item $backup).Length -lt 1000) {
    throw 'MySQL backup failed. Schema NOT installed.'
}
Write-Host "PASS: Backup $backup" -ForegroundColor Green
Write-Host 'Applying only WM04 invitation schema (password prompt again).'
# Use process stdin redirection in cmd.exe to avoid embedding SQL or passwords in shell args.
$cmd = '"' + $Mysql + '" --host=127.0.0.1 --port=3306 --user=sakai_framework_8082 --password --database=' + $database + ' < "' + $schema + '"'
& cmd.exe /d /s /c $cmd
if ($LASTEXITCODE -ne 0) { throw 'WM04 schema migration failed. Inspect error; do not deploy.' }
Write-Host 'PASS: WM04 schema migration command returned 0. Verify table using SHOW TABLES LIKE ''SAKAI_BOOTSTRAP_INVITATION'';' -ForegroundColor Green
