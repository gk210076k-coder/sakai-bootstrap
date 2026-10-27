# Sakai-native CLI roadmap

## CLI-001 (implemented and tested)
- Source and Tomcat inventory: `doctor`, `modules list`, `runtime status`
- Feature architecture advice: `feature plan`
- No mutation of source/deployments; targeted local HTTP probe only
- No dependency on an additional application runtime

## CLI-002 (next, not implemented)
- Sakai reactor-aware feature planner: read POM inheritance, module graph and Spring XML
- Check service and interface reuse against Sakai kernel + existing modules
- Generate reviewable `api/impl/tool` changes and tests in a **new branch/worktree**, require `--apply` and `--target`, ensure rollback
- No default mutations to 8080/8081

## CLI-003 (not implemented)
- Spring `@Autowired`, `@Qualifier`, XML/annotation graph checks and optional-bean warnings
- WAR CSS/JS URL contract check and deployment module presence
- Isolated local browser interaction suite integrated with Sakai e2e-tests (Playwright)
- Session tests, real DOM mutations, role and permission checks
- Persist result manifests (no secrets) and compare with baseline 8081

## CLI-004+ (not implemented)
- Maven build/test orchestration with opt-in commands
- Controlled deployment only to 8082 with artifact checksums and rollback
- Feature-specific commands for migration generation using the project's established patterns
- Administrator dashboard optional, gated by real Sakai permissions
