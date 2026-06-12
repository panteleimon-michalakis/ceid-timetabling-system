# Επαναφορά backup (Windows PowerShell) — ΠΡΟΣΟΧΗ: αντικαθιστά τα δεδομένα!
# Χρήση:  .\scripts\restore-db.ps1 -File ..\backups\ceid_timetable_2026-06-12_1200.dump
param([Parameter(Mandatory=$true)][string]$File)
$PgBin = "C:\Program Files\PostgreSQL\16\bin"

$confirm = Read-Host "Θα ΑΝΤΙΚΑΤΑΣΤΑΘΟΥΝ τα δεδομένα της ceid_timetable. Συνέχεια; (yes/no)"
if ($confirm -ne "yes") { Write-Host "Ακυρώθηκε."; exit }

$env:PGPASSWORD = Read-Host "DB password για ceid_admin" -AsSecureString |
    ForEach-Object { [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($_)) }

& "$PgBin\pg_restore.exe" -U ceid_admin -h localhost -d ceid_timetable --clean --if-exists $File
Write-Host "Ολοκληρώθηκε (exit $LASTEXITCODE)"
Remove-Item Env:\PGPASSWORD
