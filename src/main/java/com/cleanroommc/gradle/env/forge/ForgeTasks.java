package com.cleanroommc.gradle.env.forge;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.patch.bin.ApplyBinPatches;
import com.cleanroommc.gradle.api.structure.IO;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.mcp.task.MergeJars;
import com.cleanroommc.gradle.env.mcp.task.Remap;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;

public class ForgeTasks {

    public static final String PATCH_JAR = "binPatchJar";
    private static final String RUN_CLIENT = "runClient";

    private TaskProvider<ApplyBinPatches> binPatchJar;


    private final Project project;
    private final String version;
    private final TaskGroup group;
    private final File cache;
    private final VanillaTasks vanillaTasks;
    private final MCPTasks mcpTasks;
    private TaskProvider<RunMinecraft> runClient;

    private Configuration cleanroomConfig, cleanroomNativesConfig;
    private Configuration forgeConfig;

    //FIXME DOESNT DO SHIT

    @Inject
    public ForgeTasks(Project project, VanillaTasks vanillaTasks, MCPTasks mcpTasks, String minecraftVersion) {
        this.project = project;
        this.vanillaTasks = vanillaTasks;
        this.mcpTasks = mcpTasks;
        this.version = minecraftVersion;
        this.group = TaskGroup.of("forge " + minecraftVersion);
        this.cache = Locations.build(project, "versions", minecraftVersion, "forge");

        this.initRepos();
        this.initConfigs();
        this.initTasks();
    }

    private void initRepos() {
        var repos = project.getRepositories();
    }

    private void initConfigs() {
        this.forgeConfig = Configurations.of(this.project, "forge" + this.version.replace('.', '_')).get();
            Dependencies.add(this.project, this.forgeConfig, "net.minecraftforge:forge:1.12.2-14.23.5.2857");
    }

    private void initTasks() {
       // var extractForgeConfig = group.add(Tasks.unzip(this.project, "extractForgeJar", this.forgeConfig, this.location("unZippedForge")));

        this.binPatchJar = group.add(Tasks.with(this.project, PATCH_JAR, ApplyBinPatches.class, t ->{
            t.getCleanJar().set(mcpTasks.remapJar().flatMap(Remap::getRemappedJar));
            //t.getPatchLMZA().set(IO.download(this.project,  ));
            t.getPatchedJar().set(this.location("forgeBinPatched.jar"));
        }));
    }

    private File location(String... paths) {
        return Locations.file(this.cache, paths);
    }
}
