package ru.nelande.minelark.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nelande.minelark.Minelark;
import ru.nelande.minelark.script.RemovalSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies the script-declared {@code recipes.remove(...)} filters. After vanilla parses the datapack
 * recipes and builds its (immutable) {@code recipesByType}/{@code recipesById} collections, this
 * rebuilds both, dropping every recipe that matches any active {@link RemovalSpec}. The filters live
 * in {@link Minelark#serverRecipeRemovals()} (swapped on {@code /minelark reload}, which re-runs
 * recipe loading, so removals re-apply).
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    private Multimap<RecipeType<?>, RecipeEntry<?>> recipesByType;

    @Shadow
    private Map<Identifier, RecipeEntry<?>> recipesById;

    @Shadow
    @Final
    private RegistryWrapper.WrapperLookup registryLookup;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("TAIL"))
    private void minelark$removeRecipes(Map<Identifier, JsonElement> map, ResourceManager resourceManager,
                                        Profiler profiler, CallbackInfo ci) {
        List<RemovalSpec> removals = Minelark.serverRecipeRemovals();
        if (removals.isEmpty()) {
            return;
        }

        ImmutableMultimap.Builder<RecipeType<?>, RecipeEntry<?>> byType = ImmutableMultimap.builder();
        ImmutableMap.Builder<Identifier, RecipeEntry<?>> byId = ImmutableMap.builder();
        int removed = 0;
        for (Map.Entry<RecipeType<?>, RecipeEntry<?>> entry : recipesByType.entries()) {
            RecipeEntry<?> recipeEntry = entry.getValue();
            if (minelark$isRemoved(recipeEntry, removals)) {
                removed++;
                continue;
            }
            byType.put(entry.getKey(), recipeEntry);
            byId.put(recipeEntry.id(), recipeEntry);
        }

        if (removed > 0) {
            this.recipesByType = byType.build();
            this.recipesById = byId.build();
            Minelark.LOGGER.info("Minelark: removed {} recipe(s) via {} filter(s)", removed, removals.size());
        }
    }

    private boolean minelark$isRemoved(RecipeEntry<?> recipeEntry, List<RemovalSpec> removals) {
        String recipeId = recipeEntry.id().toString();
        Recipe<?> recipe = recipeEntry.value();

        Identifier typeId = Registries.RECIPE_TYPE.getId(recipe.getType());
        String recipeType = typeId == null ? null : typeId.toString();

        List<String> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                if (itemId != null) {
                    inputs.add(itemId.toString());
                }
            }
        }

        String outputId = null;
        try {
            ItemStack result = recipe.getResult(registryLookup);
            if (result != null && !result.isEmpty()) {
                Identifier itemId = Registries.ITEM.getId(result.getItem());
                if (itemId != null) {
                    outputId = itemId.toString();
                }
            }
        } catch (RuntimeException ignored) {
            // Some recipes have no meaningful static result; leave outputId null.
        }

        for (RemovalSpec spec : removals) {
            if (spec.matches(recipeId, recipeType, inputs, outputId)) {
                return true;
            }
        }
        return false;
    }
}
