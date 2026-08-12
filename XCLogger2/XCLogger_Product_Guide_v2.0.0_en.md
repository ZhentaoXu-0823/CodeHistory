# XCLogger Product Guide

> Version: v2.0.1 / Configuration Protocol API 4
>
> Updated: 2026-08-12

## 1. Product Scope

XCLogger is a system-signed Android log capture and management application for device operations, diagnostics, and MDM integration. It continuously captures logcat and provides filtering, rotation, capacity cleanup, ZIP export, upload-state handling, AIDL control, and operation auditing.

This guide describes behavior implemented by the current source. Use `XCLogger_AAR_API_Reference_en.md` for exact AAR signatures, parameters, and complete examples.

## 2. Current Capabilities

| Capability | Current behavior |
|---|---|
| Capture | `LogCaptureService` is a foreground service; the collector reads `logcat -v threadtime,uid` |
| Actual state | `isRunning()` is true only while the Service exists and `ProcessController` has an active collector |
| Tag filtering | Allowlist; `all` disables restriction; `AndroidRuntime`, `DEBUG`, and `libc` bypass filters |
| Level filtering | `f/e/w/i/d/v` threshold; `v` is the default no-drop value; validation also accepts `all` |
| Package filtering | Mutually exclusive `OFF`, `WHITELIST`, and `BLACKLIST`; exact names and dot-suffixed prefixes resolve to UIDs/PIDs |
| File management | Rotates by per-file size and removes old logs by retention and total capacity before creating a file; when available space is ≤ 1GB or ≤ 10% of total storage, deletes oldest files (excluding the current file) until at least one file size is released |
| Initial capacity | Only when the database has no configuration: API 33/35 normalize storage to 8/16/32/64 GB; 8/16 GB map to a 512 MB log quota and 32/64 GB to 1024 MB; failures fall back to the log partition and then 256 MB; other versions use flavor XML |
| Configuration | Compatibility partial patch and API 4 atomic update; an effective update restarts active capture after persistence |
| Compression | No-range requests create per-day ZIPs; requests with either boundary create one range ZIP from overlapping file intervals |
| Upload lifecycle | Success deletes ZIPs; failure increments a counter; the third failure forces cleanup; cancel clears the task |
| ZIP pipe | `getLogZip()` pipes the first ZIP only while state is `WAIT_UPLOAD_RESULT` |
| Audit | Control operations are recorded; an operation-history snapshot is the final entry in each ZIP |
| Customer builds | Three flavors: `common`, `p1416TPinelabs`, and `r2351Combo`, each with package, signing, and XML defaults |

## 3. Filtering

### 3.1 Tag and Level

The tag allowlist and level threshold are both applied. Tags accept letters, digits, underscore, dot, and hyphen, with a length of 1–64. Lists contain at most 64 entries; an empty allowlist normalizes to `all`.

Legacy tag-denylist, level-denylist, content-allowlist, and content-denylist fields remain in Parcelable/XML/database storage but are not used by the current capture pipeline.

### 3.2 Package Modes

| Mode | Behavior |
|---|---|
| `OFF` | Preserves both lists without package filtering |
| `WHITELIST` | Keeps logs mapped to allowlisted UIDs/PIDs |
| `BLACKLIST` | Excludes logs mapped to denylisted UIDs/PIDs |

Package lists support exact names and dot-suffixed prefixes. Package install, removal, and replacement events refresh UID/PID mappings. Mode is not stored in the legacy `XcLoggerConfig` Parcelable; read it with `getPackageFilterMode()` and update it through the API 4 updater.

## 4. Configuration and Storage

| Setting | common | p1416TPinelabs | r2351Combo |
|---|---:|---:|---:|
| XML total size | 1024 MB | 300 MB | 1024 MB |
| File size | 4 MB | 4 MB | 4 MB |
| Buffer | 2048 B | 2048 B | 4096 B |
| Retention | 168 h | 96 h | 96 h |
| Package mode | OFF | OFF | OFF |

The total-size values above are XML seeds. On API 33/35, first persistence prefers the primary-storage policy. Existing database settings, upgrades, and explicit updates do not reapply that first-run policy.

API 4 structured update rules:

- `totalSizeMb/fileSizeMb/logPeriodHours > 0`, and `fileSizeMb <= totalSizeMb`.
- `totalSizeMb` is bounded: the floor is the first-run quota (512MB for 8/16GB, 1024MB for 32/64GB, 256MB fallback) and the ceiling is normalized capacity × 90%; all four channels (AIDL/broadcast/UI/import) validate uniformly, and an out-of-range value rejects only that field while others apply.
- `bufferSizeBytes` is 512–4096 and aligned to 512.
- Lists support replace/add/remove; the package denylist also supports clear and allowlists support `all`.
- A single-thread FIFO merges each request against the latest server configuration.
- Persistence failure leaves the in-memory configuration unchanged; active-capture restart failure has a distinct result code.

## 5. Compression and Upload

`triggerCompression()` and `triggerCompressionWithRange()` return only request acceptance. Obtain final results from Listener callbacks or status queries.

Either range boundary may be omitted. The implementation strips non-digits before parsing; an unparseable or reversed range keeps the unfiltered snapshot. An end-only request with results can currently fail during ZIP naming because the start value is absent, so production clients should pass two valid, ordered `yyyyMMddHHmmss` values.

After successful compression:

1. State becomes `WAIT_UPLOAD_RESULT`.
2. Listener receives `onCompressReady()` and `onCompressFinished()`.
3. The AAR client reads the first ZIP through `getLogZip()`.
4. Report `true` only after all business uploads complete.
5. Report failures with `false`; the third failure deletes ZIPs and returns to `IDLE`.

## 6. AAR Integration

`IXcLoggerService` currently has 16 methods, plus four Listener callbacks and one configuration-result callback. The AAR covers capture control, actual state, configuration reads, compatibility updates, API 4 atomic updates, both compression requests, status, ZIP pipe, upload success/failure, cancel, Listener lifecycle, API version, and package mode.

Use `XCLogger_AAR_API_Reference_en.md` as the authoritative API reference. The Demo `xclogger_aar/USAGE.md` maps all 16 methods and every updater overload to executable tests.

## 7. Platform and Delivery

| Item | Current value |
|---|---|
| Minimum SDK | API 23 (Android 6.0) |
| Target SDK | API 33 (Android 13) |
| XCLogger version | 2.0.0 (versionCode 15) |
| Configuration protocol | API 4 |
| Default common package | `com.xcheng.xclogger` |
| Default log path | `/storage/emulated/0/XcLogger` |
| Internal ZIP directory | `/data/xclogger/mobilelog` |
| Upload failure limit | 3 |

See `README.md`, `ARCHITECTURE.md`, and `INTEGRATION.md` for system installation, signing, permissions, build, and deployment details.
