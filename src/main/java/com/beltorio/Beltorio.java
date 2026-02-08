package com.beltorio;

import com.beltorio.block.ConveyorBeltBlock;
import com.beltorio.block.entity.ConveyorBeltBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class Beltorio implements ModInitializer {

    public static final String MOD_ID = "beltorio";

    public static final Block CONVEYOR_BELT_BLOCK = Registry.register(
            Registries.BLOCK,
            Identifier.of(MOD_ID, "conveyor_belt"),
            new ConveyorBeltBlock(AbstractBlock.Settings.create()
                    .strength(1.5f, 6.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque()
                    .requiresTool())
    );

    public static final Item CONVEYOR_BELT_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "conveyor_belt"),
            new BlockItem(CONVEYOR_BELT_BLOCK, new Item.Settings())
    );

    public static final BlockEntityType<ConveyorBeltBlockEntity> CONVEYOR_BELT_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "conveyor_belt"),
            FabricBlockEntityTypeBuilder.create(ConveyorBeltBlockEntity::new, CONVEYOR_BELT_BLOCK).build()
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(content -> {
            content.add(CONVEYOR_BELT_ITEM);
        });
    }
}
