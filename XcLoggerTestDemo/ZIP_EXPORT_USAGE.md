# XCLoggerTestDemo 1.4 ZIP export

XCLoggerTestDemo provides two silent ZIP export commands. Neither command requests external storage permissions, and XCLogger2/AAR are unchanged.

## FETCH DOWNLOADS

Uses `MediaStore.Downloads` and saves to the public directory:

```text
/sdcard/Download/XCLogger/xclogger_fetch_yyyyMMddHHmmss.zip
```

The file is inserted with `IS_PENDING=1`. It becomes visible to other applications only after the AIDL pipe transfer succeeds. A failed export deletes the incomplete MediaStore entry.

## FETCH APP DIR

Saves to XCLoggerTestDemo's external files directory:

```text
/sdcard/Android/data/com.xcheng.xcloggertestdemo/files/XCLogger/xclogger_fetch_yyyyMMddHHmmss.zip
```

This directory is application-specific and requires no storage permission. Android may remove it when XCLoggerTestDemo is uninstalled.

## Usage

1. Open XCLoggerTestDemo and select the installed XCLogger package.
2. Open `COMPRESSION TEST`.
3. Trigger `FULL` or `RANGE` compression.
4. Wait for `compressReady` and `compressFinished` success callbacks.
5. Tap `FETCH DOWNLOADS` or `FETCH APP DIR`.
6. Wait for the corresponding `zip_fetched_*: PASS` result.
7. Report the upload result only after the consumer has finished with the ZIP.

Both buttons are disabled while a transfer is active. Each export uses a second-resolution timestamp in the file name.

## Build

```text
gradlew.bat clean testDebugUnitTest assembleStressFleetDebug :app:lintDebug :stress-agent:lint
```

The six APKs are collected under:

```text
build/outputs/stress-fleet/debug
```

## Device verification

- Android 15 / API 35, Demo `1.5` (`versionCode 6`).
- `MANAGE_EXTERNAL_STORAGE` remained ungranted.
- Both destinations received the same `6,981,820`-byte ZIP.
- The two ZIP files had identical SHA-256 hashes and readable archive entries.
- No `EPERM`, `EPIPE`, or `Pipe transfer failed` occurred.
