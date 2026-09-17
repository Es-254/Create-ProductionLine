package com.create.productionline.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Best-effort localized display names for item / block registry ids, used when
 * rendering the pipeline plan (needs the Create resource language to be loaded,
 * e.g. zh_cn for 机械手/粉碎轮/动力搅拌器 …).
 */
public final class Names {

    private Names() {
    }

    public static String nameOfItem(String id) {
        if (id != null && id.startsWith("#")) {
            return nameOfTag(id);
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return id;
        }
        var item = BuiltInRegistries.ITEM.get(key);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return id;
        }
        return item.getName(new ItemStack(item)).getString();
    }

    /**
     * Display name of a tag reference like {@code "#c:ingots/steel"}: resolve the
     * tag's first registered member and show its (localized) item name, so the
     * plan reads "钢锭" instead of an english tag path. Falls back to the tag
     * path when the tag is missing or empty.
     */
    private static String nameOfTag(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id.substring(1));
        if (key == null) {
            return id;
        }
        try {
            TagKey<net.minecraft.world.item.Item> tagKey =
                    TagKey.create(Registries.ITEM, key);
            var holders = BuiltInRegistries.ITEM.getTag(tagKey);
            if (holders.isPresent()) {
                for (var holder : holders.get()) {
                    net.minecraft.world.item.Item item = holder.value();
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        continue;
                    }
                    return item.getName(new ItemStack(item)).getString();
                }
            }
        } catch (Throwable ignored) {
            // tag lookup unavailable (e.g. registry not loaded) — fall through
        }
        return "#" + key.getPath();
    }

    public static String nameOfBlock(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return id;
        }
        var block = BuiltInRegistries.BLOCK.get(key);
        if (block != null && block != Blocks.AIR) {
            return block.getName().getString();
        }
        String itemName = nameOfItem(id);
        return itemName.equals(id) ? id : itemName;
    }

    /**
     * Display name of a plan step's facility. The pseudo step {@code cpl:feed} is
     * rendered as a flexible "feed the material" label (arm / funnel / chute /
     * drop-in, any works).
     */
    public static String facilityName(String id) {
        if ("cpl:feed".equals(id)) {
            return net.minecraft.network.chat.Component.translatable("cpl.create_productionline.feed").getString();
        }
        // A step may carry a RECIPE TYPE (e.g. create:deploying) instead of a machine block
        // id — mirrors/topologies built from a parsed recipe do. Map it to the machine first,
        // otherwise the tooltip prints the raw "create:deploying".
        String facility = com.create.productionline.recipegen.CreateRecipePack.facilityOf(id);
        String blockId = facility != null ? facility : id;
        String name = nameOfBlock(blockId);
        if (!name.equals(blockId)) {
            return name;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? id : key.getPath();
    }

    public static String cap(String s) {
        return s != null && s.length() > 24 ? s.substring(0, 24) + "…" : s;
    }
}
