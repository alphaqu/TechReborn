/*
 * This file is part of RebornCore, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 TeamReborn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package reborncore.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.registry.Registry;
import org.apache.commons.io.FileUtils;

import java.io.File;

/**
 * Initially take from https://github.com/JamiesWhiteShirt/developer-mode/tree/experimental-item-render and then ported to 1.15
 * Thanks 2xsaiko for fixing the lighting + odd issues above
 */
public class ItemStackRenderer implements HudRenderCallback {

	@Override
	public void onHudRender(MatrixStack matrixStack, float v) {
		if (!ItemStackRenderManager.RENDER_QUEUE.isEmpty()) {

			MinecraftClient.getInstance().textRenderer.draw(matrixStack, "Rendering " + ItemStackRenderManager.RENDER_QUEUE.size() + " items left", 5, 5, -1);

			ItemStack itemStack = ItemStackRenderManager.RENDER_QUEUE.poll();
			export(matrixStack, itemStack, 512, Registry.ITEM.getId(itemStack.getItem()));
		}
	}

	private void export(MatrixStack matrixStack, ItemStack stack, int size, Identifier identifier) {
		File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "item_renderer/" + identifier.getNamespace());
		if (!dir.exists()) {
			dir.mkdir();
		}
		File file = new File(dir, identifier.getPath() + ".png");

		if (file.exists()) {
			file.delete();
		}

		MinecraftClient minecraft = MinecraftClient.getInstance();

		if (minecraft.getItemRenderer() == null || minecraft.world == null) {
			return;
		}

		final Framebuffer framebuffer = new SimpleFramebuffer(size, size, true, true);
		framebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
		framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);

		framebuffer.beginWrite(true);

		final ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
		final BakedModel model = itemRenderer.getHeldItemModel(stack, minecraft.world, minecraft.player, 0);
		
		// FIXME 1.17
		Matrix4f matrix4f = Matrix4f.projectionMatrix(-1, 1, 1, -1, -100.0F, -100.0F); // ortho
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(matrix4f);

		matrixStack.push();
		matrixStack.loadIdentity();
		RenderSystem.applyModelViewMatrix();

		{
			minecraft.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).setFilter(false, false);
			RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

			RenderSystem.enableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			MatrixStack matrixStack2 = new MatrixStack();

			matrixStack2.scale(2F, -2F, 1F);

			boolean frontLit = !model.isSideLit();
			if (frontLit) {
				DiffuseLighting.disableGuiDepthLighting();
			}

			VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
			itemRenderer.renderItem(stack, ModelTransformation.Mode.GUI, false, matrixStack2, immediate, 15728880, OverlayTexture.DEFAULT_UV, model);
			immediate.draw();

			RenderSystem.enableDepthTest();

			if (frontLit) {
				DiffuseLighting.enableGuiDepthLighting();
			}
		}

		matrixStack.pop();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();

		framebuffer.endWrite();

		try (NativeImage nativeImage = new NativeImage(size, size, false)) {
			RenderSystem.bindTexture(framebuffer.getColorAttachment());
			nativeImage.loadFromTextureImage(0, false);
			nativeImage.mirrorVertically();

			try {
				byte[] bytes = nativeImage.getBytes();
				FileUtils.writeByteArrayToFile(file, bytes);
				System.out.println("Wrote " + file.getAbsolutePath());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		framebuffer.delete();
	}
}
