package com.example.tudursguns.screen;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.SoldierRoutePayload;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** Soldier post: the weapon slot, four armor slots, the food slots, and (through the property
 * delegate) what the soldier is up to, and a slot for a Drone Route Book (the route then comes
 * from it - see SoldierPostBlockEntity). Buttons: BUTTON_TOGGLE sends the soldier out / stands it
 * down; BUTTON_ROUTE swaps this screen for the route editor (SoldierRoutePayload). */
public class SoldierPostScreenHandler extends AbstractContainerMenu {

	public static final int BUTTON_TOGGLE = 0;
	public static final int BUTTON_ROUTE = 1;

	public static final int ARMOR_X = 8;
	public static final int WEAPON_X = 30;
	public static final int WEAPON_Y = 45;
	public static final int BOOK_Y = 72;
	public static final int FOOD_X = 140;
	public static final int TOP_Y = 18;
	public static final int PLAYER_INVENTORY_Y = 100;

	private final ContainerData properties;
	private final ContainerLevelAccess context;

	public SoldierPostScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(SoldierPostBlockEntity.SIZE),
				new SimpleContainerData(SoldierPostBlockEntity.PROPERTY_COUNT), ContainerLevelAccess.NULL);
	}

	public SoldierPostScreenHandler(int syncId, Inventory playerInventory, Container inventory, ContainerData properties,
			ContainerLevelAccess context) {
		super(ModScreenHandlers.SOLDIER_POST, syncId);
		this.properties = properties;
		this.context = context;
		this.addSlot(new Slot(inventory, SoldierPostBlockEntity.WEAPON_SLOT, WEAPON_X, WEAPON_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return HandheldWeaponItem.isWeapon(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < 4; i++) {
			EquipmentSlot slot = SoldierPostBlockEntity.EQUIPMENT_SLOTS[1 + i];
			this.addSlot(new Slot(inventory, SoldierPostBlockEntity.ARMOR_SLOT_START + i, ARMOR_X, TOP_Y + i * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return fitsArmorSlot(stack, slot);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int i = 0; i < SoldierPostBlockEntity.FOOD_SLOTS; i++) {
			this.addSlot(new Slot(inventory, SoldierPostBlockEntity.FOOD_SLOT_START + i, FOOD_X + (i % 3) * 18, TOP_Y + (i / 3) * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.has(DataComponents.FOOD);
				}
			});
		}
		this.addSlot(new Slot(inventory, SoldierPostBlockEntity.BOOK_SLOT, WEAPON_X, BOOK_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addStandardInventorySlots(playerInventory, 8, PLAYER_INVENTORY_Y);
		this.addDataSlots(properties);
	}

	private static boolean fitsArmorSlot(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	public int property(int index) {
		return this.properties.get(index);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id != BUTTON_TOGGLE && id != BUTTON_ROUTE) {
			return false;
		}
		this.context.execute((world, pos) -> {
			if (!(world.getBlockEntity(pos) instanceof SoldierPostBlockEntity post)) {
				return;
			}
			if (id == BUTTON_TOGGLE) {
				post.toggleActive();
			} else if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.closeContainer();
				ServerPlayNetworking.send(serverPlayer, new SoldierRoutePayload(pos, post.route(), post.engageRange()));
			}
		});
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int postSlots = SoldierPostBlockEntity.SIZE;
		if (index < postSlots) {
			if (!this.moveItemStackTo(stack, postSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertIntoPost(stack)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	/** Into the first post slot that takes it. */
	private boolean insertIntoPost(ItemStack stack) {
		for (int i = 0; i < SoldierPostBlockEntity.SIZE; i++) {
			if (this.slots.get(i).mayPlace(stack) && this.moveItemStackTo(stack, i, i + 1, false)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.context, player, ModBlocks.SOLDIER_POST);
	}
}
