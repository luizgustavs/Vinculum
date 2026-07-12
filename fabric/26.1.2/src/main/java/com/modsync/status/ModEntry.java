package com.modsync.status;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record ModEntry(String id, String name, String version, String environment, String fileName, List<String> authors, long fileSize) {
	public static final Codec<ModEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("id").forGetter(ModEntry::id),
		Codec.STRING.fieldOf("name").forGetter(ModEntry::name),
		Codec.STRING.fieldOf("version").forGetter(ModEntry::version),
		Codec.STRING.optionalFieldOf("environment", "universal").forGetter(ModEntry::environment),
		Codec.STRING.optionalFieldOf("file", "").forGetter(ModEntry::fileName),
		Codec.STRING.listOf().optionalFieldOf("authors", List.of()).forGetter(ModEntry::authors),
		Codec.LONG.optionalFieldOf("size", -1L).forGetter(ModEntry::fileSize)
	).apply(instance, ModEntry::new));

	public boolean clientOnly() {
		return this.environment.equals("client");
	}
}
