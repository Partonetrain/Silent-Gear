package net.silentchaos512.gear.crafting.recipe;

import com.google.gson.JsonObject;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistryEntry;
import net.silentchaos512.gear.SilentGear;
import net.silentchaos512.gear.api.item.ICoreItem;
import net.silentchaos512.gear.api.parts.IGearPart;
import net.silentchaos512.gear.api.parts.PartDataList;
import net.silentchaos512.gear.api.parts.PartType;
import net.silentchaos512.gear.parts.PartData;
import net.silentchaos512.gear.parts.PartManager;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.lib.collection.StackList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class GearPartSwapRecipe implements ICraftingRecipe {
    public static final ResourceLocation NAME = new ResourceLocation(SilentGear.MOD_ID, "swap_gear_part");
    public static final Serializer SERIALIZER = new Serializer();

    @Override
    public boolean matches(CraftingInventory inv, World worldIn) {
        StackList list = StackList.from(inv);
        ItemStack gear = list.uniqueOfType(ICoreItem.class);
        if (gear.isEmpty()) return false;

        ICoreItem item = (ICoreItem) gear.getItem();
        Collection<ItemStack> others = list.allMatches(stack -> !(stack.getItem() instanceof ICoreItem));
        if (others.isEmpty()) return false;

        Collection<PartType> typesFound = new HashSet<>();

        for (ItemStack stack : others) {
            PartData part = PartData.fromStackFast(stack);
            if (part == null) return false;

            // Only required part types (no mains), and no duplicates
            PartType type = part.getType();
            if (type == PartType.MAIN || !item.requiresPartOfType(type) || typesFound.contains(type)) {
                return false;
            }
            typesFound.add(type);
        }
        return true;
    }

    @Override
    public ItemStack getCraftingResult(CraftingInventory inv) {
        StackList list = StackList.from(inv);
        ItemStack gear = list.uniqueOfType(ICoreItem.class);
        if (gear.isEmpty()) return ItemStack.EMPTY;

        Collection<ItemStack> others = list.allMatches(stack -> !(stack.getItem() instanceof ICoreItem));
        if (others.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = gear.copy();
        PartDataList parts = GearData.getConstructionParts(result);

        for (ItemStack stack : others) {
            PartData part = PartData.from(stack);
            if (part == null) return ItemStack.EMPTY;

            // Remove old part of type, replace
            PartType type = part.getType();
            Collection<PartData> toRemove = parts.stream().filter(p -> p.getType() == type).collect(Collectors.toList());
            parts.removeAll(toRemove);
            toRemove.forEach(p -> p.onRemoveFromGear(result));
            parts.add(part);
            part.onAddToGear(result);
        }

        GearData.writeConstructionParts(result, parts);
        GearData.recalculateStats(result, null);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInventory inv) {
        NonNullList<ItemStack> list = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        StackList stackList = StackList.from(inv);
        ItemStack gear = stackList.uniqueMatch(s -> s.getItem() instanceof ICoreItem);
        PartDataList oldParts = GearData.getConstructionParts(gear);

        for (int i = 0; i < list.size(); ++i) {
            ItemStack stack = inv.getStackInSlot(i);

            if (stack.getItem() instanceof ICoreItem) {
                list.set(i, ItemStack.EMPTY);
            } else {
                IGearPart part = PartManager.from(stack);
                if (part != null) {
                    List<PartData> partsOfType = oldParts.getPartsOfType(part.getType());
                    if (!partsOfType.isEmpty()) {
                        PartData partData = partsOfType.get(0);
                        partData.onRemoveFromGear(gear);
                        list.set(i, partData.getCraftingItem());
                    } else {
                        list.set(i, ItemStack.EMPTY);
                    }
                }
            }
        }

        return list;
    }

    @Override
    public boolean canFit(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getRecipeOutput() {
        // Cannot determine
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return NAME;
    }

    @Override
    public IRecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    public static final class Serializer extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<GearPartSwapRecipe> {

        @Override
        public GearPartSwapRecipe read(ResourceLocation recipeId, JsonObject json) {
            return new GearPartSwapRecipe();
        }

        @Override
        public GearPartSwapRecipe read(ResourceLocation recipeId, PacketBuffer buffer) {
            return new GearPartSwapRecipe();
        }

        @Override
        public void write(PacketBuffer buffer, GearPartSwapRecipe recipe) {}
    }
}
