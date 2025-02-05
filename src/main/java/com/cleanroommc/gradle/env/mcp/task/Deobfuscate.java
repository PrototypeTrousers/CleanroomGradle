package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

public abstract class Deobfuscate extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getObfuscatedJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @InputFile
    @Optional
    public abstract RegularFileProperty getAccessTransformerFile();

    @OutputFile
    public abstract RegularFileProperty getDeobfuscatedJar();

    public Deobfuscate() {
        super("deobfuscate", "net.minecraftforge:ForgeAutoRenamingTool:1.1.0");
        this.getMainClass().set("net.minecraftforge.fart.Main");
        this.args("--input", getObfuscatedJar(),
                "--output", getDeobfuscatedJar(),
                "--map", getSrgMappingFile(),
                "--src-fix");
        this.setup(false);
    }
}
