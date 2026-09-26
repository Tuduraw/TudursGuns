package com.example.tudursguns.screen;

import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.item.AttachmentItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.registry.ModBlocks;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.registry.ModScreenHandlers;
import com.example.tudursguns.weapon.HandheldCombat;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weapon workbench: one weapon slot and up to MAX_ATTACHMENT_SLOTS attachment slots.
 *
 * The attachment slots mirror the weapon's tudursguns:attachments component: putting a weapon in
 * fills them with its fitted attachments, and putting an attachment in or taking one out rewrites
 * the component. The attachments therefore only ever exist in one place at a time - inside the
 * weapon - and closing the screen hands back just the weapon. The slot order follows the weapon
 * definition's attachment slot names, sorted. All of this runs on the server; the client just
 * shows what the server syncs. */
public class WeaponWorkbenchScreenHandler extends ScreenHandler {

	public static final int MAX_ATTACHMENT_SLOTS = 4;
	public static final int WEAPON_SLOT_X = 26;
	public static final int WEAPON_SLOT_Y = 35;
	public static final int ATTACHMENT_SLOT_X = 80;
	public static final int ATTACHMENT_SLOT_Y = 17;
	public static final int ATTACHMENT_SLOT_SPACING = 18;

	private final SimpleInventory inventory = new SimpleInventory(1 + MAX_ATTACHMENT_SLOTS);
	private final ScreenHandlerContext context;
	private final PlayerEntity player;
	/** True while this handler itself rewrites the attachment slots, so that doesn't count as the
	 * player fitting or removing anything. */
	private boolean syncing;
	/** The weapon stack last seen in the weapon slot (by identity) - a different one means the
	 * weapon was put in, taken out or swapped. */
	private ItemStack lastWeapon = ItemStack.EMPTY;

	public WeaponWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
	}

	public WeaponWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
		super(ModScreenHandlers.WEAPON_WORKBENCH, syncId);
		this.context = context;
		this.player = playerInventory.player;
		this.inventory.addListener(this::onWorkbenchChanged);

		this.addSlot(new Slot(this.inventory, 0, WEAPON_SLOT_X, WEAPON_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.getItem() instanceof HandheldWeaponItem && stack.contains(ModComponents.WEAPON);
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		for (int i = 0; i < MAX_ATTACHMENT_SLOTS; i++) {
			final int attachmentIndex = i;
			this.addSlot(new Slot(this.inventory, 1 + i, ATTACHMENT_SLOT_X, ATTACHMENT_SLOT_Y + i * ATTACHMENT_SLOT_SPACING) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return accepts(attachmentIndex, stack);
				}

				@Override
				public int getMaxItemCount() {
					return 1;
				}

				@Override
				public boolean isEnabled() {
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
		return this.inventory.getStack(0);
	}

	/** Attachment slot names of the weapon in the workbench, in slot order. */
	public List<String> slotNames() {
		HandheldDefinition def = HandheldDefinitions.getAny(weapon().get(ModComponents.WEAPON));
		return def == null ? List.of() : def.attachmentSlotNames();
	}

	private boolean accepts(int attachmentIndex, ItemStack stack) {
		if (!(stack.getItem() instanceof AttachmentItem)) {
			return false;
		}
		HandheldDefinition def = HandheldDefinitions.getAny(weapon().get(ModComponents.WEAPON));
		if (def == null) {
			return false;
		}
		List<String> names = def.attachmentSlotNames();
		Identifier attachmentId = stack.get(ModComponents.ATTACHMENT);
		return attachmentIndex < names.size() && attachmentId != null && def.mountFor(names.get(attachmentIndex), attachmentId) != null;
	}

	private void onWorkbenchChanged(Inventory changed) {
		if (this.syncing || this.player.getEntityWorld().isClient()) {
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
				this.inventory.setStack(1 + i, attachmentId == null ? ItemStack.EMPTY : createAttachmentStack(attachmentId));
			}
		} finally {
			this.syncing = false;
		}
	}

	/** Rewrites the weapon's component from the attachment slots, then its derived state. */
	private void writeFittedAttachments(ItemStack weapon) {
		HandheldDefinition def = HandheldDefinitions.getServer(weapon.get(ModComponents.WEAPON));
		if (def == null) {
			return;
		}
		List<String> names = def.attachmentSlotNames();
		Map<String, Identifier> fitted = new LinkedHashMap<>();
		for (int i = 0; i < MAX_ATTACHMENT_SLOTS && i < names.size(); i++) {
			ItemStack attachment = this.inventory.getStack(1 + i);
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

	public static ItemStack createAttachmentStack(Identifier attachmentId) {
		ItemStack stack = new ItemStack(ModItems.ATTACHMENT);
		stack.set(ModComponents.ATTACHMENT, attachmentId);
		return stack;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(this.context, player, ModBlocks.WEAPON_WORKBENCH);
	}

	/** Hands back only the weapon - its attachments are inside it. */
	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.context.run((world, pos) -> {
			this.syncing = true;
			try {
				for (int i = 0; i < MAX_ATTACHMENT_SLOTS; i++) {
					this.inventory.setStack(1 + i, ItemStack.EMPTY);
				}
			} finally {
				this.syncing = false;
			}
			this.dropInventory(player, this.inventory);
		});
	}

	private static final int WORKBENCH_SLOTS = 1 + MAX_ATTACHMENT_SLOTS;

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getStack();
		ItemStack result = original.copy();
		if (index < WORKBENCH_SLOTS) {
			if (!this.insertItem(original, WORKBENCH_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof HandheldWeaponItem) {
			if (!this.insertItem(original, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof AttachmentItem) {
			if (!this.insertItem(original, 1, WORKBENCH_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return result;
	}
}
