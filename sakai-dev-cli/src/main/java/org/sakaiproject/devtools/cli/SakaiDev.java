package org.sakaiproject.devtools.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * CLI-001: read-only diagnostics tailored to the existing Sakai Maven/Component/Tomcat ecosystem.
 * This program does NOT embed Sakai, start/stop Tomcat, access MySQL, or change a deployment.
 */
public final class SakaiDev {
    static final String VERSION = "0.1.0 (CLI-001)";
    private SakaiDev() { }

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            if (exitCode != 0) System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws Exception {
        Args flags = Args.parse(args);
        if (flags.words.isEmpty() || flags.words.get(0).equals("help") || flags.has("help")) {
            usage();
            return 0;
        }
        String verb = flags.words.get(0);
        switch (verb) {
            case "version" -> System.out.println("sakai-dev " + VERSION + " | Sakai-aware developer diagnostics");
            case "doctor" -> doctor(flags);
            case "modules" -> {
                if (!flags.words.equals(List.of("modules", "list"))) throw new IllegalArgumentException("Try: modules list --repo PATH");
                modules(flags);
            }
            case "runtime" -> {
                if (!flags.words.equals(List.of("runtime", "status"))) throw new IllegalArgumentException("Try: runtime status --base PATH");
                if (!flags.has("base")) throw new IllegalArgumentException("runtime status requires --base PATH");
                runtimeStatus(Path.of(flags.get("base")), flags.has("probe"));
            }
            case "feature" -> {
                if (flags.words.size() != 3 || !flags.words.get(1).equals("plan")) throw new IllegalArgumentException("Try: feature plan notes --repo PATH");
                featurePlan(flags.words.get(2), flags);
            }
            default -> throw new IllegalArgumentException("Unknown command: " + verb + ". Try help");
        }
        return 0;
    }

    static void usage() {
        System.out.println("Sakai Developer CLI | " + VERSION);
        System.out.println("Commands (CLI-001 - read only):");
        System.out.println("  version");
        System.out.println("  doctor [--repo PATH] [--baseline PATH] [--candidate PATH] [--runtime PATH]");
        System.out.println("  modules list --repo PATH [--filter TEXT] [--limit N]");
        System.out.println("  runtime status --base PATH [--probe]");
        System.out.println("  feature plan NAME [--repo PATH]    (design guidance only; no generated files)");
        System.out.println("  help");
        System.out.println("No code generation, build, deployment, database access, or external writes in CLI-001.");
    }

    static Path repo(Args options) {
        Path location = Path.of(options.getOrDefault("repo", System.getProperty("user.dir"))).toAbsolutePath().normalize();
        if (!Files.isRegularFile(location.resolve("pom.xml")) || !Files.isRegularFile(location.resolve("master/pom.xml"))) {
            throw new IllegalArgumentException("Not a Sakai source root (expected pom.xml and master/pom.xml): " + location);
        }
        return location;
    }

    static void doctor(Args options) throws Exception {
        Path root = repo(options);
        System.out.println("=== Sakai developer doctor (read-only) ===");
        System.out.println("Source root: " + root);
        System.out.println("Java runtime: " + System.getProperty("java.version") + " (JDK >= 17 recommended for this CLI)");
        String maven = executableInPath("mvn.cmd", "mvn.bat", "mvn");
        System.out.println("Maven executable: " + (maven == null ? "NOT FOUND in PATH" : maven));
        System.out.println("Sakai master POM: " + present(root.resolve("master/pom.xml")));
        System.out.println("Kernel component manager: " + present(root.resolve("kernel/component-manager/pom.xml")));
        System.out.println("Portal: " + present(root.resolve("portal/pom.xml")));
        System.out.println("Static library: " + present(root.resolve("library/pom.xml")));
        System.out.println("Webcomponents: " + present(root.resolve("webcomponents/pom.xml")));
        System.out.println("Real Sakai E2E suite: " + present(root.resolve("e2e-tests/pom.xml")));
        List<SakaiModules.Module> found = SakaiModules.scan(root);
        System.out.println("Sakai Maven modules discovered: " + found.size());
        long components = found.stream().filter(m -> m.kind().equals("SPRING_COMPONENT")).count();
        long wars = found.stream().filter(m -> m.packaging().equals("war")).count();
        System.out.println("Component modules: " + components + " | WAR modules: " + wars);
        Path sibling = root.getParent();
        String baseline = options.getOrDefault("baseline", sibling.resolve("sakai-framework-runtime-8081").toString());
        String candidate = options.getOrDefault("candidate", sibling.resolve("sakai-framework-phase1/framework-phase2-candidate").toString());
        String runtime = options.getOrDefault("runtime", sibling.resolve("sakai-framework-runtime-8082").toString());
        System.out.println("-- Sakai deployment layout --");
        reportDeployment("Baseline 8081", Path.of(baseline));
        reportDeployment("Candidate", Path.of(candidate));
        reportDeployment("Experimental 8082", Path.of(runtime));
        System.out.println("No processes started or stopped. No database credentials read or printed.");
    }

    static void reportDeployment(String label, Path base) throws Exception {
        if (!Files.isDirectory(base)) {
            System.out.println(label + ": NOT FOUND (" + base + ")");
            return;
        }
        int warCount = countFiles(base.resolve("webapps"), ".war");
        int componentCount = countDirs(base.resolve("components"));
        boolean library = Files.isRegularFile(base.resolve("webapps/library.war")) || Files.isDirectory(base.resolve("webapps/library"));
        System.out.println(label + ": WARs=" + warCount + ", components=" + componentCount
                + ", /library=" + (library ? "PRESENT" : "MISSING") + " | " + base);
        if (warCount > 0 && !library) {
            System.out.println("  WARNING: /portal can return HTML while CSS and JavaScript requests to /library fail.");
        }
    }

    static int countFiles(Path directory, String extension) throws Exception {
        if (!Files.isDirectory(directory)) return 0;
        try (Stream<Path> stream = Files.list(directory)) {
            return (int) stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(extension)).count();
        }
    }

    static int countDirs(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) return 0;
        try (Stream<Path> stream = Files.list(directory)) {
            return (int) stream.filter(Files::isDirectory).count();
        }
    }

    private static String present(Path file) {
        return Files.isRegularFile(file) ? "OK" : "MISSING";
    }

    private static String executableInPath(String... names) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            if (dir.isBlank()) continue;
            for (String name : names) {
                try {
                    Path candidate = Path.of(dir, name);
                    if (Files.isRegularFile(candidate)) return candidate.toString();
                } catch (Exception ignored) { /* malformed PATH entry */ }
            }
        }
        return null;
    }

    static void modules(Args options) throws Exception {
        Path root = repo(options);
        String filter = options.getOrDefault("filter", "").toLowerCase(Locale.ROOT);
        int limit = Integer.parseInt(options.getOrDefault("limit", "50"));
        if (limit < 1 || limit > 2000) throw new IllegalArgumentException("--limit must be between 1 and 2000");
        List<SakaiModules.Module> all = SakaiModules.scan(root);
        List<SakaiModules.Module> matches = all.stream().filter(m -> (m.path() + " " + m.artifact() + " " + m.kind())
                .toLowerCase(Locale.ROOT).contains(filter)).toList();
        System.out.println("=== Sakai module inventory ===");
        System.out.println("Repository: " + root + " | found=" + all.size() + " | matching=" + matches.size());
        System.out.printf("%-45s %-32s %-18s %s%n", "PATH", "ARTIFACT", "PACKAGING", "ROLE");
        for (SakaiModules.Module m : matches.stream().limit(limit).toList()) {
            System.out.printf("%-45s %-32s %-18s %s%n", m.path(), m.artifact(), m.packaging(), m.kind());
        }
        if (matches.size() > limit) System.out.println("... " + (matches.size() - limit) + " additional modules; increase --limit");
    }

    static void runtimeStatus(Path base, boolean probe) throws Exception {
        base = base.toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) throw new IllegalArgumentException("Runtime does not exist: " + base);
        System.out.println("=== Sakai runtime status (read-only) ===");
        System.out.println("Base: " + base);
        reportDeployment("Runtime", base);
        Path serverPath = base.resolve("conf/server.xml");
        String httpPort = "?";
        if (Files.isRegularFile(serverPath)) {
            Document document = SafeXml.read(serverPath);
            Element root = document.getDocumentElement();
            System.out.println("Tomcat shutdown port: " + root.getAttribute("port"));
            NodeList connectors = document.getElementsByTagName("Connector");
            for (int i = 0; i < connectors.getLength(); i++) {
                Element connector = (Element) connectors.item(i);
                String port = connector.getAttribute("port");
                if (port.isBlank()) continue;
                System.out.println("Connector: port=" + port + ", address=" + connector.getAttribute("address")
                        + ", protocol=" + connector.getAttribute("protocol"));
                if (httpPort.equals("?") && !port.contains("$") && !port.equals("-1")
                        && (connector.getAttribute("protocol").isBlank() || connector.getAttribute("protocol").toLowerCase(Locale.ROOT).contains("http"))) {
                    httpPort = port;
                }
            }
        } else {
            System.out.println("WARNING: conf/server.xml not found");
        }
        Path properties = base.resolve("sakai/sakai.properties");
        System.out.println("Sakai properties: " + present(properties) + " (values never printed)");
        if (probe) {
            if (!httpPort.matches("[0-9]{2,5}")) throw new IllegalArgumentException("Cannot infer an HTTP connector for local probe");
            URI uri = URI.create("http://127.0.0.1:" + httpPort + "/portal");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(5)).build();
            try {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                System.out.println("Portal probe (loopback only): HTTP " + response.statusCode());
                if (response.statusCode() == 200) System.out.println("NOTE: HTML 200 does not prove assets or JavaScript interactions are working.");
            } catch (Exception e) {
                System.out.println("Portal probe (loopback only): FAILED (" + e.getClass().getSimpleName() + ")");
            }
        }
    }

    static void featurePlan(String rawName, Args options) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z][a-z0-9-]{1,39}")) throw new IllegalArgumentException("Feature name must be 2-40 lowercase letters, digits, or hyphens");
        Path source = repo(options);
        System.out.println("=== Sakai native feature plan: " + name + " (advisory only) ===");
        System.out.println("Sakai source: " + source);
        System.out.println("1. " + name + "/pom.xml         : Maven module aggregator, register through reviewed reactor changes");
        System.out.println("2. " + name + "/api             : Java interfaces / DTO / model; shared API JAR");
        System.out.println("3. " + name + "/impl            : Sakai service/DAO implementations; packaging sakai-component");
        System.out.println("   Register Spring beans under impl/src/webapp/WEB-INF/components.xml");
        System.out.println("4. " + name + "/tool            : Sakai-integrated UI (WAR), tool registration XML under WEB-INF/tools");
        System.out.println("   Prefer existing Sakai UI conventions; use native browser JS/DOM for new interactions");
        System.out.println("5. Security             : reuse UserDirectoryService, SessionManager, SecurityService, ToolManager");
        System.out.println("   Never trust a client-supplied user/site id; authorize server-side for every write");
        System.out.println("6. Transactions         : let the service define transactional behavior; DAO handles persistence");
        System.out.println("7. Tests                : real Spring wiring + SakaiTestConfiguration; Playwright E2E for clicks/DOM");
        System.out.println("8. Deployment           : build/test modules via Maven and deploy only to isolated 8082 after review");
        System.out.println("Not implemented: scaffolding, builds, Spring wiring changes, migrations, tests, deployment.");
    }

    static final class Args {
        final List<String> words = new ArrayList<>();
        final Map<String, String> flags = new HashMap<>();
        static Args parse(String[] args) {
            Args result = new Args();
            Set<String> valueFlags = Set.of("repo", "base", "baseline", "candidate", "runtime", "filter", "limit");
            Set<String> booleanFlags = Set.of("probe", "help");
            for (int i = 0; i < args.length; i++) {
                String item = args[i];
                if (!item.startsWith("--")) {
                    result.words.add(item);
                    continue;
                }
                String key = item.substring(2);
                if (valueFlags.contains(key)) {
                    if (++i >= args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key);
                    result.flags.put(key, args[i]);
                } else if (booleanFlags.contains(key)) {
                    result.flags.put(key, "true");
                } else throw new IllegalArgumentException("Unknown flag: " + item);
            }
            return result;
        }
        boolean has(String key) { return flags.containsKey(key); }
        String get(String key) { return flags.get(key); }
        String getOrDefault(String key, String other) { return flags.getOrDefault(key, other); }
    }
}
