@echo off
echo Checking Java installation...
java -version
echo.

echo Checking Maven installation...
mvn -version
echo.

echo Checking if PostgreSQL is running...
netstat -an | findstr :5432
echo.

echo Cleaning project...
mvn clean
echo.

echo Compiling project...
mvn compile
echo.

echo Starting application with debug info...
mvn spring-boot:run -Dspring-boot.run.arguments="--debug --server.port=8080"
pause
