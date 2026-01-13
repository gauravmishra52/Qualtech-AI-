@echo off
echo Starting QualTech AI Application...
echo.
echo IMPORTANT: First run this SQL in PostgreSQL:
echo TRUNCATE TABLE flyway_schema_history;
echo.
echo Press any key to continue with application startup...
pause > nul

echo Cleaning and compiling...
mvn clean compile

echo.
echo Starting application...
mvn spring-boot:run

pause
