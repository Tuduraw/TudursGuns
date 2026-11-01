package com.example.tudursguns.block;

import com.example.tudursguns.TudursGunsConfig;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModBlockEntities;
import com.example.tudursguns.registry.ModEntityTypes;
import com.example.tudursguns.screen.SoldierPostScreenHandler;
import com.example.tudursguns.soldier.FriendlySoldierEntity;
import com.example.tudursguns.soldier.SoldierState;
import com.example.tudursguns.soldier.SoldierWaypoint;
import com.example.tudursvehiclemod.block.DroneRouteBookWaypoint;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import com.example.tudursvehiclemod.item.ModComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
 * - Stood down, the soldier walks back and goes into the post.
 * - Route: the post's own, or - while a Drone Route Book (Tudur's Vehicle Mod) is in BOOK_SLOT - the
 *   book's, like a Drone Center: its absolute points (plus the book's offset) become points relative
 *   to the post, and editing the route writes them back into the book. The book has no wait times;
 *   those stay the post's, by point number. Taking the book out keeps its route as the post's own. */
public class SoldierPostBlockEntity extends BlockEntity implements Container, MenuProvider {

	public static final int WEAPON_SLOT = 0;
	public static final int ARMOR_SLOT_START = 1;
	public static final int FOOD_SLOT_START = 5;
	public static final int FOOD_SLOTS = 6;
	public static final int BOOK_SLOT = FOOD_SLOT_START + FOOD_SLOTS;
	public static final int SIZE = BOOK_SLOT + 1;
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
	public static final int PROPERTY_ROUTE_FROM_BOOK = 7;
	public static final int PROPERTY_COUNT = 8;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private boolean active;
	/** The post's own route; with a book in, only its wait times are used. */
	private List<SoldierWaypoint> route = List.of();
	/** The book's route as last read (see route()), and the book data it was read from. */
	private List<SoldierWaypoint> bookRoute = List.of();
	private String bookRouteData;
	private BlockPos bookRoutePos;
	private int engageRange = DEFAULT_ENGAGE_RANGE;
	private float food;
	private UUID soldier;
	/** Ticks until another soldier may be sent; respawnDue: the next one costs soldier_respawn_food. */
	private int respawnTicks;
	private boolean respawnDue;
	private BlockPos lastSeen = BlockPos.ZERO;
	private int missingTicks;
	private SoldierState state = SoldierState.STANDBY;
	private float soldierHealth;

	private final ContainerData properties = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case PROPERTY_ACTIVE -> SoldierPostBlockEntity.this.active ? 1 : 0;
				case PROPERTY_FOOD_TENTHS -> Math.round(SoldierPostBlockEntity.this.food * 10f);
				case PROPERTY_STATE -> SoldierPostBlockEntity.this.state.ordinal();
				case PROPERTY_RESPAWN_SECONDS -> (SoldierPostBlockEntity.this.respawnTicks + 19) / 20;
				case PROPERTY_HEALTH_TENTHS -> Math.round(SoldierPostBlockEntity.this.soldierHealth * 10f);
				case PROPERTY_ENGAGE_RANGE -> SoldierPostBlockEntity.this.engageRange;
				case PROPERTY_ROUTE_SIZE -> SoldierPostBlockEntity.this.route().size();
				case PROPERTY_ROUTE_FROM_BOOK -> hasBook() ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return PROPERTY_COUNT;
		}
	};

	public SoldierPostBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLDIER_POST, pos, state);
	}

	// ---------------------------------------------------------------- what the soldier asks

	public boolean isSoldier(FriendlySoldierEntity entity) {
		return entity.getUUID().equals(this.soldier);
	}

	public boolean isActive() {
		return this.active;
	}

	public boolean hasFood() {
		return this.food > 0f;
	}

	/** The route in use, relative to the post: the book's while one is in, else the post's own. */
	public List<SoldierWaypoint> route() {
		if (!hasBook()) {
			return this.route;
		}
		ItemStack book = this.items.get(BOOK_SLOT);
		String data = book.get(ModComponents.DRONE_ROUTE_WAYPOINTS) + "|" + book.get(ModComponents.DRONE_ROUTE_OFFSET);
		if (!data.equals(this.bookRouteData) || !this.getBlockPos().equals(this.bookRoutePos)) {
			Vec3i offset = DroneRouteBookItem.tudursvehiclemod$getOffset(book);
			List<SoldierWaypoint> relative = new ArrayList<>();
			for (DroneRouteBookWaypoint point : DroneRouteBookItem.tudursvehiclemod$getWaypoints(book)) {
				if (relative.size() >= MAX_WAYPOINTS) {
					break;
				}
				relative.add(new SoldierWaypoint(point.x() + offset.getX() - this.getBlockPos().getX(), point.y() + offset.getY() - this.getBlockPos().getY(),
						point.z() + offset.getZ() - this.getBlockPos().getZ(), 0).clamped());
			}
			this.bookRoute = relative;
			this.bookRouteData = data;
			this.bookRoutePos = this.getBlockPos();
		}
		List<SoldierWaypoint> withWaits = new ArrayList<>(this.bookRoute.size());
		for (int i = 0; i < this.bookRoute.size(); i++) {
			SoldierWaypoint point = this.bookRoute.get(i);
			int wait = i < this.route.size() ? this.route.get(i).waitTicks() : 0;
			withWaits.add(new SoldierWaypoint(point.x(), point.y(), point.z(), wait));
		}
		return withWaits;
	}

	private boolean hasBook() {
		return this.items.get(BOOK_SLOT).getItem() instanceof DroneRouteBookItem;
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
			if (entity.getItemBySlot(EQUIPMENT_SLOTS[i]) != stack) {
				entity.setItemSlot(EQUIPMENT_SLOTS[i], stack);
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
			this.setChanged();
		}
	}

	private void lose() {
		this.soldier = null;
		this.respawnTicks = TudursGunsConfig.get().soldier_respawn_ticks;
		this.respawnDue = true;
		if (this.level instanceof ServerLevel serverWorld) {
			for (int i = ARMOR_SLOT_START; i < FOOD_SLOT_START; i++) {
				ItemStack armor = this.items.get(i);
				if (armor.isDamageableItem()) {
					armor.hurtAndBreak(Math.max(1, Math.round(armor.getMaxDamage() * DEATH_WEAR)), serverWorld, null, item -> {
					});
				}
			}
		}
		this.setChanged();
	}

	// ---------------------------------------------------------------- what the player sets

	public void toggleActive() {
		this.active = !this.active;
		this.setChanged();
	}

	/** Route and engage range, brought into range (they come from a client). With a book in, the
	 * points are written into it (as absolute positions, less its offset); the wait times stay here. */
	public void setRoute(List<SoldierWaypoint> route, int engageRange) {
		applyRoute(route, engageRange);
		if (hasBook()) {
			ItemStack book = this.items.get(BOOK_SLOT);
			Vec3i offset = DroneRouteBookItem.tudursvehiclemod$getOffset(book);
			List<DroneRouteBookWaypoint> absolute = new ArrayList<>();
			for (SoldierWaypoint point : this.route) {
				BlockPos at = point.absolute(this.getBlockPos()).subtract(offset);
				absolute.add(DroneRouteBookWaypoint.createDefault(at.getX(), at.getY(), at.getZ()));
			}
			DroneRouteBookItem.tudursvehiclemod$setWaypoints(book, absolute);
		}
		this.setChanged();
	}

	/** Before the book slot changes: the book's route (if any) becomes the post's own. */
	private void keepBookRoute(int slot) {
		if (slot == BOOK_SLOT && hasBook()) {
			this.route = route();
		}
	}

	private void applyRoute(List<SoldierWaypoint> route, int engageRange) {
		this.route = route.stream().limit(MAX_WAYPOINTS).map(SoldierWaypoint::clamped).toList();
		this.engageRange = Mth.clamp(engageRange, MIN_ENGAGE_RANGE, MAX_ENGAGE_RANGE);
	}

	// ---------------------------------------------------------------- ticking

	public static void tick(Level world, BlockPos pos, BlockState state, SoldierPostBlockEntity post) {
		if (world instanceof ServerLevel serverWorld) {
			post.serverTick(serverWorld);
		}
	}

	private void serverTick(ServerLevel world) {
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
		if (world.getGameTime() % 20 == 0) {
			this.setChanged();
		}
	}

	/** Eats from the food slots while there's room in the store. */
	private void eat() {
		for (int i = FOOD_SLOT_START; i < FOOD_SLOT_START + FOOD_SLOTS; i++) {
			ItemStack stack = this.items.get(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			while (food != null && !stack.isEmpty() && this.food + food.nutrition() <= MAX_FOOD) {
				this.food = Math.min(MAX_FOOD, this.food + food.nutrition() + food.saturation());
				stack.shrink(1);
			}
		}
	}

	private SoldierState findState(ServerLevel world) {
		if (this.soldier == null) {
			this.soldierHealth = 0f;
			return this.respawnDue ? SoldierState.DOWN : SoldierState.STANDBY;
		}
		if (world.getEntity(this.soldier) instanceof FriendlySoldierEntity entity && entity.isAlive()) {
			this.lastSeen = entity.blockPosition();
			this.missingTicks = 0;
			this.soldierHealth = entity.getHealth();
			return entity.state();
		}
		// Not found: fine if it's somewhere unloaded; gone if it's missing from where it was last seen.
		if (world.isPositionEntityTicking(this.lastSeen) && ++this.missingTicks > LOST_TICKS) {
			this.missingTicks = 0;
			lose();
			return SoldierState.DOWN;
		}
		return SoldierState.AWAY;
	}

	private void trySend(ServerLevel world) {
		float cost = this.respawnDue ? TudursGunsConfig.get().soldier_respawn_food : 0f;
		if (!HandheldWeaponItem.isWeapon(this.items.get(WEAPON_SLOT)) || this.food <= cost) {
			return;
		}
		FriendlySoldierEntity entity = ModEntityTypes.SOLDIER.create(world, EntitySpawnReason.MOB_SUMMONED);
		if (entity == null) {
			return;
		}
		BlockPos pos = this.getBlockPos();
		entity.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
		if (!world.noCollision(entity)) {
			return;
		}
		entity.setPost(pos);
		entity.setPersistenceRequired();
		equip(entity);
		world.addFreshEntity(entity);
		this.soldier = entity.getUUID();
		this.lastSeen = entity.blockPosition();
		this.missingTicks = 0;
		this.food -= cost;
		this.respawnDue = false;
		this.setChanged();
	}

	/** Broken: the gear drops (which also takes it off the soldier) and the soldier is gone. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
		if (this.level instanceof ServerLevel world) {
			Containers.dropContents(world, pos, this);
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
	public Component getDisplayName() {
		return Component.translatable("container.tudursguns.soldier_post");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new SoldierPostScreenHandler(syncId, playerInventory, this, this.properties, ContainerLevelAccess.create(this.level, this.getBlockPos()));
	}

	// ---------------------------------------------------------------- saving

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items);
		view.putBoolean("active", this.active);
		view.store("route", SoldierWaypoint.CODEC.listOf(), this.route);
		view.putInt("engage_range", this.engageRange);
		view.putFloat("food", this.food);
		view.storeNullable("soldier", UUIDUtil.CODEC, this.soldier);
		view.putInt("respawn_ticks", this.respawnTicks);
		view.putBoolean("respawn_due", this.respawnDue);
		view.store("last_seen", BlockPos.CODEC, this.lastSeen);
	}

	@Override
	protected void loadAdditional(ValueInput view) {
		super.loadAdditional(view);
		this.items.clear();
		ContainerHelper.loadAllItems(view, this.items);
		this.active = view.getBooleanOr("active", false);
		applyRoute(view.read("route", SoldierWaypoint.CODEC.listOf()).orElse(List.of()), view.getIntOr("engage_range", DEFAULT_ENGAGE_RANGE));
		this.food = Mth.clamp(view.getFloatOr("food", 0f), 0f, MAX_FOOD);
		this.soldier = view.read("soldier", UUIDUtil.CODEC).orElse(null);
		this.respawnTicks = view.getIntOr("respawn_ticks", 0);
		this.respawnDue = view.getBooleanOr("respawn_due", false);
		this.lastSeen = view.read("last_seen", BlockPos.CODEC).orElse(this.getBlockPos());
	}

	// ---------------------------------------------------------------- Inventory

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		keepBookRoute(slot);
		ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
		if (!removed.isEmpty()) {
			this.setChanged();
		}
		return removed;
	}

	/** Emptied through split rather than swapped out, so the soldier wearing it loses it too. */
	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		keepBookRoute(slot);
		ItemStack stack = this.items.get(slot);
		this.setChanged();
		return stack.isEmpty() ? ItemStack.EMPTY : stack.split(stack.getCount());
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		// A swapped-out stack stays on the soldier only until its next tick puts the new one on (equip).
		if (stack != this.items.get(slot)) {
			keepBookRoute(slot);
		}
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}
}
