package com.create.productionline.block.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import com.create.productionline.item.LineSchemeMirrorItem;
import com.create.productionline.line.scheme.LineScheme;
import com.create.productionline.line.scheme.LineSchemeSerializer;
import com.create.productionline.registry.ModBlockEntities;
import com.create.productionline.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 破拆机 block entity.
 *
 * <p>Slot 0 holds the generic intermediate / finished item to dismantle; slot 1
 * holds the plan — only a genuine Line Scheme item is accepted there (mirrors /
 * paper / clipboards are rejected, P0-1). The dismantle action first validates
 * that slot 0's item matches the plan's output, then consumes one slot-0 item
 * and refunds the plan's raw materials, placing a read-only
 * {@link LineSchemeMirrorItem} into slot 0.
 */
public class DismantlerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_SCHEME = 1;

    private final ModContainer inventory = new ModContainer(this, 2, (s) -> setChanged());

    public DismantlerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISMANTLER.get(), pos, state);
    }

    /**
     * Consume-then-refund dismantle, authoritative on the server side.
     *
     * <p>Guards (all must pass before anything is consumed or produced): slot 1
     * must hold a genuine Line Scheme item, the scheme must be non-empty, slot 0
     * must hold exactly the item the scheme produces, and no refundable input may
     * be a {@code #tag} reference (it cannot be materialized into a concrete
     * item — 宁可拒绝,不可吞物).
     *
     * @return {@code true} when exactly one slot-0 item was consumed and the
     *         raw materials (plus the mirror) were produced; {@code false} when
     *         nothing was consumed and nothing was produced — the slots are left
     *         untouched and the call may safely be retried after fixing them.
     */
    public boolean revert() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        // 槽 1 必须是真正的产线方案(LineSchemeItem):镜像/纸/剪贴板虽然携带相同的
        // 方案 NBT,但在这里不能充当方案(P0-1:此前镜像可被复喂,使 revert() 无守卫执行)。
        ItemStack schemeStack = inventory.getItem(SLOT_SCHEME);
        if (schemeStack.isEmpty()
                || !(schemeStack.getItem() instanceof com.create.productionline.item.LineSchemeItem)) {
            return false;
        }
        LineScheme scheme = LineSchemeSerializer.fromStack(schemeStack);
        if (scheme.isEmpty()) {
            return false;
        }
        // 槽 0 必须非空,且其物品注册表 id 必须与方案的 outputItem 完全一致;
        // 不一致直接拒绝——不消费、不产出,杜绝"拿任意/空槽刷退还物"。
        ItemStack slotZero = inventory.getItem(SLOT_ITEM);
        if (slotZero.isEmpty()) {
            return false;
        }
        ResourceLocation slotZeroKey = BuiltInRegistries.ITEM.getKey(slotZero.getItem());
        if (slotZeroKey == null || !scheme.getOutputItem().equals(slotZeroKey.toString())) {
            return false;
        }
        // 退款集合不信任方案 Steps：仅按 scheme.recipeId 在服务端 RecipeManager
        // 重新解析出的"真实唯一输入"退还；解析不到/输出不符/tag 输入一律拒绝，
        // 从结构上杜绝"伪造方案刷贵重原料"。
        String recipeId = scheme.getRecipeId();
        if (recipeId == null || recipeId.isBlank()) {
            return false;
        }
        com.create.productionline.line.mapper.RecipeDescriptor desc =
                com.create.productionline.line.mapper.ServerRecipeLookup.findById(serverLevel, recipeId);
        if (desc == null || desc.outputs().isEmpty()) {
            return false;
        }
        String authoritativeOutput = desc.outputs().get(0);
        if (!scheme.getOutputItem().equals(authoritativeOutput)) {
            return false; // 方案声称的产物与服务端配方不一致 -> 拒绝
        }
        Set<String> toRestore = new LinkedHashSet<>();
        for (String in : desc.uniqueInputs()) {
            if (in.startsWith("#")) {
                // '#tag' 输入无法还原成具体物品:宁可拒绝,不可吞物
                return false;
            }
            if (!in.equals(authoritativeOutput)) {
                toRestore.add(in); // 自引用(原料==产物)不入退款
            }
        }
        BlockPos pos = getBlockPos();
        // 顺序不可颠倒:先消费槽 0 一份,再退还原料,最后处理镜像——重复/并发调用
        // 绝不会多退一份原料(每次调用至多产出一套退还物 + 一面镜像)。
        if (slotZero.getCount() > 1) {
            slotZero.shrink(1);
            inventory.setChanged();
        } else {
            inventory.setItem(SLOT_ITEM, ItemStack.EMPTY);
        }
        // 退还 base material + 各步 inputs,维持原"每种 1 个"的尽力而为语义。
        for (String id : toRestore) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) {
                continue;
            }
            var item = BuiltInRegistries.ITEM.get(key);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            net.minecraft.world.level.block.Block.popResource(serverLevel, pos, new ItemStack(item));
        }
        ItemStack mirror = new ItemStack(ModItems.LINE_SCHEME_MIRROR.get());
        com.create.productionline.item.LineSchemeMirrorItem.write(mirror, scheme);
        // 槽 0 若已被清空,镜像直接放回槽 0;若只是 shrink(槽内还剩同类物品),则把镜像
        // 弹出到世界而不是用 setItem 覆盖——覆盖会静默吞掉剩余物品(选此最简单自洽做法)。
        if (inventory.getItem(SLOT_ITEM).isEmpty()) {
            inventory.setItem(SLOT_ITEM, mirror);
        } else {
            net.minecraft.world.level.block.Block.popResource(serverLevel, pos, mirror);
        }
        setChanged();
        return true;
    }

    public ModContainer getInventory() {
        return inventory;
    }

    public net.minecraft.world.SimpleMenuProvider menuProvider() {
        return new net.minecraft.world.SimpleMenuProvider((id, inv, player) ->
                com.create.productionline.menu.DismantlerMenu.fromServer(id, inv, this),
                net.minecraft.network.chat.Component.translatable(
                        "container.create_productionline.dismantler"));
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : inventory.snapshot()) {
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stack);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag items = new ListTag();
        for (ItemStack stack : inventory.snapshot()) {
            items.add(stack.saveOptional(provider));
        }
        tag.put("Items", items);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Items", Tag.TAG_LIST)) {
            ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
            java.util.ArrayList<ItemStack> list = new java.util.ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                list.add(ItemStack.parseOptional(provider, items.getCompound(i)));
            }
            inventory.loadFrom(list);
        }
    }
}
