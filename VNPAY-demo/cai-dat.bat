@echo off
rem Bam dup file nay de cai thu vien cho demo VNPAY tren Windows.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0cai-dat.ps1" %*
