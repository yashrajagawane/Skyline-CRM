@echo off
cd /d "%~dp0"
mvn -Dspring.flyway.locations=classpath:/db/migration-h2/ spring-boot:run -q
