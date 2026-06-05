# 🛠️ Enterprise Engineering Runbook & Debugging Ledger

This document serves as an immutable, technical ledger of architectural hurdles, AI generation errors, compilation failures, and data-integrity course corrections encountered during the development of the Multi-Tenant Payment Gateway Middleware.

---

## 📋 The Master Debugging Template
*Whenever you encounter an error during a Verification Checkpoint, copy this empty block, paste it at the BOTTOM of this file, and fill it out.*

```markdown
## 🔍 Issue #[Insert Number]: [Short, clear title of the issue]
* **Phase & User Story:** Phase [X], Story [X.X]
* **The Failure Perimeter:** [e.g., Maven Compile / Spring Context Boot / JUnit Test / Postman API Call]

### 🚨 The Error Signature
```text
[Paste the exact console error message, stack trace, or red underline code snippet here]
```

### 🧠 Root Cause Analysis
[Explain why it failed and where the mismatch occurred]

### ✅ Corrective Action Implemented
[List the exact configuration/code changes made]

### ✅ Verification Checkpoint
[List the commands or requests used to verify and the exact success result]

### 🔒 Preventive Guardrail
[Document the engineering rule to avoid recurrence]
```

---

## 🔍 Issue #1: H2 runtime startup failure due to incorrect dependency scope
* **Phase & User Story:** Phase 1, Story 1.1
* **The Failure Perimeter:** Spring Context Boot via Maven run command

### 🚨 The Error Signature
```text
Failed to load driver class org.h2.Driver
APPLICATION FAILED TO START during datasource initialization
```

### 🧠 Root Cause Analysis
H2 datasource was configured for runtime use, but H2 dependency was set with test scope in Maven. The H2 driver was therefore excluded from runtime classpath.

### ✅ Corrective Action Implemented
Changed H2 dependency scope from test to runtime in pom.xml.

### ✅ Verification Checkpoint
Ran mvn spring-boot:run and verified successful startup with H2 datasource initialization.

### 🔒 Preventive Guardrail
If a datasource is used by runtime profile, its JDBC driver must be in runtime or compile scope, never test-only scope.

---

## 🔍 Issue #2: Actuator health endpoint returned DOWN due to Redis health contributor
* **Phase & User Story:** Phase 1, Story 1.1
* **The Failure Perimeter:** Postman/API verification of /actuator/health

### 🚨 The Error Signature
```text
GET http://localhost:8080/actuator/health
Response: {"status":"DOWN"}
```

### 🧠 Root Cause Analysis
Redis starter is present, so Actuator auto-enables Redis health checks. No Redis instance was running during Story 1.1 verification, which pulled aggregated health to DOWN.

### ✅ Corrective Action Implemented
Added the following property in src/main/resources/application.properties:

```properties
management.health.redis.enabled=false
```

### ✅ Verification Checkpoint
1. Freed port 8080 by terminating stale Java listener.
2. Started app using mvn spring-boot:run.
3. Called endpoint:

```text
http://localhost:8080/actuator/health
```

4. Confirmed response:

```json
{"status":"UP"}
```

### 🔒 Preventive Guardrail
Disable health indicators for infrastructure not provisioned in current story scope, or provision those services before health verification.


## 🔍 Issue #3: H2 Console login failed with mem:gatewaydb not found
* **Phase & User Story:** Phase 1, Story 1.2
* **The Failure Perimeter:** H2 Console verification checkpoint

### 🚨 The Error Signature
```text
Database "mem:gatewaydb" not found, either pre-create it or allow remote database creation [90149-224]
```

### 🧠 Root Cause Analysis
The running application datasource URL was set to `jdbc:h2:mem:paymentgateway`, while H2 console login used `jdbc:h2:mem:gatewaydb`. These point to different in-memory databases.

### ✅ Corrective Action Implemented
Aligned the H2 console JDBC URL with the application datasource URL and restarted the application.

### ✅ Verification Checkpoint
1. Started service using `mvn spring-boot:run`.
2. Connected in H2 console with the exact datasource URL from application properties.
3. Executed `SELECT * FROM TRANSACTION_RECORDS;`.
4. Confirmed empty result set with expected headers including `AMOUNT`.

### 🔒 Preventive Guardrail
Use one canonical H2 in-memory database name across `spring.datasource.url` and H2 console login for every local verification run.