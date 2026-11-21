package io.github.watdafawx.shapewright.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Virtual {@link BlockAndTintGetter} backed by a map of preview block states, optionally
 * falling back to a real level. Lets connected-texture mods (Fusion, Athena, CTM, Continuity,
 * Rechiseled, Chipped, etc.) see neighboring preview blocks when baking quads.
 */
public final class PreviewBlockGetter implements BlockAndTintGetter {

    private final @Nullable BlockAndTintGetter delegate;
    private final Map<BlockPos, BlockState> blocks;

    public PreviewBlockGetter(@Nullable BlockAndTintGetter delegate, Map<BlockPos, BlockState> blocks) {
        this.delegate = delegate;
        this.blocks = blocks;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos);
        if (state != null) return state;
        return delegate != null ? delegate.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        if (blocks.containsKey(pos)) return null;
        return delegate != null ? delegate.getBlockEntity(pos) : null;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (delegate != null) return delegate.getShade(direction, shade);
        if (!shade) return 1.0f;
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        if (delegate != null) return delegate.getLightEngine();
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getLightEngine() : null;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return delegate != null ? delegate.getBlockTint(pos, colorResolver) : 0xFFFFFF;
    }

    @Override
    public int getBrightness(LightLayer lightType, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return 15;
    }

    @Override
    public int getHeight() {
        return delegate != null ? delegate.getHeight() : 384;
    }

    @Override
    public int getMinBuildHeight() {
        return delegate != null ? delegate.getMinBuildHeight() : -64;
    }
}
