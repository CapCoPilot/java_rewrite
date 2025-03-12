# java_rewrite

# Java Method Rewriter

A tool to replace method bodies in Java source files while preserving comments and formatting.

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run --args="path/to/JavaFile.java -m methodName -r path/to/replacement.txt"
```

## Options

- `-m, --method-name`: The name of the method to replace (required)
- `-r, --replacement`: The file containing the replacement method body (required)
- `-c, --class-name`: The fully qualified name of the class containing the method
- `-o, --output`: The output file (defaults to overwriting the source file)
- `-d, --dry-run`: Print the result without modifying any files

## Example

```bash
./gradlew run --args="src/main/java/com/example/javarewrite/SampleClass.java -m processString -r replacement.txt -d"
```
