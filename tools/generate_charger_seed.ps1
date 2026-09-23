param(
    [Parameter(Mandatory = $true)] [string] $CsvPath,
    [Parameter(Mandatory = $true)] [string] $DeviceSerial,
    [string] $AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string] $OutputPath = "app/src/main/assets/databases/charger_cache.db",
    [long] $GeneratedAtMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
)

$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $CsvPath)) { throw "No se encuentra el CSV RIPREE: $CsvPath" }
if (!(Test-Path -LiteralPath $AdbPath)) { throw "No se encuentra adb: $AdbPath" }

$remoteCsv = '/sdcard/Android/data/com.bgr3108.kilonom/files/kilonom-ripree.csv'
$remoteDatabase = 'databases/charger_seed_generator.db'
& $AdbPath -s $DeviceSerial push $CsvPath $remoteCsv
& $AdbPath -s $DeviceSerial shell am instrument -w -r `
    -e class com.bgr3108.kilonom.chargers.ChargerSeedGeneratorTest `
    -e generateChargerSeed true `
    -e seedGeneratedAtMillis $GeneratedAtMillis `
    com.bgr3108.kilonom.test/androidx.test.runner.AndroidJUnitRunner
if ($LASTEXITCODE -ne 0) { throw 'La generación de la seed Room falló.' }

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
cmd.exe /c "`"$AdbPath`" -s $DeviceSerial exec-out run-as com.bgr3108.kilonom cat $remoteDatabase > `"$OutputPath`""
if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $OutputPath).Length -eq 0) { throw 'No se pudo extraer la seed generada.' }
& $AdbPath -s $DeviceSerial shell run-as com.bgr3108.kilonom rm $remoteDatabase
& $AdbPath -s $DeviceSerial shell rm $remoteCsv
Write-Host "Seed Room creada: $OutputPath"
