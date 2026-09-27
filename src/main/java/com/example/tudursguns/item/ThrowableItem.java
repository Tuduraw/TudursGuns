package com.example.tudursguns.item;

import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.ThrowableCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.function.Consumer;

/** The one item every throwable is; which one comes from its tudursguns:throwable component.
 *
 * Hold use (right click) to pull the pin - a cookable throwable's fuse is already burning while it's
 * held, and it goes off in hand if held past its fuse - and release to throw. Holding the aim key
 * while releasing lobs it underhand instead. Not usable while riding anything. */
public class ThrowableItem extends DefinedItem<ThrowableDefinition> {

	private static final int MAX_USE_TICKS = 72000;

	public ThrowableItem(Settings settings) {
		super(settings, ModDefinitions.THROWABLES, ModComponents.THROWABLE);
	}

	private static ThrowableDefinition serverDefinition(ItemStack stack) {
		return ModDefinitions.THROWABLES.getServer(stack.get(ModComponents.THROWABLE));
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
		if (ThrowableCombat.checkCookedOff(player, stack, def)) {
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
	protected void appendDetails(ItemStack stack, ThrowableDefinition def, Consumer<Text> textConsumer) {
		if (def.impact()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.impact").formatted(Formatting.GRAY));
		} else if (def.fuseTicks() > 0) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.fuse",
					String.format(Locale.ROOT, "%.1f", def.fuseTicks() / 20f)).formatted(Formatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		if (def.cooks()) {
			textConsumer.accept(Text.translatable("tooltip.tudursguns.throwable.cookable").formatted(Formatting.GRAY));
		}
	}
}
