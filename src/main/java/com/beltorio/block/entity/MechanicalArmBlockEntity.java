package com.beltorio.block.entity;

import com.beltorio.Beltorio;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public class MechanicalArmBlockEntity extends BlockEntity {

    public enum ArmAnimState {
        IDLE,
        GRABBING,
        RETURNING_FROM_GRAB,
        PLACING,
        RETURNING_FROM_PLACE
    }

    public static final int ANIM_DURATION = 10;
    private static final float MAX_ROTATION_DEG = 35.0f;
    private static final float MAX_ROTATION_RAD = (float) Math.toRadians(MAX_ROTATION_DEG);

    private ItemStack heldItem = ItemStack.EMPTY;
    private ArmAnimState animState = ArmAnimState.IDLE;
    private int animTick = 0;

    // Client-only counter, not serialized
    private int clientAnimTick = 0;

    public MechanicalArmBlockEntity(BlockPos pos, BlockState state) {
        super(Beltorio.MECHANICAL_ARM_BLOCK_ENTITY, pos, state);
    }

    // ── Server tick ──────────────────────────────────────────────────────
    public static void tick(World world, BlockPos pos, BlockState state, MechanicalArmBlockEntity arm) {
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        BlockPos inputPos = pos.offset(facing.getOpposite());
        BlockPos outputPos = pos.offset(facing);

        switch (arm.animState) {
            case IDLE -> {
                if (!arm.heldItem.isEmpty()) {
                    arm.transitionTo(ArmAnimState.PLACING, world, pos, state);
                } else if (arm.hasPickupTarget(world, inputPos)) {
                    arm.transitionTo(ArmAnimState.GRABBING, world, pos, state);
                }
            }
            case GRABBING -> {
                arm.animTick++;
                if (arm.animTick >= ANIM_DURATION) {
                    arm.tryPickup(world, inputPos);
                    arm.transitionTo(ArmAnimState.RETURNING_FROM_GRAB, world, pos, state);
                }
            }
            case RETURNING_FROM_GRAB -> {
                arm.animTick++;
                if (arm.animTick >= ANIM_DURATION) {
                    if (!arm.heldItem.isEmpty()) {
                        arm.transitionTo(ArmAnimState.PLACING, world, pos, state);
                    } else {
                        arm.transitionTo(ArmAnimState.IDLE, world, pos, state);
                    }
                }
            }
            case PLACING -> {
                arm.animTick++;
                if (arm.animTick >= ANIM_DURATION) {
                    arm.tryPlace(world, outputPos);
                    arm.transitionTo(ArmAnimState.RETURNING_FROM_PLACE, world, pos, state);
                }
            }
            case RETURNING_FROM_PLACE -> {
                arm.animTick++;
                if (arm.animTick >= ANIM_DURATION) {
                    if (arm.heldItem.isEmpty()) {
                        arm.transitionTo(ArmAnimState.IDLE, world, pos, state);
                    } else {
                        // Place failed (container full), retry
                        arm.transitionTo(ArmAnimState.PLACING, world, pos, state);
                    }
                }
            }
        }
    }

    // ── Client tick ──────────────────────────────────────────────────────
    public static void clientTick(World world, BlockPos pos, BlockState state, MechanicalArmBlockEntity arm) {
        if (arm.animState != ArmAnimState.IDLE) {
            arm.clientAnimTick++;
        }
    }

    // ── Pickup target detection (no side-effects) ────────────────────────
    private boolean hasPickupTarget(World world, BlockPos inputPos) {
        BlockEntity be = world.getBlockEntity(inputPos);
        if (be instanceof Inventory inventory) {
            for (int i = inventory.size() - 1; i >= 0; i--) {
                if (!inventory.getStack(i).isEmpty()) {
                    return true;
                }
            }
        }
        Box box = new Box(inputPos);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive);
        return !items.isEmpty();
    }

    // ── Pickup / place logic ─────────────────────────────────────────────
    private void tryPickup(World world, BlockPos inputPos) {
        BlockEntity be = world.getBlockEntity(inputPos);
        if (be instanceof Inventory inventory) {
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

        // No container or empty — scan for ItemEntity (isAlive allows conveyor items)
        Box box = new Box(inputPos);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box, ItemEntity::isAlive);
        if (!items.isEmpty()) {
            ItemEntity itemEntity = items.get(0);
            ItemStack entityStack = itemEntity.getStack();
            heldItem = entityStack.split(1);
            if (entityStack.isEmpty()) {
                itemEntity.discard();
            }
            markDirty();
        }
    }

    private void tryPlace(World world, BlockPos outputPos) {
        BlockEntity be = world.getBlockEntity(outputPos);
        if (be instanceof Inventory inventory) {
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
            for (int i = 0; i < inventory.size(); i++) {
                if (inventory.getStack(i).isEmpty()) {
                    inventory.setStack(i, heldItem);
                    heldItem = ItemStack.EMPTY;
                    inventory.markDirty();
                    markDirty();
                    return;
                }
            }
            // Container full — keep holding, will retry next cycle
            return;
        }

        // No container — spawn ItemEntity
        double x = outputPos.getX() + 0.5;
        double y = outputPos.getY() + 0.5;
        double z = outputPos.getZ() + 0.5;
        ItemEntity itemEntity = new ItemEntity(world, x, y, z, heldItem.copy());
        itemEntity.setVelocity(0, 0, 0);
        world.spawnEntity(itemEntity);
        heldItem = ItemStack.EMPTY;
        markDirty();
    }

    // ── State transition ─────────────────────────────────────────────────
    private void transitionTo(ArmAnimState newState, World world, BlockPos pos, BlockState state) {
        this.animState = newState;
        this.animTick = 0;
        markDirty();
        world.updateListeners(pos, state, state, 3); // NOTIFY_ALL
    }

    // ── Animation queries (for renderer) ─────────────────────────────────
    public ArmAnimState getAnimState() {
        return animState;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public float getAnimationProgress(float partialTick) {
        if (animState == ArmAnimState.IDLE) return 0f;
        return MathHelper.clamp((clientAnimTick + partialTick) / ANIM_DURATION, 0f, 1f);
    }

    /**
     * Returns arm pitch rotation in radians.
     * Negative = tilts toward input (grabbing), positive = tilts toward output (placing).
     */
    public float getArmRotationRad(float partialTick) {
        float progress = getAnimationProgress(partialTick);
        return switch (animState) {
            case IDLE -> 0f;
            case GRABBING -> -progress * MAX_ROTATION_RAD;
            case RETURNING_FROM_GRAB -> -(1f - progress) * MAX_ROTATION_RAD;
            case PLACING -> progress * MAX_ROTATION_RAD;
            case RETURNING_FROM_PLACE -> (1f - progress) * MAX_ROTATION_RAD;
        };
    }

    // ── Drop ─────────────────────────────────────────────────────────────
    public void dropHeldItem() {
        if (!heldItem.isEmpty() && world != null) {
            ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), heldItem);
            heldItem = ItemStack.EMPTY;
            markDirty();
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("AnimState", animState.ordinal());
        nbt.putInt("AnimTick", animTick);
        if (!heldItem.isEmpty()) {
            nbt.put("HeldItem", heldItem.encode(registryLookup));
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);

        ArmAnimState oldState = this.animState;
        int stateOrd = nbt.getInt("AnimState");
        ArmAnimState[] values = ArmAnimState.values();
        this.animState = (stateOrd >= 0 && stateOrd < values.length) ? values[stateOrd] : ArmAnimState.IDLE;
        this.animTick = nbt.getInt("AnimTick");

        if (oldState != this.animState) {
            this.clientAnimTick = 0;
        }

        if (nbt.contains("HeldItem", NbtElement.COMPOUND_TYPE)) {
            heldItem = ItemStack.fromNbt(registryLookup, nbt.get("HeldItem")).orElse(ItemStack.EMPTY);
        } else {
            heldItem = ItemStack.EMPTY;
        }
    }

    // ── Network sync ─────────────────────────────────────────────────────
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }
}
