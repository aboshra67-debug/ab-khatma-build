#!/usr/bin/env bash
set -euo pipefail
mkdir -p screen_images
adb install -r ci_tests/p97-ci-app.apk
adb install -r ci_tests/p97-ci-test.apk
adb shell am instrument -w -r -e class com.ab.khatma.guide.P85ReaderDeviceTest,com.ab.khatma.guide.P88IbtehalDeviceTest com.ab.khatma.preview.test/androidx.test.runner.AndroidJUnitRunner > screen_tests.txt
cat screen_tests.txt
grep -E '^OK \([0-9]+ tests?\)' screen_tests.txt
for name in p85-reader-187.png p85-reader-187-reading.png p85-reader-604.png p88-ibtehal-categories.png p88-ibtehal-tracks.png p88-ibtehal-detail.png; do
    if adb exec-out run-as com.ab.khatma.preview cat "files/$name" > "screen_images/$name" && head -c 8 "screen_images/$name" | od -An -tx1 | grep -q '89 50 4e 47 0d 0a 1a 0a'; then
        file "screen_images/$name"
    else
        rm -f "screen_images/$name"
        echo "WARNING: not exported $name"
    fi
done
adb shell am force-stop com.ab.khatma.preview || true
adb shell monkey -p com.ab.khatma.preview 1 || true
sleep 10
adb exec-out screencap -p > screen_images/app-entry-emulator.png
file screen_images/app-entry-emulator.png
