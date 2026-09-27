package com.example.tudursguns.item;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.WeaponSummary;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.function.Consumer;

/** The one item every handheld weapon is. Which weapon a stack is comes from its tudursguns:weapon
 * component (a HandheldDefinition id) - the same "one type, data-defined variants" approach Tudur's
 * Vehicle Mod uses for vehicles, since items can't be registered after startup.
 *
 * Controls: hold use (right click) to fire. SEMI weapons fire once per press, AUTO weapons keep
 * firing while held, and AA/AT/Missile weapons lock on while held and fire on release once the
 * lock completes. Nothing fires while the player is riding anything. */
public class HandheldWeaponItem extends DefinedItem<HandheldDefinition> {

	private static final int MAX_USE_TICKS = 72000;

	public HandheldWeaponItem(Settings settings) {
		super(settings, ModDefinitions.HANDHELD, ModComponents.WEAPON);
	}

	/** The stack's definition on the server, or null. */
	public static HandheldDefinition serverDefinition(ItemStack stack) {
		return ModDefinitions.HANDHELD.getServer(stack.get(ModComponents.WEAPON));
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (user.getVehicle() != null) {
			return ActionResult.PASS;
		}
		ItemStack stack = user.getStackInHand(hand);
		if (!stack.contains(ModComponents.WEAPON)) {
			return ActionResult.PASS;
		}
		// In its fire delay (a bolt being worked, a launcher between shots) use doesn't raise the weapon:
		// it couldn't fire anyway. Both sides see the delay on the stack, so they agree.
		if (HandheldCombat.isCoolingDown(stack, world.getTime())) {
			return ActionResult.PASS;
		}
		// Every mode keeps the item "in use" while the key is held: AUTO fires from usageTick(),
		// lock-on weapons track from usageTick() and fire from onStoppedUsing(), and SEMI simply
		// doesn't fire again until the key is released and pressed again.
		user.setCurrentHand(hand);
		if (user instanceof ServerPlayerEntity player) {
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
		return ActionResult.CONSUME;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		if (player.getVehicle() != null) {
			player.stopUsingItem();
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
			HandheldCombat.tryFire(player, stack, player.getActiveHand(), def, stats, null);
		}
	}

	@Override
	public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayerEntity player) {
			HandheldDefinition base = serverDefinition(stack);
			if (base != null) {
				Firing firing = Firing.of(stack, base);
				if (HandheldCombat.requiresLock(firing.stats().weaponType())) {
					var target = HandheldCombat.completedLockTarget(player);
					if (target != null && HandheldCombat.isRaised(player, base)) {
						HandheldCombat.tryFire(player, stack, player.getActiveHand(), firing.definition(), firing.stats(), target);
					}
				}
			}
			HandheldCombat.clearLock(player);
			HandheldCombat.stopRaisingUnlessAiming(player);
		}
		return false;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return MAX_USE_TICKS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
		if (!(entity instanceof ServerPlayerEntity player)) {
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
		if (HandheldCombat.hasLockState(player) && !(player.isUsingItem() && player.getActiveItem().getItem() instanceof HandheldWeaponItem)) {
			HandheldCombat.clearLock(player);
		}
	}

	@Override
	protected void appendDetails(ItemStack stack, HandheldDefinition def, Consumer<Text> textConsumer) {
		if (stack.contains(ModComponents.ALT_AMMO) || stack.getOrDefault(ModComponents.ALT_SELECTED, false)) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.underbarrel_ammo", stack.getOrDefault(ModComponents.ALT_AMMO, 0))
					.formatted(Formatting.GRAY));
		}
		WeaponSummary weapon = ModDefinitions.HANDHELD.weapon(stack.get(ModComponents.WEAPON));
		int magazineSize = weapon == null ? 0 : WeaponModifiers.of(stack, def).magazineSize(weapon.magazineSize());
		if (magazineSize > 0) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.ammo",
					stack.getOrDefault(ModComponents.AMMO, 0), magazineSize).formatted(Formatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		for (Identifier attachmentId : WeaponModifiers.fitted(stack).values()) {
			String name = ModDefinitions.ATTACHMENTS.name(attachmentId);
			textConsumer.accept(Text.literal("+ " + (name != null ? name : attachmentId.toString())).formatted(Formatting.BLUE));
		}
	}
}
