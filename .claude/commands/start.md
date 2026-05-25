# Start Arima Notebooks

Starts the Arima Notebooks server in development mode.

```bash
cd $BARISTA_HOME && mvn spring-boot:run
```

The server will start at http://localhost:8585

If you need to build first:
```bash
cd $BARISTA_HOME && mvn clean package -DskipTests && mvn spring-boot:run
```
