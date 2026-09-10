#!/usr/bin/env python3
"""Validate Ad Hoc material and install it on an ephemeral macOS Actions runner."""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent
APP_ID = "dev.scorpion7slayer.modelsmeter"
WIDGET_ID = APP_ID + ".Widgets"
GROUP_ID = "group." + APP_ID


def validate_profile(profile, bundle_id, team, now=None):
    """Reject wrong, expired, development, App Store and Enterprise profiles."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    expiry = profile.get("ExpirationDate")
    if not isinstance(expiry, datetime.datetime):
        raise ValueError("Profile has no expiration date")
    if expiry.tzinfo is None:
        expiry = expiry.replace(tzinfo=datetime.timezone.utc)
    if expiry <= now:
        raise ValueError("Provisioning profile has expired; regenerate it in Apple Developer")
    if profile.get("TeamIdentifier") != [team]:
        raise ValueError("Provisioning profile does not belong to IOS_TEAM_ID")
    entitlements = profile.get("Entitlements", {})
    prefixes = profile.get("ApplicationIdentifierPrefix", [])
    if len(prefixes) != 1 or entitlements.get("application-identifier") != prefixes[0] + "." + bundle_id:
        raise ValueError("Provisioning profile does not match the exact app/widget bundle ID")
    if entitlements.get("get-task-allow") is not False:
        raise ValueError("Use an Ad Hoc distribution profile, not a development profile")
    devices = profile.get("ProvisionedDevices", [])
    if not devices or not all(isinstance(device, str) and device for device in devices) or profile.get("ProvisionsAllDevices"):
        raise ValueError("Use an Ad Hoc profile containing registered devices")
    if GROUP_ID not in entitlements.get("com.apple.security.application-groups", []):
        raise ValueError("Enable the Models Meter App Group for both profiles")
    if not profile.get("DeveloperCertificates"):
        raise ValueError("Provisioning profile contains no signing certificate")
    return str(uuid.UUID(profile["UUID"])).upper()


def release_version(root=ROOT):
    gradle = (root / "android/app/build.gradle.kts").read_text()
    version = re.search(r'versionName = "([0-9A-Za-z.-]+)"', gradle).group(1)
    build = re.search(r"versionCode = (\d+)", gradle).group(1)
    project = (root / "ios/ModelsMeter.xcodeproj/project.pbxproj").read_text()
    blocks = re.findall(r"buildSettings = \{(.*?)\n\t\t\t\};", project, re.S)
    matched = 0
    for block in blocks:
        if not any("PRODUCT_BUNDLE_IDENTIFIER = " + name + ";" in block for name in [APP_ID, WIDGET_ID]):
            continue
        matched += 1
        if "MARKETING_VERSION = " + version.split("-")[0] + ";" not in block or "CURRENT_PROJECT_VERSION = " + build + ";" not in block:
            raise ValueError("iOS app/widget versions must match Android before archiving")
    if matched != 4:
        raise ValueError("Expected Debug and Release versions for both iOS targets")
    if os.environ.get("GITHUB_REF_TYPE") == "tag" and os.environ.get("GITHUB_REF_NAME") != "v" + version:
        raise ValueError("Tag must match the application version")
    return version


def profile_directories():
    return [Path.home() / "Library/Developer/Xcode/UserData/Provisioning Profiles",
            Path.home() / "Library/MobileDevice/Provisioning Profiles"]


def cleanup(directory):
    manifest = directory / "installed-profiles.json"
    if manifest.exists():
        for identifier in json.loads(manifest.read_text()):
            identifier = str(uuid.UUID(identifier)).upper()
            for destination in profile_directories():
                (destination / (identifier + ".mobileprovision")).unlink(missing_ok=True)


def prepare():
    version = release_version()
    names = ["IOS_CERTIFICATE_P12_BASE64", "IOS_CERTIFICATE_PASSWORD", "IOS_APP_PROFILE_BASE64", "IOS_WIDGET_PROFILE_BASE64", "IOS_TEAM_ID"]
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise ValueError("Configure GitHub Actions secrets/variables: " + ", ".join(missing))
    team = os.environ["IOS_TEAM_ID"]
    if not re.fullmatch(r"[A-Z0-9]{10}", team):
        raise ValueError("IOS_TEAM_ID must be your 10-character Apple team identifier")
    os.umask(0o077)
    directory = Path(os.environ["RUNNER_TEMP"]) / "models-meter-signing"
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    (directory / "distribution.p12").write_bytes(base64.b64decode(os.environ["IOS_CERTIFICATE_P12_BASE64"], validate=True))
    profiles = []
    for label, bundle, secret in [("app", APP_ID, "IOS_APP_PROFILE_BASE64"), ("widget", WIDGET_ID, "IOS_WIDGET_PROFILE_BASE64")]:
        path = directory / (label + ".mobileprovision")
        path.write_bytes(base64.b64decode(os.environ[secret], validate=True))
        decoded = subprocess.run(["security", "cms", "-D", "-i", str(path)], capture_output=True, check=True)
        profile = plistlib.loads(decoded.stdout)
        identifier = validate_profile(profile, bundle, team)
        profiles.append((identifier, profile, path, bundle))
    if set(profiles[0][1]["ProvisionedDevices"]) != set(profiles[1][1]["ProvisionedDevices"]):
        raise ValueError("App and widget profiles must contain the same registered devices")
    if not set(profiles[0][1]["DeveloperCertificates"]) & set(profiles[1][1]["DeveloperCertificates"]):
        raise ValueError("App and widget profiles must share the distribution certificate")
    (directory / "installed-profiles.json").write_text(json.dumps([item[0] for item in profiles]))
    for identifier, _, path, _ in profiles:
        for destination in profile_directories():
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(path, destination / (identifier + ".mobileprovision"))
    options = {"method": "release-testing", "destination": "export", "teamID": team,
               "signingStyle": "manual", "signingCertificate": "Apple Distribution",
               "provisioningProfiles": {bundle: profile["Name"] for _, profile, _, bundle in profiles},
               "manageAppVersionAndBuildNumber": False, "thinning": "<none>"}
    (directory / "ExportOptions.plist").write_bytes(plistlib.dumps(options))
    with open(os.environ["GITHUB_ENV"], "a") as output:
        output.write("APP_PROVISIONING_PROFILE=" + profiles[0][1]["Name"] + "\n")
        output.write("WIDGET_PROVISIONING_PROFILE=" + profiles[1][1]["Name"] + "\n")
        output.write("IOS_VERSION=" + version + "\n")
    print("Validated Ad Hoc profiles for the app and widget; device identifiers are not logged.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cleanup", action="store_true")
    parser.add_argument("--validate-version", action="store_true")
    args = parser.parse_args()
    try:
        if args.cleanup:
            cleanup(Path(os.environ["RUNNER_TEMP"]) / "models-meter-signing")
        elif args.validate_version:
            print(release_version())
        else:
            prepare()
    except (ValueError, KeyError, subprocess.CalledProcessError) as error:
        # Never dump CMS contents, credentials, or device identifiers into CI logs.
        message = str(error) if isinstance(error, ValueError) else "Unable to decode or install Apple signing material"
        raise SystemExit("::error::" + message)
