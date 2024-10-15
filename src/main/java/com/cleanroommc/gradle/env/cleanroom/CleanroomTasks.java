package com.cleanroommc.gradle.env.cleanroom;

import com.cleanroommc.gradle.api.Environment;
import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.named.dependency.Dependencies;
import com.cleanroommc.gradle.api.named.task.TaskGroup;
import com.cleanroommc.gradle.api.named.task.Tasks;
import com.cleanroommc.gradle.api.structure.Locations;
import com.cleanroommc.gradle.env.common.task.RunMinecraft;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import net.minecraftforge.fml.relauncher.Side;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.stream.Collectors;

public class CleanroomTasks {

    private static final String RUN_CLIENT = "runClient";
    private final Project project;
    private final String version;
    private final TaskGroup group;
    private final File cache;
    private final VanillaTasks vanillaTasks;
    private TaskProvider<RunMinecraft> runClient;

    private Configuration cleanroomConfig, cleanroomNativesConfig;

    @Inject
    public CleanroomTasks(Project project, VanillaTasks vanillaTasks, String minecraftVersion) {
        this.project = project;
        this.vanillaTasks = vanillaTasks;
        this.version = minecraftVersion;
        this.group = TaskGroup.of("cleanroom " + minecraftVersion);
        this.cache = Locations.build(project, "versions", minecraftVersion, "cleanroom");

        this.initRepos();
        this.initConfigs();
        this.initTasks();
    }

    private void initRepos() {
    }

    private void initConfigs() {
        this.cleanroomConfig = Configurations.of(this.project, "cleanroom" + this.version.replace('.', '_'), true).get();
        this.project.afterEvaluate(project -> {
            Configuration c = this.project.getConfigurations().getByName("compileClasspath");
            Set<ResolvedDependency> d = c.getResolvedConfiguration().getFirstLevelModuleDependencies();
            for (ResolvedDependency rd : d) {
                if (rd.getName().contains("cleanroom")) {
                    ArrayDeque<ResolvedDependency> arrayDeque = new ArrayDeque<>(rd.getChildren());
                    while (!arrayDeque.isEmpty()) {
                        ResolvedDependency f = arrayDeque.poll();
                        arrayDeque.addAll(f.getChildren());
                    }
                }
            }
        });
    }

    private void initTasks() {
        this.runClient = group.add(Tasks.with(project, RUN_CLIENT, RunMinecraft.class, t -> {
            t.dependsOn(vanillaTasks.getGroup().get(vanillaTasks.taskName(VanillaTasks.DOWNLOAD_ASSETS)));
            t.getMinecraftVersion().set(version);
            t.getSide().set(Side.CLIENT);
            //t.getNatives().fileProvider(extractNatives.map(Copy::getDestinationDir));
            t.getAssetIndexVersion().set(vanillaTasks.assetIndexId());
            t.getVanillaAssetsLocation().set(Locations.build(project, "assets"));
            t.setWorkingDir(Locations.run(project, version, Environment.VANILLA, Side.CLIENT));
            //t.classpath(clientJar());
            //t.classpath(vanillaConfig);
            t.getMainClass().set("net.minecraft.client.main.Main");
        }));
    }
}
