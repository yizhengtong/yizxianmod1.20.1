package net.minecraft.client.yiz.xian.item.curio;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.UUID;

/**
 * 饰品基类（Curios API 1.20.1 / curios-forge-5.14.1）。
 *
 * <h2>Curios 5.x 的四件事，别再按老教程写</h2>
 * <ol>
 *   <li><b>槽位是两段式的：先声明类型，再分配实体</b>（最容易漏的一条，漏了就是「槽位注册成功、
 *       但玩家一个槽都没有」，且完全没有报错）：
 *       <ul>
 *         <li>{@code data/<模组id>/curios/slots/<槽位标识>.json} —— 只声明「有这么一种槽」；</li>
 *         <li>{@code data/<模组id>/curios/entities/<任意名>.json} —— 声明「谁拥有这些槽」，
 *             内容形如 {@code {"replace": false, "entities": ["minecraft:player"], "slots": [...]}}。
 *             <b>Curios 自带的 10 个槽位同样不会自动分配给玩家</b>，引用模组必须自己声明。
 *             缺这个文件时 {@code CuriosApi.getEntitySlots(player)} 为空 →
 *             curios capability 根本不挂载（{@code CurioInventoryCapability$Provider} 直接返回
 *             {@code LazyOptional.empty()}）→ 界面里没有任何槽，{@code /curios add} 也是静默 no-op。</li>
 *       </ul></li>
 *   <li><b>物品能进哪个槽位靠物品标签，不靠类</b>：由数据包标签
 *       {@code data/curios/tags/items/<槽位标识>.json} 决定（槽位标识 = 槽位 JSON 的文件名，
 *       例如 {@code back.json} → 标签 {@code curios:back}）。本模组的绑定见
 *       {@code data/curios/tags/items/*.json}。</li>
 *   <li><b>行为靠 {@link ICurioItem}</b>：本类实现它，属性/每 tick/装备钩子都从这里走。
 *       只打标签不实现接口的物品也能戴上，但没有任何效果。</li>
 *   <li><b>属性由 Curios 代管</b>：{@link #getAttributeModifiers} 返回的修饰符由 Curios 在
 *       装备/卸下/换槽时自动应用与移除，<b>不要</b>自己在 curioTick 里改属性，否则重登/死亡会残留。
 *       参数里的 {@code uuid} 就是 Curios 按「槽位 + 槽位序号」算出的稳定 UUID，直接拿来用即可。</li>
 * </ol>
 *
 * <h2>想加一个专属槽位（本模组的「遗物」{@code relic} 就是完整范例）</h2>
 * <ol>
 *   <li><b>声明类型</b>：新建 {@code data/<模组id>/curios/slots/<槽位标识>.json}，字段见
 *       {@code data/yizxianmod/curios/slots/relic.json}（order 越小越靠前，icon 是纹理路径）。
 *       注意 <b>{@code size} 缺省就是 1、{@code validators} 缺省就是 {@code ["curios:tag"]}</b>，
 *       所以最小可用内容只有 order + icon（写出来也无害）。文件名<b>不能以 {@code _} 开头</b>（会被整份忽略）。</li>
 *   <li><b>分配实体（必做，最容易漏）</b>：新建 {@code data/<模组id>/curios/entities/player.json}，
 *       把槽位标识写进 {@code slots}，并把 <b>用到的 Curios 原生槽位也一并写上</b>
 *       （原生槽位同样不会自动给玩家）。同一实体可被多个模组的文件累加，前提是
 *       {@code "replace": false}（写 true 会顶掉其他模组给玩家的分配）。改完<b>重进世界即生效，不用删档</b>。</li>
 *   <li><b>物品标签</b>：新建 {@code data/curios/tags/items/<槽位标识>.json}，把物品 id 写进 values。
 *       <b>注意命名空间是 curios</b>（验证器 {@code curios:tag} 查的就是这个标签）。</li>
 *   <li><b>语言键</b>：{@code curios.identifier.<槽位标识>}（槽位名）与
 *       {@code curios.modifiers.<槽位标识>}（属性提示头「佩戴遗物时：」）。</li>
 *   <li><b>图标</b>：{@code "icon"} 指向的纹理要能被图集找到。偷懒可直接复用 Curios 自带的
 *       {@code curios:slot/empty_charm_slot}（本模组 relic 就是这么占位的）；要用自己的图，
 *       放 {@code assets/<模组id>/textures/slot/xxx.png}（16×16），并在
 *       {@code assets/minecraft/atlases/blocks.json} 里加
 *       <code>{"type": "directory", "source": "<模组id>:slot", "prefix": "slot/"}</code>。</li>
 * </ol>
 *
 * <p>打开界面：默认键位 <b>G</b>（{@code key.curios.open.desc}），或物品栏界面上的 Curios 按钮。
 * 验证槽位是否真的分配到玩家：{@code /curios list}；别拿物品提示里的栏位行当证据（那行只依赖
 * 槽位类型 + 谓词，跟实体分配无关）。</p>
 *
 * <h2>子类要做什么</h2>
 * 构造时传入属性（{@link CurioAttribute}），按需覆写 {@link #onEquipped} / {@link #onUnequipped} /
 * {@link #onSlowTick}；纯数值饰品不用写任何方法。
 */
public abstract class YizxianCurioItem extends Item implements ICurioItem {

    /**
     * 一条「佩戴时生效」的属性修饰符。
     *
     * @param id 只用于修饰符名字（便于在属性面板里辨认）；<b>同一个属性只放一条</b>，
     *           因为 Minecraft 按 UUID+属性去重，同属性同 UUID 的第二条会被忽略。
     */
    public record CurioAttribute(Attribute attribute, double amount, AttributeModifier.Operation operation, String id) {

        /** 加法：+{@code amount}（例如 +6 最大生命）。 */
        public static CurioAttribute add(Attribute attribute, double amount, String id) {
            return new CurioAttribute(attribute, amount, AttributeModifier.Operation.ADDITION, id);
        }

        /** 乘基础值：基础值 × (1 + {@code amount})（例如 0.1 = +10%）。 */
        public static CurioAttribute multiplyBase(Attribute attribute, double amount, String id) {
            return new CurioAttribute(attribute, amount, AttributeModifier.Operation.MULTIPLY_BASE, id);
        }

        /** 乘总值：最终值 × (1 + {@code amount})。 */
        public static CurioAttribute multiplyTotal(Attribute attribute, double amount, String id) {
            return new CurioAttribute(attribute, amount, AttributeModifier.Operation.MULTIPLY_TOTAL, id);
        }
    }

    private final List<CurioAttribute> curioAttributes;

    protected YizxianCurioItem(Item.Properties properties, CurioAttribute... curioAttributes) {
        super(properties);
        this.curioAttributes = List.of(curioAttributes);
    }

    // ────────────────────────── 属性 ──────────────────────────

    /**
     * 佩戴时给穿戴者加的属性。Curios 负责应用/移除；返回空表表示纯功能型饰品。
     *
     * <p>想用前置库 yizmodqzk 的自定义属性，把 {@code attribute} 换成
     * {@code net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get()} 即可
     * （玩家默认属性已由 {@code EntityAttributeModificationEvent} 注册，可直接生效）。</p>
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        for (CurioAttribute curioAttribute : this.curioAttributes) {
            modifiers.put(curioAttribute.attribute(), new AttributeModifier(
                uuid, "yizxianmod:curio." + curioAttribute.id(), curioAttribute.amount(), curioAttribute.operation()));
        }
        return modifiers;
    }

    // ────────────────────────── 生命周期 ──────────────────────────

    @Override
    public void onEquip(SlotContext slotContext, ItemStack previousStack, ItemStack stack) {
        onEquipped(slotContext, stack);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        onUnequipped(slotContext, stack);
        clampHealthAfterMaxDrop(slotContext);
    }

    /**
     * +最大生命 的饰品卸下后，把当前血量夹回新的上限。
     *
     * <p><b>为什么需要这一步</b>：原版语义里 {@code AttributeInstance.removeModifier} 只 setDirty，
     * <b>不会</b>反向夹 current health（只有 {@link LivingEntity#setHealth} 才会按上限夹）。
     * 于是「上限 26 → 20」而当前血量仍是 26：血条数字一点不回退，看起来像"饰品加成没被摘掉"，
     * 实际属性已被 Curios 正确移除（+2 护甲没有"当前值"这种伴随量，所以看不出这个问题）。</p>
     *
     * <p>夹两次：Curios 的每 tick 差分器既可能"先删 modifier 再调 onUnequip"、也可能反过来，
     * 立刻夹一次 + 下个服务端 tick 再夹一次，保证任一顺序下都生效。</p>
     */
    private void clampHealthAfterMaxDrop(SlotContext slotContext) {
        boolean touchesMaxHealth = false;
        for (CurioAttribute curioAttribute : this.curioAttributes) {
            if (curioAttribute.attribute() == Attributes.MAX_HEALTH) {
                touchesMaxHealth = true;
                break;
            }
        }
        if (!touchesMaxHealth) return;
        LivingEntity wearer = slotContext.entity();
        if (wearer == null || wearer.level().isClientSide()) return;
        clampToMaxHealth(wearer);
        if (wearer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> clampToMaxHealth(wearer));
        }
    }

    private static void clampToMaxHealth(LivingEntity wearer) {
        if (wearer.isAlive() && wearer.getHealth() > wearer.getMaxHealth()) {
            wearer.setHealth(wearer.getMaxHealth());
        }
    }

    /**
     * Curios 统一分发的每 tick 钩子（客户端服务端都会调）。
     * 默认只在服务端每 20 tick 触发一次 {@link #onSlowTick}，需要每 tick 逻辑就覆写本方法
     * 并自己做 {@code wearer.tickCount % n == 0} 节流，别在饰品里跑无节流的全量逻辑。
     */
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        LivingEntity wearer = slotContext.entity();
        if (wearer == null || wearer.level().isClientSide()) return;
        if (wearer.tickCount % 20 == 0) {
            onSlowTick(slotContext, stack, wearer);
        }
    }

    /** 装备瞬间（服务端与客户端都会触发，写世界状态前记得判 isClientSide）。 */
    protected void onEquipped(SlotContext slotContext, ItemStack stack) {
        // 默认无行为
    }

    /** 卸下瞬间。清理本饰品写过的临时状态（药水效果、追踪表等）就放这里。 */
    protected void onUnequipped(SlotContext slotContext, ItemStack stack) {
        // 默认无行为
    }

    /** 每 20 tick 一次的服务端钩子（约 1 秒）。持续型效果写这里。 */
    protected void onSlowTick(SlotContext slotContext, ItemStack stack, LivingEntity wearer) {
        // 默认无行为
    }

    // ────────────────────────── 提示文本 ──────────────────────────

    /**
     * 饰品说明行：语言键 = {@code <物品描述键>.desc}（例如
     * {@code item.yizxianmod.heartsteel.desc}）。属性的数值行不用自己写，
     * Curios 会按槽位自动加上「佩戴XX时：」+ 属性列表。
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
