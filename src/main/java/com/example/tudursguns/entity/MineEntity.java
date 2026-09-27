package com.example.tudursguns.entity;

import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.WeaponTargeting;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import com.example.tudursvehiclemod.entity.projectile.WeaponProjectileFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.UUID;

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

	private static final TrackedData<String> MINE_ID = DataTracker.registerData(MineEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Integer> NORMAL = DataTracker.registerData(MineEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> ARMED = DataTracker.registerData(MineEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Ticks a stuck-on vehicle may be missing (not loaded yet after a restart) before the charge falls off. */
	private static final int VEHICLE_MISSING_LIMIT = 100;

	private UUID owner;
	private int armingProgress;
	private int triggerCountdown = -1;
	private UUID attachedVehicle;
	private Vec3d localOffset = Vec3d.ZERO;
	private float localYaw;
	private int vehicleMissingTicks;

	public MineEntity(EntityType<?> type, World world) {
		super(type, world);
		this.setNoGravity(true);
	}

	/** Places a charge on a block face (vehicle == null) or on a vehicle. */
	public static MineEntity place(ServerWorld world, PlayerEntity placer, Identifier mineId, Vec3d pos, Direction normal, float yaw,
			Entity vehicle) {
		MineEntity mine = new MineEntity(ModEntityTypes.MINE, world);
		mine.dataTracker.set(MINE_ID, mineId.toString());
		mine.dataTracker.set(NORMAL, normal.getIndex());
		mine.owner = placer == null ? null : placer.getUuid();
		mine.setPosition(pos.x, pos.y, pos.z);
		mine.setYaw(yaw);
		if (vehicle != null) {
			float vehicleYawRad = (float) Math.toRadians(vehicle.getYaw());
			mine.attachedVehicle = vehicle.getUuid();
			mine.localOffset = pos.subtract(vehicle.getEntityPos()).rotateY(vehicleYawRad);
			mine.localYaw = yaw - vehicle.getYaw();
		}
		world.spawnEntity(mine);
		MineDefinition def = mine.definition();
		if (def != null) {
			def.sounds().place().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, pos, 0.6f, 1.0f, 0.05f));
		}
		return mine;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(MINE_ID, "");
		builder.add(NORMAL, Direction.UP.getIndex());
		builder.add(ARMED, false);
	}

	public Identifier mineId() {
		return Identifier.tryParse(this.dataTracker.get(MINE_ID));
	}

	public MineDefinition definition() {
		return ModDefinitions.MINES.getAny(mineId());
	}

	public Direction normal() {
		return Direction.byIndex(this.dataTracker.get(NORMAL));
	}

	public boolean isArmed() {
		return this.dataTracker.get(ARMED);
	}

	public boolean isOwnedBy(PlayerEntity player) {
		return this.owner != null && this.owner.equals(player.getUuid());
	}

	/** Where the charge is: a little out from the surface it sits on. */
	public Vec3d center() {
		return this.getEntityPos().add(normal().getDoubleVector().multiply(0.1));
	}

	/** The direction a directional charge fires in: the way it faces (horizontal). */
	public Vec3d facing() {
		return Vec3d.fromPolar(0f, this.getYaw());
	}

	public ItemStack toItem() {
		ItemStack stack = new ItemStack(ModItems.MINE);
		Identifier id = mineId();
		if (id != null) {
			stack.set(ModComponents.MINE, id);
		}
		return stack;
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick() {
		super.tick();
		if (!(this.getEntityWorld() instanceof ServerWorld world)) {
			return;
		}
		MineDefinition def = ModDefinitions.MINES.getServer(mineId());
		if (def == null) {
			// Its definition was removed (data pack change) - give it back rather than leave a dud.
			if (this.age > 20) {
				dropAsItem(world);
			}
			return;
		}
		if (!followVehicle(world)) {
			return;
		}
		if (this.attachedVehicle == null && this.age % 20 == 0 && !isSupported(world)) {
			dropAsItem(world);
			return;
		}
		if (!isArmed()) {
			if (++this.armingProgress >= def.armingTicks()) {
				this.dataTracker.set(ARMED, true);
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
		if (def.trigger() != MineDefinition.Trigger.REMOTE && this.age % 2 == 0 && isSetOff(world, def)) {
			setOff(world, def);
		}
	}

	/** Moves with the vehicle it's stuck to. Returns false if it fell off (and is gone). */
	private boolean followVehicle(ServerWorld world) {
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
		float vehicleYawRad = (float) Math.toRadians(vehicle.getYaw());
		Vec3d pos = vehicle.getEntityPos().add(this.localOffset.rotateY(-vehicleYawRad));
		this.setPosition(pos.x, pos.y, pos.z);
		this.setYaw(vehicle.getYaw() + this.localYaw);
		return true;
	}

	private boolean isSupported(ServerWorld world) {
		BlockPos support = BlockPos.ofFloored(this.getEntityPos().subtract(normal().getDoubleVector().multiply(0.2)));
		return !world.getBlockState(support).getCollisionShape(world, support).isEmpty();
	}

	private boolean isSetOff(ServerWorld world, MineDefinition def) {
		Vec3d c = center();
		double r = def.triggerRadius();
		Box box;
		if (def.trigger() == MineDefinition.Trigger.DIRECTIONAL) {
			box = new Box(c, c).expand(r);
		} else if (normal() == Direction.UP) {
			// A ground mine feels what's on the ground around it.
			box = new Box(c.x - r, c.y - 0.25, c.z - r, c.x + r, c.y + 1.0, c.z + r);
		} else {
			box = new Box(c, c).expand(r);
		}
		if (def.triggerLiving()) {
			for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, MineEntity::canSetOff)) {
				if (def.trigger() != MineDefinition.Trigger.DIRECTIONAL || inCone(world, def, living.getBoundingBox().getCenter())) {
					return true;
				}
			}
		}
		if (def.triggerVehicles()) {
			for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class, box,
					vehicle -> vehicle.isAlive() && !vehicle.tudursvehiclemod$isDestroyed()
							&& !vehicle.getUuid().equals(this.attachedVehicle))) {
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
		return living.isAlive() && !living.isSpectator() && !(living instanceof MarkerEntity) && !(living instanceof ArmorStandEntity);
	}

	private boolean inCone(ServerWorld world, MineDefinition def, Vec3d target) {
		Vec3d c = center();
		Vec3d toTarget = target.subtract(c);
		double distance = toTarget.length();
		if (distance < 1.0E-3) {
			return true;
		}
		double halfAngle = Math.toRadians(def.fragments().map(MineDefinition.Fragments::spreadDegrees).orElse(60f) / 2.0);
		if (toTarget.multiply(1.0 / distance).dotProduct(facing()) < Math.cos(halfAngle)) {
			return false;
		}
		return world.raycast(new RaycastContext(c, target, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS;
	}

	private void setOff(ServerWorld world, MineDefinition def) {
		def.sounds().trigger().ifPresent(sound -> HandheldCombat.playSoundAt(world, sound, center(), 0.8f, 1.0f, 0.05f));
		if (def.triggerDelayTicks() <= 0) {
			detonate(world);
		} else {
			this.triggerCountdown = def.triggerDelayTicks();
		}
	}

	// ---------------------------------------------------------------- going off

	/** Explodes now (and fires fragments). Used by triggers, hits and detonators. */
	public void detonate(ServerWorld world) {
		if (this.isRemoved()) {
			return;
		}
		MineDefinition def = ModDefinitions.MINES.getServer(mineId());
		// Gone first, so the explosion hitting this entity can't set it off again.
		this.discard();
		if (def == null) {
			return;
		}
		LivingEntity owner = this.owner == null ? null : world.getServer().getPlayerManager().getPlayer(this.owner);
		Vec3d c = center();
		WeaponStats stats = WeaponStatsLoader.get(def.weapon());
		VehicleProjectileEntity blast = WeaponProjectileFactory.create(world, owner, new ItemStack(Items.IRON_NUGGET), stats, 0);
		blast.tudursvehiclemod$setFuseTicks(-1, 0);
		blast.tudursvehiclemod$setBounceStrength(0f);
		blast.setPosition(c.x, c.y, c.z);
		blast.setVelocity(Vec3d.ZERO);
		world.spawnEntity(blast);
		def.fragments().ifPresent(fragments -> fireFragments(world, owner, fragments, c));
	}

	private void fireFragments(ServerWorld world, LivingEntity owner, MineDefinition.Fragments fragments, Vec3d c) {
		WeaponStats stats = WeaponStatsLoader.get(fragments.weapon());
		float half = fragments.spreadDegrees() / 2f;
		for (int i = 0; i < fragments.count(); i++) {
			float yaw = this.getYaw() + (world.random.nextFloat() * 2f - 1f) * half;
			// Mostly flat, slightly upward: a claymore's fan is wide and low.
			float pitch = -3f + (world.random.nextFloat() * 2f - 1f) * half / 6f;
			Vec3d direction = Vec3d.fromPolar(pitch, yaw);
			VehicleProjectileEntity fragment = WeaponProjectileFactory.create(world, owner, new ItemStack(Items.IRON_NUGGET), stats, 0);
			Vec3d start = c.add(0, 0.2, 0).add(direction.multiply(0.3));
			fragment.setPosition(start.x, start.y, start.z);
			Vec3d velocity = WeaponTargeting.applyAccuracySpread(direction.multiply(stats.velocity()), stats.accuracyDegrees(), world.random);
			fragment.setVelocity(velocity);
			fragment.setAngles(yaw, pitch);
			world.spawnEntity(fragment);
		}
	}

	private void dropAsItem(ServerWorld world) {
		if (this.isRemoved()) {
			return;
		}
		this.discard();
		this.dropStack(world, toItem());
	}

	/** Taken back without going off: by its placer, or with a defuse kit. */
	public void pickUp(ServerPlayerEntity player) {
		if (this.isRemoved()) {
			return;
		}
		this.discard();
		player.getInventory().offerOrDrop(toItem());
	}

	// ---------------------------------------------------------------- interaction

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		}
		if (source.getAttacker() instanceof PlayerEntity player && player.isCreative()) {
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
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (!player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			if (isOwnedBy(serverPlayer) || serverPlayer.isCreative()) {
				pickUp(serverPlayer);
			} else {
				serverPlayer.sendMessage(Text.translatable("message.tudursguns.mine.not_owner"), true);
				return ActionResult.FAIL;
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return toItem();
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void readCustomData(ReadView view) {
		this.dataTracker.set(MINE_ID, view.getString("Mine", ""));
		this.dataTracker.set(NORMAL, MathHelper.clamp(view.getInt("Normal", Direction.UP.getIndex()), 0, 5));
		this.dataTracker.set(ARMED, view.getBoolean("Armed", false));
		this.armingProgress = view.getInt("Arming", 0);
		this.owner = view.read("Owner", Uuids.INT_STREAM_CODEC).orElse(null);
		this.attachedVehicle = view.read("Vehicle", Uuids.INT_STREAM_CODEC).orElse(null);
		this.localOffset = new Vec3d(view.getDouble("LocalX", 0.0), view.getDouble("LocalY", 0.0), view.getDouble("LocalZ", 0.0));
		this.localYaw = view.getFloat("LocalYaw", 0f);
	}

	@Override
	protected void writeCustomData(WriteView view) {
		view.putString("Mine", this.dataTracker.get(MINE_ID));
		view.putInt("Normal", this.dataTracker.get(NORMAL));
		view.putBoolean("Armed", isArmed());
		view.putInt("Arming", this.armingProgress);
		if (this.owner != null) {
			view.put("Owner", Uuids.INT_STREAM_CODEC, this.owner);
		}
		if (this.attachedVehicle != null) {
			view.put("Vehicle", Uuids.INT_STREAM_CODEC, this.attachedVehicle);
			view.putDouble("LocalX", this.localOffset.x);
			view.putDouble("LocalY", this.localOffset.y);
			view.putDouble("LocalZ", this.localOffset.z);
			view.putFloat("LocalYaw", this.localYaw);
		}
	}
}
