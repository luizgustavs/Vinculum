package com.vinculum;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Applies the configured gitignore-like rules to mod JAR paths. */
public final class JarFilter {
	private JarFilter() {
	}

	public static boolean shouldManage(ModContainer container) {
		return container.getOrigin().getPaths().stream()
			.filter(path -> path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
			.findFirst()
			.map(JarFilter::shouldManage)
			.orElse(true);
	}

	public static boolean shouldManage(Path jar) {
		VinculumConfig.Config config = VinculumConfig.get();
		if (config.filterMode() == VinculumConfig.FilterMode.NONE) {
			return true;
		}
		String path = relativePath(jar);
		boolean matches = config.filterRules().stream().anyMatch(rule -> matches(rule, path));
		return config.filterMode() == VinculumConfig.FilterMode.WHITELIST ? matches : !matches;
	}

	static boolean matches(String rule, String path) {
		String normalized = rule.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		boolean directory = normalized.endsWith("/");
		if (directory) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isEmpty()) {
			return false;
		}
		boolean pathRule = normalized.indexOf('/') >= 0;
		String target = pathRule || directory ? path : fileName(path);
		String regex = globRegex(normalized);
		if (directory) {
			regex += "(?:/.*)?";
		}
		return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(target).matches();
	}

	private static String relativePath(Path jar) {
		Path absolute = jar.toAbsolutePath().normalize();
		Path mods = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
		String value = absolute.startsWith(mods) ? mods.relativize(absolute).toString() : jar.getFileName().toString();
		return value.replace('\\', '/');
	}

	private static String fileName(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	private static String globRegex(String glob) {
		StringBuilder regex = new StringBuilder();
		for (int index = 0; index < glob.length(); index++) {
			char current = glob.charAt(index);
			if (current == '*') {
				if (index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
					regex.append(".*");
					index++;
				} else {
					regex.append("[^/]*");
				}
			} else if (current == '?') {
				regex.append("[^/]");
			} else {
				if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
					regex.append('\\');
				}
				regex.append(current);
			}
		}
		return regex.toString();
	}
}
