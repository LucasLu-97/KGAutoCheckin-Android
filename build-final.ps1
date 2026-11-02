$ErrorActionPreference = 'Continue'
$be = "C:\Users\Lucas.Lu\.local\share\TeleAgent\TeleAgent的工作空间\.temp\build-env"
$env:JAVA_HOME = "$be\jdk\jdk-21.0.13+9"
$env:PATH = "$be\gradle\gradle-8.14.3\bin;$env:JAVA_HOME\bin;$env:PATH"
$project = "C:\Users\Lucas.Lu\.local\share\TeleAgent\TeleAgent的工作空间\KGAC"
$log = Join-Path $project 'build-final.log'
Set-Location $project
& "$be\gradle\gradle-8.14.3\bin\gradle.bat" assembleRelease --offline --console=plain *> $log
"EXIT_CODE=$LASTEXITCODE" | Out-File -FilePath $log -Append -Encoding utf8