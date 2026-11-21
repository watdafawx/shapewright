package io.github.watdafawx.shapewright.platform.services;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {

        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Renders a block into a preview buffer with neighbor context so connected-texture mods
     * (Fusion, Athena, CTM, Continuity, etc.) can resolve connections.
     */
    default void renderPreviewBlock(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                    PoseStack poseStack, VertexConsumer consumer, boolean checkSides) {
        Minecraft.getInstance().getBlockRenderer().renderBatched(
                state, pos, level, poseStack, consumer, checkSides, RandomSource.create(state.getSeed(pos)));
    }

    /**
     * Returns the baked quads for one face (or unculled if {@code side == null}) of a block
     * in a virtual preview level, including loader-specific connected-texture model data.
     */
    default List<BakedQuad> getPreviewQuads(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                            @Nullable Direction side) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        return model.getQuads(state, side, RandomSource.create(state.getSeed(pos)));
    }
}