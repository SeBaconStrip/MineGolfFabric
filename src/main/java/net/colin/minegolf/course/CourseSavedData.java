package net.colin.minegolf.course;

import com.mojang.serialization.Codec;
import net.colin.minegolf.MineGolf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All courses of the server, stored once per world. */
public final class CourseSavedData extends SavedData {
	private static final Codec<CourseSavedData> CODEC = Course.CODEC.listOf()
			.xmap(CourseSavedData::new, data -> List.copyOf(data.courses.values()));

	// Command storage fixes do nothing to foreign data, so they are safe to borrow
	public static final SavedDataType<CourseSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(MineGolf.MOD_ID, "courses"), CourseSavedData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Course> courses = new LinkedHashMap<>();

	public CourseSavedData() {
	}

	private CourseSavedData(List<Course> list) {
		for (Course c : list) courses.put(c.id(), c);
	}

	public static CourseSavedData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Course create(String name, UUID owner) {
		Course course = new Course(UUID.randomUUID(), name, owner, List.of());
		courses.put(course.id(), course);
		setDirty();
		return course;
	}

	public @Nullable Course get(UUID id) {
		return courses.get(id);
	}
}
