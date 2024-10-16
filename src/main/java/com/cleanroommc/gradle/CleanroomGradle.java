package com.cleanroommc.gradle;

import com.cleanroommc.gradle.env.cleanroom.CleanroomTasks;
import com.cleanroommc.gradle.env.forge.ForgeTasks;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import com.cleanroommc.gradle.env.extensions.RelauncherExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Map;

public class CleanroomGradle implements Plugin<Project> {

    Map<Class<?>, Object> taskMap;

    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("Welcome to CleanroomGradle!");

        // After Evaluation
        project.afterEvaluate(VanillaTasks::downloadVersionManifest);

        // TODO: move elsewhere
        var objectFactory = project.getObjects();

        var vanillaTasks = new VanillaTasks(project, "1.12.2");
        // var vanillaTasks = objectFactory.newInstance(VanillaTasks.class, project, "1.12.2");
        var mcpTasks = new MCPTasks(project, vanillaTasks);
        // var mcpTasks = objectFactory.newInstance(MCPTasks.class, project, vanillaTasks);

        //var forgeTasks = new ForgeTasks(project, vanillaTasks, mcpTasks, "1.12.2");
        var cleanroomTasks = new CleanroomTasks(project, vanillaTasks, "1.12.2");
        var extension = project.getExtensions().create("relauncher", RelauncherExtension.class);
    }

}
