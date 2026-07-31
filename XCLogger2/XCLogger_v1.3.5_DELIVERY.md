# XCLogger 1.3.5 package filter delivery

## Versions

- XCLogger: `1.3.5` (`versionCode 11`)
- XCLogger AAR API: API level 3
- XCLoggerTestDemo: `1.2` (`versionCode 3`)

## Package filtering

- The mode is mutually exclusive: `whitelist` or `blacklist`.
- The default mode is `whitelist`.
- Whitelist `all` passes every ordinary log line.
- A non-empty whitelist passes only UIDs/PIDs resolved from its package entries.
- Blacklist rejects UIDs/PIDs resolved from its package entries and passes other ordinary lines.
- Switching mode preserves both lists.
- `AndroidRuntime`, `DEBUG`, and `libc` always pass all application-side filters.
- PackageManager UID resolution is the primary identity source. PID resolution is a compatibility fallback.
- UID/PID values come from parsed `logcat -v threadtime,uid` fields, never from message-body text.

Level is a single threshold and is emitted in the native logcat filterspec. Fresh configuration defaults to `V`. Content filtering, Level blacklist, and Tag blacklist are not active filtering dimensions. Their old data fields remain only for binary/data compatibility.

Existing installations retain their stored configuration during upgrade. If the new mode key does not exist, it is initialized to `whitelist`; the old `filter_package` value is not renamed or replaced.

## AAR usage

Fluent update:

```java
client.configUpdater()
        .filterLevel(XcLoggerConfigUpdater.LogLevel.VERBOSE)
        .filterPackages("com.example.target")
        .usePackageWhitelist()
        .commitAsync(result -> {
            // Check result.isSuccess() and result.getChangedFields().
        });
```

Blacklist mode:

```java
client.configUpdater()
        .blacklistPackages("com.example.noisy")
        .usePackageBlacklist()
        .commitAsync(callback);
```

Direct API 3 object update:

```java
XcLoggerConfigUpdate base = new XcLoggerConfigUpdate();
base.setRequestId(UUID.randomUUID().toString());

XcLoggerConfigUpdateV3 update = new XcLoggerConfigUpdateV3();
update.setBaseUpdate(base);
update.setPackageFilterMode(XcLoggerConfigUpdateV3.PACKAGE_FILTER_MODE_WHITELIST);

service.updateConfigurationV3(update, callback);
```

Call `getApiVersion()` before API 3 operations. Read the active mode with `getPackageFilterMode()`; the legacy `XcLoggerConfig` Parcelable layout remains unchanged.

## Demo usage

1. Install the XCLogger APK matching the product flavor.
2. Install all six APKs in the `Demo` directory by running `install_all_debug.bat`.
3. Open `XCLoggerTestDemo` and select the installed XCLogger package. R2351 uses `com.ko.xclogger`; common uses `com.xcheng.xclogger`.
4. Open `CONFIG`, edit both package lists, and use `Package whitelist mode` (`ON` is whitelist, `OFF` is blacklist).
5. `APPLY CHANGES` demonstrates the fluent updater. `APPLY MODE OBJECT` demonstrates direct `XcLoggerConfigUpdateV3` submission.
6. Open `STRESS TEST`, choose 10-100 lines/second, then start all six installed processes.

Android Studio/command-line build commands:

```text
XCLogger2\gradlew.bat test :xclogger-api:test :app:assembleCommonRelease :app:assembleP1416TPinelabsRelease :app:assembleR2351ComboRelease
XcLoggerTestDemo\gradlew.bat clean testDebugUnitTest assembleStressFleetDebug
```

## Verification

- XCLogger app tests passed for all build variants.
- AAR debug/release tests passed.
- Three XCLogger release flavors compiled, shrank, signed, and packaged.
- Demo unit tests and clean six-APK fleet build passed.
- Demo and stress-agent Lint passed.
- Device reported AIDL API version 3.
- Fluent blacklisting of fork1 resolved UID `10126`: fork1 saved `0` lines while fork2-fork5 each saved `704-722` lines.
- Direct V3 object switch to whitelist resolved main UID `10125`: main saved `733` stress lines and fork1-fork5 each saved `0` lines.
- Device operation log showed native Level filterspec `*:W` plus `AndroidRuntime:V`, `DEBUG:V`, and `libc:V`.

XCLogger Lint still reports 7 pre-existing errors and 88 warnings:

- `READ_LOGS`: Lint cannot infer that the selected flavor is platform-signed/installed as a system application.
- `QUERY_ALL_PACKAGES`: Play-style package visibility policy warning; this system utility intentionally enumerates installed packages for prefix filters.
- Five `MissingTranslation` errors: old filter hint strings are absent from Chinese and Russian resources.

These are static-analysis failures, not Java compilation or APK packaging failures. No baseline or suppression was added, and Java 8 warnings were left unchanged as requested.

## Known filter limitations

- Shared UID: one UID can belong to several packages. The implementation refuses unsafe UID-wide matching and falls back to PID. If the platform hides the target PID from `ActivityManager`, exact package separation is not guaranteed. A privileged process observer or trusted `/proc`/`ps` resolver is required for this case.
- Secondary Android users: PackageManager currently resolves the current user's application UID. Cross-user logs require UserHandle-aware package lookup.
- Legacy Tag blacklist updater methods and fields remain for API compatibility but do not participate in filtering. A future incompatible API should explicitly reject or remove them.

