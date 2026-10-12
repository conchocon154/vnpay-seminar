Set-Location $PSScriptRoot
javac --release 17 -cp lib/json.jar SeminarDemo.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp '.;lib/json.jar' SeminarDemo
