package com.example.tudursguns.item;

import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.handheld.ThrowableDefinition;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.ThrowableCombat;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The one item every throwable is; which one comes from its tudursguns:throwable component.
 *
 * Hold use (right click) to pull the pin - a cookable throwable's fuse is already burning while it's
 * held, and it goes off in hand if held past its fuse - and release to throw. Holding the aim key
 * while releasing lobs it underhand instead. Not usable while riding anything. */
public class ThrowableItem extends DefinedItem<ThrowableDefinition> {

	private static final int MAX_USE_TICKS = 72000;

	public ThrowableItem(Properties settings) {
		super(settings, ModDefinitions.THROWABLES, ModComponents.THROWABLE);
	}

	private static ThrowableDefinition serverDefinition(ItemStack stack) {
		return ModDefinitions.THROWABLES.getServer(stack.get(ModComponents.THROWABLE));
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		if (user.getVehicle() != null || !stack.has(ModComponents.THROWABLE)) {
			return InteractionResult.PASS;
		}
		user.startUsingItem(hand);
		if (user instanceof ServerPlayer player) {
			ThrowableDefinition def = serverDefinition(stack);
			if (def != null) {
				ThrowableCombat.pullPin(player, def);
			}
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player)) {
			return;
		}
		ThrowableDefinition def = serverDefinition(stack);
		if (def == null) {
			return;
		}
		if (player.getVehicle() != null) {
			ThrowableCombat.forget(player.getUUID());
			player.releaseUsingItem();
			return;
		}
		if (ThrowableCombat.checkCookedOff(player, stack, def)) {
			player.releaseUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof ServerPlayer player && player.getVehicle() == null) {
			ThrowableDefinition def = serverDefinition(stack);
			if (def != null && !stack.isEmpty()) {
				ThrowableCombat.throwHeld(player, stack, def);
			}
		}
		return false;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return MAX_USE_TICKS;
	}

	@Override
	protected void appendDetails(ItemStack stack, ThrowableDefinition def, Consumer<Component> textConsumer) {
		if (def.impact()) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.throwable.impact").withStyle(ChatFormatting.GRAY));
		} else if (def.fuseTicks() > 0) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.throwable.fuse",
					String.format(Locale.ROOT, "%.1f", def.fuseTicks() / 20f)).withStyle(ChatFormatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		if (def.cooks()) {
			textConsumer.accept(Component.translatable("tooltip.tudursguns.throwable.cookable").withStyle(ChatFormatting.GRAY));
		}
	}
}
