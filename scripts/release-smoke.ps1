param(
  [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1'
)
$ErrorActionPreference = 'Stop'

function Assert-Equal($actual, $expected, $label) {
  if ($actual -ne $expected) { throw "$label failed: expected [$expected], got [$actual]" }
  Write-Host "PASS $label" -ForegroundColor Green
}

function Login([string]$email) {
  Invoke-RestMethod "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' -Body (@{ email = $email; password = 'ChangeMe!2026' } | ConvertTo-Json)
}

$admin = Login 'admin@saivandan.local'; $adminHeaders = @{ Authorization = "Bearer $($admin.accessToken)" }
$health = Invoke-RestMethod "$BaseUrl/actuator/health"; Assert-Equal $health.status 'UP' 'health'
$reports = Invoke-RestMethod "$BaseUrl/reports/catalog" -Headers $adminHeaders; if ($reports.Count -lt 1) { throw 'report catalog is empty' }; Write-Host "PASS report catalog ($($reports.Count) reports)" -ForegroundColor Green
$notifications = Invoke-RestMethod "$BaseUrl/notifications" -Headers $adminHeaders; if ($notifications.Count -lt 1) { throw 'seed notifications are missing' }; Write-Host "PASS notification feed" -ForegroundColor Green
$csv = Invoke-WebRequest "$BaseUrl/reports/lead-funnel/export?format=csv" -Headers $adminHeaders -UseBasicParsing; Assert-Equal $csv.StatusCode 200 'CSV export'
$pdf = Invoke-WebRequest "$BaseUrl/reports/lead-funnel/export?format=pdf" -Headers $adminHeaders -UseBasicParsing; Assert-Equal $pdf.StatusCode 200 'PDF export'
$filtered = Invoke-RestMethod "$BaseUrl/reports/lead-funnel/data?status=NEW&page=0&size=1" -Headers $adminHeaders; Assert-Equal $filtered.page 0 'report page'; Assert-Equal $filtered.size 1 'report page size'
$finance = Login 'finance@saivandan.local'; $financeHeaders = @{ Authorization = "Bearer $($finance.accessToken)" }
try { Invoke-RestMethod "$BaseUrl/reports/payroll/data" -Headers $financeHeaders; throw 'finance user unexpectedly accessed payroll report' } catch { if ($_.Exception.Response.StatusCode.value__ -ne 403) { throw } }
Write-Host 'PASS report permission boundary' -ForegroundColor Green
Write-Host 'Release smoke checks passed.' -ForegroundColor Cyan
