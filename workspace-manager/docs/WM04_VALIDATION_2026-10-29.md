# Sakai Bootstrap â€” WM04 validation (29 October 2026)

WM01 Workspace Dashboard, WM02 Workspace creation, WM03 native Sakai membership,
and WM04 private Workspace Invitations are maintained under `workspace-manager/`.

## Validation evidence (isolated Tomcat 8082 / MySQL 8)
- On 2026-10-29, local `mvn clean test package` succeeded: **54 tests, 0 failures, 0 errors, 0 skipped**.
- WM04 schema `SAKAI_BOOTSTRAP_INVITATION` installed successfully.
- A pending invitation persisted in MySQL; acceptance became `ACCEPTED`.
- Correct recipient gained an active native Sakai realm membership with role `access`.
- Wrong-user request was denied (HTTP 403), pending invitation unchanged.
- Declined invitation became `DECLINED` without creating native membership.
- Revoked invitation became `REVOKED` without creating native membership.
- Accept after revoke (from a previously opened form) was rejected HTTP 400.
- Decline after accept (from a previously opened form) was rejected HTTP 400;
  native membership remained active `access` and invitation stayed `ACCEPTED`.
- Re-opening an accepted invitation shows that it is no longer available.
- **A distinct second POST Accept after Accept was not independently recorded.**

## Release integrity
- Private archival ZIP created locally: `WM04-release-20261029-184336.zip`.
- Private ZIP SHA-256: `B17EDD0DCCB35321ABB15E42E26348EA1D179C991FE34B6B5AE5C2FFCF4A6F07`.
- Do not commit private ZIP, WAR binaries, database dumps, personal credentials,
  `sakai.properties`, session cookies, or invitation tokens to GitHub.

## Next milestone
WM05 â€” Application Catalog and secure Java WAR installation/activation per Workspace.
Preserve WM03 protected principals, site permissions, native realm authority, and
WM04 tokens, validation and expiration rules.
