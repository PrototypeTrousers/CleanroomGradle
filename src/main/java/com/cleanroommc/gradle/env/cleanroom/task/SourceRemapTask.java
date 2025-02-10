package com.cleanroommc.gradle.env.cleanroom.task;

import com.cleanroommc.gradle.api.patch.ModifiedSrgReader;
import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.github.abrarsyed.jastyle.constants.EnumFormatStyle;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
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
import org.gradle.api.tasks.Optional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class SourceRemapTask extends DefaultTask {

    static Pattern FUNC_ID = Pattern.compile("_(i?\\d+)_");
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
    static final ASFormatter formatter = new ASFormatter();
    static int paramOffset;

    @TaskAction
    public void remapSources() throws Exception {

        final Mercury mercury = new Mercury();
        mercury.getProcessors().add(MercuryRemapper.create(new ModifiedSrgReader(Files.newBufferedReader(getSrg().get().getAsFile().toPath(), StandardCharsets.UTF_8)).read(MappingSet.create()),false));

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

        formatter.setFormattingStyle(EnumFormatStyle.ALLMAN);
        formatter.setBreakClosingHeaderBracketsMode(true);
        formatter.setSwitchIndent(true);
        formatter.setMaxInStatementIndentLength(40);
        formatter.setOperatorPaddingMode(true);

        formatter.setParensUnPaddingMode(true);
        formatter.setBreakBlocksMode(true);
        formatter.setDeleteEmptyLinesMode(true);
        formatter.setUseProperInnerClassIndenting(false);

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

        if (!constructorMap.isEmpty()) {
            System.out.println( constructorMap.size() + "Unmatched constructors");
            for ( Map.Entry<String, Integer> entSet : constructorMap.entrySet()) {
                System.out.println(entSet.getValue() + ':' + entSet.getKey());
            }
        }
    }

    public static void main(String[] args) {
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
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLexicalPreservationEnabled(true);
        JavaParser parser = new JavaParser(config);

        formatter.setFormattingStyle(EnumFormatStyle.ALLMAN);
        formatter.setBreakClosingHeaderBracketsMode(true);

        formatter.setSwitchIndent(true);
        formatter.setMaxInStatementIndentLength(40);
        formatter.setOperatorPaddingMode(true);

        formatter.setParensUnPaddingMode(true);
        formatter.setBreakBlocksMode(true);
        formatter.setDeleteEmptyLinesMode(true);
        formatter.setUseProperInnerClassIndenting(false);

        try {
            parseFile(new File("/mnt/ldata/git/cleanroomarms/build/cg/versions/1.12.2/cleanroom/rerererer/net/minecraft/block/Block.java").toPath(),
                    parser, typeSolver);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

     static void parseFile(Path java, JavaParser parser, TypeSolver typeSolver) throws IOException {
         System.out.println("Parsing file? " + java.toString());

        CompilationUnit cu = parser.parse(java).getResult().get();
        cu = LexicalPreservingPrinter.setup(cu);

        cu.findAll(ConstructorDeclaration.class).forEach(constructor -> {
            paramOffset =0;
            String signature = generateBytecodeSignature(constructor, typeSolver);
            Integer idinteger = constructorMap.get(signature);
            if (idinteger == null) {
                System.out.println("Could not find id for " + signature);
                System.out.println(java.toString());
//                    getLogger().lifecycle("Could not find id for {}", signature);
//                    getLogger().lifecycle(java.toString());
            } else {
                constructorMap.remove(signature);
                int id = idinteger;

                int paramIdx = paramOffset + 1;

                for (int i = 0; i < constructor.getParameters().size(); i++) {
                    Parameter param = constructor.getParameter(i);

                    String newName = "p_i" + id + "_" + paramIdx + "_";

                    constructor.findAll(NameExpr.class).forEach(nameExpr -> {
                        if (nameExpr.equals(param.getNameAsExpression())) {
                            nameExpr.setName(newName);
                        }
                    });

                    param.setName(newName); // Rename based on index

                    Type paramType = param.getType();
                    if (paramType.isPrimitiveType()) {
                        PrimitiveType.Primitive primitiveParamtype = paramType.asPrimitiveType().getType();
                        if (primitiveParamtype == PrimitiveType.Primitive.DOUBLE ||
                                primitiveParamtype == PrimitiveType.Primitive.LONG) {
                            paramIdx += 2;
                            continue;
                        }
                    }
                    paramIdx++;
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

                    String newName = "p_" + methodId + "_" + paramIdx + "_";

                    method.findAll(NameExpr.class).forEach(nameExpr -> {
                        if (nameExpr.equals(param.getNameAsExpression())) {
                            nameExpr.setName(newName);
                        }
                    });

                    param.setName(newName); // Rename based on index

                    Type paramType = param.getType();
                    if (paramType.isPrimitiveType()) {
                        PrimitiveType.Primitive primitiveParamtype = paramType.asPrimitiveType().getType();
                        if (primitiveParamtype == PrimitiveType.Primitive.DOUBLE ||
                                primitiveParamtype == PrimitiveType.Primitive.LONG) {
                            paramIdx += 2;
                            continue;
                        }
                    }
                    paramIdx++;
                }
            }
        });

//        // Write the modified code back to the file
//        StringReader reader = new StringReader(LexicalPreservingPrinter.print(cu));
//        StringWriter writer = new StringWriter();
//        formatter.format(reader, writer);
//        Files.write(java, writer.toString().getBytes());
         Files.write(java, LexicalPreservingPrinter.print(cu).getBytes());
    }

    private static String generateBytecodeSignature(ConstructorDeclaration constructor, TypeSolver typeSolver) {
        // Get the declaring class name
        String className = constructor.resolve().declaringType().getClassName();
        String qualifiedClassName = constructor.resolve().declaringType().getQualifiedName();
        qualifiedClassName = qualifiedClassName.replace(className, className.replace('.','$'));

        List<String> parameterTypes = new ArrayList<>();

        // Check if the constructor belongs to an class
        java.util.Optional<ClassOrInterfaceDeclaration> parentClass = constructor.getParentNode()
                .filter(node -> node instanceof ClassOrInterfaceDeclaration)
                .map(node -> (ClassOrInterfaceDeclaration) node);

        if (parentClass.isPresent()) {
            boolean isInnerClass = parentClass.get().isInnerClass();
            boolean isNested = parentClass.get().isNestedType();
            boolean isStatic = parentClass.get().isStatic();
            boolean isPrivate = constructor.isPrivate();
            // Get the resolved parameter types

            if (isNested && isStatic && isPrivate) {
                String outerClassType = constructor.resolve().declaringType().asReferenceType().getQualifiedName();
                outerClassType = outerClassType.substring(0, outerClassType.lastIndexOf('.'));
                parameterTypes.add("L" + outerClassType.replace('.', '/') + "$1;");
                paramOffset++;
            }

            // Add the implicit outer class reference as the first parameter for inner classes
            if (isInnerClass) {
                String outerClassType = constructor.resolve().declaringType().asReferenceType().getQualifiedName();
                outerClassType = outerClassType.substring(0, outerClassType.lastIndexOf('.')); // Get the outer class
                parameterTypes.add("L" + outerClassType.replace('.', '/') + ";");
                paramOffset++;
            }
        }

        // Check if the constructor belongs to an enumClass
        java.util.Optional<EnumDeclaration> parentEnumClass = constructor.getParentNode()
                .filter(node -> node instanceof EnumDeclaration)
                .map(node -> (EnumDeclaration) node);

        if (parentEnumClass.isPresent()) {
            parameterTypes.add("Ljava/lang/String;I");
            paramOffset += 2;
        }

        // Add the explicit parameters
        parameterTypes.addAll(constructor.getParameters().stream()
                .map(p -> {
                    Type type = p.getType();
                    ResolvedType resolvedType = JavaParserFacade.get(typeSolver).getType(p);
                    return (p.isVarArgs() ? '[' : "") + getBytecodeTypeName(resolvedType);
                })
                .toList());

        // Combine into a bytecode-like signature
        String parameters = String.join("", parameterTypes);
        return qualifiedClassName.replace('.', '/') + ' ' + "(" + parameters + ")V";
    }

    private static String getBytecodeTypeName(ResolvedType resolvedType) {
        if (resolvedType.isReferenceType()) {
            // Erase generics by getting the base type
            ResolvedType baseType = resolvedType.asReferenceType();
            ResolvedReferenceTypeDeclaration typeDecl = baseType.asReferenceType().getTypeDeclaration().orElseThrow();
            String className = typeDecl.getClassName();
            String qualifiedName = typeDecl.getQualifiedName().replace(className, className.replace('.','$'));

            // Replace dots with slashes for bytecode format
            return "L" + qualifiedName.replace('.', '/') + ";";
        } else if (resolvedType.isPrimitive()) {
            // Map primitives to their bytecode descriptors
            return getPrimitiveBytecodeDescriptor(resolvedType.describe());
        } else if (resolvedType.isArray()) {
            // Handle arrays recursively
            return "[" + getBytecodeTypeName(resolvedType.asArrayType().getComponentType());
        } else if (resolvedType.isTypeVariable()) {
            if (resolvedType.asTypeParameter().hasLowerBound()) {
                return getBytecodeTypeName(resolvedType.asTypeParameter().getLowerBound());
            }
            else {
               return  "Ljava/lang/Object;";
            }
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + resolvedType);
        }
    }

    private static String getPrimitiveBytecodeDescriptor(String primitiveType) {
        switch (primitiveType) {
            case "int": return "I";
            case "boolean": return "Z";
            case "byte": return "B";
            case "char": return "C";
            case "short": return "S";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            case "void": return "V";
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + primitiveType);
        }
    }
}
