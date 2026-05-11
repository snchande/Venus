# Build Venus Notebooks

Builds the Venus Notebooks project using Maven.

## Full build with tests
```bash
cd $VENUS_HOME && mvn clean package
```

## Fast build (skip tests)
```bash
cd $VENUS_HOME && mvn clean package -DskipTests
```

## Build output
The JAR will be at: `target/venus-notebooks-1.0.0-SNAPSHOT.jar`

## Run the built JAR
```bash
java --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     -jar target/venus-notebooks-1.0.0-SNAPSHOT.jar
```
