package com.example.tudursguns.item;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
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

import java.util.function.Consumer;

/** The one item every handheld weapon is. Which weapon a stack is comes from its tudursguns:weapon
 * component (a HandheldDefinition id) - the same "one type, data-defined variants" approach Tudur's
 * Vehicle Mod uses for vehicles, since items can't be registered after startup.
 *
 * Controls: hold use (right click) to fire. SEMI weapons fire once per press, AUTO weapons keep
 * firing while held, and AA/AT/Missile weapons lock on while held and fire on release once the
 * lock completes. Nothing fires while the player is riding anything. */
public class HandheldWeaponItem extends Item {

	private static final int MAX_USE_TICKS = 72000;

	public HandheldWeaponItem(Settings settings) {
		super(settings);
	}

	/** The stack's definition on the server, or null. */
	public static HandheldDefinition serverDefinition(ItemStack stack) {
		return HandheldDefinitions.getServer(stack.get(ModComponents.WEAPON));
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
		// Every mode keeps the item "in use" while the key is held: AUTO fires from usageTick(),
		// lock-on weapons track from usageTick() and fire from onStoppedUsing(), and SEMI simply
		// doesn't fire again until the key is released and pressed again.
		user.setCurrentHand(hand);
		if (user instanceof ServerPlayerEntity player) {
			HandheldDefinition base = serverDefinition(stack);
			if (base != null) {
				Firing firing = Firing.of(stack, base);
				if (!HandheldCombat.requiresLock(firing.stats().weaponType())) {
					HandheldCombat.tryFire(player, stack, hand, firing.definition(), firing.stats(), null);
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
		} else if (def.fireMode() == HandheldDefinition.FireMode.AUTO) {
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
					if (target != null) {
						HandheldCombat.tryFire(player, stack, player.getActiveHand(), firing.definition(), firing.stats(), target);
					}
				}
			}
			HandheldCombat.clearLock(player);
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
		// A lock only lasts while this weapon is actually being held down.
		if (HandheldCombat.hasLockState(player) && !(player.isUsingItem() && player.getActiveItem().getItem() instanceof HandheldWeaponItem)) {
			HandheldCombat.clearLock(player);
		}
	}

	@Override
	public Text getName(ItemStack stack) {
		Identifier id = stack.get(ModComponents.WEAPON);
		String name = displayName(id);
		return name != null ? Text.literal(name) : super.getName(stack);
	}

	/** The definition's display_name, else its weapon file's DisplayName. Looks at the server's
	 * definitions first (they're the source of truth on a server or in singleplayer) and falls back to
	 * the client's synced copy (a client connected to a dedicated server). */
	private static String displayName(Identifier id) {
		HandheldDefinition serverDef = HandheldDefinitions.getServer(id);
		if (serverDef != null) {
			return serverDef.displayName().orElseGet(() -> WeaponStatsLoader.get(serverDef.weapon()).displayName());
		}
		HandheldDefinitions.ClientEntry clientEntry = HandheldDefinitions.getClient(id);
		if (clientEntry != null) {
			return clientEntry.definition().displayName().orElse(clientEntry.weapon().displayName());
		}
		return null;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		Identifier id = stack.get(ModComponents.WEAPON);
		if (id == null) {
			return;
		}
		HandheldDefinitions.ClientEntry entry = HandheldDefinitions.getClient(id);
		if (stack.contains(ModComponents.ALT_AMMO) || stack.getOrDefault(ModComponents.ALT_SELECTED, false)) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.underbarrel_ammo", stack.getOrDefault(ModComponents.ALT_AMMO, 0))
					.formatted(Formatting.GRAY));
		}
		if (entry != null) {
			int magazineSize = WeaponModifiers.of(stack, entry.definition()).magazineSize(entry.weapon().magazineSize());
			if (magazineSize > 0) {
				textConsumer.accept(Text.translatable("tooltip.tudursguns.ammo",
						stack.getOrDefault(ModComponents.AMMO, 0), magazineSize).formatted(Formatting.GRAY));
			}
		}
		HandheldDefinition movementDef = HandheldDefinitions.getAny(id);
		if (movementDef != null) {
			movementDef.movement().appendTooltip(textConsumer);
		}
		for (Identifier attachmentId : WeaponModifiers.fitted(stack).values()) {
			AttachmentDefinition attachment = HandheldDefinitions.getAnyAttachment(attachmentId);
			textConsumer.accept(Text.literal("+ " + (attachment != null ? attachment.displayName() : attachmentId.toString()))
					.formatted(Formatting.BLUE));
		}
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(id.toString()).formatted(Formatting.DARK_GRAY));
		}
	}
}
