package dev.legacywhisperer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LegacyWhispererApplication {

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("analyze")) {
            System.exit(Cli.run(args));
        }
        SpringApplication.run(LegacyWhispererApplication.class, args);
    }
}
