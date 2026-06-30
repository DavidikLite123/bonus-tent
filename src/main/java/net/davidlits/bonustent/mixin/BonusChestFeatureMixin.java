package net.davidlits.bonustent.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.BonusChestFeature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(BonusChestFeature.class)
public class BonusChestFeatureMixin {

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void onGenerate(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, DefaultFeatureConfig config, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
