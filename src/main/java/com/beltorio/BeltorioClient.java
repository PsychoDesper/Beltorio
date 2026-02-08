package com.beltorio;

import com.beltorio.client.MechanicalArmBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class BeltorioClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(Beltorio.CONVEYOR_BELT_BLOCK, RenderLayer.getCutout());
        BlockEntityRendererFactories.register(Beltorio.MECHANICAL_ARM_BLOCK_ENTITY, MechanicalArmBlockEntityRenderer::new);
    }
}
