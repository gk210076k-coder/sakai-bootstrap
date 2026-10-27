package org.sakaiproject.devtools.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

/** Small dependency-free smoke suite; run via scripts/run-smoke-tests.sh or .ps1. */
public final class CliSmoke {
    static int assertions = 0;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sakai-dev-cli-test-");
        try {
            put(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>base</artifactId><packaging>pom</packaging></project>
                """);
            put(root.resolve("master/pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>master</artifactId><packaging>pom</packaging></project>
                """);
            put(root.resolve("sample/api/pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>sample-api</artifactId><properties><deploy.target>shared</deploy.target></properties></project>
                """);
            put(root.resolve("sample/impl/pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>sample-impl</artifactId><packaging>sakai-component</packaging></project>
                """);
            put(root.resolve("sample/tool/pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>sample-tool</artifactId><packaging>war</packaging></project>
                """);
            put(root.resolve("library/pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>library</artifactId><packaging>war</packaging></project>
                """);
            Path runtime = root.resolve("runtime");
            put(runtime.resolve("conf/server.xml"), """
                <Server port="8007"><Service name="Catalina"><Connector port="8082" protocol="HTTP/1.1" address="127.0.0.1" /></Service></Server>
                """);
            Files.createDirectories(runtime.resolve("components/a-component"));
            Files.createDirectories(runtime.resolve("webapps"));
            put(runtime.resolve("webapps/portal.war"), "fixture");
            put(runtime.resolve("webapps/library.war"), "fixture");
            put(runtime.resolve("sakai/sakai.properties"), "password@javax.sql.BaseDataSource=NEVER_PRINT_THIS_VALUE");

            List<SakaiModules.Module> modules = SakaiModules.scan(root);
            require(modules.stream().anyMatch(m -> m.artifact().equals("sample-impl") && m.kind().equals("SPRING_COMPONENT")), "impl detected");
            require(modules.stream().anyMatch(m -> m.artifact().equals("sample-api") && m.kind().equals("SHARED_API_JAR")), "api detected");
            require(modules.stream().anyMatch(m -> m.artifact().equals("sample-tool") && m.kind().equals("SAKAI_TOOL_WAR")), "tool detected");
            require(modules.stream().anyMatch(m -> m.artifact().equals("library") && m.kind().equals("STATIC_LIBRARY_WAR")), "library detected");
            require(SakaiDev.Args.parse(new String[]{"modules", "list", "--filter", "sample"}).get("filter").equals("sample"), "flag parser");
            String modOut = capture(() -> SakaiDev.run(new String[]{"modules", "list", "--repo", root.toString(), "--filter", "sample"}));
            require(modOut.contains("SPRING_COMPONENT") && modOut.contains("SAKAI_TOOL_WAR"), "module output");
            String status = capture(() -> SakaiDev.run(new String[]{"runtime", "status", "--base", runtime.toString()}));
            require(status.contains("8082") && status.contains("/library=PRESENT"), "runtime output");
            require(!status.contains("NEVER_PRINT_THIS_VALUE"), "secret redaction");
            String plan = capture(() -> SakaiDev.run(new String[]{"feature", "plan", "notes", "--repo", root.toString()}));
            require(plan.contains("SecurityService") && plan.contains("sakai-component") && plan.contains("advisory"), "feature plan");
            boolean blocked = false;
            try { SafeXml.read(write(root.resolve("bad.xml"), "<!DOCTYPE d [ <!ENTITY leak SYSTEM 'file:///etc/passwd'> ]><d>&leak;</d>")); }
            catch (Exception e) { blocked = true; }
            require(blocked, "external entities blocked");
            System.out.println("CLI-001 smoke tests: PASS (" + assertions + " assertions)");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static Path write(Path path, String content) throws Exception {
        put(path, content);
        return path;
    }
    private static void put(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
    private static void require(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError("FAIL: " + message);
    }
    private static String capture(Checked action) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream old = System.out;
        try (PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            action.run();
        } finally {
            System.setOut(old);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
    private interface Checked { void run() throws Exception; }
}
