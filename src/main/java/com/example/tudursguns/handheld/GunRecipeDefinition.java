package com.example.tudursguns.handheld;

import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.registry.ModItemGroups;
import com.example.tudursguns.registry.ModItems;
import com.example.tudursguns.screen.WeaponWorkbenchScreenHandler;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A recipe of the gun crafting table, read from data/<namespace>/gun_recipe/<name>.json - in a data
 * pack, or in an addon pack under tudursvehiclemod-addons/ (so a weapon pack brings its own recipes
 * without a data pack in every world).
 *
 * result: what's made; ingredients: what's taken from the player's inventory. Both are ItemRefs -
 * a vanilla/modded item ("item") or tag ("tag", ingredients only), or one of this mod's data-driven
 * things by id ("weapon", "attachment", "ammo", "throwable", "mine", "armor", "equipment") - with a
 * count. category: a heading to group recipes under in the table (optional). */
public record GunRecipeDefinition(ItemRef result, List<ItemRef> ingredients, Optional<String> category) {

	public static final Codec<GunRecipeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemRef.CODEC.fieldOf("result").forGetter(GunRecipeDefinition::result),
			ItemRef.CODEC.listOf().fieldOf("ingredients").forGetter(GunRecipeDefinition::ingredients),
			Codec.STRING.optionalFieldOf("category").forGetter(GunRecipeDefinition::category)
	).apply(instance, GunRecipeDefinition::new));

	/** One kind of item (exactly one of the id fields set) and how many. */
	public record ItemRef(Optional<Identifier> item, Optional<Identifier> tag, Optional<Identifier> weapon,
			Optional<Identifier> attachment, Optional<Identifier> ammo, Optional<Identifier> throwable, Optional<Identifier> mine,
			Optional<Identifier> armor, Optional<Identifier> equipment, int count) {

		public static final Codec<ItemRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.optionalFieldOf("item").forGetter(ItemRef::item),
				Identifier.CODEC.optionalFieldOf("tag").forGetter(ItemRef::tag),
				Identifier.CODEC.optionalFieldOf("weapon").forGetter(ItemRef::weapon),
				Identifier.CODEC.optionalFieldOf("attachment").forGetter(ItemRef::attachment),
				Identifier.CODEC.optionalFieldOf("ammo").forGetter(ItemRef::ammo),
				Identifier.CODEC.optionalFieldOf("throwable").forGetter(ItemRef::throwable),
				Identifier.CODEC.optionalFieldOf("mine").forGetter(ItemRef::mine),
				Identifier.CODEC.optionalFieldOf("armor").forGetter(ItemRef::armor),
				Identifier.CODEC.optionalFieldOf("equipment").forGetter(ItemRef::equipment),
				Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(ItemRef::count)
		).apply(instance, ItemRef::new));

		public boolean matches(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			if (this.item.isPresent()) {
				return stack.isOf(Registries.ITEM.get(this.item.get()));
			}
			if (this.tag.isPresent()) {
				return stack.isIn(TagKey.of(RegistryKeys.ITEM, this.tag.get()));
			}
			if (this.weapon.isPresent()) {
				return stack.isOf(ModItems.HANDHELD_WEAPON) && this.weapon.get().equals(stack.get(ModComponents.WEAPON));
			}
			if (this.attachment.isPresent()) {
				return stack.isOf(ModItems.ATTACHMENT) && this.attachment.get().equals(stack.get(ModComponents.ATTACHMENT));
			}
			if (this.ammo.isPresent()) {
				return stack.isOf(ModItems.AMMO) && this.ammo.get().equals(stack.get(ModComponents.AMMO_TYPE));
			}
			if (this.throwable.isPresent()) {
				return stack.isOf(ModItems.THROWABLE) && this.throwable.get().equals(stack.get(ModComponents.THROWABLE));
			}
			if (this.mine.isPresent()) {
				return stack.isOf(ModItems.MINE) && this.mine.get().equals(stack.get(ModComponents.MINE));
			}
			if (this.armor.isPresent()) {
				return stack.isOf(ModItems.ARMOR) && this.armor.get().equals(stack.get(ModComponents.ARMOR));
			}
			if (this.equipment.isPresent()) {
				return stack.isOf(ModItems.EQUIPMENT) && this.equipment.get().equals(stack.get(ModComponents.EQUIPMENT));
			}
			return false;
		}

		/** Stacks this stands for, for display (a tag: each of its items, cycled through by the screen). */
		public List<ItemStack> displayStacks() {
			List<ItemStack> stacks = new ArrayList<>();
			if (this.tag.isPresent()) {
				for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, this.tag.get()))) {
					stacks.add(new ItemStack(entry, this.count));
				}
				return stacks;
			}
			ItemStack stack = createStack();
			if (!stack.isEmpty()) {
				stacks.add(stack);
			}
			return stacks;
		}

		/** The stack this makes (a result) - empty for a tag. A weapon comes with a full magazine when
		 * its weapon file is known on this side. */
		public ItemStack createStack() {
			ItemStack stack = ItemStack.EMPTY;
			if (this.item.isPresent()) {
				stack = new ItemStack(Registries.ITEM.get(this.item.get()));
			} else if (this.weapon.isPresent()) {
				HandheldDefinitions.ClientEntry client = HandheldDefinitions.getClient(this.weapon.get());
				HandheldDefinition server = HandheldDefinitions.getServer(this.weapon.get());
				int rounds = server != null ? com.example.tudursvehiclemod.asset.WeaponStatsLoader.get(server.weapon()).magazineSize()
						: client != null ? client.weapon().magazineSize() : 0;
				stack = ModItemGroups.createStack(this.weapon.get(), rounds);
			} else if (this.attachment.isPresent()) {
				stack = WeaponWorkbenchScreenHandler.createAttachmentStack(this.attachment.get());
			} else if (this.ammo.isPresent()) {
				stack = new ItemStack(ModItems.AMMO);
				stack.set(ModComponents.AMMO_TYPE, this.ammo.get());
			} else if (this.throwable.isPresent()) {
				stack = new ItemStack(ModItems.THROWABLE);
				stack.set(ModComponents.THROWABLE, this.throwable.get());
			} else if (this.mine.isPresent()) {
				stack = new ItemStack(ModItems.MINE);
				stack.set(ModComponents.MINE, this.mine.get());
			} else if (this.armor.isPresent()) {
				stack = ModItemGroups.createArmorStack(this.armor.get());
			} else if (this.equipment.isPresent()) {
				stack = ModItemGroups.createEquipmentStack(this.equipment.get());
			}
			if (!stack.isEmpty()) {
				stack.setCount(Math.min(this.count, stack.getMaxCount()));
			}
			return stack;
		}
	}
}
