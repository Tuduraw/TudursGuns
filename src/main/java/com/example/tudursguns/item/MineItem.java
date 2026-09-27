package com.example.tudursguns.item;

import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** The one item every placeable charge is; which one comes from its tudursguns:mine component.
 * Use it on a block to place it (on top only, for placement "ground"). A charge with placement
 * "anywhere" can also be stuck to a vehicle: sneak and use it while looking at the vehicle. */
public class MineItem extends DefinedItem<MineDefinition> {

	private static final double VEHICLE_REACH = 4.5;

	public MineItem(Settings settings) {
		super(settings, ModDefinitions.MINES, ModComponents.MINE);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		ItemStack stack = context.getStack();
		Identifier id = stack.get(ModComponents.MINE);
		MineDefinition def = ModDefinitions.MINES.getAny(id);
		if (def == null || player == null || player.getVehicle() != null) {
			return ActionResult.PASS;
		}
		Direction side = context.getSide();
		if (def.placement() == MineDefinition.Placement.GROUND && side != Direction.UP) {
			return ActionResult.FAIL;
		}
		if (context.getWorld() instanceof ServerWorld world) {
			Vec3d hit = context.getHitPos();
			Vec3d pos = switch (side) {
				case UP -> hit;
				case DOWN -> hit.add(0, -0.2, 0);
				default -> hit.add(side.getDoubleVector().multiply(0.1)).add(0, -0.1, 0);
			};
			float yaw = side.getAxis().isHorizontal() ? yawOf(side.getDoubleVector()) : player.getYaw();
			MineEntity.place(world, player, id, pos, side, yaw, null);
			stack.decrementUnlessCreative(1, player);
		}
		return ActionResult.SUCCESS;
	}

	/** Sneak + use while looking at a vehicle: stick it on (placement "anywhere" only). */
	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Identifier id = stack.get(ModComponents.MINE);
		MineDefinition def = ModDefinitions.MINES.getAny(id);
		if (def == null || def.placement() != MineDefinition.Placement.ANYWHERE || !user.isSneaking() || user.getVehicle() != null) {
			return ActionResult.PASS;
		}
		Vec3d eye = user.getEyePos();
		Vec3d end = eye.add(user.getRotationVec(1.0f).multiply(VEHICLE_REACH));
		BlockHitResult blockHit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, user));
		double limit = blockHit.getType() == HitResult.Type.MISS ? VEHICLE_REACH : blockHit.getPos().distanceTo(eye);
		AbstractVehicleEntity best = null;
		Vec3d bestHit = null;
		double bestDistance = limit;
		for (AbstractVehicleEntity vehicle : world.getEntitiesByClass(AbstractVehicleEntity.class,
				new Box(eye, end).expand(1.0), AbstractVehicleEntity::isAlive)) {
			Optional<Vec3d> hit = vehicle.getBoundingBox().raycast(eye, end);
			if (hit.isPresent() && hit.get().distanceTo(eye) < bestDistance) {
				best = vehicle;
				bestHit = hit.get();
				bestDistance = hit.get().distanceTo(eye);
			}
		}
		if (best == null) {
			return ActionResult.PASS;
		}
		if (world instanceof ServerWorld serverWorld) {
			Direction face = faceOf(best.getBoundingBox(), bestHit);
			float yaw = face.getAxis().isHorizontal() ? yawOf(face.getDoubleVector()) : user.getYaw();
			Vec3d pos = face == Direction.DOWN ? bestHit.add(0, -0.2, 0)
					: face.getAxis().isHorizontal() ? bestHit.add(face.getDoubleVector().multiply(0.1)).add(0, -0.1, 0) : bestHit;
			MineEntity.place(serverWorld, user, id, pos, face, yaw, best);
			stack.decrementUnlessCreative(1, user);
		}
		return ActionResult.SUCCESS;
	}

	/** The face of box that point lies on (the nearest one). */
	private static Direction faceOf(Box box, Vec3d point) {
		Direction best = Direction.UP;
		double bestDistance = Double.MAX_VALUE;
		for (Direction direction : Direction.values()) {
			double distance = switch (direction) {
				case UP -> Math.abs(box.maxY - point.y);
				case DOWN -> Math.abs(point.y - box.minY);
				case EAST -> Math.abs(box.maxX - point.x);
				case WEST -> Math.abs(point.x - box.minX);
				case SOUTH -> Math.abs(box.maxZ - point.z);
				case NORTH -> Math.abs(point.z - box.minZ);
			};
			if (distance < bestDistance) {
				bestDistance = distance;
				best = direction;
			}
		}
		return best;
	}

	/** Entity yaw (degrees) that faces along a horizontal vector. */
	private static float yawOf(Vec3d direction) {
		return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
	}

	@Override
	protected void appendDetails(ItemStack stack, MineDefinition def, Consumer<Text> textConsumer) {
		textConsumer.accept(Text.translatable("tooltip.tudursguns.mine.trigger." + def.trigger().name().toLowerCase(Locale.ROOT))
				.formatted(Formatting.GRAY));
		if (def.trigger() == MineDefinition.Trigger.PROXIMITY) {
			String key = def.triggerLiving() && def.triggerVehicles() ? "both" : def.triggerVehicles() ? "vehicles" : "living";
			textConsumer.accept(Text.translatable("tooltip.tudursguns.mine.set_off_by." + key).formatted(Formatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		textConsumer.accept(Text.translatable("tooltip.tudursguns.mine.placement." + def.placement().name().toLowerCase(Locale.ROOT))
				.formatted(Formatting.DARK_GRAY));
	}
}
