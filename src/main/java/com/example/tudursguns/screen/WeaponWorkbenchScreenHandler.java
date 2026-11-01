package com.example.tudursguns.screen;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.AttachmentItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Weapon workbench: one weapon slot and up to MAX_ATTACHMENT_SLOTS attachment slots.
 *
 * The attachment slots mirror the weapon's tudursguns:attachments component: putting a weapon in
 * fills them with its fitted attachments, and putting an attachment in or taking one out rewrites
 * the component. The attachments therefore only ever exist in one place at a time - inside the
 * weapon - and closing the screen hands back just the weapon. The slot order follows the weapon
 * definition's attachment slot names, sorted. All of this runs on the server; the client just
 * shows what the server syncs. */
public class WeaponWorkbenchScreenHandler extends AbstractContainerMenu {

	public static final int MAX_ATTACHMENT_SLOTS = 4;
	public static final int WEAPON_SLOT_X = 26;
	public static final int WEAPON_SLOT_Y = 35;
	public static final int ATTACHMENT_SLOT_X = 80;
	public static final int ATTACHMENT_SLOT_Y = 17;
	public static final int ATTACHMENT_SLOT_SPACING = 18;
	private static final int WORKBENCH_SLOTS = 1 + MAX_ATTACHMENT_SLOTS;

	private final SimpleContainer inventory = new SimpleContainer(WORKBENCH_SLOTS);
	private final ContainerLevelAccess context;
	private final Player player;
	/** True while this handler itself rewrites the attachment slots, so that doesn't count as the
	 * player fitting or removing anything. */
	private boolean syncing;
	/** The weapon stack last seen in the weapon slot (by identity) - a different one means the
	 * weapon was put in, taken out or swapped. */
	private ItemStack lastWeapon = ItemStack.EMPTY;

	public WeaponWorkbenchScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, ContainerLevelAccess.NULL);
	}

	public WeaponWorkbenchScreenHandler(int syncId, Inventory playerInventory, ContainerLevelAccess context) {
		super(ModScreenHandlers.WEAPON_WORKBENCH, syncId);
		this.context = context;
		this.player = playerInventory.player;
		this.inventory.addListener(this::onWorkbenchChanged);

		this.addSlot(new Slot(this.inventory, 0, WEAPON_SLOT_X, WEAPON_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof HandheldWeaponItem && stack.has(ModComponents.WEAPON);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int i = 0; i < MAX_ATTACHMENT_SLOTS; i++) {
			final int attachmentIndex = i;
			this.addSlot(new Slot(this.inventory, 1 + i, ATTACHMENT_SLOT_X, ATTACHMENT_SLOT_Y + i * ATTACHMENT_SLOT_SPACING) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return accepts(attachmentIndex, stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}

				@Override
				public boolean isActive() {
					return attachmentIndex < slotNames().size();
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 160));
		}
	}

	/** The weapon currently in the workbench (EMPTY if none). */
	public ItemStack weapon() {
		return this.inventory.getItem(0);
	}

	private HandheldDefinition weaponDefinition() {
		return ModDefinitions.HANDHELD.getAny(weapon().get(ModComponents.WEAPON));
	}

	/** Attachment slot names of the weapon in the workbench, in slot order. */
	public List<String> slotNames() {
		HandheldDefinition def = weaponDefinition();
		return def == null ? List.of() : def.attachmentSlotNames();
	}

	private boolean accepts(int attachmentIndex, ItemStack stack) {
		HandheldDefinition def = weaponDefinition();
		Identifier attachmentId = stack.get(ModComponents.ATTACHMENT);
		if (!(stack.getItem() instanceof AttachmentItem) || def == null || attachmentId == null) {
			return false;
		}
		List<String> names = def.attachmentSlotNames();
		return attachmentIndex < names.size() && def.mountFor(names.get(attachmentIndex), attachmentId) != null;
	}

	private void onWorkbenchChanged(Container changed) {
		if (this.syncing || this.player.level().isClientSide()) {
			return;
		}
		ItemStack weapon = weapon();
		if (weapon != this.lastWeapon) {
			this.lastWeapon = weapon;
			showFittedAttachments(weapon);
		} else if (!weapon.isEmpty()) {
			writeFittedAttachments(weapon);
		}
	}

	/** Fills the attachment slots from the weapon's component (or empties them for no weapon). */
	private void showFittedAttachments(ItemStack weapon) {
		this.syncing = true;
		try {
			List<String> names = slotNames();
			Map<String, Identifier> fitted = WeaponModifiers.fitted(weapon);
			for (int i = 0; i < MAX_ATTACHMENT_SLOTS; i++) {
				Identifier attachmentId = i < names.size() ? fitted.get(names.get(i)) : null;
				this.inventory.setItem(1 + i, attachmentId == null ? ItemStack.EMPTY : ModItems.attachmentStack(attachmentId));
			}
		} finally {
			this.syncing = false;
		}
	}

	/** Rewrites the weapon's component from the attachment slots, then its derived state. */
	private void writeFittedAttachments(ItemStack weapon) {
		HandheldDefinition def = ModDefinitions.HANDHELD.getServer(weapon.get(ModComponents.WEAPON));
		if (def == null) {
			return;
		}
		List<String> names = def.attachmentSlotNames();
		Map<String, Identifier> fitted = new LinkedHashMap<>();
		for (int i = 0; i < MAX_ATTACHMENT_SLOTS && i < names.size(); i++) {
			ItemStack attachment = this.inventory.getItem(1 + i);
			Identifier attachmentId = attachment.get(ModComponents.ATTACHMENT);
			if (!attachment.isEmpty() && attachmentId != null) {
				fitted.put(names.get(i), attachmentId);
			}
		}
		if (fitted.isEmpty()) {
			weapon.remove(ModComponents.ATTACHMENTS);
		} else {
			weapon.set(ModComponents.ATTACHMENTS, fitted);
		}
		HandheldCombat.applyAttachmentEffects(weapon, def, WeaponStatsLoader.get(def.weapon()));
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.context, player, ModBlocks.WEAPON_WORKBENCH);
	}

	/** Hands back only the weapon - its attachments are inside it. */
	@Override
	public void removed(Player player) {
		super.removed(player);
		this.context.execute((world, pos) -> {
			this.syncing = true;
			try {
				for (int i = 0; i < MAX_ATTACHMENT_SLOTS; i++) {
					this.inventory.setItem(1 + i, ItemStack.EMPTY);
				}
			} finally {
				this.syncing = false;
			}
			this.clearContainer(player, this.inventory);
		});
	}


	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack result = original.copy();
		if (index < WORKBENCH_SLOTS) {
			if (!this.moveItemStackTo(original, WORKBENCH_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof HandheldWeaponItem) {
			if (!this.moveItemStackTo(original, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof AttachmentItem) {
			if (!this.moveItemStackTo(original, 1, WORKBENCH_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}
}
