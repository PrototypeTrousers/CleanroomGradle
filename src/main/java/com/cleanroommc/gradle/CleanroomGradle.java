package com.cleanroommc.gradle;

import com.cleanroommc.gradle.env.cleanroom.CleanroomExtension;
import com.cleanroommc.gradle.env.cleanroom.CleanroomTasks;
import com.cleanroommc.gradle.env.mcp.MCPTasks;
import com.cleanroommc.gradle.env.mixins.MixinProps;
import com.cleanroommc.gradle.env.vanilla.VanillaTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CleanroomGradle implements Plugin<Project> {

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
        var cleanExtension = project.getExtensions().create(
                "cleanroom",
                CleanroomExtension.class,
                project,
                mcpTasks
        );
        var cleanroomTasks = new CleanroomTasks(project, vanillaTasks, mcpTasks, cleanExtension, "1.12.2");
        var mixinProps = new MixinProps(project, mcpTasks);
        project.getExtensions().add("mixinProps", mixinProps);
    }
}
