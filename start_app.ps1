Write-Host "Starting QualTech AI Application..." -ForegroundColor Green
Write-Host ""
Write-Host "IMPORTANT: First run this SQL in PostgreSQL:" -ForegroundColor Yellow
Write-Host "TRUNCATE TABLE flyway_schema_history;" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to continue with application startup..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

Write-Host "Cleaning and compiling..." -ForegroundColor Blue
& mvn clean compile

Write-Host ""
Write-Host "Starting application..." -ForegroundColor Green
& mvn spring-boot:run

Read-Host "Press Enter to exit"
