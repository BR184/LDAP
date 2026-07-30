import unittest

from scripts import verify_part_time_departments_api as verifier


class PartTimeDepartmentContractTest(unittest.TestCase):
    def test_detects_legacy_array_contract_without_structured_field(self) -> None:
        users = [
            {
                "userId": "legacy-user",
                "partTimeDeptCodes": ["FD_A", "FD_B"],
                "partTimeDeptNames": ["研发部", "财务部"],
            }
        ]

        self.assertEqual(verifier.detect_contract(users), verifier.LEGACY_CONTRACT)
        self.assertEqual(
            verifier.validate_part_time_departments(users, verifier.LEGACY_CONTRACT),
            [],
        )
        count, details, details_truncated = verifier.build_part_time_department_details(
            users, verifier.LEGACY_CONTRACT, 0
        )
        self.assertEqual(count, 1)
        self.assertFalse(details_truncated)
        self.assertNotIn("partTimeDepartments", details[0])

    def test_detects_extended_contract_and_validates_object_order(self) -> None:
        users = [
            {
                "userId": "extended-user",
                "partTimeDeptCodes": ["FD_A", "FD_B"],
                "partTimeDeptNames": ["研发部", "财务部"],
                "partTimeDepartments": [
                    {"deptCode": "FD_A", "deptName": "研发部"},
                    {"deptCode": "FD_B", "deptName": "财务部"},
                ],
            }
        ]

        self.assertEqual(verifier.detect_contract(users), verifier.EXTENDED_CONTRACT)
        self.assertEqual(
            verifier.validate_part_time_departments(users, verifier.EXTENDED_CONTRACT),
            [],
        )

    def test_rejects_mixed_contracts_in_one_response(self) -> None:
        users = [
            {
                "userId": "legacy-user",
                "partTimeDeptCodes": ["FD_A"],
                "partTimeDeptNames": ["研发部"],
            },
            {
                "userId": "extended-user",
                "partTimeDeptCodes": ["FD_B"],
                "partTimeDeptNames": ["财务部"],
                "partTimeDepartments": [{"deptCode": "FD_B", "deptName": "财务部"}],
            },
        ]

        self.assertEqual(verifier.detect_contract(users), verifier.MIXED_CONTRACT)


if __name__ == "__main__":
    unittest.main()
