package com.lootbeams;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringDecomposer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public abstract class LootBeamRenderer extends RenderType {

	/**
	 * ISSUES:
	 * Beam renders behind things like chests/clouds/water/beds/entities.
	 */

	private static final ResourceLocation LOOT_BEAM_TEXTURE = new ResourceLocation(LootBeams.MODID, "textures/entity/loot_beam.png");
	private static final RenderType LOOT_BEAM_RENDERTYPE = createRenderType();

	public LootBeamRenderer(String string, VertexFormat vertexFormat, VertexFormat.Mode mode, int i, boolean bl, boolean bl2, Runnable runnable, Runnable runnable2) {
		super(string, vertexFormat, mode, i, bl, bl2, runnable, runnable2);
	}

	public static void renderLootBeam(PoseStack stack, MultiBufferSource buffer, float pticks, long worldtime, ItemEntity item) {
		float beamRadius = 0.05f * LootBeams.config.beamRadius;
		float glowRadius = beamRadius + (beamRadius * 0.2f);
		float beamAlpha = LootBeams.config.beamAlpha;
		float beamHeight = LootBeams.config.beamHeight;
		float yOffset = LootBeams.config.beamYOffset;

		TextColor color = getItemColor(item);
		float R = ((color.getValue() >> 16) & 0xff) / 255f;
		float G = ((color.getValue() >> 8) & 0xff) / 255f;
		float B = (color.getValue() & 0xff) / 255f;

		//I will rewrite the beam rendering code soon! I promise!

		stack.pushPose();

		//Render main beam
		stack.pushPose();
		float rotation = (float) Math.floorMod(worldtime, 40L) + pticks;
		stack.mulPose(Vector3f.YP.rotationDegrees(rotation * 2.25F - 45.0F));
		stack.translate(0, yOffset, 0);
		stack.translate(0, 1, 0);
		stack.mulPose(Vector3f.XP.rotationDegrees(180));
		renderPart(stack, buffer.getBuffer(LOOT_BEAM_RENDERTYPE), R, G, B, beamAlpha, beamHeight, 0.0F, beamRadius, beamRadius, 0.0F, -beamRadius, 0.0F, 0.0F, -beamRadius);
		stack.mulPose(Vector3f.XP.rotationDegrees(-180));
		renderPart(stack, buffer.getBuffer(LOOT_BEAM_RENDERTYPE), R, G, B, beamAlpha, beamHeight, 0.0F, beamRadius, beamRadius, 0.0F, -beamRadius, 0.0F, 0.0F, -beamRadius);
		stack.popPose();

		//Render glow around main beam
		stack.translate(0, yOffset, 0);
		stack.translate(0, 1, 0);
		stack.mulPose(Vector3f.XP.rotationDegrees(180));
		renderPart(stack, buffer.getBuffer(LOOT_BEAM_RENDERTYPE), R, G, B, beamAlpha * 0.4f, beamHeight, -glowRadius, -glowRadius, glowRadius, -glowRadius, -beamRadius, glowRadius, glowRadius, glowRadius);
		stack.mulPose(Vector3f.XP.rotationDegrees(-180));
		renderPart(stack, buffer.getBuffer(LOOT_BEAM_RENDERTYPE), R, G, B, beamAlpha * 0.4f, beamHeight, -glowRadius, -glowRadius, glowRadius, -glowRadius, -beamRadius, glowRadius, glowRadius, glowRadius);

		stack.popPose();

		if (LootBeams.config.renderNametags) {
			renderNameTag(stack, buffer, item, color);
		}
	}

	private static void renderNameTag(PoseStack stack, MultiBufferSource buffer, ItemEntity item, TextColor color) {
		//If player is crouching or looking at the item
		if (Minecraft.getInstance().player.isCrouching() || (LootBeams.config.renderNametagsOnlook && isLookingAt(Minecraft.getInstance().player, item, LootBeams.config.nametagLookSensitivity))) {

			float foregroundAlpha = LootBeams.config.nametagTextAlpha;
			float backgroundAlpha = LootBeams.config.nametagBackgroundAlpha;
			double yOffset = LootBeams.config.nametagYOffset;
			int foregroundColor = (color.getValue() & 0xffffff) | ((int) (255 * foregroundAlpha) << 24);
			int backgroundColor = (color.getValue() & 0xffffff) | ((int) (255 * backgroundAlpha) << 24);

			stack.pushPose();

			//Render nametags at heights based on player distance
			stack.translate(0.0D, Math.min(1D, Minecraft.getInstance().player.distanceToSqr(item) * 0.025D) + yOffset, 0.0D);
			stack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

			float nametagScale = LootBeams.config.nametagScale;
			stack.scale(-0.02F * nametagScale, -0.02F * nametagScale, 0.02F * nametagScale);

			//Render stack counts on nametag
			Font fontrenderer = Minecraft.getInstance().font;
			String itemName = StringUtil.stripColor(item.getItem().getHoverName().getString());
			if (LootBeams.config.renderStackcount) {
				int count = item.getItem().getCount();
				if (count > 1) {
					itemName = itemName + " x" + count;
				}
			}

			//Move closer to the player so we dont render in beam, and render the tag
			stack.translate(0, 0, -10);
			RenderText(fontrenderer, stack, buffer, itemName, foregroundColor, backgroundColor, backgroundAlpha);

			//Render small tags
			stack.translate(0.0D, 10, 0.0D);
			stack.scale(0.75f, 0.75f, 0.75f);
			boolean textDrawn = false;
			List<Component> tooltip = item.getItem().getTooltipLines(null, TooltipFlag.Default.NORMAL);
			if (tooltip.size() >= 2) {
				Component tooltipRarity = tooltip.get(1);

				//Render dmcloot rarity small tags
				//NOFIX: as dmcloot has no support with Fabric and Minecraft 1.18.x, such support is commented out
//				if (LootBeams.config.DMCLOOT_COMPAT_RARITY.get() && FabricLoader.getInstance().isModLoaded("dmcloot")) {
//					if (item.getItem().hasTag() && item.getItem().getTag().contains("dmcloot.rarity")) {
//						Color rarityColor = LootBeams.config.WHITE_RARITIES.get() ? Color.WHITE : getRawColor(tooltipRarity);
//						TranslatableComponent translatedRarity = new TranslatableComponent("rarity.dmcloot." + item.getItem().getTag().getString("dmcloot.rarity"));
//						RenderText(fontrenderer, stack, buffer, translatedRarity.getString(), rarityColor.getRGB(), backgroundColor, backgroundAlpha);
//						textDrawn = true;
//					}
//				}

				//Render custom rarities
				if (!textDrawn && LootBeams.config.customRarities.contains(tooltipRarity.getString())) {
					TextColor rarityColor = LootBeams.config.whiteRarities ? TextColor.fromLegacyFormat(ChatFormatting.WHITE) : getRawColor(tooltipRarity);
					foregroundColor = (rarityColor.getValue() & 0xffffff) | ((int) (255 * foregroundAlpha) << 24);
					backgroundColor = (rarityColor.getValue() & 0xffffff) | ((int) (255 * backgroundAlpha) << 24);
					RenderText(fontrenderer, stack, buffer, tooltipRarity.getString(), foregroundColor, backgroundColor, backgroundAlpha);
				}
			}

			stack.popPose();
		}
	}

	private static void RenderText(Font fontRenderer, PoseStack stack, MultiBufferSource buffer, String text, int foregroundColor, int backgroundColor, float backgroundAlpha) {
		if (LootBeams.config.borders) {
			float w = -fontRenderer.width(text) / 2f;
			int bg = new Color(0, 0, 0, (int) (255 * backgroundAlpha)).getRGB();

			//Draws background (border) text
			fontRenderer.draw(stack, text, w + 1f, 0, bg);
			fontRenderer.draw(stack, text, w - 1f, 0, bg);
			fontRenderer.draw(stack, text, w, 1f, bg);
			fontRenderer.draw(stack, text, w, -1f, bg);

			//Draws foreground text in front of border
			stack.translate(0.0D, 0.0D, -0.01D);
			fontRenderer.draw(stack, text, w, 0, foregroundColor);
			stack.translate(0.0D, 0.0D, 0.01D);
		} else {
			fontRenderer.drawInBatch(text, (float) (-fontRenderer.width(text) / 2), 0f, foregroundColor, false, stack.last().pose(), buffer, false, backgroundColor, 15728864);
		}
	}

	/**
	 * Returns the color from the item's name, rarity, tag, or override.
	 */
	private static TextColor getItemColor(ItemEntity item) {
		if(LootBeams.CRASH_BLACKLIST.contains(item.getItem())) {
			return TextColor.fromLegacyFormat(ChatFormatting.WHITE);
		}

		try {

			//From Config Overrides
			TextColor override = Configuration.getColorFromItemOverrides(item.getItem().getItem());
			if (override != null) {
				return override;
			}

			//From NBT
			if (item.getItem().hasTag() && item.getItem().getTag().contains("lootbeams.color")) {
				return TextColor.parseColor(item.getItem().getTag().getString("lootbeams.color"));
			}

			//From Name
			if (LootBeams.config.renderNameColor) {
				TextColor nameColor = getRawColor(item.getItem().getHoverName());
				if (!nameColor.equals(TextColor.fromLegacyFormat(ChatFormatting.WHITE))) {
					return nameColor;
				}
			}

			//From Rarity
			if (LootBeams.config.renderRarityColor && item.getItem().getRarity().color != null) {
				return TextColor.fromLegacyFormat(item.getItem().getRarity().color);
			} else {
				return TextColor.fromLegacyFormat(ChatFormatting.WHITE);
			}
		} catch (Exception e) {
			LootBeams.LOGGER.error("Failed to get color for ("+ item.getItem().getDisplayName() + "), added to temporary blacklist");
			LootBeams.CRASH_BLACKLIST.add(item.getItem());
			LootBeams.LOGGER.info("Temporary blacklist is now : " );
			for(ItemStack s : LootBeams.CRASH_BLACKLIST){
				LootBeams.LOGGER.info(s.getDisplayName());
			}
			return TextColor.fromLegacyFormat(ChatFormatting.WHITE);
		}
	}

	/**
	 * Gets color from the first letter in the text component.
	 */
	private static TextColor getRawColor(Component text) {
		List<Style> list = Lists.newArrayList();
		text.visit((acceptor, styleIn) -> {
			StringDecomposer.iterateFormatted(styleIn, acceptor, (string, style, consumer) -> {
				list.add(style);
				return true;
			});
			return Optional.empty();
		}, Style.EMPTY);
		if (list.get(0).getColor() != null) {
			return list.get(0).getColor();
		}
		return TextColor.fromLegacyFormat(ChatFormatting.WHITE);
	}

	private static void renderPart(PoseStack stack, VertexConsumer builder, float red, float green, float blue, float alpha, float height, float radius_1, float radius_2, float radius_3, float radius_4, float radius_5, float radius_6, float radius_7, float radius_8) {
		PoseStack.Pose matrixentry = stack.last();
		Matrix4f matrixpose = matrixentry.pose();
		Matrix3f matrixnormal = matrixentry.normal();
		renderQuad(matrixpose, matrixnormal, builder, red, green, blue, alpha, height, radius_1, radius_2, radius_3, radius_4);
		renderQuad(matrixpose, matrixnormal, builder, red, green, blue, alpha, height, radius_7, radius_8, radius_5, radius_6);
		renderQuad(matrixpose, matrixnormal, builder, red, green, blue, alpha, height, radius_3, radius_4, radius_7, radius_8);
		renderQuad(matrixpose, matrixnormal, builder, red, green, blue, alpha, height, radius_5, radius_6, radius_1, radius_2);
	}

	private static void renderQuad(Matrix4f pose, Matrix3f normal, VertexConsumer builder, float red, float green, float blue, float alpha, float y, float z1, float texu1, float z, float texu) {
		addVertex(pose, normal, builder, red, green, blue, alpha, y, z1, texu1, 1f, 0f);
		addVertex(pose, normal, builder, red, green, blue, alpha, 0f, z1, texu1, 1f, 1f);
		addVertex(pose, normal, builder, red, green, blue, alpha, 0f, z, texu, 0f, 1f);
		addVertex(pose, normal, builder, red, green, blue, alpha, y, z, texu, 0f, 0f);
	}

	private static void addVertex(Matrix4f pose, Matrix3f normal, VertexConsumer builder, float red, float green, float blue, float alpha, float y, float x, float z, float texu, float texv) {
		builder.vertex(pose, x, y, z).color(red, green, blue, alpha).uv(texu, texv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
	}

	private static String toBinaryName(String mapName){
		return "L" + mapName.replace('.', '/') + ";";
	}

	private static RenderType createRenderType() {
		RenderType.CompositeState state = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(LOOT_BEAM_TEXTURE, false, false))
				.setLightmapState(LIGHTMAP)
				.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
				.setShaderState(RenderType.RENDERTYPE_TRANSLUCENT_SHADER)
				.setOverlayState(RenderStateShard.NO_OVERLAY)
				.setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
				.setWriteMaskState(RenderType.COLOR_WRITE).createCompositeState(false);
		try {
			Method method = RenderType.class.getDeclaredMethod(
					FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_1921", "method_24049",
							"(Ljava/lang/String;" + toBinaryName("net.minecraft.class_293")
									+ toBinaryName("net.minecraft.class_293$class_5596")
									+ "IZZ"
									+ toBinaryName("net.minecraft.class_1921$class_4688") + ")"
									+ toBinaryName("net.minecraft.class_1921$class_4687")),
					String.class, VertexFormat.class, VertexFormat.Mode.class, int.class, boolean.class, boolean.class, CompositeState.class);
			method.setAccessible(true);
			return (RenderType) method.invoke(null, "loot_beam", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 256, false, true, state);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return RenderType.entityTranslucent(LOOT_BEAM_TEXTURE, false);
	}

	/**
	 * Checks if the player is looking at the given entity, accuracy determines how close the player has to look.
	 */
	private static boolean isLookingAt(LocalPlayer player, Entity target, double accuracy) {
		Vec3 difference = new Vec3(target.getX() - player.getX(), target.getEyeY() - player.getEyeY(), target.getZ() - player.getZ());
		double length = difference.length();
		double dot = player.getViewVector(1.0F).normalize().dot(difference.normalize());
		return dot > 1.0D - accuracy / length && player.hasLineOfSight(target);
	}

}
