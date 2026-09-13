package com.create.productionline.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for the private {@code template/base/addition} ingredients of smithing transform recipes. */
@Mixin(net.minecraft.world.item.crafting.SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {

    @Accessor("template")
    Ingredient createproductionline$getTemplate();

    @Accessor("base")
    Ingredient createproductionline$getBase();

    @Accessor("addition")
    Ingredient createproductionline$getAddition();
}
