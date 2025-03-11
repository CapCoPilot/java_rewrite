package com.example.javarewrite;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "java-rewriter",
    description = "Replace methods in Java source files while preserving comments and formatting",
    mixinStandardHelpOptions = true,
    version = "1.0"
)
public class JavaRewriter implements Callable<Integer> {

    @Parameters(index = "0", description = "The Java source file to modify")
    private File sourceFile;

    @Option(names = {"-c", "--class-name"}, description = "The fully qualified name of the class containing the method")
    private String className;

    @Option(names = {"-m", "--method-name"}, description = "The name of the method to replace", required = true)
    private String methodName;

    @Option(names = {"-r", "--replacement"}, description = "The file containing the replacement method body", required = true)
    private File replacementFile;

    @Option(names = {"-o", "--output"}, description = "The output file (defaults to overwriting the source file)")
    private File outputFile;

    @Option(names = {"-d", "--dry-run"}, description = "Print the result without modifying any files")
    private boolean dryRun = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JavaRewriter()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        // Configure JavaParser with symbol resolution and comment attribution
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setAttributeComments(true);
        
        JavaParser javaParser = new JavaParser(config);
        
        try {
            
            // Parse the source file
            CompilationUnit cu = javaParser.parse(sourceFile)
                    .getResult()
                    .orElseThrow(() -> new RuntimeException("Failed to parse the source file"));
            
            // Enable lexical preservation to maintain formatting and comments
            LexicalPreservingPrinter.setup(cu);
            
            // Read the replacement method body
            String replacementBody = Files.readString(replacementFile.toPath());
            
            // Find and replace the method
            boolean methodFound = replaceMethod(cu, methodName, replacementBody);
            
            if (!methodFound) {
                System.err.println("Method '" + methodName + "' not found in the source file.");
                return 1;
            }
            
            // Get the modified source code
            String modifiedSource = LexicalPreservingPrinter.print(cu);
            
            if (dryRun) {
                // Print the result without modifying any files
                System.out.println(modifiedSource);
            } else {
                // Write the result to the output file or overwrite the source file
                Path outputPath = outputFile != null ? outputFile.toPath() : sourceFile.toPath();
                Files.writeString(outputPath, modifiedSource);
                System.out.println("Successfully replaced method '" + methodName + "' in " + outputPath);
            }
            
            return 0;
        } catch (FileNotFoundException e) {
            System.err.println("Source file not found: " + sourceFile);
            return 1;
        } catch (IOException e) {
            System.err.println("Error reading or writing files: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
    
    private boolean replaceMethod(CompilationUnit cu, String methodName, String replacementBody) {
        final boolean[] methodFound = {false};
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                
                if (method.getNameAsString().equals(methodName)) {
                    if (className == null || method.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                            .map(td -> td.getNameAsString().equals(getSimpleClassName(className)))
                            .orElse(false)) {
                        
                        // Parse the replacement method body
                        try {
                            // Configure parser to attach comments to closest nodes
                            ParserConfiguration config = new ParserConfiguration()
                                    .setAttributeComments(true);
                            JavaParser parser = new JavaParser(config);
                            
                            // We need to create a valid method structure that includes the replacement body
                            String methodTemplate = 
                                    "class Temp {\n" +
                                    "    public void dummyMethod() {\n" + 
                                    replacementBody + "\n" +
                                    "    }\n" +
                                    "}";
                            
                            CompilationUnit tempCu = parser.parse(methodTemplate)
                                    .getResult()
                                    .orElseThrow(() -> new RuntimeException("Failed to parse the replacement method"));
                            
                            // Extract the body from the temporary method
                            MethodDeclaration replacementMethod = tempCu.findFirst(MethodDeclaration.class)
                                    .orElseThrow(() -> new RuntimeException("Failed to find method in parsed code"));
                            
                            // Get the body with its comments
                            method.setBody(replacementMethod.getBody().orElseThrow());
                            methodFound[0] = true;
                            
                            // Debug output
                            System.err.println("Method body replaced successfully");
                        } catch (Exception e) {
                            System.err.println("Failed to replace method body: " + e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException("Failed to parse the replacement method body", e);
                        }
                    }
                }
            }
        }, null);
        
        return methodFound[0];
    }
    
    private String getSimpleClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot == -1 ? fullyQualifiedName : fullyQualifiedName.substring(lastDot + 1);
    }
}