package com.example.tudursguns.block;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModBlockEntities;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.screen.SoldierPostScreenHandler;
import com.example.tudursguns.soldier.FriendlySoldierEntity;
import com.example.tudursguns.soldier.SoldierState;
import com.example.tudursguns.soldier.SoldierWaypoint;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Uuids;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/** A soldier post: sends out one soldier (FriendlySoldierEntity) with the weapon and armor in its
 * slots, feeds it from its food slots, and sends another after one is killed.
 *
 * - The soldier wears the post's own stacks (see equip), so the post always shows its gear as it is.
 * - Food: items with a food component are eaten into a store (nutrition + saturation, up to MAX_FOOD),
 *   which the soldier uses at the server config's soldier_food_* rates for what it is doing
 *   (SoldierState.foodPerTick). With the store empty the soldier walks back and waits, and can't fire.
 * - A soldier killed (or lost - missing from a loaded chunk it was last seen in) costs its armor
 *   DEATH_WEAR of its durability; the post sends another after soldier_respawn_ticks, for
 *   soldier_respawn_food.
 * - Stood down, the soldier walks back and goes into the post. */
public class SoldierPostBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

	public static final int WEAPON_SLOT = 0;
	public static final int ARMOR_SLOT_START = 1;
	public static final int FOOD_SLOT_START = 5;
	public static final int FOOD_SLOTS = 6;
	public static final int SIZE = FOOD_SLOT_START + FOOD_SLOTS;
	/** The soldier's equipment slot for each of the post's weapon and armor slots. */
	public static final EquipmentSlot[] EQUIPMENT_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public static final float MAX_FOOD = 40f;
	public static final int MAX_WAYPOINTS = 64;
	public static final int MIN_ENGAGE_RANGE = 8;
	public static final int MAX_ENGAGE_RANGE = 128;
	public static final int DEFAULT_ENGAGE_RANGE = 24;
	/** Share of its durability each armor piece loses when the soldier is killed. */
	private static final float DEATH_WEAR = 0.1f;
	/** Ticks a soldier may be missing from a loaded chunk before it counts as lost. */
	private static final int LOST_TICKS = 100;

	/** Indices of the values PROPERTIES gives the screen. */
	public static final int PROPERTY_ACTIVE = 0;
	public static final int PROPERTY_FOOD_TENTHS = 1;
	public static final int PROPERTY_STATE = 2;
	public static final int PROPERTY_RESPAWN_SECONDS = 3;
	public static final int PROPERTY_HEALTH_TENTHS = 4;
	public static final int PROPERTY_ENGAGE_RANGE = 5;
	public static final int PROPERTY_ROUTE_SIZE = 6;
	public static final int PROPERTY_COUNT = 7;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
	private boolean active;
	private List<SoldierWaypoint> route = List.of();
	private int engageRange = DEFAULT_ENGAGE_RANGE;
	private float food;
	private UUID soldier;
	/** Ticks until another soldier may be sent; respawnDue: the next one costs soldier_respawn_food. */
	private int respawnTicks;
	private boolean respawnDue;
	private BlockPos lastSeen = BlockPos.ORIGIN;
	private int missingTicks;
	private SoldierState state = SoldierState.STANDBY;
	private float soldierHealth;

	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case PROPERTY_ACTIVE -> SoldierPostBlockEntity.this.active ? 1 : 0;
				case PROPERTY_FOOD_TENTHS -> Math.round(SoldierPostBlockEntity.this.food * 10f);
				case PROPERTY_STATE -> SoldierPostBlockEntity.this.state.ordinal();
				case PROPERTY_RESPAWN_SECONDS -> (SoldierPostBlockEntity.this.respawnTicks + 19) / 20;
				case PROPERTY_HEALTH_TENTHS -> Math.round(SoldierPostBlockEntity.this.soldierHealth * 10f);
				case PROPERTY_ENGAGE_RANGE -> SoldierPostBlockEntity.this.engageRange;
				case PROPERTY_ROUTE_SIZE -> SoldierPostBlockEntity.this.route.size();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return PROPERTY_COUNT;
		}
	};

	public SoldierPostBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLDIER_POST, pos, state);
	}

	// ---------------------------------------------------------------- what the soldier asks

	public boolean isSoldier(FriendlySoldierEntity entity) {
		return entity.getUuid().equals(this.soldier);
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean hasFood() {
		return this.food > 0f;
	}

	public List<SoldierWaypoint> route() {
		return this.route;
	}

	public int engageRange() {
		return this.engageRange;
	}

	/** Dresses the soldier in the post's stacks - the same objects, so what it does to them (rounds
	 * fired, armor worn down) is what the post holds, and a stack taken out of the post (emptied by
	 * the split) is gone from the soldier too. A stack put in is a new object, and is put on here. */
	public void equip(FriendlySoldierEntity entity) {
		for (int i = 0; i < EQUIPMENT_SLOTS.length; i++) {
			ItemStack stack = this.items.get(WEAPON_SLOT + i);
			if (entity.getEquippedStack(EQUIPMENT_SLOTS[i]) != stack) {
				entity.equipStack(EQUIPMENT_SLOTS[i], stack);
			}
		}
	}

	public void onSoldierDied(FriendlySoldierEntity entity) {
		if (isSoldier(entity)) {
			lose();
		}
	}

	/** A stood-down soldier back at the post goes into it. */
	public void recall(FriendlySoldierEntity entity) {
		if (isSoldier(entity)) {
			entity.discard();
			this.soldier = null;
			this.markDirty();
		}
	}

	private void lose() {
		this.soldier = null;
		this.respawnTicks = TudursGunsConfig.get().soldier_respawn_ticks;
		this.respawnDue = true;
		if (this.world instanceof ServerWorld serverWorld) {
			for (int i = ARMOR_SLOT_START; i < FOOD_SLOT_START; i++) {
				ItemStack armor = this.items.get(i);
				if (armor.isDamageable()) {
					armor.damage(Math.max(1, Math.round(armor.getMaxDamage() * DEATH_WEAR)), serverWorld, null, item -> {
					});
				}
			}
		}
		this.markDirty();
	}

	// ---------------------------------------------------------------- what the player sets

	public void toggleActive() {
		this.active = !this.active;
		this.markDirty();
	}

	/** Route and engage range, brought into range (they come from a client, or a saved world). */
	public void setRoute(List<SoldierWaypoint> route, int engageRange) {
		applyRoute(route, engageRange);
		this.markDirty();
	}

	private void applyRoute(List<SoldierWaypoint> route, int engageRange) {
		this.route = route.stream().limit(MAX_WAYPOINTS).map(SoldierWaypoint::clamped).toList();
		this.engageRange = MathHelper.clamp(engageRange, MIN_ENGAGE_RANGE, MAX_ENGAGE_RANGE);
	}

	// ---------------------------------------------------------------- ticking

	public static void tick(World world, BlockPos pos, BlockState state, SoldierPostBlockEntity post) {
		if (world instanceof ServerWorld serverWorld) {
			post.serverTick(serverWorld);
		}
	}

	private void serverTick(ServerWorld world) {
		eat();
		this.state = findState(world);
		this.food = Math.max(0f, this.food - this.state.foodPerTick());
		if (this.soldier == null) {
			if (this.respawnTicks > 0) {
				this.respawnTicks--;
			} else if (this.active) {
				trySend(world);
			}
		}
		if (world.getTime() % 20 == 0) {
			this.markDirty();
		}
	}

	/** Eats from the food slots while there's room in the store. */
	private void eat() {
		for (int i = FOOD_SLOT_START; i < SIZE; i++) {
			ItemStack stack = this.items.get(i);
			FoodComponent food = stack.get(DataComponentTypes.FOOD);
			while (food != null && !stack.isEmpty() && this.food + food.nutrition() <= MAX_FOOD) {
				this.food = Math.min(MAX_FOOD, this.food + food.nutrition() + food.saturation());
				stack.decrement(1);
			}
		}
	}

	private SoldierState findState(ServerWorld world) {
		if (this.soldier == null) {
			this.soldierHealth = 0f;
			return this.respawnDue ? SoldierState.DOWN : SoldierState.STANDBY;
		}
		if (world.getEntity(this.soldier) instanceof FriendlySoldierEntity entity && entity.isAlive()) {
			this.lastSeen = entity.getBlockPos();
			this.missingTicks = 0;
			this.soldierHealth = entity.getHealth();
			return entity.state();
		}
		// Not found: fine if it's somewhere unloaded; gone if it's missing from where it was last seen.
		if (world.shouldTickEntityAt(this.lastSeen) && ++this.missingTicks > LOST_TICKS) {
			this.missingTicks = 0;
			lose();
			return SoldierState.DOWN;
		}
		return SoldierState.AWAY;
	}

	private void trySend(ServerWorld world) {
		float cost = this.respawnDue ? TudursGunsConfig.get().soldier_respawn_food : 0f;
		if (!HandheldWeaponItem.isWeapon(this.items.get(WEAPON_SLOT)) || this.food <= cost) {
			return;
		}
		FriendlySoldierEntity entity = ModEntityTypes.SOLDIER.create(world, SpawnReason.MOB_SUMMONED);
		if (entity == null) {
			return;
		}
		BlockPos pos = this.getPos();
		entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
		if (!world.isSpaceEmpty(entity)) {
			return;
		}
		entity.setPost(pos);
		entity.setPersistent();
		equip(entity);
		world.spawnEntity(entity);
		this.soldier = entity.getUuid();
		this.lastSeen = entity.getBlockPos();
		this.missingTicks = 0;
		this.food -= cost;
		this.respawnDue = false;
		this.markDirty();
	}

	/** Broken: the gear drops (which also takes it off the soldier) and the soldier is gone. */
	@Override
	public void onBlockReplaced(BlockPos pos, BlockState oldState) {
		if (this.world instanceof ServerWorld world) {
			ItemScatterer.spawn(world, pos, this);
			if (this.soldier != null) {
				Entity entity = world.getEntity(this.soldier);
				if (entity != null) {
					entity.discard();
				}
			}
		}
	}

	// ---------------------------------------------------------------- screen

	@Override
	public Text getDisplayName() {
		return Text.translatable("container.tudursguns.soldier_post");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SoldierPostScreenHandler(syncId, playerInventory, this, this.properties, ScreenHandlerContext.create(this.world, this.getPos()));
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items);
		view.putBoolean("active", this.active);
		view.put("route", SoldierWaypoint.CODEC.listOf(), this.route);
		view.putInt("engage_range", this.engageRange);
		view.putFloat("food", this.food);
		view.putNullable("soldier", Uuids.INT_STREAM_CODEC, this.soldier);
		view.putInt("respawn_ticks", this.respawnTicks);
		view.putBoolean("respawn_due", this.respawnDue);
		view.put("last_seen", BlockPos.CODEC, this.lastSeen);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.items.clear();
		Inventories.readData(view, this.items);
		this.active = view.getBoolean("active", false);
		applyRoute(view.read("route", SoldierWaypoint.CODEC.listOf()).orElse(List.of()), view.getInt("engage_range", DEFAULT_ENGAGE_RANGE));
		this.food = MathHelper.clamp(view.getFloat("food", 0f), 0f, MAX_FOOD);
		this.soldier = view.read("soldier", Uuids.INT_STREAM_CODEC).orElse(null);
		this.respawnTicks = view.getInt("respawn_ticks", 0);
		this.respawnDue = view.getBoolean("respawn_due", false);
		this.lastSeen = view.read("last_seen", BlockPos.CODEC).orElse(this.getPos());
	}

	// ---------------------------------------------------------------- Inventory

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack removed = Inventories.splitStack(this.items, slot, amount);
		if (!removed.isEmpty()) {
			this.markDirty();
		}
		return removed;
	}

	/** Emptied through split rather than swapped out, so the soldier wearing it loses it too. */
	@Override
	public ItemStack removeStack(int slot) {
		ItemStack stack = this.items.get(slot);
		this.markDirty();
		return stack.isEmpty() ? ItemStack.EMPTY : stack.split(stack.getCount());
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		// A swapped-out stack stays on the soldier only until its next tick puts the new one on (equip).
		this.items.set(slot, stack);
		this.markDirty();
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		this.items.clear();
	}
}
