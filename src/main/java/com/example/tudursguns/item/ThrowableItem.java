package com.example.tudursguns.item;

import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.ThrowableCombat;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.function.Consumer;

/** The one item every throwable is; which one comes from its tudursguns:throwable component.
 *
 * Hold use (right click) to pull the pin - a cookable throwable's fuse is already burning while it's
 * held, and it goes off in hand if held past its fuse - and release to throw. Holding the aim key
 * while releasing lobs it underhand instead. Not usable while riding anything. */
public class ThrowableItem extends Item {

	private static final int MAX_USE_TICKS = 72000;

	public ThrowableItem(Settings settings) {
		super(settings);
	}

	private static ThrowableDefinition serverDefinition(ItemStack stack) {
		Identifier id = stack.get(ModComponents.THROWABLE);
		return id == null ? null : HandheldDefinitions.serverThrowables().get(id);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getVehicle() != null || !stack.contains(ModComponents.THROWABLE)) {
			return ActionResult.PASS;
		}
		user.setCurrentHand(hand);
		if (user instanceof ServerPlayerEntity player) {
			ThrowableDefinition def = serverDefinition(stack);
			if (def != null) {
				ThrowableCombat.pullPin(player, def);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return;
		}
		ThrowableDefinition def = serverDefinition(stack);
		if (def == null) {
			return;
		}
		if (player.getVehicle() != null) {
			ThrowableCombat.forget(player.getUuid());
			player.stopUsingItem();
			return;
		}
		if (ThrowableCombat.checkCookedOff(player, stack, player.getActiveHand(), def)) {
			player.stopUsingItem();
		}
	}

	@Override
	public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayerEntity player && player.getVehicle() == null) {
			ThrowableDefinition def = serverDefinition(stack);
			if (def != null && !stack.isEmpty()) {
				ThrowableCombat.throwHeld(player, stack, def);
			}
		}
		return false;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return MAX_USE_TICKS;
	}

	@Override
	public Text getName(ItemStack stack) {
		Identifier id = stack.get(ModComponents.THROWABLE);
		ThrowableDefinition serverDef = HandheldDefinitions.serverThrowables().get(id);
		if (serverDef != null) {
			return Text.literal(serverDef.displayName().orElseGet(() -> WeaponStatsLoader.get(serverDef.weapon()).displayName()));
		}
		HandheldDefinitions.ClientThrowable clientEntry = id == null ? null : HandheldDefinitions.clientThrowables().get(id);
		if (clientEntry != null) {
			return Text.literal(clientEntry.definition().displayName().orElse(clientEntry.weaponDisplayName()));
		}
		return super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		ThrowableDefinition def = HandheldDefinitions.getAnyThrowable(stack.get(ModComponents.THROWABLE));
		if (def == null) {
			return;
		}
		if (def.impact()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.impact").formatted(Formatting.GRAY));
		} else if (def.fuseTicks() > 0) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.fuse",
					String.format(java.util.Locale.ROOT, "%.1f", def.fuseTicks() / 20f)).formatted(Formatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		if (def.cooks()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.cookable").formatted(Formatting.GRAY));
		}
		if (type.isAdvanced()) {
			textConsumer.accept(Text.literal(stack.get(ModComponents.THROWABLE).toString()).formatted(Formatting.DARK_GRAY));
		}
	}
}
