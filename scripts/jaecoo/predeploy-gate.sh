#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Felipe Rodrigues
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 SERIAL JCONFIG_APK JSTORE_APK" >&2
  exit 64
fi

serial="$1"
jconfig_apk="$2"
jstore_apk="$3"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
apksigner="$(find "$sdk_root/build-tools" -name apksigner -type f -print 2>/dev/null | sort -V | tail -n 1)"
apkanalyzer="$(find "$sdk_root/cmdline-tools" -name apkanalyzer -type f -print 2>/dev/null | sort -V | tail -n 1)"

[ -n "$serial" ] || { echo "serial is empty" >&2; exit 65; }
[ -f "$jconfig_apk" ] || { echo "jconfig APK not found" >&2; exit 66; }
[ -f "$jstore_apk" ] || { echo "JStore APK not found" >&2; exit 66; }
[ -x "$apksigner" ] || { echo "apksigner not found" >&2; exit 69; }

device_state="$(adb devices | awk -v wanted="$serial" '$1 == wanted { print $2 }')"
[ "$device_state" = "device" ] || {
  echo "ADB serial $serial is not connected and authorized" >&2
  exit 67
}

build_id="$(adb -s "$serial" shell getprop ro.build.display.id | tr -d '\r')"
case "$build_id" in
  *SOP6*) ;;
  *) echo "unsupported vehicle build (SOP6 required): $build_id" >&2; exit 68 ;;
esac

owners="$(adb -s "$serial" shell dpm list-owners 2>/dev/null || adb -s "$serial" shell dumpsys device_policy)"
printf '%s\n' "$owners" | grep -q 'com.frodrigues.jconfig' || {
  echo "jconfig is not the current Device Owner; deploy blocked" >&2
  exit 70
}

certificate_sha256() {
  "$apksigner" verify --print-certs "$1" |
    awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print tolower($2); exit }'
}

assert_package() {
  local apk="$1"
  local expected="$2"
  if [ -x "$apkanalyzer" ]; then
    local actual
    actual="$("$apkanalyzer" manifest application-id "$apk")"
    [ "$actual" = "$expected" ] || {
      echo "unexpected package in $apk: $actual (expected $expected)" >&2
      exit 71
    }
  fi
}

assert_package "$jconfig_apk" "com.frodrigues.jconfig"
assert_package "$jstore_apk" "com.aurora.store"

jconfig_new_cert="$(certificate_sha256 "$jconfig_apk")"
jstore_new_cert="$(certificate_sha256 "$jstore_apk")"
[ -n "$jconfig_new_cert" ] && [ "$jconfig_new_cert" = "$jstore_new_cert" ] || {
  echo "new car APK certificates differ; deploy blocked" >&2
  exit 72
}

temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/jaecoo-predeploy.XXXXXX")"
trap 'rm -rf "$temporary_dir"' EXIT

compare_installed_certificate() {
  local package_name="$1"
  local expected_cert="$2"
  local remote_path
  remote_path="$(adb -s "$serial" shell pm path "$package_name" 2>/dev/null |
    sed -n 's/^package://p' | tr -d '\r' | head -n 1)"
  [ -n "$remote_path" ] || return 0
  local pulled_apk="$temporary_dir/${package_name}.apk"
  adb -s "$serial" pull "$remote_path" "$pulled_apk" >/dev/null
  local installed_cert
  installed_cert="$(certificate_sha256 "$pulled_apk")"
  [ "$installed_cert" = "$expected_cert" ] || {
    echo "$package_name certificate mismatch; deploy blocked without uninstall" >&2
    exit 73
  }
}

compare_installed_certificate "com.frodrigues.jconfig" "$jconfig_new_cert"
compare_installed_certificate "com.aurora.store" "$jstore_new_cert"

echo "predeploy gate passed"
echo "serial=$serial"
echo "build=$build_id"
echo "certificate_sha256=$jconfig_new_cert"
