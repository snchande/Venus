# Start Venus Notebooks

Starts the Venus Notebooks server in development mode.

```bash
cd $VENUS_HOME && mvn spring-boot:run
```

The server will start at http://localhost:8585

If you need to build first:
```bash
cd $VENUS_HOME && mvn clean package -DskipTests && mvn spring-boot:run
```
