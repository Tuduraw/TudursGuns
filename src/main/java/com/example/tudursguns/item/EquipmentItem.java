package com.example.tudursguns.item;

import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.EquipmentActions;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The one item every piece of support equipment is; which one comes from its tudursguns:equipment
 * component. What it does is in EquipmentDefinition / EquipmentActions; the binoculars' view, the
 * rangefinder, the mine detector's outlines and the laser beam are drawn client-side. */
public class EquipmentItem extends DefinedItem<EquipmentDefinition> {

	private static final int HOLD_TICKS = 72000;

	public EquipmentItem(Properties settings) {
		super(settings, ModDefinitions.EQUIPMENT, ModComponents.EQUIPMENT);
	}

	public static EquipmentDefinition definition(ItemStack stack) {
		return stack.getItem() instanceof EquipmentItem ? ModDefinitions.EQUIPMENT.getAny(stack.get(ModComponents.EQUIPMENT)) : null;
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		EquipmentDefinition def = definition(stack);
		if (def == null || user.getVehicle() != null) {
			return InteractionResult.PASS;
		}
		if (def.type() == EquipmentDefinition.Type.DETONATOR) {
			if (user instanceof ServerPlayer player) {
				EquipmentActions.detonate(player, stack, hand, def);
			}
			return InteractionResult.SUCCESS;
		}
		if (!def.isHeld()) {
			return InteractionResult.PASS;
		}
		user.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		EquipmentDefinition def = definition(stack);
		return def != null && def.type() == EquipmentDefinition.Type.FIRST_AID ? def.useTicks() : HOLD_TICKS;
	}

	@Override
	public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player)) {
			return;
		}
		EquipmentDefinition def = definition(stack);
		if (def == null || player.getVehicle() != null) {
			player.releaseUsingItem();
			return;
		}
		int ticksUsed = getUseDuration(stack, user) - remainingUseTicks;
		InteractionHand hand = player.getUsedItemHand();
		boolean keepGoing = switch (def.type()) {
			case REPAIR_KIT -> EquipmentActions.repairTick(player, stack, hand, def, ticksUsed);
			case DEFUSE_KIT -> EquipmentActions.defuseTick(player, stack, hand, def);
			case LASER_DESIGNATOR -> {
				EquipmentActions.laserTick(player, def);
				yield true;
			}
			default -> true;
		};
		if (!keepGoing) {
			player.releaseUsingItem();
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
		EquipmentDefinition def = definition(stack);
		if (def != null && def.type() == EquipmentDefinition.Type.FIRST_AID && user instanceof ServerPlayer player) {
			EquipmentActions.firstAid(player, stack, player.getUsedItemHand(), def);
		}
		return stack;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayer player) {
			EquipmentActions.clearLaser(player);
			EquipmentActions.stopDefusing(player);
		}
		return false;
	}

	/** Server side: stack size and durability follow the definition (see ArmorEffects.applyComponents
	 * for why this is done here). */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
		EquipmentDefinition def = definition(stack);
		if (def != null) {
			applyComponents(stack, def);
		}
	}

	public static void applyComponents(ItemStack stack, EquipmentDefinition def) {
		if (def.uses() > 0) {
			setIfChanged(stack, DataComponents.MAX_STACK_SIZE, 1);
			setIfChanged(stack, DataComponents.MAX_DAMAGE, def.uses());
			if (!stack.has(DataComponents.DAMAGE)) {
				stack.set(DataComponents.DAMAGE, 0);
			}
		} else {
			stack.remove(DataComponents.MAX_DAMAGE);
			stack.remove(DataComponents.DAMAGE);
			setIfChanged(stack, DataComponents.MAX_STACK_SIZE, def.maxStack());
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, EquipmentDefinition def, Consumer<Component> textConsumer) {
		textConsumer.accept(Component.translatable("tooltip.tudursguns.equipment." + def.type().name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		def.movement().appendTooltip(textConsumer);
	}
}
