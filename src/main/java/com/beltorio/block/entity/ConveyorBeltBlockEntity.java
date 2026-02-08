package com.beltorio.block.entity;

import com.beltorio.Beltorio;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ConveyorBeltBlockEntity extends BlockEntity {

    public ConveyorBeltBlockEntity(BlockPos pos, BlockState state) {
        super(Beltorio.CONVEYOR_BELT_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ConveyorBeltBlockEntity blockEntity) {
        // Placeholder for future item transport logic
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
    }
}
