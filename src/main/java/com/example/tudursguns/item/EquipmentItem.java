package com.example.tudursguns.item;

import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.EquipmentActions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** The one item every piece of support equipment is; which one comes from its tudursguns:equipment
 * component. What it does is in EquipmentDefinition / EquipmentActions; the binoculars' view, the
 * rangefinder, the mine detector's outlines and the laser beam are drawn client-side. */
public class EquipmentItem extends Item {

	private static final int HOLD_TICKS = 72000;

	public EquipmentItem(Settings settings) {
		super(settings);
	}

	public static EquipmentDefinition definition(ItemStack stack) {
		return stack.getItem() instanceof EquipmentItem ? ModDefinitions.EQUIPMENT.getAny(stack.get(ModComponents.EQUIPMENT)) : null;
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		EquipmentDefinition def = definition(stack);
		if (def == null || user.getVehicle() != null) {
			return ActionResult.PASS;
		}
		if (def.type() == EquipmentDefinition.Type.DETONATOR) {
			if (user instanceof ServerPlayerEntity player) {
				EquipmentActions.detonate(player, stack, hand, def);
			}
			return ActionResult.SUCCESS;
		}
		if (!def.isHeld()) {
			return ActionResult.PASS;
		}
		user.setCurrentHand(hand);
		return ActionResult.CONSUME;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		EquipmentDefinition def = definition(stack);
		return def != null && def.type() == EquipmentDefinition.Type.FIRST_AID ? def.useTicks() : HOLD_TICKS;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		EquipmentDefinition def = definition(stack);
		if (def == null || player.getVehicle() != null) {
			player.stopUsingItem();
			return;
		}
		int ticksUsed = getMaxUseTime(stack, user) - remainingUseTicks;
		Hand hand = player.getActiveHand();
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
			player.stopUsingItem();
		}
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		EquipmentDefinition def = definition(stack);
		if (def != null && def.type() == EquipmentDefinition.Type.FIRST_AID && user instanceof ServerPlayerEntity player) {
			EquipmentActions.firstAid(player, stack, player.getActiveHand(), def);
		}
		return stack;
	}

	@Override
	public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayerEntity player) {
			EquipmentActions.clearLaser(player);
			EquipmentActions.stopDefusing(player);
		}
		return false;
	}

	/** Server side: stack size and durability follow the definition (see ArmorEffects.applyComponents
	 * for why this is done here). */
	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		EquipmentDefinition def = definition(stack);
		if (def != null) {
			applyComponents(stack, def);
		}
	}

	public static void applyComponents(ItemStack stack, EquipmentDefinition def) {
		if (def.uses() > 0) {
			if (!Objects.equals(stack.get(DataComponentTypes.MAX_STACK_SIZE), 1)) {
				stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
			}
			if (!Objects.equals(stack.get(DataComponentTypes.MAX_DAMAGE), def.uses())) {
				stack.set(DataComponentTypes.MAX_DAMAGE, def.uses());
			}
			if (!stack.contains(DataComponentTypes.DAMAGE)) {
				stack.set(DataComponentTypes.DAMAGE, 0);
			}
		} else {
			stack.remove(DataComponentTypes.MAX_DAMAGE);
			stack.remove(DataComponentTypes.DAMAGE);
			if (!Objects.equals(stack.get(DataComponentTypes.MAX_STACK_SIZE), def.maxStack())) {
				stack.set(DataComponentTypes.MAX_STACK_SIZE, def.maxStack());
			}
		}
	}

	@Override
	public Text getName(ItemStack stack) {
		String name = ModDefinitions.EQUIPMENT.name(stack.get(ModComponents.EQUIPMENT));
		return name != null ? Text.literal(name) : super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		Identifier id = stack.get(ModComponents.EQUIPMENT);
		EquipmentDefinition def = ModDefinitions.EQUIPMENT.getAny(id);
		if (def == null) {
			return;
		}
		textConsumer.accept(Text.translatable("tooltip.tudursguns.equipment." + def.type().name().toLowerCase(Locale.ROOT))
				.formatted(Formatting.GRAY));
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(id.toString()).formatted(Formatting.DARK_GRAY));
		}
	}
}
