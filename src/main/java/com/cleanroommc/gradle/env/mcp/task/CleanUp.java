package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class CleanUp extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getDirtyJar();

    @OutputFile
    public abstract RegularFileProperty getCleanJar();

    public CleanUp() {
        super("cleanup", "net.minecraftforge:mcpcleanup:2.3.6:fatjar");
        this.getMainClass().set("net.minecraftforge.mcpcleanup.ConsoleTool");
        this.args("--input", getDirtyJar(),
                "--output", getCleanJar());
        //this.setup(true);
    }
}
