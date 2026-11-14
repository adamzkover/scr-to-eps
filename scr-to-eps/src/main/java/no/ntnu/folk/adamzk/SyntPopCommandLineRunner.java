package no.ntnu.folk.adamzk;

import java.nio.file.Path;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("syntpop")
public class SyntPopCommandLineRunner implements CommandLineRunner {

    private final Environment environment;
    private final SyntPopPatientConverter converter;

    public SyntPopCommandLineRunner(Environment environment, SyntPopPatientConverter converter) {
        this.environment = environment;
        this.converter = converter;
    }

    @Override
    public void run(String... args) throws Exception {
        String input = requiredProperty("syntpop.input");
        String index = requiredProperty("syntpop.index");
        String target = requiredProperty("syntpop.target");

        int personIndex;
        try {
            personIndex = Integer.parseInt(index);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SyntPop index must be a non-negative integer: " + index, e);
        }

        Path output = converter.convert(Path.of(input), personIndex, Path.of(target));
        System.out.println("Wrote EPS Patient resource to " + output);
    }

    private String requiredProperty(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required command-line option --" + name + ".");
        }
        return value;
    }
}
