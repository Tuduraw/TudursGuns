package com.example.tudursguns.screen;

import com.example.tudursguns.block.SoldierPostBlockEntity;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.SoldierRoutePayload;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

/** Soldier post: the weapon slot, four armor slots, the food slots, and (through the property
 * delegate) what the soldier is up to, and a slot for a Drone Route Book (the route then comes
 * from it - see SoldierPostBlockEntity). Buttons: BUTTON_TOGGLE sends the soldier out / stands it
 * down; BUTTON_ROUTE swaps this screen for the route editor (SoldierRoutePayload). */
public class SoldierPostScreenHandler extends ScreenHandler {

	public static final int BUTTON_TOGGLE = 0;
	public static final int BUTTON_ROUTE = 1;

	public static final int ARMOR_X = 8;
	public static final int WEAPON_X = 30;
	public static final int WEAPON_Y = 45;
	public static final int BOOK_Y = 72;
	public static final int FOOD_X = 140;
	public static final int TOP_Y = 18;
	public static final int PLAYER_INVENTORY_Y = 100;

	private final PropertyDelegate properties;
	private final ScreenHandlerContext context;

	public SoldierPostScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(SoldierPostBlockEntity.SIZE),
				new ArrayPropertyDelegate(SoldierPostBlockEntity.PROPERTY_COUNT), ScreenHandlerContext.EMPTY);
	}

	public SoldierPostScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties,
			ScreenHandlerContext context) {
		super(ModScreenHandlers.SOLDIER_POST, syncId);
		this.properties = properties;
		this.context = context;
		this.addSlot(new Slot(inventory, SoldierPostBlockEntity.WEAPON_SLOT, WEAPON_X, WEAPON_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return HandheldWeaponItem.isWeapon(stack);
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		for (int i = 0; i < 4; i++) {
			EquipmentSlot slot = SoldierPostBlockEntity.EQUIPMENT_SLOTS[1 + i];
			this.addSlot(new Slot(inventory, SoldierPostBlockEntity.ARMOR_SLOT_START + i, ARMOR_X, TOP_Y + i * 18) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return fitsArmorSlot(stack, slot);
				}

				@Override
				public int getMaxItemCount() {
					return 1;
				}
			});
		}
		for (int i = 0; i < SoldierPostBlockEntity.FOOD_SLOTS; i++) {
			this.addSlot(new Slot(inventory, SoldierPostBlockEntity.FOOD_SLOT_START + i, FOOD_X + (i % 3) * 18, TOP_Y + (i / 3) * 18) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return stack.contains(DataComponentTypes.FOOD);
				}
			});
		}
		this.addSlot(new Slot(inventory, SoldierPostBlockEntity.BOOK_SLOT, WEAPON_X, BOOK_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		this.addPlayerSlots(playerInventory, 8, PLAYER_INVENTORY_Y);
		this.addProperties(properties);
	}

	private static boolean fitsArmorSlot(ItemStack stack, EquipmentSlot slot) {
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	public int property(int index) {
		return this.properties.get(index);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id != BUTTON_TOGGLE && id != BUTTON_ROUTE) {
			return false;
		}
		this.context.run((world, pos) -> {
			if (!(world.getBlockEntity(pos) instanceof SoldierPostBlockEntity post)) {
				return;
			}
			if (id == BUTTON_TOGGLE) {
				post.toggleActive();
			} else if (player instanceof ServerPlayerEntity serverPlayer) {
				serverPlayer.closeHandledScreen();
				ServerPlayNetworking.send(serverPlayer, new SoldierRoutePayload(pos, post.route(), post.engageRange()));
			}
		});
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		int postSlots = SoldierPostBlockEntity.SIZE;
		if (index < postSlots) {
			if (!this.insertItem(stack, postSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!insertIntoPost(stack)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	/** Into the first post slot that takes it. */
	private boolean insertIntoPost(ItemStack stack) {
		for (int i = 0; i < SoldierPostBlockEntity.SIZE; i++) {
			if (this.slots.get(i).canInsert(stack) && this.insertItem(stack, i, i + 1, false)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(this.context, player, ModBlocks.SOLDIER_POST);
	}
}
