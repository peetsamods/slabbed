package com.slabbed.util;

import java.net.URI;
import java.net.URL;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Jar-identity stamp, read once from this jar's own MANIFEST.MF (attributes {@code Slabbed-Git-Sha} and
 * {@code Slabbed-Build-Time}, written by the {@code jar} task in build.gradle).
 *
 * <p>Why this exists (anti-whack-a-mole audit, 2026-07-06): a built jar could not be traced to its
 * commit from its own bytes — two different artifacts have already shipped self-identifying as the same
 * fabric.mod.json version, and one full live sweep on the 1.21.11 line was analyzed against a jar built
 * AFTER the session it supposedly produced. Recorder manifests and {@code /slabdev record} feedback
 * carry the descriptive Git/jar stamp; exact-evidence consumers must additionally bind
 * {@link #RUNTIME_CONTENT_SHA256}, because {@code unknown} dev launches and multiple dirty builds at
 * one HEAD can share the descriptive label.
 *
 * <p>Static-final caching is deliberate and safe here (unlike runtime debug flags, which must stay
 * live-readable): a process's jar identity cannot change after launch. All lookups are best-effort and
 * exception-proof — identity metadata must never affect startup. In a dev launch (classes directory,
 * no jar manifest) the values read {@code dev-classes}/{@code unknown}.
 */
public final class BuildStamp {
    private static final String UNKNOWN = "unknown";

    public static final String GIT_SHA;
    public static final String BUILD_TIME;
    public static final String JAR_FILE;
    /** Exact jar bytes, or a path-independent digest of the dev main classes/resources tree. */
    public static final String RUNTIME_CONTENT_SHA256;

    static {
        String sha = UNKNOWN;
        String time = UNKNOWN;
        String jarFile = "dev-classes";
        String runtimeContentSha256 = "unavailable";
        try {
            URL location = BuildStamp.class.getProtectionDomain().getCodeSource() == null
                    ? null
                    : BuildStamp.class.getProtectionDomain().getCodeSource().getLocation();
            Path jarPath = toLocalJarPath(location);
            if (jarPath == null && location != null && location.toString().contains(".jar")) {
                // A jar-shaped code source we could not resolve — do NOT mislabel it as a dev launch
                // ("dev-classes" conflating 'dev' with 'resolution failed' would hide a real gap).
                jarFile = "unresolved-code-source";
            }
            if (jarPath != null) {
                jarFile = jarPath.getFileName() == null ? jarPath.toString() : jarPath.getFileName().toString();
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Manifest manifest = jar.getManifest();
                    if (manifest != null) {
                        String stampedSha = manifest.getMainAttributes().getValue("Slabbed-Git-Sha");
                        String stampedTime = manifest.getMainAttributes().getValue("Slabbed-Build-Time");
                        if (stampedSha != null && !stampedSha.isBlank()) {
                            sha = stampedSha;
                        }
                        if (stampedTime != null && !stampedTime.isBlank()) {
                            time = stampedTime;
                        }
                    }
                }
            }
            runtimeContentSha256 = runtimeContentSha256(location, jarPath);
        } catch (Throwable ignored) {
            // Identity is best-effort; never let it interfere with mod init.
        }
        GIT_SHA = sha;
        BUILD_TIME = time;
        JAR_FILE = jarFile;
        RUNTIME_CONTENT_SHA256 = runtimeContentSha256;
    }

    private BuildStamp() {
    }

    /** One-line identity for chat feedback / log headers, e.g. {@code build=ab12cd34ef jar=TEST (3).jar}. */
    public static String describeShort() {
        return "build=" + GIT_SHA + " jar=" + JAR_FILE;
    }

    /** True only when provenance can bind to exact runtime bytes instead of a guessed build label. */
    public static boolean hasExactRuntimeContent() {
        return RUNTIME_CONTENT_SHA256.matches("[0-9a-f]{64}");
    }

    /**
     * Extend the main runtime digest with exact class bytes from another source set. RegistrySweep is
     * GameTest-only, so its run identity uses this to bind both the shipped main implementation and
     * the exact sweep/barrier classes that are executing in the development JVM.
     */
    public static String extendRuntimeContentSha256(Class<?>... exactClasses) {
        if (!hasExactRuntimeContent() || exactClasses == null || exactClasses.length == 0) {
            return "unavailable";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFramed(digest, "main-runtime-sha256", RUNTIME_CONTENT_SHA256.getBytes(StandardCharsets.UTF_8));
            Set<Class<?>> recursive = new HashSet<>();
            for (Class<?> type : exactClasses) {
                collectDeclaredClasses(type, recursive);
            }
            List<Class<?>> classes = new ArrayList<>(recursive);
            classes.sort(Comparator.comparing(Class::getName));
            for (Class<?> type : classes) {
                String resource = "/" + type.getName().replace('.', '/') + ".class";
                try (InputStream in = type.getResourceAsStream(resource)) {
                    if (in == null) {
                        return "unavailable";
                    }
                    updateFramed(digest, type.getName(), in.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static void collectDeclaredClasses(Class<?> type, Set<Class<?>> classes) {
        if (!classes.add(type)) {
            return;
        }
        for (Class<?> declared : type.getDeclaredClasses()) {
            collectDeclaredClasses(declared, classes);
        }
    }

    /**
     * Resolve a code-source URL to a local jar path, tolerating the nested/percent-encoded URL shapes the
     * Fabric Knot classloader can produce ({@code jar:file:...!/}, plain {@code file:...}). Returns null
     * for a non-jar source (a dev classes directory) or anything unresolvable.
     */
    private static Path toLocalJarPath(URL location) {
        if (location == null) {
            return null;
        }
        try {
            String spec = location.toString();
            if (spec.startsWith("jar:")) {
                int bang = spec.indexOf("!/");
                spec = bang >= 0 ? spec.substring(4, bang) : spec.substring(4);
            }
            if (!spec.startsWith("file:")) {
                return null;
            }
            Path path = Paths.get(new URI(spec));
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            return name.endsWith(".jar") ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String runtimeContentSha256(URL location, Path jarPath)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        if (jarPath != null && Files.isRegularFile(jarPath)) {
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(jarPath)));
        }

        Path classesRoot = toLocalFilePath(location);
        if (classesRoot == null || !Files.isDirectory(classesRoot)) {
            return "unavailable";
        }

        // Use Slabbed's unique resource, not the generic fabric.mod.json name shared by every mod.
        URL metadata = BuildStamp.class.getClassLoader().getResource("slabbed.mixins.json");
        Path metadataPath = toLocalFilePath(metadata);
        if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
            return "unavailable";
        }
        Path resourcesRoot = metadataPath.getParent();
        return devRuntimeContentSha256(classesRoot, resourcesRoot);
    }

    /**
     * Path-independent exact identity for a development launch. Both roots are mandatory: accepting
     * classes alone would alias two launches whose mixin/config/resource bytes differ at one HEAD.
     * Public only as a deterministic GameTest seam for the fail-closed provenance contract.
     */
    public static String devRuntimeContentSha256(Path classesRoot, Path resourcesRoot) {
        if (classesRoot == null || resourcesRoot == null
                || !Files.isDirectory(classesRoot) || !Files.isDirectory(resourcesRoot)) {
            return "unavailable";
        }
        Path uniqueResource = resourcesRoot.resolve("slabbed.mixins.json");
        if (!Files.isRegularFile(uniqueResource)) {
            return "unavailable";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDirectory(digest, "main-classes", classesRoot.toAbsolutePath().normalize());
            updateDirectory(digest, "main-resources", resourcesRoot.toAbsolutePath().normalize());
            return HexFormat.of().formatHex(digest.digest());
        } catch (Throwable ignored) {
            return "unavailable";
        }
    }

    private static void updateDirectory(MessageDigest digest, String label, Path root) throws Exception {
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> normalizedRelative(root, path)))
                    .toList();
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("runtime content root is empty: " + label);
        }
        updateFramed(digest, "tree", label.getBytes(StandardCharsets.UTF_8));
        for (Path file : files) {
            updateFramed(digest, normalizedRelative(root, file), Files.readAllBytes(file));
        }
    }

    private static String normalizedRelative(Path root, Path file) {
        return root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static void updateFramed(MessageDigest digest, String label, byte[] bytes) {
        byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(labelBytes.length).array());
        digest.update(labelBytes);
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }

    private static Path toLocalFilePath(URL location) {
        if (location == null) {
            return null;
        }
        try {
            String spec = location.toString();
            if (spec.startsWith("jar:")) {
                return null;
            }
            if (!spec.startsWith("file:")) {
                return null;
            }
            return Paths.get(new URI(spec)).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
}
