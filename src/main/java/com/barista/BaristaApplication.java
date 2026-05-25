package com.barista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arima Notebooks - Interactive Java notebook environment.
 *
 * Starts an embedded Tomcat server at http://localhost:8585
 * Serves the single-page web UI from src/main/resources/static/
 *
 * JVM flags required for JShell access:
 *   --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED
 *   --add-opens=java.base/java.lang=ALL-UNNAMED
 *   --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED
 */
@SpringBootApplication
public class BaristaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaristaApplication.class, args);
    }
}
