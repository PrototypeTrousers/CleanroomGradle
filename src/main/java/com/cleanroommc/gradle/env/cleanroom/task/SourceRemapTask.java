package com.cleanroommc.gradle.env.cleanroom.task;

import com.cleanroommc.gradle.api.patch.CsvMappingsReader;
import com.cleanroommc.gradle.utils.Utilities;
import com.google.common.collect.Sets;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.io.srg.SrgReader;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

public abstract class SourceRemapTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSrg();

    @InputFile
    @Optional
    public abstract RegularFileProperty getParamsCsv();

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getSrcFolder();

    @OutputDirectory
    public abstract DirectoryProperty getRemappedFolder();

    @InputFiles
    public abstract ConfigurableFileCollection getClasspasthFiles();

    @TaskAction
    public void remapSources() throws Exception {
        final Mercury mercury = new Mercury();


        mercury.getProcessors().add(MercuryRemapper.create(new SrgReader(Files.newBufferedReader(getSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create())));
        CsvMappingsReader c = new CsvMappingsReader(Files.newBufferedReader(getParamsCsv().get().getAsFile().toPath(), StandardCharsets.UTF_8), true);
        mercury.getProcessors().add(MercuryRemapper.create(c.read(MappingSet.create())));

        Set<File> set = Sets.newHashSet(getProject().getConfigurations().getByName("compileClasspath").getFiles());
        set.addAll(getProject().getConfigurations().getByName("cleanroom1_12_2").getFiles());
        set.addAll(getClasspasthFiles().getFiles());
        for (File dependencies : set) {
            mercury.getClassPath().add(dependencies.toPath());
            getLogger().lifecycle("Adding {} to classpath", dependencies);
        }
        mercury.setGracefulClasspathChecks(true);
        mercury.rewrite(getSrcFolder().get().getAsFile().toPath(), getRemappedFolder().get().getAsFile().toPath());
    }
}
