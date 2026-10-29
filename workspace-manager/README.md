# WM01 — Workspace Dashboard (Sakai Bootstrap)

A **read-only** Sakai Tool that uses Sakai's own `SiteService` and current session to list authorized project/application sites. No site creation, invitations, role edits, database migrations, or LMS module dependencies.

## Install in source

Overlay `workspace-manager/` into the root of `sakai-bootstrap` (`framework/phase1` recommended). Add `<module>workspace-manager</module>` inside the root `pom.xml` `all` profile (see `add-module.ps1` in the release root). Keep the full Sakai source modules and the reduced deployment completely separate.

## Build (on the Windows machine with Maven and JDK 21)

```powershell
mvn -f .\workspace-manager\pom.xml -U test package
```

This command requires parent POM and existing Sakai artifacts from a successful source build in the local Maven repository. The WAR will be under the Maven build directory configured by `master/pom.xml` (usually `workspace-manager/target/`). Its artifact name is `sakai-bootstrap-workspace-manager-27-SNAPSHOT.war` by default.

**Do not hot-copy a WAR into the running 8082 deployment.** First inspect the build/test results, stop and back up that isolated runtime, then explicitly deploy the renamed WAR as `workspace-manager.war` through the established Sakai deployment process; restart and test. Do not overwrite the 8081 instance.

## Navigation

Registration: `sakai.bootstrap.workspaces` (categories `myworkspace`, `project`). Attach the registered tool to the appropriate home/project site through an administrator-authorized Sakai placement process. It can then be loaded from the portal. Do not assume a WAR copy automatically adds a portal menu item.

## Security

- No userId/siteId request input is accepted.
- Missing Sakai user session yields HTTP 401.
- `getUserSites(false, true)` is restricted to the *current* session.
- `allowAccessSite(siteId)` is checked for every record.
- `course` and personal home sites are excluded from the workspace listing.
- Presentation uses escaped `th:text` attributes; site IDs are URL path-segment encoded.
- State refers to Sakai `published`, NOT the proposed WM02 lifecycle/visibility.
- No mutations, mock data, or sample workspace records are included.

## Limitations / next milestones

- `application` is recognized as a display category, but actually provisioning this new site type is deferred. WM02 should use a backed `project` type with approved template/metadata initially.
- Invitation UI and permission mutation are **not** implemented (WM03/WM04).
- UI strings are currently English; integrate with Sakai `LocaleService` and message bundles for localization before production release.
- Site listing uses existing Sakai current-user selection; an explicit framework-only marker can be introduced once WM02 provisions workspaces.
- The service tests mock only external Sakai kernel boundaries; full application-server integration, authorization-negative regression, UI E2E, and Tomcat 8082 smoke tests **must** be run before production deployment.
