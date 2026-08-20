package ru.levin.util.render.providers;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class ResourceProvider {
	public static final ShaderProgramKey TEXTURE_SHADER_KEY = new ShaderProgramKey(getShaderIdentifier("texture"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
	public static final ShaderProgramKey RECTANGLE_SHADER_KEY = new ShaderProgramKey(getShaderIdentifier("rectangle"), VertexFormats.POSITION_COLOR, Defines.EMPTY);
	public static final ShaderProgramKey BLUR_SHADER_KEY = new ShaderProgramKey(getShaderIdentifier("blur"), VertexFormats.POSITION_COLOR, Defines.EMPTY);
	public static final ShaderProgramKey RECTANGLE_BORDER_SHADER_KEY = new ShaderProgramKey(getShaderIdentifier("border"), VertexFormats.POSITION_COLOR, Defines.EMPTY);
	public static final ShaderProgramKey GLASS_SHADER_KEY = new ShaderProgramKey(getGlass("data"), VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);

	public static final Identifier firefly = Identifier.of("lupaware", "images/particles/firefly.png");
	public static final Identifier bloom = Identifier.of("lupaware", "images/particles/bloom.png");
	public static final Identifier snowflake = Identifier.of("lupaware", "images/particles/snowflake.png");
	public static final Identifier dollar = Identifier.of("lupaware", "images/particles/dollar.png");
	public static final Identifier heart = Identifier.of("lupaware", "images/particles/heart.png");
	public static final Identifier star = Identifier.of("lupaware", "images/particles/star.png");
	public static final Identifier spark = Identifier.of("lupaware", "images/particles/spark.png");
	public static final Identifier crown = Identifier.of("lupaware", "images/particles/crown.png");
	public static final Identifier lightning = Identifier.of("lupaware", "images/particles/lightning.png");
	public static final Identifier line = Identifier.of("lupaware", "images/particles/line.png");
	public static final Identifier point = Identifier.of("lupaware", "images/particles/point.png");
	public static final Identifier rhombus = Identifier.of("lupaware", "images/particles/rhombus.png");


	public static final Identifier marker = Identifier.of("lupaware", "images/targetesp/target.png");
	public static final Identifier marker2 = Identifier.of("lupaware", "images/targetesp/target2.png");
		public static final Identifier skeletonSkull = Identifier.of("lupaware", "textures/targetesp/skull_skeleton.png");


	public static final Identifier CUSTOM_CAPE = Identifier.of("lupaware", "cape/cape.png");
	public static final Identifier CUSTOM_ELYTRA = Identifier.of("lupaware", "cape/elytra.png");

	public static final Identifier container = Identifier.of("lupaware", "images/hud/container.png");

	public static final Identifier color_image = Identifier.of("lupaware", "images/gui/pick.png");


	private static Identifier getGlass(String name) {
		return Identifier.of("lupaware", "core/glass/" + name);
	}
	private static Identifier getShaderIdentifier(String name) {
		return Identifier.of("lupaware", "core/" + name);
	}
}