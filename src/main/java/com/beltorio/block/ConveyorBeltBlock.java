package com.beltorio.block;

import com.beltorio.Beltorio;
import com.beltorio.block.entity.ConveyorBeltBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class ConveyorBeltBlock extends Block implements BlockEntityProvider, Waterloggable {

    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 3, 16);

    private static final double BELT_SPEED = 0.08;
    private static final double MAX_BELT_VELOCITY = 0.3;

    public ConveyorBeltBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient()) {
            return;
        }

        Direction facing = state.get(FACING);
        Vec3d direction = Vec3d.of(facing.getVector()).multiply(BELT_SPEED);

        double vx = entity.getVelocity().x + direction.x;
        double vz = entity.getVelocity().z + direction.z;

        vx = Math.max(-MAX_BELT_VELOCITY, Math.min(MAX_BELT_VELOCITY, vx));
        vz = Math.max(-MAX_BELT_VELOCITY, Math.min(MAX_BELT_VELOCITY, vz));

        entity.addVelocity(direction.x, 0, direction.z);

        // Clamp final velocity
        Vec3d currentVel = entity.getVelocity();
        double clampedX = Math.max(-MAX_BELT_VELOCITY, Math.min(MAX_BELT_VELOCITY, currentVel.x));
        double clampedZ = Math.max(-MAX_BELT_VELOCITY, Math.min(MAX_BELT_VELOCITY, currentVel.z));
        entity.setVelocity(clampedX, currentVel.y, clampedZ);

        entity.velocityModified = true;

        if (entity instanceof ItemEntity itemEntity) {
            itemEntity.setPickupDelay(10);
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBeltBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) {
            return null;
        }
        return type == Beltorio.CONVEYOR_BELT_BLOCK_ENTITY
                ? (tickWorld, tickPos, tickState, blockEntity) ->
                    ConveyorBeltBlockEntity.tick(tickWorld, tickPos, tickState, (ConveyorBeltBlockEntity) blockEntity)
                : null;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            world.removeBlockEntity(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
