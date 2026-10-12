#!/bin/sh
set -eu
cd "$(dirname "$0")"
javac --release 17 -cp lib/json.jar SeminarDemo.java
exec java -cp '.:lib/json.jar' SeminarDemo
