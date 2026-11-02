package com.example.tudursguns.item;

import com.example.tudursguns.entity.MineEntity;
import com.example.tudursguns.handheld.MineDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The one item every placeable charge is; which one comes from its tudursguns:mine component.
 * Use it on a block to place it (on top only, for placement "ground"). A charge with placement
 * "anywhere" can also be stuck to a vehicle: sneak and use it while looking at the vehicle. */
public class MineItem extends DefinedItem<MineDefinition> {

	private static final double VEHICLE_REACH = 4.5;

	public MineItem(Properties settings) {
		super(settings, ModDefinitions.MINES, ModComponents.MINE);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		Identifier id = stack.get(ModComponents.MINE);
		MineDefinition def = ModDefinitions.MINES.getAny(id);
		if (def == null || player == null || player.getVehicle() != null) {
			return InteractionResult.PASS;
		}
		Direction side = context.getClickedFace();
		if (def.placement() == MineDefinition.Placement.GROUND && side != Direction.UP) {
			return InteractionResult.FAIL;
		}
		if (context.getLevel() instanceof ServerLevel world) {
			MineEntity.place(world, player, id, mountPos(context.getClickLocation(), side), side, mountYaw(side, player), null);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sneak + use while looking at a vehicle: stick it on (placement "anywhere" only). */
	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		ItemStack stack = user.getItemInHand(hand);
		Identifier id = stack.get(ModComponents.MINE);
		MineDefinition def = ModDefinitions.MINES.getAny(id);
		if (def == null || def.placement() != MineDefinition.Placement.ANYWHERE || !user.isShiftKeyDown() || user.getVehicle() != null) {
			return InteractionResult.PASS;
		}
		Vec3 eye = user.getEyePosition();
		Vec3 end = eye.add(user.getViewVector(1.0f).scale(VEHICLE_REACH));
		BlockHitResult blockHit = world.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, user));
		double limit = blockHit.getType() == HitResult.Type.MISS ? VEHICLE_REACH : blockHit.getLocation().distanceTo(eye);
		AbstractVehicleEntity best = null;
		Vec3 bestHit = null;
		double bestDistance = limit;
		for (AbstractVehicleEntity vehicle : world.getEntitiesOfClass(AbstractVehicleEntity.class,
				new AABB(eye, end).inflate(1.0), AbstractVehicleEntity::isAlive)) {
			Optional<Vec3> hit = vehicle.getBoundingBox().clip(eye, end);
			if (hit.isPresent() && hit.get().distanceTo(eye) < bestDistance) {
				best = vehicle;
				bestHit = hit.get();
				bestDistance = hit.get().distanceTo(eye);
			}
		}
		if (best == null) {
			return InteractionResult.PASS;
		}
		if (world instanceof ServerLevel serverWorld) {
			Direction face = faceOf(best.getBoundingBox(), bestHit);
			MineEntity.place(serverWorld, user, id, mountPos(bestHit, face), face, mountYaw(face, user), best);
			stack.consume(1, user);
		}
		return InteractionResult.SUCCESS;
	}

	/** Where a mine placed at hit on a face sits: on top, hanging under, or just off a side. */
	private static Vec3 mountPos(Vec3 hit, Direction face) {
		return switch (face) {
			case UP -> hit;
			case DOWN -> hit.add(0, -0.2, 0);
			default -> hit.add(face.getUnitVec3().scale(0.1)).add(0, -0.1, 0);
		};
	}

	/** On a side it faces out from it; on top or underneath, the way the player looks. */
	private static float mountYaw(Direction face, Player player) {
		return face.getAxis().isHorizontal() ? yawOf(face.getUnitVec3()) : player.getYRot();
	}

	/** The face of box that point lies on (the nearest one). */
	private static Direction faceOf(AABB box, Vec3 point) {
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
	private static float yawOf(Vec3 direction) {
		return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
	}

	@Override
	protected void appendDetails(ItemStack stack, MineDefinition def, Consumer<Component> textConsumer) {
		textConsumer.accept(Component.translatable("tooltip.tudursguns.mine.trigger." + def.trigger().name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		if (def.trigger() == MineDefinition.Trigger.PROXIMITY) {
			String key = def.triggerLiving() && def.triggerVehicles() ? "both" : def.triggerVehicles() ? "vehicles" : "living";
			textConsumer.accept(Component.translatable("tooltip.tudursguns.mine.set_off_by." + key).withStyle(ChatFormatting.GRAY));
		}
		def.movement().appendTooltip(textConsumer);
		textConsumer.accept(Component.translatable("tooltip.tudursguns.mine.placement." + def.placement().name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
