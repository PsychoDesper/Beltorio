package com.beltorio.block.entity;

import com.beltorio.Beltorio;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public class MechanicalArmBlockEntity extends BlockEntity {

    private ItemStack heldItem = ItemStack.EMPTY;
    private int transferInterval = 20;
    private int cooldown = 0;

    public MechanicalArmBlockEntity(BlockPos pos, BlockState state) {
        super(Beltorio.MECHANICAL_ARM_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MechanicalArmBlockEntity arm) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockPos inputPos = pos.offset(facing.getOpposite());
        BlockPos outputPos = pos.offset(facing);

        // 持有物品时每 tick 尝试放置
        if (!arm.heldItem.isEmpty()) {
            arm.tryPlace(world, outputPos);
            return; // 放置期间不拾取新物品
        }

        // 空手时才计算拾取冷却
        arm.cooldown++;
        if (arm.cooldown < arm.transferInterval) {
            return;
        }
        arm.cooldown = 0;
        arm.tryPickup(world, inputPos);
    }

    private void tryPickup(World world, BlockPos inputPos) {
        BlockEntity be = world.getBlockEntity(inputPos);
        if (be instanceof Inventory inventory) {
            // 反向遍历：优先取输出槽（熔炉槽位2=输出）
            for (int i = inventory.size() - 1; i >= 0; i--) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    heldItem = stack.split(1);
                    inventory.markDirty();
                    markDirty();
                    return;
                }
            }
        }

        // No container or container empty — scan for ItemEntity
        Box box = new Box(inputPos);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box,
                item -> !item.cannotPickup());
        if (!items.isEmpty()) {
            ItemEntity itemEntity = items.get(0);
            ItemStack entityStack = itemEntity.getStack();
            heldItem = entityStack.split(1);
            if (entityStack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setPickupDelay(10); // 防止多机械臂争抢
            }
            markDirty();
        }
    }

    private void tryPlace(World world, BlockPos outputPos) {
        BlockEntity be = world.getBlockEntity(outputPos);
        if (be instanceof Inventory inventory) {
            // First pass: merge into existing matching stacks
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack slot = inventory.getStack(i);
                if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, heldItem)
                        && slot.getCount() < slot.getMaxCount()
                        && slot.getCount() < inventory.getMaxCountPerStack()) {
                    slot.increment(1);
                    heldItem = ItemStack.EMPTY;
                    inventory.markDirty();
                    markDirty();
                    return;
                }
            }
            // Second pass: place into empty slot
            for (int i = 0; i < inventory.size(); i++) {
                if (inventory.getStack(i).isEmpty()) {
                    inventory.setStack(i, heldItem);
                    heldItem = ItemStack.EMPTY;
                    inventory.markDirty();
                    markDirty();
                    return;
                }
            }
            // Container full — keep holding, retry next tick
            return;
        }

        // No container — spawn ItemEntity at output center
        double x = outputPos.getX() + 0.5;
        double y = outputPos.getY() + 0.5;
        double z = outputPos.getZ() + 0.5;
        ItemEntity itemEntity = new ItemEntity(world, x, y, z, heldItem.copy());
        itemEntity.setVelocity(0, 0, 0);
        world.spawnEntity(itemEntity);
        heldItem = ItemStack.EMPTY;
        markDirty();
    }

    public void dropHeldItem() {
        if (!heldItem.isEmpty() && world != null) {
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), heldItem);
            heldItem = ItemStack.EMPTY;
            markDirty();
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("TransferInterval", transferInterval);
        nbt.putInt("Cooldown", cooldown);
        if (!heldItem.isEmpty()) {
            nbt.put("HeldItem", heldItem.encode(registryLookup));
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        transferInterval = nbt.getInt("TransferInterval");
        if (transferInterval <= 0) {
            transferInterval = 20;
        }
        cooldown = nbt.getInt("Cooldown");
        if (nbt.contains("HeldItem", NbtElement.COMPOUND_TYPE)) {
            heldItem = ItemStack.fromNbt(registryLookup, nbt.get("HeldItem")).orElse(ItemStack.EMPTY);
        } else {
            heldItem = ItemStack.EMPTY;
        }
    }
}
