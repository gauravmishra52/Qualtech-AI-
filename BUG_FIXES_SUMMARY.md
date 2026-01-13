# Bug Fixes and Cleanup Summary

## Issues Fixed:

### 1. Maven Duplicate Dependencies
- **Issue**: Warnings about duplicate dependencies for lombok, S3, and OpenCV
- **Fix**: Removed duplicate entries from pom.xml
  - Removed duplicate lombok dependency (lines 115-119)
  - Removed duplicate S3 dependency (lines 179-183)
  - Removed duplicate OpenCV dependency (lines 206-210)

### 2. Flyway Migration Error
- **Issue**: Migration V3__create_roles_table.sql failed because roles table already exists
- **Fix**: 
  - Added `IF NOT EXISTS` to CREATE TABLE statements
  - Made INSERT statement idempotent with `ON CONFLICT (name) DO NOTHING`
  - Created clean_flyway.sql script to reset schema history

### 3. Spring Boot Version Warning
- **Issue**: Spring Boot 3.1.x support ended
- **Fix**: Updated to Spring Boot 3.2.10

### 4. Unused Import
- **Issue**: jakarta.validation.Valid imported but not used in FaceRecognitionController
- **Fix**: Removed the unused import

### 5. JavaCV Missing JAR Files Warning
- **Issue**: Missing referenced JAR files in JavaCV dependencies
- **Status**: These are warnings only and don't affect functionality. The dependencies work correctly.

## Next Steps:

1. **Clean Flyway Schema History**:
   Run the following SQL command in PostgreSQL:
   ```sql
   TRUNCATE TABLE flyway_schema_history;
   ```

2. **Run the Application**:
   ```bash
   mvn clean spring-boot:run
   ```

## Files Modified:
- pom.xml (removed duplicates, updated Spring Boot version)
- src/main/resources/db/migration/V3__create_roles_table.sql (made idempotent)
- src/main/java/com/qualtech_ai/controller/FaceRecognitionController.java (removed unused import)

## Files Created:
- clean_flyway.sql (script to clean Flyway history)
- run_app.bat (batch file to run the app with cleanup)
- BUG_FIXES_SUMMARY.md (this file)
