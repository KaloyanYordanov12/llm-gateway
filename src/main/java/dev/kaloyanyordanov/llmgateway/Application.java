package dev.kaloyanyordanov.llmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point for the LLM Gateway.
 */
@SpringBootApplication
public class Application {

    protected Application() {
        // Utility bootstrap class; not meant to be instantiated directly.
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
