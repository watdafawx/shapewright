package io.github.watdafawx.shapewright.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.watdafawx.shapewright.platform.services.IPlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return !FMLLoader.isProduction();
    }

    @Override
    public void renderPreviewBlock(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                   PoseStack poseStack, VertexConsumer consumer, boolean checkSides) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var model = dispatcher.getBlockModel(state);
        long seed = state.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        ModelData modelData = model.getModelData(level, pos, state, ModelData.EMPTY);
        var renderTypes = model.getRenderTypes(state, random, modelData);
        if (renderTypes.isEmpty()) {
            dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, poseStack, consumer,
                    checkSides, RandomSource.create(seed), seed, OverlayTexture.NO_OVERLAY, modelData, null);
        } else {
            for (RenderType rt : renderTypes) {
                dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, poseStack, consumer,
                        checkSides, RandomSource.create(seed), seed, OverlayTexture.NO_OVERLAY, modelData, rt);
            }
        }
    }

    @Override
    public List<BakedQuad> getPreviewQuads(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                           @Nullable Direction side) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        long seed = state.getSeed(pos);
        RandomSource random = RandomSource.create(seed);
        ModelData modelData = model.getModelData(level, pos, state, ModelData.EMPTY);
        var renderTypes = model.getRenderTypes(state, random, modelData);
        if (renderTypes.isEmpty()) {
            return model.getQuads(state, side, RandomSource.create(seed), modelData, null);
        }
        List<BakedQuad> out = new ArrayList<>();
        for (RenderType rt : renderTypes) {
            out.addAll(model.getQuads(state, side, RandomSource.create(seed), modelData, rt));
        }
        return out;
    }
}