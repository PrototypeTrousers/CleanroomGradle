package com.cleanroommc.gradle.env.cleanroom;

import com.cleanroommc.gradle.api.Environment;
import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.lazy.Providers;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.os.Platform;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.api.types.Types;
import com.cleanroommc.gradle.api.types.json.schema.VersionManifest;
import com.cleanroommc.gradle.api.types.json.schema.VersionMeta;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.mcp.task.MergeJars;
import com.cleanroommc.gradle.env.mcp.task.Obfuscate;
import com.cleanroommc.gradle.env.mcp.task.PolishDeobfuscation;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CleanroomTasks {

    private static final String RUN_CLEANROOM_CLIENT = "runCleanroomClient";
    private static final String EXTRACT_CLEANROOM_NATIVES = "extractCleanroomNatives";
    private final Project project;
    private final String version;
    private final TaskGroup group;
    private final File cache;
    private final VanillaTasks vanillaTasks;
    private final MCPTasks mcpTasks;
    private TaskProvider<RunMinecraft> runClient;

    private Configuration cleanroomConfig, cleanroomNativesConfig;

    @Inject
    public CleanroomTasks(Project project, VanillaTasks vanillaTasks, MCPTasks mcpTasks, String minecraftVersion) {
        this.project = project;
        this.vanillaTasks = vanillaTasks;
        this.mcpTasks = mcpTasks;
        this.version = minecraftVersion;
        this.group = TaskGroup.of("cleanroom " + minecraftVersion);
        this.cache = Locations.build(project, "versions", minecraftVersion, "cleanroom");

        this.initRepos();
        this.initConfigs();
        this.initTasks();
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
        this.cleanroomConfig = Configurations.of(this.project, "cleanroom" + this.version.replace('.', '_'), true).get();
        this.cleanroomNativesConfig = Configurations.of(this.project, "cleanroomNatives" + this.version.replace('.', '_'), true).get();

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
        });
    }

    public Supplier<VersionMeta> versionMeta() {
        return Types.memoizedSupplier(() -> {
            try {
                var file = this.location("version.json");
                if (!file.exists()) {
                    try {
                        File installer = location("cleanroom-0.2.3-alpha-installer.jar");
                        var result = IO.download(project, "https://github.com/CleanroomMC/Cleanroom/releases/download/0.2.3-alpha/cleanroom-0.2.3-alpha-installer.jar", installer, dl -> {
                            dl.overwrite(false);
                            dl.onlyIfModified(true);
                            dl.onlyIfNewer(true);
                            dl.useETag(true);
                            dl.dest(installer);
                        });
                        result.join();
                        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(installer))) {
                            ZipEntry entry;

                            // Loop through the entries in the zip file
                            while ((entry = zipIn.getNextEntry()) != null) {
                                if (entry.getName().equals("version.json")) {

                                    // Open an output stream to write the extracted file
                                    try (FileOutputStream outFile = new FileOutputStream(location("version.json"))) {
                                        byte[] buffer = new byte[1024];
                                        int len;
                                        while ((len = zipIn.read(buffer)) > 0) {
                                            outFile.write(buffer, 0, len);
                                        }
                                        System.out.println("File extracted: " + "version.json");
                                    }
                                    break;  // Stop after finding the desired file
                                }
                                zipIn.closeEntry();
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

        var extractNatives = group.add(Tasks.unzip(project, EXTRACT_CLEANROOM_NATIVES, cleanroomNativesConfig, location("natives"), t -> {
            t.exclude("META-INF/**");
            t.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
        }));


        this.runClient = group.add(Tasks.with(project, RUN_CLEANROOM_CLIENT, RunMinecraft.class, t -> {
            t.dependsOn(vanillaTasks.getGroup().get(vanillaTasks.taskName(VanillaTasks.DOWNLOAD_ASSETS)));
            t.getMinecraftVersion().set(version);
            t.getSide().set(Side.CLIENT);
            t.getNatives().fileProvider(extractNatives.map(Copy::getDestinationDir));
            t.getAssetIndexVersion().set(vanillaTasks.assetIndexId());
            t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
            t.setWorkingDir(Locations.run(project, version, Environment.CLEANROOM, Side.CLIENT));
            t.classpath(mcpTasks.mergeJars().map(MergeJars::getMergedJar));
            t.classpath(cleanroomConfig, vanillaTasks.vanillaConfig());
            t.classpath(cleanroomNativesConfig);
            t.environment("target", "fmldevclient");
            t.getMainClass().set("com.cleanroommc.boot.MainClient");
            t.environment( "tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker");
            t.environment( "mainClass", "top.outlands.foundation.boot.Foundation");
            t.environment("MCP_MAPPINGS", mcpTasks.extractMcpConfig().get().getDestinationDir().getAbsolutePath());
            t.environment("MCP_TO_SRG", mcpTasks.mcpConfig().getSingleFile().getAbsolutePath());
        }));
    }

    private File location(String... paths) {
        return Locations.file(this.cache, paths);
    }
}
