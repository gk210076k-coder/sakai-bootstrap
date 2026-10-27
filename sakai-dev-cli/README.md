# Sakai Developer CLI — CLI-001

Java CLI e orientuar **vetëm për ekosistemin ekzistues Sakai 27** (kernel, component manager, Maven multi-module, Sakai components, WAR tools, Tomcat). Nuk është server tjetër, Spring Boot app më vete, apo zëvendësim për Maven/Sakai runtime.

## Vendosja në workspace

Shpaketo dosjen `sakai-dev-cli-cli001` brenda:

```text
C:\GjergjKadriu\Documents\GitHub\sakai-bootstrap\devtools\sakai-dev-cli
```

`devtools/` dhe ky projekt **nuk janë shtuar automatikisht** në root reactor `pom.xml`; kjo është e qëllimshme në CLI-001 që të mos modifikojë baseline-in apo të provokojë ndërtim/deployment të paqëllimshëm. Ky është një mjet zhvillimi **për Sakai**, jo një runtime paralel.

## Ndërtimi në Windows PowerShell

```powershell
$repo = 'C:\GjergjKadriu\Documents\GitHub\sakai-bootstrap'
$cli = "$repo\devtools\sakai-dev-cli"
mvn -f "$cli\pom.xml" -DskipTests package
& "$cli\bin\sakai-dev.cmd" version
& "$cli\bin\sakai-dev.cmd" doctor --repo $repo
& "$cli\bin\sakai-dev.cmd" modules list --repo $repo --filter profile2
& "$cli\bin\sakai-dev.cmd" runtime status --base 'C:\GjergjKadriu\Documents\GitHub\sakai-framework-runtime-8082' --probe
& "$cli\bin\sakai-dev.cmd" feature plan notes --repo $repo
& "$cli\scripts\run-smoke-tests.ps1"
```

Mund të nisësh edhe JAR-in e përfshirë drejtpërdrejt:

```powershell
java -jar "$cli\target\sakai-dev-cli.jar" help
```

Java 17+ kërkohet për CLI, dhe JDK 21 i përdorur nga runtime-i yt është i përshtatshëm. Maven përdoret vetëm për ndërtimin e CLI-së; diagnostika `doctor` e gjen në PATH por nuk e ekzekuton.

## Komandat reale të CLI-001

- `version`: shfaq versionin.
- `doctor --repo`: zbulon Maven modules, component-manager, portal, library, webcomponents, suitën E2E, 8081, candidate dhe 8082 (nëse gjenden). Paralajmëron kur `/library` mungon.
- `modules list --repo --filter --limit`: lexon `pom.xml`, paketimin `war` / `sakai-component` / `jar`, dhe `deploy.target`; klasifikimet `SAKAI_TOOL_WAR`, `SPRING_COMPONENT`, `SHARED_API_JAR`, `STATIC_LIBRARY_WAR` janë heuristika për inventarizim, jo prova funksionimi.
- `runtime status --base [--probe]`: kontrollon arkivat dhe `server.xml`; `--probe` bën një GET **vetëm** në `127.0.0.1` për `/portal`, pa identitet sesioni.
- `feature plan notes --repo`: plan arkitekturor Sakai-native për module `api`, `impl`, `tool`, shërbime kernel dhe teste. **Nuk krijon kode** në CLI-001.

## Kontrata të Sakai-t për gjenerimin e features në CLI-002

1. `api`: ndërfaqet publike dhe modelet, JAR i përbashkët sipas modelit ekzistues.
2. `impl`: shërbimi zbaton biznesin, DAO/Repository zbaton persistencën; Spring beans në `WEB-INF/components.xml`. Paketimi `sakai-component`.
3. `tool`: WAR i lidhur me Sakai Portal, `WEB-INF/tools/...xml`, UI me teknologji të përshtatshme për versionin e Sakai-t. Nuk imponojmë Spring Boot si proces tjetër.
4. Identiteti e konteksti: `UserDirectoryService`, `SessionManager`, `ToolManager`, `SiteService`, `SecurityService`; kontrolli i lejeve në **server**, jo vetëm UI.
5. Testimi: testet e vërteta Spring (`SakaiTestConfiguration`), autorizim/sesione, Playwright për klikime/DOM, HTTP për CSS/JS, dhe prova reale në 8082.
6. Reuse first: mos gjenero autentikim, menaxhim sesionesh, cache, schedulers apo persistence frameworks të dytë nëse Sakai i ka.
7. CLI-002 do të zhvillojë implementim **të kontrolluar dhe të rishikueshëm**, jo gjenerim shabllonesh pa verifikim.

## Testet dhe kufizimet

- Paketë Java pa varësi runtime jashtë JDK-së; ndërtohet me `javac --release 17` dhe përmban Maven POM.
- Testi `CliSmoke` kontrollon parsing Maven, klasifikimin e moduleve, statusin Tomcat, nxjerrjen e planit, mospublikimin e fjalëkalimeve dhe refuzimin e XML external entities.
- CLI-001 **nuk** nis/ndal Tomcat, nuk bën deploy, nuk hyn në MySQL, nuk shkruan Sakai configuration, nuk përmban IDE agent, nuk implementon features, dhe nuk pretendon verifikim të click/DOM. Këto mbeten hapa të mëvonshëm.
- Ndryshimet në dosjen `target/` krijohen vetëm kur përdoruesi ndërton ose ekzekuton smoke tests në CLI. Kodi nuk e modifikon workspace-in e Sakai-t.

## Burime të hulumtuara

- [Sakai source install guide (Maven + Sakai deployment)](https://sakaiproject.atlassian.net/wiki/spaces/DOC/pages/17310646930)
- [Sakai application (tool) structure (historical reference)](https://sakaiproject.atlassian.net/wiki/spaces/BOOT/pages/996442235)
- [Sakai current developer policies (AGENTS.md)](https://github.com/sakaiproject/sakai/blob/master/AGENTS.md)
- Sakai 27 code conventions inspected in `sakai-bootstrap`: `pom.xml`, `commons/api/pom.xml`, `commons/impl/pom.xml`, `commons/tool/pom.xml`, `commons/impl/src/webapp/WEB-INF/components.xml`, and `e2e-tests`.

_Research note:_ The tool-structure wiki page is old; the current Sakai source tree and `AGENTS.md` are the stronger references for current implementation practice. Verify individual tech choices against this checkout, not the old page alone.
