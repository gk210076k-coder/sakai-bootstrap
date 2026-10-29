# WM04 guarded WAR deploy; dry-run by default. Dedicated framework Tomcat 8082 ONLY.
[CmdletBinding()]
param(
  [switch]$Apply,
  [string]$RepoRoot = 'C:\GjergjKadriu\Documents\GitHub\sakai-bootstrap-phase1-audit',
  [string]$TomcatHome = 'C:\GjergjKadriu\Documents\GitHub\apache-tomcat-10.1.61',
  [string]$TomcatBase = 'C:\GjergjKadriu\Documents\GitHub\sakai-framework-runtime-8082',
  [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.12.1',
  [string]$MySqlDump = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Require-File([string]$path) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file is missing: $path" }
}
function FrameworkProcesses() {
  @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
    Where-Object {$null -ne $_.CommandLine -and
      $_.CommandLine.Contains($script:TomcatBase) -and
      $_.CommandLine.Contains('-Dcatalina.base=') -and
      $_.CommandLine.Contains('org.apache.catalina.startup.Bootstrap')})
}
function Listener() {
  @(Get-NetTCPConnection -State Listen -LocalPort 8082 -ErrorAction SilentlyContinue)
}
if (-not $RepoRoot.EndsWith('sakai-bootstrap-phase1-audit',[StringComparison]::OrdinalIgnoreCase)) {
  throw 'Primary Sakai checkout is protected. Select phase1-audit worktree.'
}
if (-not $TomcatBase.EndsWith('sakai-framework-runtime-8082',[StringComparison]::OrdinalIgnoreCase)) {
  throw 'Only the dedicated Tomcat 8082 base is permitted.'
}
$module = Join-Path $RepoRoot 'workspace-manager'
$war = Join-Path $module 'target\sakai-bootstrap-workspace-manager-27-SNAPSHOT.war'
$deployed = Join-Path $TomcatBase 'webapps\workspace-manager.war'
$expanded = Join-Path $TomcatBase 'webapps\workspace-manager'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$catalina = Join-Path $TomcatHome 'bin\catalina.bat'
$surefire = Join-Path $module 'target\surefire-reports'
$props = Join-Path $TomcatBase 'sakai\sakai.properties'
foreach ($p in @($war,$deployed,$jar,$catalina,$props)) { Require-File $p }
if (-not (Test-Path $surefire -PathType Container)) { throw 'Surefire reports missing. Run Maven tests first.' }
$db = @(Get-Content -LiteralPath $props | Where-Object {$_ -notmatch '^\s*[#!]' -and $_ -match 'jdbc:mysql://'})
if ($db.Count -ne 1 -or $db[0] -notmatch 'jdbc:mysql://127\.0\.0\.1(?::3306)?/sakai_framework_8082(?:\b|[?;])') {
  throw 'The framework configuration does not target sakai_framework_8082.'
}
$manifest = @(Import-Csv -LiteralPath (Join-Path $PSScriptRoot 'SHA256MANIFEST.csv'))
$sourceNewest = [DateTime]::MinValue
foreach ($row in $manifest) {
  $source = Join-Path $module $row.RelativePath.Replace('/','\')
  Require-File $source
  if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $row.SHA256) {
    throw "WM04 source changed since the patch; re-review: $source"
  }
  $sourceTime = (Get-Item -LiteralPath $source).LastWriteTimeUtc
  if ($sourceTime -gt $sourceNewest) {$sourceNewest=$sourceTime}
}
$reports = @(Get-ChildItem -LiteralPath $surefire -Filter 'TEST-*.xml' -File)
if ($reports.Count -lt 9) { throw "Expected WM01-WM04 JUnit reports (>= 9), got $($reports.Count)." }
$tests=0;$failures=0;$errors=0;$skips=0;$reportNewest=[DateTime]::MinValue
foreach ($r in $reports) {
  [xml]$xml = Get-Content -LiteralPath $r.FullName -Raw
  $suite = $xml.testsuite
  if ($null -eq $suite) { throw "Unreadable Surefire XML: $($r.FullName)" }
  $tests += [int]$suite.tests; $failures += [int]$suite.failures
  $errors += [int]$suite.errors; $skips += [int]$suite.skipped
  if ($r.LastWriteTimeUtc -gt $reportNewest) { $reportNewest = $r.LastWriteTimeUtc }
}
if ($tests -lt 54 -or $failures -ne 0 -or $errors -ne 0 -or $skips -ne 0) {
  throw "WM04 Maven test gate failed: $tests tests, $failures failures, $errors errors, $skips skipped."
}
$warItem = Get-Item -LiteralPath $war
if ($warItem.LastWriteTimeUtc -lt $reportNewest -or $warItem.LastWriteTimeUtc -lt $sourceNewest) {
  throw 'WAR is older than WM04 source or JUnit reports. Run clean test package.'
}
$entries = @(& $jar tf $war)
if ($LASTEXITCODE -ne 0) { throw 'WAR inspection failed.' }
$required = @(
  'WEB-INF/classes/org/sakaiproject/bootstrap/workspaces/WorkspaceInvitationService.class',
  'WEB-INF/classes/org/sakaiproject/bootstrap/workspaces/WorkspaceInvitationController.class',
  'WEB-INF/classes/org/sakaiproject/bootstrap/workspaces/WorkspaceInvitationStore.class',
  'WEB-INF/classes/org/sakaiproject/bootstrap/workspaces/WorkspaceInvitationToken.class',
  'WEB-INF/classes/org/sakaiproject/bootstrap/workspaces/WorkspaceMemberService.class',
  'WEB-INF/templates/workspace_invitations.html',
  'WEB-INF/templates/workspace_invitation_respond.html',
  'WEB-INF/templates/workspace_invitation_accepted.html',
  'WEB-INF/templates/workspace_members.html',
  'WEB-INF/templates/workspace_create.html',
  'WEB-INF/templates/workspace_index.html'
)
foreach ($name in $required) { if ($entries -notcontains $name) { throw "Missing WAR entry: $name" } }
$live = @(Listener)
$processes = @(FrameworkProcesses)
if ($live.Count -gt 0) {
  $owners = @($live | Select-Object -ExpandProperty OwningProcess -Unique)
  if ($owners.Count -ne 1) { throw 'Ambiguous port 8082 ownership.' }
  if (@($processes | Where-Object {$_.ProcessId -eq $owners[0]}).Count -ne 1) {
    throw 'Port 8082 is not owned by verified dedicated framework Tomcat.'
  }
} elseif ($processes.Count -gt 0) {throw 'Framework process exists without listener. Investigate before deploy.'}
$hash = (Get-FileHash -LiteralPath $war -Algorithm SHA256).Hash
Write-Host "PASS: WM04 WAR gate ($tests tests), source hashes, exact database target and dedicated Tomcat verified." -ForegroundColor Green
Write-Host "WAR SHA-256: $hash"
if (-not $Apply) {Write-Host 'PREFLIGHT ONLY. No changes made. After WM04 schema migration, re-run with -Apply.' -ForegroundColor Yellow;return}
Require-File $MySqlDump
$backupDir = Join-Path $env:USERPROFILE ('Documents\SakaiBackups\WM04-deploy-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$dump = Join-Path $backupDir 'sakai_framework_8082_before_wm04_deploy.sql'
& $MySqlDump '--host=127.0.0.1' '--port=3306' '--user=sakai_framework_8082' '--password' `
    '--single-transaction' '--no-tablespaces' '--set-gtid-purged=OFF' "--result-file=$dump" 'sakai_framework_8082'
if ($LASTEXITCODE -ne 0 -or !(Test-Path $dump) -or (Get-Item $dump).Length -lt 1000) {
  throw 'Database backup failed. No files replaced.'
}
$env:CATALINA_HOME=$TomcatHome
$env:CATALINA_BASE=$TomcatBase
$env:JAVA_HOME=$JavaHome
if ($live.Count -gt 0) {
  & $catalina stop
  if ($LASTEXITCODE -ne 0) {throw 'Dedicated Tomcat shutdown request failed.'}
  $stopped=$false
  for($i=0;$i -lt 90;$i++) {
    Start-Sleep -Seconds 2
    if (@(Listener).Count -eq 0 -and @(FrameworkProcesses).Count -eq 0) {$stopped=$true;break}
  }
  if (-not $stopped) {throw 'Dedicated Tomcat did not stop; WAR untouched.'}
}
if (@(Listener).Count -ne 0 -or @(FrameworkProcesses).Count -ne 0) {throw 'Dedicated Tomcat is not stopped.'}
Copy-Item -LiteralPath $deployed -Destination (Join-Path $backupDir 'workspace-manager.war')
if ((Get-FileHash -LiteralPath $deployed -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath (Join-Path $backupDir 'workspace-manager.war') -Algorithm SHA256).Hash) {
  throw 'Previous WAR backup hash mismatch.'
}
if (Test-Path -LiteralPath $expanded -PathType Container) {
  Copy-Item -LiteralPath $expanded -Destination (Join-Path $backupDir 'workspace-manager') -Recurse
  Remove-Item -LiteralPath $expanded -Recurse -Force
}
Copy-Item -LiteralPath $war -Destination $deployed -Force
if ((Get-FileHash -LiteralPath $deployed -Algorithm SHA256).Hash -ne $hash) {
  throw "Deployed WAR hash mismatch. Roll back from $backupDir"
}
& $catalina start
if ($LASTEXITCODE -ne 0) { throw "Tomcat startup failed. Roll back WAR from $backupDir" }
$started=$false
for ($i=0;$i -lt 90;$i++) {
  Start-Sleep -Seconds 2
  if (@(Listener).Count -gt 0 -and @(FrameworkProcesses).Count -gt 0) {$started=$true;break}
}
if (-not $started) {throw "Tomcat 8082 listener not restored. Roll back from $backupDir"}
Write-Host "PASS: WM04 WAR deployed to isolated Tomcat 8082. Backup: $backupDir" -ForegroundColor Green
Write-Host 'NEXT: check localhost:8082/workspace-manager/index, invitation create/revoke/accept/decline and Tomcat logs.'
Write-Host 'A listening port does NOT prove application integration tests passed.'
