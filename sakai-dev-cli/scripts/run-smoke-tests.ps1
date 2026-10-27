$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dest = Join-Path $root 'target\smoke-classes'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'src\main\java'), (Join-Path $root 'src\test\java') -Filter '*.java' -Recurse -File | ForEach-Object { $_.FullName })
& javac --release 17 -d $dest @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& java -cp $dest org.sakaiproject.devtools.cli.CliSmoke
if ($LASTEXITCODE -ne 0) { throw 'Smoke tests failed' }
