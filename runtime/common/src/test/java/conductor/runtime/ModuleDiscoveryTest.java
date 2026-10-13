package conductor.runtime;

import conductor.contract.ConductorModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Modules are plain jars with a service entry: found without any mod loader. */
class ModuleDiscoveryTest {

    @TempDir
    Path tmp;

    /** Compiles a one-class module against the contract and packs it the way a module author would. */
    private Path moduleJar(String name, String pkg, String serviceLine) throws IOException {
        Path src = tmp.resolve("src-" + name).resolve(pkg.replace('.', '/'));
        Files.createDirectories(src);
        Path file = src.resolve(name + ".java");
        Files.writeString(file, "package " + pkg + ";\n"
                + "public final class " + name + " implements conductor.contract.ConductorModule {\n"
                + "  public conductor.contract.ReleasePolicy releasePolicy() { return conductor.contract.ReleasePolicy.none(); }\n"
                + "  public void onReady(conductor.contract.ModuleContext c) {}\n"
                + "}\n");
        Path classes = tmp.resolve("classes-" + name);
        Files.createDirectories(classes);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        String cp = System.getProperty("java.class.path");
        int rc = javac.run(null, null, null, "-cp", cp, "-d", classes.toString(), file.toString());
        assertEquals(0, rc, "test module compiles");
        Path jar = tmp.resolve("modules").resolve(name.toLowerCase() + ".jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            String classFile = pkg.replace('.', '/') + "/" + name + ".class";
            out.putNextEntry(new JarEntry(classFile));
            Files.copy(classes.resolve(classFile), out);
            out.putNextEntry(new JarEntry(ModuleDiscovery.SERVICE_FILE));
            out.write((serviceLine + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    @Test
    void plainJarsInTheModulesFolderAreFoundWithoutALoader() throws IOException {
        moduleJar("Alpha", "test.a", "test.a.Alpha");
        moduleJar("Beta", "test.b", "test.b.Beta");
        ModuleDiscovery.Found found = ModuleDiscovery.discover(tmp.resolve("modules"), getClass().getClassLoader());
        assertEquals(java.util.List.of("alpha", "beta"), java.util.List.copyOf(found.modules().keySet()));
        assertEquals("test.a.Alpha", found.modules().get("alpha").getClass().getName());
        assertTrue(found.problems().isEmpty(), found.problems()::toString);
        // the module's own class loader, but the contract it implements is the running Conductor's
        ConductorModule alpha = found.modules().get("alpha");
        assertTrue(alpha instanceof ConductorModule);
    }

    @Test
    void aBrokenEntryIsReportedAndTheOthersStillLoad() throws IOException {
        moduleJar("Good", "test.g", "test.g.Good");
        moduleJar("Bad", "test.x", "test.x.DoesNotExist");
        ModuleDiscovery.Found found = ModuleDiscovery.discover(tmp.resolve("modules"), getClass().getClassLoader());
        assertEquals(java.util.Set.of("good"), found.modules().keySet());
        assertEquals(1, found.problems().size(), found.problems()::toString);
    }

    @Test
    void aMissingFolderIsNotAnError() {
        ModuleDiscovery.Found found = ModuleDiscovery.discover(tmp.resolve("nope"), null);
        assertTrue(found.modules().isEmpty());
        assertTrue(found.problems().isEmpty());
    }
}
