package in.abdulmajid.lifeclues;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LifecluesApplication {

	private static final String[] REQUIRED_KEYS =
			{ "LC_DB_URL", "LC_DB_USER", "LC_DB_PASSWORD", "LC_JWT_SECRET" };

	public static void main(String[] args) {
		Map<String, String> envFile = null;
		try {
			envFile = loadDotEnv(Path.of(".env"));
		} catch (IOException ex) {
			// Fall through: the friendly check below will report the real problem.
		}

		injectMissingIntoSystem(envFile);

		if (!checkRequiredEnv(envFile)) {
			System.exit(1);
		}
		SpringApplication.run(LifecluesApplication.class, args);
		System.out.println("Start Application ....");
	}

	/**
	 * Reads a KEY=VALUE {@code .env} file (our own loader, so it works whatever
	 * way the app is launched: ./dev.sh, mvnw spring-boot:run, an IDE or a jar).
	 *
	 * @return map of keys to values, or empty map if the file has no content
	 */
	private static Map<String, String> loadDotEnv(Path path) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		if (Files.exists(path)) {
			for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int eq = line.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String key = line.substring(0, eq).trim();
				String value = line.substring(eq + 1).trim();
				if (value.length() >= 2
						&& ((value.startsWith("\"") && value.endsWith("\""))
								|| (value.startsWith("'") && value.endsWith("'")))) {
					value = value.substring(1, value.length() - 1);
				}
				values.put(key, value);
			}
		}
		return values;
	}

	/**
	 * Puts values from the .env file into System properties for any key that is
	 * NOT already set in the environment. OS environment variables (set by
	 * ./dev.sh) keep their higher priority, so nothing is ever overwritten.
	 */
	private static void injectMissingIntoSystem(Map<String, String> envFile) {
		if (envFile == null) {
			return;
		}
		String missingDefault = "";
		for (Map.Entry<String, String> entry : envFile.entrySet()) {
			String key = entry.getKey();
			String current = System.getenv(key);
			if (current == null || current.isBlank()) {
				System.setProperty(key, entry.getValue());
			}
		}
	}

	/**
	 * Friendly startup check. A value counts as present if it is in the OS
	 * environment OR in the .env file next to the app. Missing values produce a
	 * clear one-line message instead of a big stack trace or a cryptic
	 * "Could not resolve placeholder" error.
	 */
	private static boolean checkRequiredEnv(Map<String, String> envFile) {
		List<String> missing = new ArrayList<>();
		for (String key : REQUIRED_KEYS) {
			String fromEnv = System.getenv(key);
			String fromFile = envFile == null ? null : envFile.get(key);
			if (isNullOrBlank(fromEnv) && isNullOrBlank(fromFile)) {
				missing.add(key);
			}
		}
		if (missing.isEmpty()) {
			return true;
		}
		System.out.println();
		System.out.println("LifeClues cannot start: missing required value(s): " + String.join(", ", missing));
		System.out.println("These live in the .env file inside the lifeclues/ folder.");
		System.out.println("  • Easiest fix — while inside the lifeclues/ folder run:   ./dev.sh");
		System.out.println("  • Or copy .env.example to .env, fill it in, then run from that folder:");
		System.out.println("      cp .env.example .env   # then edit it, then:");
		System.out.println("      ./mvnw spring-boot:run");
		System.out.println("  • Make sure you start it from the lifeclues/ folder, so the app can find .env.");
		System.out.println();
		return false;
	}

	private static boolean isNullOrBlank(String value) {
		return value == null || value.isBlank();
	}

}