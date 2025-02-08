package com.cleanroommc.gradle.env.cleanroom.task;

import com.cleanroommc.gradle.api.patch.ModifiedSrgReader;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.Sets;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class SourceRemapTask extends DefaultTask {

    static Pattern FUNC_ID = Pattern.compile("_(i|\\d+)_");
    static Pattern DIAMOND = Pattern.compile("(<.+>)");

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSrg();

    @InputFile
    @Optional
    public abstract RegularFileProperty getParamsSrg();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getConstructorTxt();

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getSrcFolder();

    @OutputDirectory
    public abstract DirectoryProperty getRemappedFolder();

    @InputFiles
    public abstract ConfigurableFileCollection getClasspasthFiles();

    static HashMap<String, Integer> constructorMap = new HashMap<>();

    @TaskAction
    public void remapSources() throws Exception {

        final Mercury mercury = new Mercury();


        mercury.getProcessors().add(MercuryRemapper.create(new ModifiedSrgReader(Files.newBufferedReader(getSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create())));
        mercury.getProcessors().add(MercuryRemapper.create(new ModifiedSrgReader(Files.newBufferedReader(getParamsSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create())));

        Set<File> set = Sets.newHashSet(getProject().getConfigurations().getByName("compileClasspath").getFiles());
        set.addAll(getProject().getConfigurations().getByName("cleanroom1_12_2").getFiles());
        set.addAll(getClasspasthFiles().getFiles());
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        for (File dependencies : set) {
            mercury.getClassPath().add(dependencies.toPath());
            typeSolver.add(new JarTypeSolver(dependencies));
            getLogger().lifecycle("Adding {} to classpath", dependencies);
        }
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File("/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/cleanroom/rerererer/"))); // Resolves Java SDK clasesolves project classes

        mercury.setGracefulClasspathChecks(true);
        mercury.rewrite(getSrcFolder().get().getAsFile().toPath(), getRemappedFolder().get().getAsFile().toPath());

        String filePath = "/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/mcp_config/20201025_185735/config/constructors.txt"; // Replace with your file path

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Split the line into the number and the rest of the string
                String[] parts = line.split(" ", 2); // Split into 2 parts: number and the rest
                if (parts.length == 2) {
                    int value = Integer.parseInt(parts[0]);
                    String key = parts[1];
                    constructorMap.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Configure JavaParser with SymbolSolver
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(config);

        try {
            Files.walkFileTree(getRemappedFolder().getAsFile().get().toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".java")) {
                        parseFile(file, parser, typeSolver);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void main(String[] args) {
        //parse constructors.txt
        String filePath = "/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/mcp_config/20201025_185735/config/constructors.txt"; // Replace with your file path

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Split the line into the number and the rest of the string
                String[] parts = line.split(" ", 2); // Split into 2 parts: number and the rest
                if (parts.length == 2) {
                    int value = Integer.parseInt(parts[0]);
                    String key = parts[1];
                    constructorMap.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(), // Resolves Java SDK classes
                new JavaParserTypeSolver(new File("/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/cleanroom/rerererer/")) // Resolves project classes
        );

        // Configure JavaParser with SymbolSolver
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(config);

        try {
            parseFile(new File("/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/cleanroom/rerererer/net/minecraft/block/BlockRailBase.java").toPath(),
                    parser, typeSolver);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void parseFile(Path java, JavaParser parser, TypeSolver typeSolver) throws IOException {

        CompilationUnit cu = parser.parse(java).getResult().get();
        LexicalPreservingPrinter.setup(cu);

        cu.findAll(ConstructorDeclaration.class).forEach(constructor -> {
// Get the parent class of the constructor
            java.util.Optional<ClassOrInterfaceDeclaration> parentClass = constructor.getParentNode()
                    .filter(node -> node instanceof ClassOrInterfaceDeclaration)
                    .map(node -> (ClassOrInterfaceDeclaration) node);
            String constructorName = "";
            String innerParams = "";
            String enumParams = "";


            if (parentClass.isPresent()) {
                // Recursively construct the fully qualified name of the class
                String fullyQualifiedClassName = getFullyQualifiedClassName(parentClass.get());
                constructorName = fullyQualifiedClassName.replace('.', '/');
                if (parentClass.get().isInnerClass()) {
                    java.util.Optional<ClassOrInterfaceDeclaration> outerClass = parentClass.get().getParentNode()
                            .filter(node -> node instanceof ClassOrInterfaceDeclaration)
                            .map(node -> (ClassOrInterfaceDeclaration) node);
                    String innerClass = ('L' + getFullyQualifiedClassName(outerClass.get()) + ';');
                    innerParams = innerClass.replace('.', '/');
                }
            }

            java.util.Optional<EnumDeclaration> p = constructor.getParentNode().filter(node -> node instanceof EnumDeclaration).map(node -> (EnumDeclaration) node);
            if (p.isPresent()) {
                enumParams = "Ljava/lang/String;I";
                String fullyQualifiedClassName = getFullyQualifiedEnumClassName(p.get());
                constructorName = fullyQualifiedClassName.replace('.', '/');
            }

            NodeList<Parameter> pl = constructor.getParameters();

            if (pl.isNonEmpty()) {
                String params = "(" + innerParams + enumParams + constructor.getParameters().stream()
                        .map(param -> resolveTypeName(param, typeSolver))
                        .collect(Collectors.joining("")) + ")V";

                Integer idinteger = constructorMap.get(constructorName + ' ' + params);
                if (idinteger == null) {
                    getLogger().lifecycle("Could not find id for {}", constructorName + ' ' + params);
                    getLogger().lifecycle(java.toString());
                } else {
                    int id = idinteger;

                    int paramIdx = 1;

                    for (int i = 0; i < constructor.getParameters().size(); i++) {
                        Parameter param = constructor.getParameter(i);

                        String oldName = param.getName().asString();
                        String newName = "p_" + id + "_" + paramIdx + "_";
                        param.setName(newName); // Rename based on index


                        constructor.findAll(NameExpr.class).forEach(nameExpr -> {
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
            }
        });


        // Traverse all methods in the file
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            CallableDeclaration.Signature s = method.getSignature();
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
    }

    private static String getFullyQualifiedEnumClassName(EnumDeclaration enumDeclaration) {
        // Start with the current class name

        // Traverse up the parent nodes to find outer classes
        java.util.Optional<TypeDeclaration<?>> parentNode = enumDeclaration.getParentNode()
                .filter(node -> node instanceof TypeDeclaration)
                .map(node -> (TypeDeclaration<?>) node);

        StringBuilder fullyQualifiedName = new StringBuilder();

        fullyQualifiedName.insert(0, (parentNode.isPresent() ?
                enumDeclaration.getNameAsString() : enumDeclaration.getFullyQualifiedName().get()));


        while (parentNode.isPresent()) {
            // Prepend the outer class name and a '$'
            fullyQualifiedName.insert(0, parentNode.get().getFullyQualifiedName().get()
                    + "$");

            // Move to the next outer class
            parentNode = parentNode.get().getParentNode()
                    .filter(node -> node instanceof TypeDeclaration)
                    .map(node -> (TypeDeclaration<?>) node);
        }

        return fullyQualifiedName.toString();
    }

    /**
     * Recursively constructs the fully qualified name of a class, including outer classes.
     */
    private static String getFullyQualifiedClassName(ClassOrInterfaceDeclaration classOrInterface) {
        // Start with the current class name

        // Traverse up the parent nodes to find outer classes
        java.util.Optional<TypeDeclaration<?>> parentNode = classOrInterface.getParentNode()
                .filter(node -> node instanceof TypeDeclaration)
                .map(node -> (TypeDeclaration<?>) node);

        StringBuilder fullyQualifiedName = new StringBuilder();
        if (classOrInterface.isInterface()) {
            fullyQualifiedName.insert(0, classOrInterface.getFullyQualifiedName());
        } else {
            fullyQualifiedName.insert(0, (parentNode.isPresent() ?
                    classOrInterface.getNameAsString() : classOrInterface.getFullyQualifiedName().get()));
        }


        while (parentNode.isPresent()) {
            // Prepend the outer class name and a '$'
            fullyQualifiedName.insert(0, parentNode.get().getFullyQualifiedName().get()
                    + "$");

            // Move to the next outer class
            parentNode = parentNode.get().getParentNode()
                    .filter(node -> node instanceof TypeDeclaration)
                    .map(node -> (TypeDeclaration<?>) node);
        }

        return fullyQualifiedName.toString();
    }

    private static String resolveTypeName(Parameter param, TypeSolver typeSolver) {
        if (param.getType() instanceof ClassOrInterfaceType classOrInterfaceType) {
            String typeName;
            if (classOrInterfaceType.getName().asString().equals("T")) {
                ResolvedType o = JavaParserFacade.get(typeSolver).getType(param);
                typeName = o.asTypeParameter().getBounds().getFirst().getType().describe();
                return toJVMDescriptor(typeName.replace('.', '/'));
            } else {
                typeName = classOrInterfaceType.getNameAsString();


                // Check if the type is an inner class
                if (classOrInterfaceType.getScope().isPresent()) {
                    String p = JavaParserFacade.get(typeSolver).getType(param).describe();
                    // If the type is scoped (e.g., Outer.Inner), resolve the scope
                    String scopeName = classOrInterfaceType.getScope().get().toString();
                    p = p.replace(scopeName + "." + typeName, scopeName + "$" + typeName);
                    p = p.replace('.', '/');

                    Matcher diamond = DIAMOND.matcher(p);
                    if (diamond.find()) {
                        for (int i = 1; i < diamond.groupCount(); i++) {
                            p = p.replace(diamond.group(i), "");
                        }
                    }
                    return toJVMDescriptor(p);
                } else {
                    // If the type is not scoped, check if it's an inner class of the context class
                    String p = JavaParserFacade.get(typeSolver).getType(param).describe();
                    Matcher diamond = DIAMOND.matcher(p);
                    if (diamond.find()) {
                        for (int i = 1; i < diamond.groupCount() + 1; i++) {
                            p = p.replace(diamond.group(i), "");
                        }
                    }
                    return toJVMDescriptor(p.replace('.', '/'));
                }
            }
        }
        // For non-class types (e.g., primitives, arrays), return the type as is
        return toJVMDescriptor(JavaParserFacade.get(typeSolver).getType(param).describe().replace('.', '/'));
    }

    private static String toJVMDescriptor(String fullType) {
        return switch (fullType) {
            case "byte" -> "B";
            case "char" -> "C";
            case "double" -> "D";
            case "float" -> "F";
            case "int" -> "I";
            case "long" -> "J";
            case "short" -> "S";
            case "boolean" -> "Z";
            case "void" -> "V";
            default -> {
                if (fullType.endsWith("[]")) {
                    yield "[" + toJVMDescriptor(fullType.substring(0, fullType.length() - 2));
                }
                yield "L" + fullType.replace('.', '/') + ";";
            }
        };
    }
}
