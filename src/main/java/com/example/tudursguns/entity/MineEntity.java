package com.example.tudursguns.entity;

import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.WeaponTargeting;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A placed mine/claymore/charge (see MineDefinition). Visible to everyone and set off by anyone -
 * the placer included - once armed.
 *
 * It sits on a block face (normal = the face's direction, the model's +Y) or rides on a vehicle
 * (kept at a fixed offset in the vehicle's own yaw frame). It drops back as an item if the block
 * under it goes, or the vehicle it's stuck to is gone. Hitting it - a bullet, an explosion, a punch -
 * sets it off if it's armed (or knocks it loose as an item if not), so charges can be cleared by
 * shooting them, and chain-detonate each other. A creative player's punch just removes it.
 *
 * The explosion is a Tudur's Vehicle Mod projectile from the definition's weapon file, set to go off
 * at once where the charge is; a claymore's fragments are that mod's projectiles too. */
public class MineEntity extends Entity {

	private static final EntityDataAccessor<String> MINE_ID = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> NORMAL = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(MineEntity.class, EntityDataSerializers.BOOLEAN);

	/** Ticks a stuck-on vehicle may be missing (not loaded yet after a restart) before the charge falls off. */
	private static final int VEHICLE_MISSING_LIMIT = 100;

	private UUID owner;
	private int armingProgress;
	private int triggerCountdown = -1;
	private UUID attachedVehicle;
	private Vec3 localOffset = Vec3.ZERO;
	private float localYaw;
	private int vehicleMissingTicks;

	public MineEntity(EntityType<?> type, Level world) {
		super(type, world);
		this.setNoGravity(true);
	}

	/** Places a charge on a block face (vehicle == null) or on a vehicle. */
	public static MineEntity place(ServerLevel world, Player placer, Identifier mineId, Vec3 pos, Direction normal, float yaw,
			Entity vehicle) {
		MineEntity mine = new MineEntity(ModEntityTypes.MINE, world);
		mine.entityData.set(MINE_ID, mineId.toString());
		mine.entityData.set(NORMAL, normal.get3DDataValue());
		mine.owner = placer == null ? null : placer.getUUID();
		mine.setPos(pos.x, pos.y, pos.z);
		mine.setYRot(yaw);
		if (vehicle != null) {
			float vehicleYawRad = (float) Math.toRadians(vehicle.getYRot());
			mine.attachedVehicle = vehicle.getUUID();
			mine.localOffset = pos.subtract(vehicle.position()).yRot(vehicleYawRad);
			mine.localYaw = yaw - vehicle.getYRot();
		}
		world.addFreshEntity(mine);
		MineDefinition def = mine.definition();
		if (def != null) {
			def.sounds().place().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, pos, 0.6f, 1.0f, 0.05f));
		}
		return mine;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(MINE_ID, "");
		builder.define(NORMAL, Direction.UP.get3DDataValue());
		builder.define(ARMED, false);
	}

	public Identifier mineId() {
		return Identifier.tryParse(this.entityData.get(MINE_ID));
	}

	public MineDefinition definition() {
		return ModDefinitions.MINES.getAny(mineId());
	}

	public Direction normal() {
		return Direction.from3DDataValue(this.entityData.get(NORMAL));
	}

	public boolean isArmed() {
		return this.entityData.get(ARMED);
	}

	public boolean isOwnedBy(Player player) {
		return this.owner != null && this.owner.equals(player.getUUID());
	}

	/** Where the charge is: a little out from the surface it sits on. */
	private Vec3 center() {
		return this.position().add(normal().getUnitVec3().scale(0.1));
	}

	/** The direction a directional charge fires in: the way it faces (horizontal). */
	private Vec3 facing() {
		return Vec3.directionFromRotation(0f, this.getYRot());
	}

	public ItemStack toItem() {
		Identifier id = mineId();
		return id != null ? ModItems.mineStack(id) : new ItemStack(ModItems.MINE);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel world)) {
			return;
		}
		MineDefinition def = ModDefinitions.MINES.getServer(mineId());
		if (def == null) {
			// Its definition was removed (data pack change) - give it back rather than leave a dud.
			if (this.tickCount > 20) {
				dropAsItem(world);
			}
			return;
		}
		if (!followVehicle(world)) {
			return;
		}
		if (this.attachedVehicle == null && this.tickCount % 20 == 0 && !isSupported(world)) {
			dropAsItem(world);
			return;
		}
		if (!isArmed()) {
			if (++this.armingProgress >= def.armingTicks()) {
				this.entityData.set(ARMED, true);
				def.sounds().arm().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, center(), 0.4f, 1.0f, 0.05f));
			}
			return;
		}
		if (this.triggerCountdown >= 0) {
			if (this.triggerCountdown-- <= 0) {
				detonate(world);
			}
			return;
		}
		if (def.trigger() != MineDefinition.Trigger.REMOTE && this.tickCount % 2 == 0 && isSetOff(world, def)) {
			setOff(world, def);
		}
	}

	/** Moves with the vehicle it's stuck to. Returns false if it fell off (and is gone). */
	private boolean followVehicle(ServerLevel world) {
		if (this.attachedVehicle == null) {
			return true;
		}
		Entity vehicle = world.getEntity(this.attachedVehicle);
		if (vehicle == null || vehicle.isRemoved()) {
			if (vehicle != null || ++this.vehicleMissingTicks > VEHICLE_MISSING_LIMIT) {
				dropAsItem(world);
				return false;
			}
			return true;
		}
		this.vehicleMissingTicks = 0;
		float vehicleYawRad = (float) Math.toRadians(vehicle.getYRot());
		Vec3 pos = vehicle.position().add(this.localOffset.yRot(-vehicleYawRad));
		this.setPos(pos.x, pos.y, pos.z);
		this.setYRot(vehicle.getYRot() + this.localYaw);
		return true;
	}

	private boolean isSupported(ServerLevel world) {
		BlockPos support = BlockPos.containing(this.position().subtract(normal().getUnitVec3().scale(0.2)));
		return !world.getBlockState(support).getCollisionShape(world, support).isEmpty();
	}

	private boolean isSetOff(ServerLevel world, MineDefinition def) {
		Vec3 c = center();
		double r = def.triggerRadius();
		// A ground mine feels what's on the ground around it.
		AABB box = normal() == Direction.UP && def.trigger() != MineDefinition.Trigger.DIRECTIONAL
				? new AABB(c.x - r, c.y - 0.25, c.z - r, c.x + r, c.y + 1.0, c.z + r)
				: new AABB(c, c).inflate(r);
		if (def.triggerLiving()) {
			for (LivingEntity living : world.getEntitiesOfClass(LivingEntity.class, box, MineEntity::canSetOff)) {
				if (def.trigger() != MineDefinition.Trigger.DIRECTIONAL || inCone(world, def, living.getBoundingBox().getCenter())) {
					return true;
				}
			}
		}
		if (def.triggerVehicles()) {
			for (AbstractVehicleEntity vehicle : world.getEntitiesOfClass(AbstractVehicleEntity.class, box,
					vehicle -> vehicle.isAlive() && !vehicle.tudursvehiclemod$isDestroyed()
							&& !vehicle.getUUID().equals(this.attachedVehicle))) {
				if (def.trigger() != MineDefinition.Trigger.DIRECTIONAL || inCone(world, def, vehicle.getBoundingBox().getCenter())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Anything alive that isn't a spectator or an invisible marker (smoke decoy, laser spot). No
	 * owner or team exception: a mine doesn't know whose side anyone is on. */
	private static boolean canSetOff(LivingEntity living) {
		return living.isAlive() && !living.isSpectator() && !(living instanceof MarkerEntity) && !(living instanceof ArmorStand);
	}

	private boolean inCone(ServerLevel world, MineDefinition def, Vec3 target) {
		Vec3 c = center();
		Vec3 toTarget = target.subtract(c);
		double distance = toTarget.length();
		if (distance < 1.0E-3) {
			return true;
		}
		double halfAngle = Math.toRadians(def.fragments().map(MineDefinition.Fragments::spreadDegrees).orElse(60f) / 2.0);
		if (toTarget.scale(1.0 / distance).dot(facing()) < Math.cos(halfAngle)) {
			return false;
		}
		return world.clip(new ClipContext(c, target, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
	}

	private void setOff(ServerLevel world, MineDefinition def) {
		def.sounds().trigger().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, center(), 0.8f, 1.0f, 0.05f));
		if (def.triggerDelayTicks() <= 0) {
			detonate(world);
		} else {
			this.triggerCountdown = def.triggerDelayTicks();
		}
	}

	// ---------------------------------------------------------------- going off

	/** Explodes now (and fires fragments). Used by triggers, hits and detonators. */
	public void detonate(ServerLevel world) {
		if (this.isRemoved()) {
			return;
		}
		MineDefinition def = ModDefinitions.MINES.getServer(mineId());
		// Gone first, so the explosion hitting this entity can't set it off again.
		this.discard();
		if (def == null) {
			return;
		}
		LivingEntity owner = this.owner == null ? null : world.getServer().getPlayerList().getPlayer(this.owner);
		Vec3 c = center();
		WeaponStats stats = WeaponStatsLoader.get(def.weapon());
		VehicleProjectileEntity blast = WeaponProjectileFactory.create(world, owner, new ItemStack(Items.IRON_NUGGET), stats, 0);
		blast.tudursvehiclemod$setFuseTicks(-1, 0);
		blast.tudursvehiclemod$setBounceStrength(0f);
		blast.setPos(c.x, c.y, c.z);
		blast.setDeltaMovement(Vec3.ZERO);
		world.addFreshEntity(blast);
		def.fragments().ifPresent(fragments -> fireFragments(world, owner, fragments, c));
	}

	private void fireFragments(ServerLevel world, LivingEntity owner, MineDefinition.Fragments fragments, Vec3 c) {
		WeaponStats stats = WeaponStatsLoader.get(fragments.weapon());
		float half = fragments.spreadDegrees() / 2f;
		for (int i = 0; i < fragments.count(); i++) {
			float yaw = this.getYRot() + (world.random.nextFloat() * 2f - 1f) * half;
			// Mostly flat, slightly upward: a claymore's fan is wide and low.
			float pitch = -3f + (world.random.nextFloat() * 2f - 1f) * half / 6f;
			Vec3 direction = Vec3.directionFromRotation(pitch, yaw);
			VehicleProjectileEntity fragment = WeaponProjectileFactory.create(world, owner, new ItemStack(Items.IRON_NUGGET), stats, 0);
			Vec3 start = c.add(0, 0.2, 0).add(direction.scale(0.3));
			fragment.setPos(start.x, start.y, start.z);
			Vec3 velocity = WeaponTargeting.applyAccuracySpread(direction.scale(stats.velocity()), stats.accuracyDegrees(), world.random);
			fragment.setDeltaMovement(velocity);
			fragment.absSnapRotationTo(yaw, pitch);
			world.addFreshEntity(fragment);
		}
	}

	private void dropAsItem(ServerLevel world) {
		if (this.isRemoved()) {
			return;
		}
		this.discard();
		this.spawnAtLocation(world, toItem());
	}

	/** Taken back without going off: by its placer, or with a defuse kit. */
	public void pickUp(ServerPlayer player) {
		if (this.isRemoved()) {
			return;
		}
		this.discard();
		player.getInventory().placeItemBackInInventory(toItem());
	}

	// ---------------------------------------------------------------- interaction

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		}
		if (source.getEntity() instanceof Player player && player.isCreative()) {
			this.discard();
			return true;
		}
		if (isArmed()) {
			detonate(world);
		} else {
			dropAsItem(world);
		}
		return true;
	}

	/** Sneak + use: the placer (or a creative player) picks it back up. Anyone else needs a defuse kit. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (isOwnedBy(serverPlayer) || serverPlayer.isCreative()) {
				pickUp(serverPlayer);
			} else {
				serverPlayer.displayClientMessage(Component.translatable("message.tudursguns.mine.not_owner"), true);
				return InteractionResult.FAIL;
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	public ItemStack getPickResult() {
		return toItem();
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void readAdditionalSaveData(ValueInput view) {
		this.entityData.set(MINE_ID, view.getStringOr("Mine", ""));
		this.entityData.set(NORMAL, Mth.clamp(view.getIntOr("Normal", Direction.UP.get3DDataValue()), 0, 5));
		this.entityData.set(ARMED, view.getBooleanOr("Armed", false));
		this.armingProgress = view.getIntOr("Arming", 0);
		this.owner = view.read("Owner", UUIDUtil.CODEC).orElse(null);
		this.attachedVehicle = view.read("Vehicle", UUIDUtil.CODEC).orElse(null);
		this.localOffset = new Vec3(view.getDoubleOr("LocalX", 0.0), view.getDoubleOr("LocalY", 0.0), view.getDoubleOr("LocalZ", 0.0));
		this.localYaw = view.getFloatOr("LocalYaw", 0f);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput view) {
		view.putString("Mine", this.entityData.get(MINE_ID));
		view.putInt("Normal", this.entityData.get(NORMAL));
		view.putBoolean("Armed", isArmed());
		view.putInt("Arming", this.armingProgress);
		if (this.owner != null) {
			view.store("Owner", UUIDUtil.CODEC, this.owner);
		}
		if (this.attachedVehicle != null) {
			view.store("Vehicle", UUIDUtil.CODEC, this.attachedVehicle);
			view.putDouble("LocalX", this.localOffset.x);
			view.putDouble("LocalY", this.localOffset.y);
			view.putDouble("LocalZ", this.localOffset.z);
			view.putFloat("LocalYaw", this.localYaw);
		}
	}
}
