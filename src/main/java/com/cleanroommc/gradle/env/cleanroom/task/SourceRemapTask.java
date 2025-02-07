package com.cleanroommc.gradle.env.cleanroom.task;

import com.cleanroommc.gradle.api.patch.ModifiedSrgReader;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.google.common.collect.Sets;
import org.cadixdev.lorenz.MappingSet;
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
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SourceRemapTask extends DefaultTask {

    static Pattern FUNC_ID = Pattern.compile("_(i|\\d+)_");

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSrg();

    @InputFile
    @Optional
    public abstract RegularFileProperty getParamsSrg();

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


        mercury.getProcessors().add(MercuryRemapper.create(new ModifiedSrgReader(Files.newBufferedReader(getSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create())));
        mercury.getProcessors().add(MercuryRemapper.create(new ModifiedSrgReader(Files.newBufferedReader(getParamsSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create())));

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

    public static void main(String[] args) throws Exception {
        // Path to your .java file
        Path java = Path.of("/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/cleanroom/rerererer/net/minecraft/block/Block.java");
        // Parse the source code
        CompilationUnit cu = new JavaParser().parse(java).getResult().get();

        //Path path = Paths.get("path/to/YourClass.java");
        //CompilationUnit cu = StaticJavaParser.parse(path);

        // Traverse all methods in the file
        LexicalPreservingPrinter.setup(cu);
        cu.findAll(MethodDeclaration.class).forEach(method -> {

            Matcher m = FUNC_ID.matcher(method.getNameAsString());
            if (m.find()) {

                String methodId = m.group(1);

                // Rename each parameter to arg0, arg1, etc.
                int paramIdx = 0;
                if (!method.isStatic()) {
                    paramIdx++;
                }
                for (int i = 0; i < method.getParameters().size(); i++) {
                    Parameter param = method.getParameter(i);

                    String oldName = param.getName().asString();
                    String newName = "p_" + methodId + "_" + paramIdx + "_";
                    param.setName(newName); // Rename based on index


                    method.findAll(NameExpr.class).forEach(nameExpr -> {
                        if (nameExpr.getNameAsString().equals(oldName)) {
                            nameExpr.setName(newName);
                        }
                    });

                    Type paramType = param.getType();
                    if (paramType.isPrimitiveType() && paramType.asPrimitiveType().getType() == PrimitiveType.Primitive.DOUBLE) {
                        paramIdx += 2;
                        continue;
                    }
                    paramIdx++;
                }
            }
        });

        // Write the modified code back to the file
        Files.write(java, cu.toString().getBytes());

        //System.out.printf(cu.toString());
    }
}
