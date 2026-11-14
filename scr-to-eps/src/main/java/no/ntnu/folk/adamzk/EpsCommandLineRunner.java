package no.ntnu.folk.adamzk;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import no.ntnu.folk.adamzk.eps.EpsBuilder;

@Component
@Profile("!syntpop")
public class EpsCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EpsCommandLineRunner.class);

    private final EpsBuilder epsBuilder;

    public EpsCommandLineRunner(EpsBuilder epsBuilder) {
        this.epsBuilder = epsBuilder;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("A directory name must be provided.");
        }

        Path directory = Path.of(args[0]);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("The provided path is not a directory: " + directory);
        }

        logger.info("Creating EPS files for directory: {}", directory);
        epsBuilder.buildEps(directory);
    }
}
