@echo off
cd /d "%~dp0"
if "%DB_URL%"=="" set "DB_URL=jdbc:postgresql://localhost:5432/sai_vandan_crm"
if "%DB_USERNAME%"=="" (
  echo DB_USERNAME is not set. Configure PostgreSQL environment variables before starting.
  exit /b 1
)
if "%DB_PASSWORD%"=="" (
  echo DB_PASSWORD is not set. Configure PostgreSQL environment variables before starting.
  exit /b 1
)
set "SPRING_PROFILES_ACTIVE=prod"
mvn spring-boot:run -q
