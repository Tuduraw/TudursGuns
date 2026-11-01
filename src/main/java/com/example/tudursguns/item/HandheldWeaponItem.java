package com.example.tudursguns.item;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStats;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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

/** The one item every handheld weapon is. Which weapon a stack is comes from its tudursguns:weapon
 * component (a HandheldDefinition id) - the same "one type, data-defined variants" approach Tudur's
 * Vehicle Mod uses for vehicles, since items can't be registered after startup.
 *
 * Controls: hold use (right click) to fire. SEMI weapons fire once per press, AUTO weapons keep
 * firing while held, and AA/AT/Missile weapons lock on while held and fire on release once the
 * lock completes. Nothing fires while the player is riding anything. */
public class HandheldWeaponItem extends DefinedItem<HandheldDefinition> {

	private static final int MAX_USE_TICKS = 72000;

	public HandheldWeaponItem(Properties settings) {
		super(settings, ModDefinitions.HANDHELD, ModComponents.WEAPON);
	}

	/** A weapon stack that names its definition. */
	public static boolean isWeapon(ItemStack stack) {
		return stack.getItem() instanceof HandheldWeaponItem && stack.has(ModComponents.WEAPON);
	}

	/** The stack's definition on the server, or null. */
	public static HandheldDefinition serverDefinition(ItemStack stack) {
		return ModDefinitions.HANDHELD.getServer(stack.get(ModComponents.WEAPON));
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		if (user.getVehicle() != null) {
			return InteractionResult.PASS;
		}
		ItemStack stack = user.getItemInHand(hand);
		if (!stack.has(ModComponents.WEAPON)) {
			return InteractionResult.PASS;
		}
		// In its fire delay (a bolt being worked, a launcher between shots) use doesn't raise the weapon:
		// it couldn't fire anyway. Both sides see the delay on the stack, so they agree.
		if (HandheldCombat.isCoolingDown(stack, world.getGameTime())) {
			return InteractionResult.PASS;
		}
		// Every mode keeps the item "in use" while the key is held: AUTO fires from usageTick(),
		// lock-on weapons track from usageTick() and fire from onStoppedUsing(), and SEMI simply
		// doesn't fire again until the key is released and pressed again.
		user.startUsingItem(hand);
		if (user instanceof ServerPlayer player) {
			HandheldDefinition base = serverDefinition(stack);
			if (base != null) {
				HandheldCombat.startRaisingForUse(player);
				Firing firing = Firing.of(stack, base);
				if (!HandheldCombat.requiresLock(firing.stats().weaponType())) {
					// From the hip the shot waits for the weapon to be fully up (see tickPendingShot).
					if (HandheldCombat.isRaised(player, base)) {
						if (HandheldCombat.tryFire(player, stack, hand, firing.definition(), firing.stats(), null)) {
							HandheldCombat.startBurst(player, hand, firing.definition());
						}
					} else {
						HandheldCombat.requestShot(player, hand);
					}
				}
			}
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player)) {
			return;
		}
		if (player.getVehicle() != null) {
			player.releaseUsingItem();
			return;
		}
		HandheldDefinition base = serverDefinition(stack);
		if (base == null) {
			return;
		}
		Firing firing = Firing.of(stack, base);
		HandheldDefinition def = firing.definition();
		WeaponStats stats = firing.stats();
		if (HandheldCombat.requiresLock(stats.weaponType())) {
			HandheldCombat.updateLock(player, stats);
		} else if (def.fireMode() == HandheldDefinition.FireMode.AUTO && HandheldCombat.isRaised(player, base)) {
			HandheldCombat.tryFire(player, stack, player.getUsedItemHand(), def, stats, null);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayer player) {
			HandheldDefinition base = serverDefinition(stack);
			if (base != null) {
				Firing firing = Firing.of(stack, base);
				if (HandheldCombat.requiresLock(firing.stats().weaponType())) {
					var target = HandheldCombat.completedLockTarget(player);
					if (target != null && HandheldCombat.isRaised(player, base)) {
						HandheldCombat.tryFire(player, stack, player.getUsedItemHand(), firing.definition(), firing.stats(), target);
					}
				}
			}
			HandheldCombat.clearLock(player);
			HandheldCombat.stopRaisingUnlessAiming(player);
		}
		return false;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return MAX_USE_TICKS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		HandheldDefinition base = serverDefinition(stack);
		if (base == null) {
			return;
		}
		Firing firing = Firing.of(stack, base);
		HandheldCombat.tickReload(player, stack, firing.definition(), firing.stats());
		HandheldCombat.tickRaiseState(player);
		HandheldCombat.tickPendingShot(player, stack, base, firing);
		HandheldCombat.tickBurst(player, stack, firing.definition(), firing.stats());
		HandheldCombat.syncMeleeDamage(stack, base);
		// A lock only lasts while this weapon is actually being held down.
		if (HandheldCombat.hasLockState(player) && !(player.isUsingItem() && player.getUseItem().getItem() instanceof HandheldWeaponItem)) {
			HandheldCombat.clearLock(player);
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, HandheldDefinition def, Consumer<Component> textConsumer) {
		if (stack.has(ModComponents.ALT_AMMO) || stack.getOrDefault(ModComponents.ALT_SELECTED, false)) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.underbarrel_ammo", stack.getOrDefault(ModComponents.ALT_AMMO, 0))
					.withStyle(ChatFormatting.GRAY));
		}
		WeaponSummary weapon = ModDefinitions.HANDHELD.weapon(stack.get(ModComponents.WEAPON));
		int magazineSize = weapon == null ? 0 : WeaponModifiers.of(stack, def).magazineSize(weapon.magazineSize());
		if (magazineSize > 0) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.ammo",
					stack.getOrDefault(ModComponents.AMMO, 0), magazineSize).withStyle(ChatFormatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		for (Identifier attachmentId : WeaponModifiers.fitted(stack).values()) {
			String name = ModDefinitions.ATTACHMENTS.name(attachmentId);
			textConsumer.accept(Component.literal("+ " + (name != null ? name : attachmentId.toString())).withStyle(ChatFormatting.BLUE));
		}
	}
}
