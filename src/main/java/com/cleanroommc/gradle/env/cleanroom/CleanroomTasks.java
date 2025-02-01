package com.cleanroommc.gradle.env.cleanroom;

import com.cleanroommc.gradle.api.Environment;
import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.SourceSets;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.extension.CleanroomExtension;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.os.Platform;
import com.cleanroommc.gradle.api.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.patch.bin.ApplyBinPatches;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.api.types.Types;
import com.cleanroommc.gradle.api.types.json.schema.VersionMeta;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.mcp.task.*;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.io.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CleanroomTasks {

    private static final String RUN_CLEANROOM_CLIENT = "runCleanroomClient";
    private static final String EXTRACT_CLEANROOM_NATIVES = "extractCleanroomNatives";
    private static final String PATCH_JAR2 = "patchJarwithCleanroomPatches";
    public static final String REMAP_JAR2 = "remapCleanroomJar";

    private final Project project;
    private final String version;
    private final TaskGroup group;
    private final File cache;
    private final VanillaTasks vanillaTasks;
    private final MCPTasks mcpTasks;
    private final CleanroomExtension cleanroomExtension;
    private TaskProvider<RunMinecraft> runClient;
    private TaskProvider<ApplyDiffs> patchJar2;
    private TaskProvider<Remap> remapJar2;
    private TaskProvider<Jar> minecraftJar;


    private NamedDomainObjectProvider<SourceSet> cleanroomminecraft;


    private NamedDomainObjectProvider<Configuration> cleanroomConfig, cleanroomNativesConfig;

    @Inject
    public CleanroomTasks(Project project, VanillaTasks vanillaTasks, MCPTasks mcpTasks, CleanroomExtension cleanroomExtension, String minecraftVersion) {
        this.project = project;
        this.vanillaTasks = vanillaTasks;
        this.mcpTasks = mcpTasks;
        this.version = minecraftVersion;
        this.group = TaskGroup.of("cleanroom " + minecraftVersion);
        this.cache = Locations.build(project, "versions", minecraftVersion, "cleanroom");
        this.cleanroomExtension = cleanroomExtension;

        this.initRepos();
        this.initConfigs();
        this.initSourceSets();
        this.initTasks();
    }

    private void initSourceSets() {
        this.cleanroomminecraft = SourceSets.getOrCreate(this.project, "cleanroomminecraft");
        this.cleanroomminecraft.configure(set -> {
            SourceSets.addCompileClasspath(set, this.cleanroomConfig.get());
            SourceSets.addRuntimeClasspath(set, this.cleanroomConfig.get());
        });
    }

    private void initRepos() {
        var repos = this.project.getRepositories();
        repos.maven(mar -> {
            mar.setName("Cleanroom Repo");
            mar.setUrl("https://repo.cleanroommc.com/releases/");
            mar.getMetadataSources().artifact();
        });
        repos.maven(mar -> {
            mar.setName("Cleanroom");
            mar.setUrl("https://maven.cleanroommc.com/");
            mar.getMetadataSources().artifact();
        });
        repos.maven(mar -> {
            mar.setName("TOP");
            mar.setUrl("https://maven.outlands.top/releases/");
            mar.getMetadataSources().artifact();
        });
        repos.maven(mar -> {
            mar.setName("minecraftlibraries wtf is even this");
            mar.setUrl("https://libraries.minecraft.net/");
            mar.getMetadataSources().artifact();
        });
    }

    private void initConfigs() {
        this.cleanroomConfig = Configurations.of(this.project, "cleanroom" + this.version.replace('.', '_'), true);
        this.cleanroomNativesConfig = Configurations.of(this.project, "cleanroomNatives" + this.version.replace('.', '_'), true);

        this.project.afterEvaluate(project -> {
            for (var library : versionMeta().get().libraries()) {
                if (library.isValidForOS(Platform.CURRENT)) {
                    Dependencies.add(project, cleanroomConfig, library.name());
                    if (library.hasNativesForOS(Platform.CURRENT)) {
                        var osClassifier = library.classifierForOS(Platform.CURRENT);
                        if (osClassifier != null) {
                            var path = osClassifier.path();
                            var matcher = Meta.NATIVES_PATTERN.matcher(path);
                            if (!matcher.find()) {
                                throw new IllegalStateException("Failed to match regex for natives path: " + path);
                            }
                            var group = matcher.group("group").replace('/', '.');
                            var name = matcher.group("name");
                            var version = matcher.group("version");
                            var classifier = matcher.group("classifier");
                            var dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);
                            Dependencies.add(project, cleanroomNativesConfig, dependencyNotation);
                        }
                    }
                }
            }
            for (var library : vanillaTasks.versionMeta().get().libraries()) {
                if (library.isValidForOS(Platform.CURRENT)) {

                    //TODO find better way to blacklist vanilla libraries.
                    //or find which ones we need

                    if (library.name().contains("patchy")) continue;
                    Dependencies.add(project, cleanroomConfig, library.name());
                    if (library.hasNativesForOS(Platform.CURRENT)) {
                        var osClassifier = library.classifierForOS(Platform.CURRENT);
                        if (osClassifier != null) {
                            var path = osClassifier.path();
                            var matcher = Meta.NATIVES_PATTERN.matcher(path);
                            if (!matcher.find()) {
                                throw new IllegalStateException("Failed to match regex for natives path: " + path);
                            }
                            var group = matcher.group("group").replace('/', '.');
                            var name = matcher.group("name");
                            var version = matcher.group("version");
                            var classifier = matcher.group("classifier");
                            var dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);
                            Dependencies.add(project, cleanroomNativesConfig, dependencyNotation);
                        }
                    }
                }
            }
            if (minecraftJar.get().getArchiveFile().get().getAsFile().exists()) {
                project.getDependencies().add("implementation", project.files(minecraftJar.get().getArchiveFile().get().getAsFile()));
            }
        });
    }

    public Supplier<VersionMeta> versionMeta() {
        return Types.memoizedSupplier(() -> {
            try {
                var file = this.location("version.json");
                var file2 = mcpTasks.location("mappings", "forge.exc");
                if (!file.exists() || !file.exists()) {
                    try {
                        File installer = location("cleanroom-0.2.4-alpha-installer.jar");
                        var result = IO.download(project, "https://github.com/CleanroomMC/Cleanroom/releases/download/0.2.4-alpha/cleanroom-0.2.4-alpha-installer.jar", installer, dl -> {
                            dl.overwrite(false);
                            dl.onlyIfModified(true);
                            dl.onlyIfNewer(true);
                            dl.useETag(true);
                            dl.dest(installer);
                        });
                        result.join();

                        int foundFiles = 0;
                        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(installer))) {
                            ZipEntry entry;

                            // Loop through the entries in the zip file
                            while ((entry = zipIn.getNextEntry()) != null) {
                                if (entry.getName().equals("version.json")) {

                                    // Extract the version.json file
                                    try (FileOutputStream outFile = new FileOutputStream(location("version.json"))) {
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = zipIn.read(buffer)) > 0) {
                                            outFile.write(buffer, 0, len);
                                        }
                                        System.out.println("File extracted: version.json");
                                    }
                                    foundFiles++;
                                }

                                // If a .jar file is found, search inside it for forge.exc
                                if (entry.getName().endsWith(".jar")) {
                                    System.out.println("Found nested JAR: " + entry.getName());

                                    // Load the .jar file from the ZIP entry
                                    ByteArrayOutputStream jarBuffer = new ByteArrayOutputStream();
                                    byte[] buffer = new byte[1024];
                                    int len;
                                    while ((len = zipIn.read(buffer)) > 0) {
                                        jarBuffer.write(buffer, 0, len);
                                    }

                                    // Open the .jar file
                                    try (ZipInputStream jarIn = new ZipInputStream(new ByteArrayInputStream(jarBuffer.toByteArray()))) {
                                        ZipEntry jarEntry;

                                        // Iterate through the .jar file entries
                                        while ((jarEntry = jarIn.getNextEntry()) != null) {
                                            if (jarEntry.getName().equals("forge.exc")) {

                                                // Extract forge.exc file
                                                try (FileOutputStream outFile = new FileOutputStream(mcpTasks.location("mappings", "forge.exc"))) {
                                                    while ((len = jarIn.read(buffer)) > 0) {
                                                        outFile.write(buffer, 0, len);
                                                    }
                                                    System.out.println("File extracted: forge.exc");
                                                }
                                                foundFiles++;
                                            }
                                            if (jarEntry.getName().equals("forge_at.cfg")) {

                                                // Extract forge_at.cfg file
                                                try (FileOutputStream outFile = new FileOutputStream(mcpTasks.location("mappings", "forge_at.cfg"))) {
                                                    while ((len = jarIn.read(buffer)) > 0) {
                                                        outFile.write(buffer, 0, len);
                                                    }
                                                    System.out.println("File extracted: forge_at.cfg");
                                                }
                                                foundFiles++;
                                            }
                                            jarIn.closeEntry();
                                            if (foundFiles == 3) {
                                                break; // Stop if both files are found
                                            }
                                        }
                                    }
                                }

                                zipIn.closeEntry();
                                if (foundFiles == 3) {
                                    break; // Stop if both files are found
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Unable to extract version manifest from cleanroom installer jar!", e);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to download version manifest!", e);
                    }
                }
                return Types.readJson(file, VersionMeta.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void initTasks() {

        var extractNatives = group.add(Tasks.unzipConf(project, EXTRACT_CLEANROOM_NATIVES, cleanroomNativesConfig, location("natives"), t -> {
            t.exclude("META-INF/**");
            t.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
        }));

        var binPatchJar = group.add(Tasks.with(this.project, "binPatchJar", ApplyBinPatches.class, t -> {
            t.getCleanJar().set(mcpTasks.deobfuscate().get().getDeobfuscatedJar());
            t.getPatchLMZA().set(location("binpatches.pack.lzma"));
            t.getPatchedJar().set(location("binPatched.jar"));
        }));

        var formatSrg = group.add(Tasks.with(this.project, "formatSrg", FormatSRG.class, t -> {
            t.getSrg().set(mcpTasks.genSrgMappings().get().getSrgToMcp());
        }));

        this.runClient = group.add(Tasks.with(project, RUN_CLEANROOM_CLIENT, RunMinecraft.class, t -> {
            t.dependsOn(vanillaTasks.getGroup().get(vanillaTasks.taskName(VanillaTasks.DOWNLOAD_ASSETS)), formatSrg);
            t.getMinecraftVersion().set(version);
            t.getSide().set(Side.CLIENT);
            t.getNatives().fileProvider(extractNatives.map(Copy::getDestinationDir));
            t.getAssetIndexVersion().set(vanillaTasks.assetIndexId());
            t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
            t.setWorkingDir(Locations.run(project, version, Environment.CLEANROOM, Side.CLIENT));
            t.classpath(this.location("build", "libs", "cleanroomminecraft", "minecraft-srg-1.12.2.jar"));
            t.classpath(project.fileTree("build/libs"));
            t.classpath(project.getTasks().named("jar").get().getOutputs().getFiles());
            t.classpath(mcpTasks.extractClientResources().map(Copy::getDestinationDir));
            t.classpath(mcpTasks.extractServerResources().map(Copy::getDestinationDir));
            t.classpath(cleanroomConfig);
            t.classpath(cleanroomNativesConfig);
            t.classpath(cleanroomExtension.config());
            t.environment("target", "fmldevclient");
            t.getMainClass().set("com.cleanroommc.boot.MainClient");
            t.environment( "tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker");
            t.environment( "mainClass", "top.outlands.foundation.boot.Foundation");
            t.environment("MCP_MAPPINGS", mcpTasks.srgMapping());
            t.environment("MCP_TO_SRG", formatSrg.get().getOutput());
        }));

        this.patchJar2 = group.add(Tasks.with(project, this.taskName(PATCH_JAR2), ApplyDiffs.class, t -> {
            t.dependsOn(mcpTasks.cleanup());
            t.getCopyOverSource().set(true);
            t.source(mcpTasks.cleanup().flatMap(CleanUp::getCleanJar));
            t.patch(mcpTasks.location("patches", "net.zip"));
            t.modified(this.location("cleamroommcjar.jar"));
        }));

        var applyATtoSources = group.add(Tasks.with(project, this.taskName("applyAccessTransformerscleanroom"), ApplySourceAccessTransformersTask.class, t -> {
            t.dependsOn(patchJar2, mcpTasks.extDepsAt());
            t.getInputJar().set(patchJar2.map(ApplyDiffs::getModifiedPath).get());
            t.getOutputJar().set(this.location("accessTransformedCleanroomPatched.jar"));
            t.getAccessTransformerFiles().from(cleanroomExtension.ats, mcpTasks.location("mappings", "forge_at.cfg"), mcpTasks.extDepsAt().flatMap(ExtractDependencyATsTask::getOutputFile));
        }));

        this.remapJar2 = group.add(Tasks.with(project, this.taskName(REMAP_JAR2), Remap.class, t -> {
            t.dependsOn(mcpTasks.extractMcpMappings(), applyATtoSources);
            t.getSrgJar().set(applyATtoSources.flatMap(ApplySourceAccessTransformersTask::getOutputJar));
            t.getFieldMappings().set(Locations.file(mcpTasks.mcpMappingFolder(), "fields.csv"));
            t.getMethodMappings().set(Locations.file(mcpTasks.mcpMappingFolder(), "methods.csv"));
            t.getParameterMappings().set(Locations.file(mcpTasks.mcpMappingFolder(), "params.csv"));
            t.getRemappedJar().set(this.location("remappedcleanroomjar.jar"));
        }));

        var addCleanroomMinecraftSources = group.add(Tasks.unzip(project, "addCleanroomMinecraftSources",
                this.remapJar2.get().getRemappedJar(), SourceSets.sourceFrom(cleanroomminecraft)));

        this.cleanroomminecraft.configure(sources -> {
            Tasks.<JavaCompile>configure(project, sources.getCompileJavaTaskName(), t -> {
                //TODO add a way to not overwrite the source every compilation
                //t.dependsOn(addCleanroomMinecraftSources);
                t.setGroup(group.getName());
                t.getJavaCompiler().set(Providers.javaCompiler(project, 21));
                t.getModularity().getInferModulePath().set(false);
                t.getDestinationDirectory().set(this.location("build", "classes", sources.getName()));
            });
            Tasks.configure(project, sources.getClassesTaskName(), t -> t.setGroup(group.getName()));
            Tasks.configure(project, sources.getProcessResourcesTaskName(), t -> t.setGroup(group.getName()));

            minecraftJar = group.add(Tasks.with(project, sources.getJarTaskName(), Jar.class, t -> {
                t.dependsOn(sources.getClassesTaskName());
                t.from(Tasks.named(project, sources.getCompileJavaTaskName(), JavaCompile.class).map(JavaCompile::getDestinationDirectory));
                t.getDestinationDirectory().set(this.location("build", "libs", sources.getName()));
                t.getArchiveFileName().set("minecraft-srg-1.12.2.jar");
            }));
        });

        var obfuscate = group.add(Tasks.with(project, this.taskName("cleanroomobfuscate"), Obfuscate.class, t -> {
            t.getDeobfuscatedJar().set(project.getTasks().named("jar").get().getOutputs().getFiles().getSingleFile());
            t.getSrgMappingFile().fileProvider(mcpTasks.genSrgMappings().get().getMcpToNotch().getAsFile());
            t.getObfuscatedJar().set(project.file("build/libs/" +project.getName() + "obf.jar"));
        }));
    }

    public TaskProvider<Remap> remapJar2() {return remapJar2;}

    public TaskProvider<ApplyDiffs> patchJar2() {
        return patchJar2;
    }

    private File location(String... paths) {
        return Locations.file(this.cache, paths);
    }

    public String taskName(String taskName) {
        // return this.version.replace('.', '_') + "_" + taskName;
        return taskName;
    }

    public NamedDomainObjectProvider<Configuration> config() {
        return cleanroomConfig;
    }
}
