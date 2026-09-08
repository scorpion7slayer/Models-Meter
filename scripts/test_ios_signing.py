import copy
import datetime
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("signing", Path(__file__).with_name("prepare-ios-signing.py"))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class AdHocProfileTests(unittest.TestCase):
    def setUp(self):
        self.now = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
        self.profile = {
            "UUID": "00000000-0000-4000-8000-000000000001",
            "TeamIdentifier": ["TESTTEAM01"],
            "ApplicationIdentifierPrefix": ["TESTTEAM01"],
            "ExpirationDate": datetime.datetime(2027, 1, 1),
            "ProvisionedDevices": ["test-device"],
            "DeveloperCertificates": [b"test-certificate"],
            "Entitlements": {
                "application-identifier": "TESTTEAM01." + signing.APP_ID,
                "get-task-allow": False,
                "com.apple.security.application-groups": [signing.GROUP_ID],
            },
        }

    def validate(self, profile):
        return signing.validate_profile(profile, signing.APP_ID, "TESTTEAM01", self.now)

    def test_registered_device_distribution_is_accepted(self):
        self.assertEqual(self.validate(self.profile), self.profile["UUID"])

    def test_wrong_team_or_bundle_is_rejected(self):
        for key, value in [("TeamIdentifier", ["OTHERTEAM1"]), ("ApplicationIdentifierPrefix", ["OTHERTEAM1"])]:
            profile = copy.deepcopy(self.profile)
            profile[key] = value
            with self.assertRaises(ValueError):
                self.validate(profile)
        profile = copy.deepcopy(self.profile)
        profile["Entitlements"]["application-identifier"] = "TESTTEAM01.*"
        with self.assertRaises(ValueError):
            self.validate(profile)

    def test_expired_profile_is_rejected(self):
        self.profile["ExpirationDate"] = self.now
        with self.assertRaises(ValueError):
            self.validate(self.profile)

    def test_store_and_enterprise_profiles_are_rejected(self):
        for changes in [{"ProvisionedDevices": []}, {"ProvisionsAllDevices": True}]:
            profile = dict(self.profile, **changes)
            with self.assertRaises(ValueError):
                self.validate(profile)

    def test_development_and_missing_app_group_are_rejected(self):
        for key, value in [("get-task-allow", True), ("com.apple.security.application-groups", [])]:
            profile = copy.deepcopy(self.profile)
            profile["Entitlements"][key] = value
            with self.assertRaises(ValueError):
                self.validate(profile)

    def test_invalid_uuid_is_rejected(self):
        self.profile["UUID"] = "../../unexpected"
        with self.assertRaises(ValueError):
            self.validate(self.profile)


if __name__ == "__main__":
    unittest.main()
