param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][ValidateSet('Deň','sk.marek.den.test')][string]$ExpectedLabel
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^[a-zA-Z0-9:._-]+$') { throw 'Invalid device serial.' }
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$deviceState = & $adbPath -s $Serial get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') { throw "Device $Serial is not connected and authorized." }
$projectRoot = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'artifacts') | Out-Null
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$outLog = Join-Path $projectRoot 'artifacts\phone-install-out.log'
$errLog = Join-Path $projectRoot 'artifacts\phone-install-error.log'
# The caller must have authorization to install this specific app. Confirm only its matching MIUI installer prompt.
$install = Start-Process -FilePath $adbPath -ArgumentList @('-s',$Serial,'install','-r','-t',('"'+$apkPath+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
$deadline = (Get-Date).AddSeconds(45)
while (-not $install.HasExited -and (Get-Date) -lt $deadline) {
    $focus = & $adbPath -s $Serial shell dumpsys window | Select-String 'mCurrentFocus'
    if ($focus -match 'com.miui.permcenter.install.AdbInstallActivity') {
        & $adbPath -s $Serial shell uiautomator dump /data/local/tmp/den-installer.xml | Out-Null
        [xml]$tree = & $adbPath -s $Serial shell cat /data/local/tmp/den-installer.xml
        $nodes = $tree.SelectNodes('//node[@package="com.miui.securitycenter"]')
        $labels = @($nodes | ForEach-Object { $_.text })
        $button = $nodes | Where-Object { $_.text -eq 'Inštalovať' } | Select-Object -First 1
        if ($labels -contains $ExpectedLabel -and $labels -contains 'Inštalovať pomocou USB' -and $button) {
            $numbers = [regex]::Matches($button.bounds,'\d+') | ForEach-Object { [int]$_.Value }
            $tapX = [int](($numbers[0]+$numbers[2])/2)
            $tapY = [int](($numbers[1]+$numbers[3])/2)
            & $adbPath -s $Serial shell input touchscreen swipe $tapX $tapY $tapX $tapY 120
        }
    }
    Start-Sleep -Milliseconds 400
    $install.Refresh()
}
if (-not $install.HasExited) { throw 'Installer is still waiting on the device; check its prompt.' }
$install.WaitForExit()
Get-Content -LiteralPath $outLog
Get-Content -LiteralPath $errLog
$outputText = [string](Get-Content -Raw -LiteralPath $outLog)
if ($outputText -notmatch '\bSuccess\b') { throw 'Installation did not succeed.' }
