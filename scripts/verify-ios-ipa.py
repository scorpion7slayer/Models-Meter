#!/usr/bin/env python3
"""Verify the exported app, extension, signatures and embedded Ad Hoc profiles."""
import importlib.util
from pathlib import Path
import plistlib
import subprocess
import sys
import zipfile

spec = importlib.util.spec_from_file_location("signing", Path(__file__).with_name("prepare-ios-signing.py"))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


def verify(ipa, output, team, version):
    with zipfile.ZipFile(ipa) as archive:
        for item in archive.namelist():
            if item.startswith("/") or ".." in Path(item).parts:
                raise ValueError("Unexpected path in IPA")
    subprocess.run(["ditto", "-x", "-k", str(ipa), str(output)], check=True)
    apps = list((output / "Payload").glob("*.app"))
    if len(apps) != 1:
        raise ValueError("IPA must contain exactly one app")
    widgets = list((apps[0] / "PlugIns").glob("*.appex"))
    if len(widgets) != 1:
        raise ValueError("IPA must contain the widget extension")
    builds = []
    device_sets = []
    for bundle, identifier in [(apps[0], signing.APP_ID), (widgets[0], signing.WIDGET_ID)]:
        subprocess.run(["codesign", "--verify", "--deep", "--strict", str(bundle)], check=True)
        info = plistlib.loads((bundle / "Info.plist").read_bytes())
        if info["CFBundleIdentifier"] != identifier or info["CFBundleShortVersionString"] != version.split("-")[0]:
            raise ValueError("Exported bundle ID or version is incorrect")
        builds.append(info["CFBundleVersion"])
        raw = subprocess.run(["security", "cms", "-D", "-i", str(bundle / "embedded.mobileprovision")], capture_output=True, check=True)
        profile = plistlib.loads(raw.stdout)
        signing.validate_profile(profile, identifier, team)
        device_sets.append(set(profile["ProvisionedDevices"]))
        raw = subprocess.run(["codesign", "-d", "--entitlements", ":-", str(bundle)], capture_output=True, check=True)
        entitlements = plistlib.loads(raw.stdout)
        if entitlements.get("com.apple.developer.team-identifier") != team or entitlements.get("get-task-allow", False):
            raise ValueError("Exported app must use the requested distribution team")
        if signing.GROUP_ID not in entitlements.get("com.apple.security.application-groups", []):
            raise ValueError("Exported app/widget is missing the shared App Group")
    if len(set(builds)) != 1 or device_sets[0] != device_sets[1]:
        raise ValueError("App and widget must share their build number and registered devices")
    print("Verified signed iOS app and widget: " + version + " (build " + builds[0] + ")")


if __name__ == "__main__":
    verify(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], sys.argv[4])
