# Cài thư viện cho demo VNPAY trên Windows.
# Chạy: bấm đúp cai-dat.bat, hoặc trong PowerShell:
#   powershell -ExecutionPolicy Bypass -File cai-dat.ps1          (Java + org.json)
#   powershell -ExecutionPolicy Bypass -File cai-dat.ps1 -Ngrok   (thêm ngrok)
param([switch]$Ngrok)
Set-Location $PSScriptRoot
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$JsonVer  = '20250517'
$JsonUrl  = "https://repo1.maven.org/maven2/org/json/json/$JsonVer/json-$JsonVer.jar"
$JsonSha1 = 'd67181bbd819ccceb929b580a4e2fcb0c8b17cd8'
$Jar      = 'lib\json.jar'

function Get-JavaMajor {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { return 0 }
    $line = (cmd /c "java -version 2>&1" | Select-Object -First 1)
    if ($line -match 'version "([^"]+)"') {
        $p = $Matches[1].Split('.')
        if ($p[0] -eq '1') { return [int]$p[1] } else { return [int]($p[0] -replace '\D.*', '') }
    }
    return 0
}

function Stop-Here($msg) { Write-Host $msg; Read-Host 'Nhấn Enter để đóng'; exit 1 }

Write-Host '== 1. Java (cần 17 trở lên)'
if ((Get-JavaMajor) -ge 17) {
    Write-Host ('Đã có: ' + (cmd /c "java -version 2>&1" | Select-Object -First 1))
} else {
    Write-Host 'Chưa có Java 17 trở lên.'
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        $ok = Read-Host 'Cài Temurin JDK 21 bằng winget? [y/N]'
        if ($ok -eq 'y') {
            winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
            Stop-Here 'Cài xong. Đóng cửa sổ này, mở PowerShell mới rồi chạy lại cai-dat.bat.'
        }
    }
    Stop-Here 'Hãy tải JDK 21 tại https://adoptium.net, cài xong chạy lại cai-dat.bat.'
}

Write-Host "== 2. Thư viện org.json $JsonVer"
function Get-Sha1($f) { (Get-FileHash $f -Algorithm SHA1).Hash.ToLower() }
if ((Test-Path $Jar) -and ((Get-Sha1 $Jar) -eq $JsonSha1)) {
    Write-Host "Đã có $Jar"
} else {
    New-Item -ItemType Directory -Force lib | Out-Null
    Write-Host 'Tải từ Maven Central...'
    try { Invoke-WebRequest -Uri $JsonUrl -OutFile "$Jar.tmp" -UseBasicParsing }
    catch { Stop-Here 'Không tải được, kiểm tra kết nối mạng.' }
    if ((Get-Sha1 "$Jar.tmp") -ne $JsonSha1) { Remove-Item "$Jar.tmp"; Stop-Here 'File tải về không đúng checksum, dừng lại.' }
    Move-Item -Force "$Jar.tmp" $Jar
    Write-Host "Đã tải $Jar"
}

if ($Ngrok) {
    Write-Host '== 3. ngrok'
    if (Get-Command ngrok -ErrorAction SilentlyContinue) { Write-Host 'Đã có ngrok' }
    elseif (Get-Command winget -ErrorAction SilentlyContinue) { winget install --id Ngrok.Ngrok -e --accept-source-agreements --accept-package-agreements }
    else { Write-Host 'Tải ngrok tại https://ngrok.com/download' }
    Write-Host 'Nhớ chạy: ngrok config add-authtoken <token trong trang ngrok>'
}

Write-Host '== Biên dịch và kiểm tra'
javac --release 17 -cp $Jar SeminarDemo.java DemoChecks.java
if ($LASTEXITCODE -ne 0) { Stop-Here 'Biên dịch lỗi.' }
java -cp ".;$Jar" DemoChecks
Write-Host ''
Write-Host "Xong. Chạy demo:  java -cp '.;lib/json.jar' SeminarDemo"
Write-Host 'Rồi mở http://localhost:8090'
Read-Host 'Nhấn Enter để đóng'
