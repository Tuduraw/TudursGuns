package com.example.tudursguns.block;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.mixin.AbstractVehicleEntityAccessor;
import com.example.tudursguns.registry.ModBlockEntities;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.soldier.FriendlySoldierEntity;
import com.example.tudursguns.weapon.EquipmentActions;
import com.example.tudursguns.weapon.Firing;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Once a second:
 * - Players within PLAYER_RADIUS get the magazine of every handheld weapon they carry filled (no
 *   ammo items needed; a reload in progress is left to finish) - but only while their weapon is
 *   lowered (HandheldCombat.isInAction).
 * - Stationary vehicles within VEHICLE_RADIUS get one step of Tudur's Vehicle Mod's own ammo supply
 *   (the same step its supply vehicles apply - magazine, then reserve, 10% at a time). Fuel and
 *   repairs are not an ammo box's job.
 * - Soldier posts' soldiers within VEHICLE_RADIUS that aren't fighting get their weapon filled (they
 *   go back to their post when their magazine is empty - put a box beside the post). */
public class AmmoBoxBlockEntity extends BlockEntity {

	private static final int INTERVAL_TICKS = 20;
	private static final double PLAYER_RADIUS = 4.0;
	private static final double VEHICLE_RADIUS = 8.0;

	public AmmoBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.AMMO_BOX, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, AmmoBoxBlockEntity blockEntity) {
		if (world instanceof ServerWorld serverWorld && (serverWorld.getTime() + pos.hashCode()) % INTERVAL_TICKS == 0) {
			blockEntity.supply(serverWorld);
		}
	}

	private void supply(ServerWorld world) {
		Vec3d center = Vec3d.ofCenter(this.getPos());
		for (ServerPlayerEntity player : world.getPlayers(candidate -> !candidate.isSpectator()
				&& !HandheldCombat.isInAction(candidate)
				&& candidate.squaredDistanceTo(center) <= PLAYER_RADIUS * PLAYER_RADIUS)) {
			var inventory = player.getInventory();
			for (int slot = 0; slot < inventory.size(); slot++) {
				refill(inventory.getStack(slot));
			}
		}
		for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class,
				new Box(this.getPos()).expand(VEHICLE_RADIUS),
				vehicle -> EquipmentActions.isSuppliable(vehicle) && vehicle.squaredDistanceTo(center) <= VEHICLE_RADIUS * VEHICLE_RADIUS)) {
			((AbstractVehicleEntityAccessor) vehicle).tudursguns$receiveAmmoSupply();
		}
		for (FriendlySoldierEntity soldier : world.getEntitiesByClass(FriendlySoldierEntity.class, new Box(this.getPos()).expand(VEHICLE_RADIUS),
				soldier -> soldier.getTarget() == null && soldier.squaredDistanceTo(center) <= VEHICLE_RADIUS * VEHICLE_RADIUS)) {
			refill(soldier.getMainHandStack());
		}
	}

	private static void refill(ItemStack stack) {
		if (!(stack.getItem() instanceof HandheldWeaponItem) || HandheldCombat.isReloading(stack)) {
			return;
		}
		HandheldDefinition def = HandheldWeaponItem.serverDefinition(stack);
		if (def == null) {
			return;
		}
		int magazineSize = WeaponModifiers.of(stack, def).magazineSize(WeaponStatsLoader.get(def.weapon()).magazineSize());
		if (magazineSize > 0 && stack.getOrDefault(ModComponents.AMMO, 0) < magazineSize) {
			stack.set(ModComponents.AMMO, magazineSize);
		}
		AttachmentDefinition.Underbarrel launcher = Firing.underbarrel(stack, def);
		if (launcher != null) {
			int launcherSize = WeaponStatsLoader.get(launcher.weapon()).magazineSize();
			if (launcherSize > 0 && stack.getOrDefault(ModComponents.ALT_AMMO, 0) < launcherSize) {
				stack.set(ModComponents.ALT_AMMO, launcherSize);
			}
		}
	}
}
