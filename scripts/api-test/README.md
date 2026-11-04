# API test

End-to-end HTTP test of every ChickTrack endpoint. It signs in each role through OAuth2 + PKCE
(the same flow as the mobile app), creates its own branches, users and documents (names carry a
random suffix), and checks roles, branch access, calculations, idempotency and error codes.

Run it against a **local or test** database only: it creates data and users.

```bash
pip install requests
# API running on http://localhost:8090 with a bootstrap admin
CT_ADMIN_PASSWORD='your-admin-password' python3 scripts/api-test/api_test.py
```

Options: `CT_BASE` (default `http://localhost:8090`), `CT_ADMIN_USERNAME` (default `admin`).

Prints one line per check and a summary; exits non-zero if any check fails. Results are also
written to `api-test-results.json`.

Some amount checks assume a fresh database (no other margin rules or prices for the test's
chicken types, which are created by the run itself).
