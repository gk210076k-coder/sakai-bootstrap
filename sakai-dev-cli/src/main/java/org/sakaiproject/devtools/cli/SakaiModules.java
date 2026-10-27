package org.sakaiproject.devtools.cli;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Reads Sakai POM packaging and deployment-target conventions, without modifying the reactor. */
final class SakaiModules {
    record Module(String path, String artifact, String packaging, String deployTarget, String kind) { }

    static List<Module> scan(Path root) throws IOException {
        List<Module> found = new ArrayList<>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) {
                    String name = dir.getFileName().toString();
                    if (Set.of(".git", ".mvn", ".idea", "target", "node_modules", "build", "dist", "webapps", "components").contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().equals("pom.xml")) return FileVisitResult.CONTINUE;
                try {
                    Document document = SafeXml.read(file);
                    Element project = document.getDocumentElement();
                    if (!"project".equals(project.getLocalName())) return FileVisitResult.CONTINUE;
                    String relative = root.relativize(file.getParent()).toString().replace('\\', '/');
                    if (relative.isEmpty()) relative = ".";
                    String artifact = SafeXml.text(project, "artifactId");
                    String packaging = SafeXml.text(project, "packaging");
                    if (packaging.isBlank()) packaging = "jar";
                    String target = SafeXml.text(SafeXml.child(project, "properties"), "deploy.target");
                    found.add(new Module(relative, artifact, packaging, target, classify(relative, packaging, target)));
                } catch (Exception e) {
                    found.add(new Module(root.relativize(file.getParent()).toString().replace('\\', '/'),
                            "[unparseable: " + e.getClass().getSimpleName() + "]", "?", "?", "INVALID_POM"));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        found.sort(Comparator.comparing(Module::path));
        return found;
    }

    private static String classify(String path, String packaging, String target) {
        if ("sakai-component".equals(packaging) || "components".equals(target)) return "SPRING_COMPONENT";
        if ("war".equals(packaging) && path.equals("library")) return "STATIC_LIBRARY_WAR";
        if ("war".equals(packaging) && path.startsWith("portal")) return "PORTAL_WAR";
        if ("war".equals(packaging) && path.startsWith("webcomponents")) return "WEB_COMPONENTS_WAR";
        if ("war".equals(packaging)) return "SAKAI_TOOL_WAR";
        if ("jar".equals(packaging) && ("shared".equals(target) || path.matches("(^|.*/)api$"))) return "SHARED_API_JAR";
        if ("jar".equals(packaging)) return "JAR_MODULE";
        if ("pom".equals(packaging)) return "MAVEN_REACTOR";
        return "OTHER";
    }
}
