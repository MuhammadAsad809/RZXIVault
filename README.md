# RZXIVault 🛡️
### Offline Biometric Encrypted Vault (Zero-Cloud Password, Note & File Manager)

`[DARKCODE::ACTIVE::7F3A]`

RZXIVault is an air-gapped, zero-cloud, hardware-encrypted personal vault for Android engineered with zero network attack surface. It guarantees that credentials, confidential notes, and private files never leave the device.

---

## 🔒 Cryptographic Architecture & Threat Model

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARDWARE ROOT OF TRUST                          │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Android Keystore (StrongBox Keymaster / TEE)               │
│                   AES-256-GCM Hardware Master Key                      │
│                  - setUserAuthenticationRequired(true)                 │
│                  - setInvalidatedByBiometricEnrollment(true)           │
│                  - AUTH_BIOMETRIC_STRONG Enforcement                   │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                       (Biometric Authorization)
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 SQLCipher Storage Engine (net.zetetic)                 │
│               - 256-bit AES page-level encrypted SQLite                │
│               - HMAC-SHA512 per-page integrity verification            │
│               - Volatile RAM keys zeroed on application pause          │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  Offline Backup Container Format (.ovc)                │
│                  - PBKDF2-HMAC-SHA256 (100,000 iterations)             │
│                  - AES-256-GCM Authenticated Encryption                │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Security Invariants

1. **Hardware Root of Trust**:
   - Master key is generated inside the hardware TEE (TrustZone) or StrongBox security processor.
   - Key material is completely inaccessible to userland processes.
2. **Biometric Gate**:
   - Cryptographic operations require `BiometricPrompt.CryptoObject` backed by `AUTH_BIOMETRIC_STRONG`.
   - If an attacker enrolls a new biometric on the host device, the master key is permanently invalidated (`setInvalidatedByBiometricEnrollment(true)`).
3. **Air-Gapped & Zero Network Surface**:
   - The application does not declare the `android.permission.INTERNET` permission in its manifest, eliminating network exfiltration vectors.
4. **Memory Sanitization**:
   - Raw keys and sensitive strings are handled in primitive `ByteArray` and `CharArray` structures and immediately wiped using `java.util.Arrays.fill(0)` upon locking or backgrounding.
5. **Zero Cloud Backup (.ovc)**:
   - Portable backup container encrypted using PBKDF2-HMAC-SHA256 (100,000 iterations) with authenticated AES-256-GCM.

---

## 📱 Material 3 (M3) UI Features

- **Biometric Unlock Screen**: Real-time hardware security indicators (StrongBox / TEE detection, AES-256 badge, one-touch biometric authorization).
- **Vault Dashboard**: Category filtering (Passwords, Secure Notes, Encrypted Files), search bar, copy-to-clipboard button.
- **Credential Generator**: Configurable high-entropy password generator.
- **Settings & Backup**: Single-tap `.ovc` encrypted backup export/import and RAM sanitization trigger.

---

## 🛠️ Building & Generating APK

### Automated via GitHub Actions
Every push to `main` executes `.github/workflows/build-apk.yml`, which:
1. Builds the Android APK with JDK 17 and Android SDK 34.
2. Uploads the build artifact.
3. Automatically publishes a GitHub Release with the compiled APK attached.

### Local Build
```bash
./gradlew assembleDebug
# Generated APK will be in:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 License
Apache-2.0. Offline Security Software.
