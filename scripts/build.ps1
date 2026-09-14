param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) {
        $studioJbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
        if (Test-Path (Join-Path $studioJbr 'bin\java.exe')) { $env:JAVA_HOME = $studioJbr }
        else { throw 'Nastav JAVA_HOME na JDK 17 alebo novší.' }
    }
    if (-not $env:ANDROID_HOME -and -not (Test-Path 'local.properties')) {
        $localSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path $localSdk) { $env:ANDROID_HOME = $localSdk }
        else { throw 'Nastav ANDROID_HOME alebo otvor projekt v Android Studio.' }
    }
    $tasks = @(':app:assembleDebug')
    if (-not $SkipChecks) { $tasks += @(':app:testDebugUnitTest', ':app:lintDebug') }
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Zostavenie alebo kontrola zlyhala.' }
    New-Item -ItemType Directory -Force -Path 'artifacts' | Out-Null
    $metadata = Get-Content -Raw 'app\build\outputs\apk\debug\output-metadata.json' | ConvertFrom-Json
    $artifact = Join-Path 'artifacts' ("Den-" + $metadata.elements[0].versionName + "-debug.apk")
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination $artifact
    (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash | Set-Content ($artifact + '.sha256')
    Get-FileHash -LiteralPath $artifact -Algorithm SHA256
} finally { Pop-Location }
