# Backup της βάσης ceid_timetable (Windows PowerShell)
# Χρήση:  .\scripts\backup-db.ps1
# Προσαρμόστε το $PgBin αν η PostgreSQL είναι αλλού εγκατεστημένη.
$PgBin = "C:\Program Files\PostgreSQL\16\bin"
$BackupDir = Join-Path $PSScriptRoot "..\backups"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$Stamp = Get-Date -Format "yyyy-MM-dd_HHmm"
$OutFile = Join-Path $BackupDir "ceid_timetable_$Stamp.dump"

$env:PGPASSWORD = Read-Host "DB password για ceid_admin" -AsSecureString |
    ForEach-Object { [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($_)) }

& "$PgBin\pg_dump.exe" -U ceid_admin -h localhost -d ceid_timetable -F c -f $OutFile
if ($LASTEXITCODE -eq 0) {
    Write-Host "OK: $OutFile" -ForegroundColor Green
} else {
    Write-Host "ΣΦΑΛΜΑ backup (exit $LASTEXITCODE)" -ForegroundColor Red
}
Remove-Item Env:\PGPASSWORD
