# TravelVista authentication repair — 19 September 2026

## Outcome

Customer registration now follows Register → REGISTRATION OTP → authenticated Home. Future customer login follows email/password → LOGIN OTP → authenticated Home. Password verification and sending an OTP never issue a customer token. Admin password login remains independent.

## Audit findings

- The existing CustomerAuthController used /api/auth, and AuthController used /api/admin. The current frontend already called /auth/login, despite the historical admin-endpoint bug described in the request.
- Registration verification activated the user but returned no JWT. RegisterPage then explicitly sent the customer to Login.
- Passwordless /login/otp/send and /login/otp/resend routes could create LOGIN challenges without checking a password. The customer login controller also directly authenticated admin roles.
- Pending registration updates skipped phone uniqueness validation. Email lookup did not reliably handle all old mixed-case addresses. Phone formatting was not normalized consistently.
- OTP rows used fast, unsalted hashes; consumption lacked transaction locking; resend deleted history used by hourly limits. Cooldown was only in memory.
- AuthContext had no expiry timer/restore expiry check, restored sessions through admin/me first, and could retain authenticated UI after API rejection.
- Provider exceptions were exposed by the controller. Configuration had hardcoded credentials and fallback secrets; startup also seeded predictable staff passwords and logged them.
- The supplied schema.sql is destructive. It was NOT run. The actual PostgreSQL public schema initially had only a raw-email unique index and no phone uniqueness constraint.

## Implemented behavior and security

- Registration validates name, email, phone, and password; frontend also checks confirmation. Password policy preserves the project's six-character minimum and adds BCrypt's 72-byte maximum. Confirmation is never stored or sent.
- Email is trimmed/lowercased with Locale.ROOT; phone becomes E.164. Legacy plain ten-digit phones default to India. Other countries must include +country-code. Application checks and database constraints enforce uniqueness.
- Pending unverified customers reuse the same user ID. A fresh successful email send replaces the old challenge before pending details/password are updated. Delivery failure leaves the pending account retryable and never authenticates it.
- Active verified email duplicates and phone duplicates return explicit 400 errors. A phone owned by a different pending account is also reserved; recovery must use that account's original email.
- Three existing active customers had a NULL email_verified flag. They remain unverified. After entering correct credentials, the login page offers Send email verification code. Registration verification activates/verifies only after a correct OTP; migration never changes activation flags.
- Purpose values returned to the browser are REGISTRATION and LOGIN; persisted purposes retain the existing register_email and login_email names. Verification binds purpose, email and random transactionId.
- SecureRandom generates six-digit codes. New rows contain salted BCrypt hashes, no plaintext OTP. Codes expire exactly ten minutes after created_at and permit at most five attempts.
- User/OTP database locks and transactions make successful verification single-use, including concurrent requests. A new successful challenge invalidates older challenges for that purpose. Failure does not authorize a new challenge.
- Resend cooldown is 60 seconds, checked against persisted challenge timestamps as well as the existing memory limiter; at most ten challenges per email/purpose/hour. Login resend requires an unexpired transaction issued after password validation. Expired login transactions require starting with the password again.
- Customer JWT expiration is exactly 3,600,000 milliseconds (3600 seconds). issuedAt and expiration share one timestamp. Backend signature/expiry checks are authoritative. Existing role-based staff durations are retained.
- Frontend uses the project's existing localStorage bearer-token approach, restores only unexpired sessions, schedules logout from exp, checks on focus/visibility, and clears auth on matching authenticated 401 responses. Protected routes redirect to the correct login page. Admin and customer API actions and session restoration endpoints are separate.
- /verify-otp requires sessionStorage challenge context. Wrong/expired OTP stays there; failed send stays on the original form. Verification redirects Home. The shared page displays a masked address.
- Resend uses environment configuration. HTTP 403 becomes a safe 502 message explaining recipient restrictions. No provider response bodies or credentials are exposed. Arbitrary recipients require a verified Resend domain; onboarding@resend.dev remains subject to provider testing restrictions.
- Secrets are no longer defaulted in application.properties. .env and .env.* are ignored, with a safe .env.example exception. Existing staff records/passwords are not changed; optional new staff bootstrap passwords come from environment variables.

## Database migration

Applied src/main/resources/db/customer-auth-migration.sql to local PostgreSQL postgres/public in one transaction, after validating it in an isolated test schema. It normalized three user rows, added uq_users_email_normalized and uq_users_phone_normalized, added a phone-format check and an OTP transaction lookup index, ensured OTP columns exist, and expired 12 legacy auth challenges. User counts, roles, and activation flags remained unchanged. No users or application tables were deleted.

The migration stops and rolls back on duplicate normalized values or invalid legacy phones rather than deleting or merging data. Apply this same migration to other deployments before running the new code. schema.sql now includes constraints for new development databases, but remains a destructive fixture and is explicitly disabled at startup.

## Actual endpoints and Postman requests

All requests below use POST, base URL http://localhost:8080, Content-Type: application/json, and Accept: application/json. Import TravelVista-Auth.postman_collection.json. Set customerEmail to a Resend-authorized test recipient, choose a unique phone, and fill passwords/OTP values locally. The collection saves transaction IDs and customer/admin tokens separately. Wait 60 seconds before resending; always use the latest returned transactionId.

### Register

POST /api/auth/register

```json
{
  "name": "{{customerName}}",
  "email": "{{customerEmail}}",
  "phone": "{{customerPhone}}",
  "password": "{{customerPassword}}"
}
```

### Verify Registration OTP

POST /api/auth/register/verify

```json
{
  "email": "{{customerEmail}}",
  "otp": "{{registrationOtp}}",
  "transactionId": "{{registrationTransactionId}}"
}
```

### Resend Registration OTP

POST /api/auth/register/resend

```json
{
  "email": "{{customerEmail}}"
}
```

### Start Customer Login

POST /api/auth/login

```json
{
  "email": "{{customerEmail}}",
  "password": "{{customerPassword}}"
}
```

### Verify Login OTP

POST /api/auth/login/verify

```json
{
  "email": "{{customerEmail}}",
  "otp": "{{loginOtp}}",
  "transactionId": "{{loginTransactionId}}"
}
```

### Resend Login OTP

POST /api/auth/login/otp/resend

```json
{
  "email": "{{customerEmail}}",
  "transactionId": "{{loginTransactionId}}"
}
```

### Admin Login

POST /api/admin/login

```json
{
  "email": "{{adminEmail}}",
  "password": "{{adminPassword}}"
}
```

The existing /api/auth/login/otp/verify remains an alias of /api/auth/login/verify. The old /api/auth/login/otp/send is retained only as a transaction-bound resend alias; it no longer provides passwordless login.

Verify customer session: GET /api/auth/me with Authorization: Bearer {{customerToken}}. Verify admin session: GET /api/admin/me with Authorization: Bearer {{adminToken}}. Customer tokens must receive 403 from admin/me. Expired tokens must receive 401.

## Verification results

- Backend: 73 tests passed, 0 failures, 0 errors, 0 skipped in the full PostgreSQL run. Covers registration, duplicate values, pending recovery, password enforcement, purpose/transaction separation, OTP expiry/reuse/resend, provider failure, admin separation, JWT expiry, real Spring Security filtering, concurrent verification and database constraints. Resend/email delivery is mocked throughout; no tests send real emails.
- Frontend: 7 Node tests passed for expiry boundaries, malformed tokens, role separation and safe delivery/OTP errors.
- Frontend production build passed (Vite 5.4.21). Existing large-bundle warning remains. No lint script/tool is configured.
- Browser smoke checks on the rebuilt preview at 127.0.0.1:5174 confirmed customer login copy, separate admin login, direct OTP access redirecting to Login, and mismatched passwords remaining on registration. Full browser-to-Resend inbox verification was not performed.
- The service already running on port 5173 served an older login UI; it was not replaced. Restart the intended checkout after configuring environment variables.

## Files changed by this task

Backend:
- .gitignore, .env.example
- src/main/java/com/travelvista/controller/CustomerAuthController.java
- src/main/java/com/travelvista/controller/CustomerController.java (profile phone validation)
- src/main/java/com/travelvista/service/UserService.java, OtpService.java, EmailOtpService.java, AuthFailure.java
- src/main/java/com/travelvista/repository/UserRepository.java, OtpVerificationRepository.java
- src/main/java/com/travelvista/model/OtpVerification.java
- src/main/java/com/travelvista/config/JwtUtil.java, JwtAuthFilter.java, SecurityConfig.java, GlobalExceptionHandler.java, DataInitializer.java
- src/main/resources/application.properties, schema.sql, db/customer-auth-migration.sql
- src/test/java/com/travelvista/CustomerAuthenticationTest.java, CustomerAuthenticationIntegrationTest.java, AuthenticationSecurityTest.java
- scripts/test-auth.cjs and these docs

Frontend:
- .gitignore, package.json
- src/App.jsx, src/context/AuthContext.jsx, src/services/api.js
- src/pages/auth/LoginPage.jsx, RegisterPage.jsx, VerifyOtpPage.jsx
- src/utils/authSession.js, authSession.test.js, otpErrors.js

Pre-existing unrelated working-tree changes were retained. No commit was created.

## Local setup and remaining external steps

1. Copy the safe .env.example to .env and fill DB_PASSWORD, JWT_SECRET, RESEND_API_KEY and RESEND_FROM_EMAIL. JWT_SECRET must be strong random key material (at least 32 bytes). Supply Cloudinary settings for existing image features.
2. Rotate the credentials that were previously hardcoded; removing them from source does not revoke them or erase Git history. Do not reuse the exposed Resend/Cloudinary keys or JWT fallback secret.
3. For onboarding@resend.dev, use the account's permitted test recipient. For arbitrary customers, configure a verified Resend sending domain. Do not bypass provider restrictions.
4. Restart the backend and the correct frontend checkout; the already running servers were not restarted or silently reconfigured.
5. Run the imported Postman flow with an authorized inbox to confirm real email delivery. Automated tests mock delivery, so they cannot prove provider account setup or inbox receipt.

To rerun backend integration tests on this Windows installation: node scripts/test-auth.cjs from travel-website. It uses local .env.docker database settings (or AUTH_TEST_DB_USER/AUTH_TEST_DB_PASSWORD), creates a new randomly named tv_auth_test_* schema, asserts the schema before testing, and retains synthetic test data without deleting any schema. Override MAVEN_PATH, PSQL_PATH and JAVA_HOME if needed. Test-only schemas from this run are retained; public application data is separate. Standard Maven tests without AUTH_TEST_JDBC_URL deliberately skip the database suite. Frontend tests: npm test; build: npm run build.
