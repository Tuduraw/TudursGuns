package com.example.tudursguns.client;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.EquipmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinition;
import com.example.tudursguns.handheld.ModDefinitions;
import com.example.tudursguns.item.EquipmentItem;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.item.ThrowableItem;
import com.example.tudursguns.network.AimKeyPayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponModifiers;
import com.example.tudursvehiclemod.client.TvMissileControlState;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The local player's aiming state, updated every client tick.
 *
 * Aiming = holding a weapon in the main hand, not riding anything, and either holding the aim key or
 * holding use (right click, which also fires). Looking through a scope additionally needs the aim
 * key itself, a scope fitted, and first-person view - right click alone raises the weapon without
 * looking through the scope. */
public final class AimController {

	private AimController() {
	}

	private static KeyBinding aimKey;

	private static boolean aiming;
	private static boolean scoped;
	private static float progress;
	private static float previousProgress;
	private static float sprintProgress;
	private static boolean wasUsingWeapon;
	/** Ticks the weapon stays up after a quick click from the hip, so its shot (which the server fires
	 * once the weapon is fully up) isn't fired while it's coming back down. */
	private static int holdRaisedTicks;
	private static float previousSprintProgress;
	private static boolean lastSentAimKey;
	private static Identifier scopeId;
	private static AttachmentDefinition.Zoom scopeZoom;
	private static float magnification = 1f;
	private static boolean binoculars;
	private static float ironSightZoom = 1f;
	private static boolean rangefinder;

	/** Entity ids of OTHER players currently holding their aim key (from PlayerAimPayload). */
	private static final Set<Integer> REMOTE_AIM_KEY = ConcurrentHashMap.newKeySet();

	public static void setAimKey(KeyBinding key) {
		aimKey = key;
	}

	public static void setRemoteAimKey(int entityId, boolean held) {
		if (held) {
			REMOTE_AIM_KEY.add(entityId);
		} else {
			REMOTE_AIM_KEY.remove(entityId);
		}
	}

	public static void reset() {
		REMOTE_AIM_KEY.clear();
		aiming = false;
		scoped = false;
		progress = 0f;
		previousProgress = 0f;
		sprintProgress = 0f;
		previousSprintProgress = 0f;
		wasUsingWeapon = false;
		holdRaisedTicks = 0;
		lastSentAimKey = false;
	}

	public static boolean isAiming() {
		return aiming;
	}

	public static boolean isScoped() {
		return scoped;
	}

	public static float magnification() {
		return magnification;
	}

	/** Magnification while aimed down the iron sights, eased in with the raise (1 = none; never while
	 * looking through a scope, which has its own). */
	public static float ironSightMagnification(float tickProgress) {
		if (scoped || ironSightZoom <= 1f) {
			return 1f;
		}
		float t = progress(tickProgress);
		return 1f + (ironSightZoom - 1f) * t * t * (3f - 2f * t);
	}

	/** Looking through binoculars (rather than a weapon's scope). */
	public static boolean isBinoculars() {
		return scoped && binoculars;
	}

	/** The current view has a rangefinder readout. */
	public static boolean hasRangefinder() {
		return scoped && rangefinder;
	}

	/** 0 = not sprinting, 1 = fully in the sprint carry pose; interpolated for the frame. */
	public static float sprintProgress(float tickProgress) {
		return previousSprintProgress + (sprintProgress - previousSprintProgress) * tickProgress;
	}

	/** Whether this player carries their weapon in the sprint pose (sprinting with a weapon in the main
	 * hand, not aiming). Other players' sprinting is synced by vanilla. */
	public static boolean isSprintCarrying(PlayerEntity player) {
		return player.isSprinting() && player.getVehicle() == null && isWeapon(player.getMainHandStack()) && !isAiming(player);
	}

	/** 0 = lowered at the hip, 1 = fully raised; interpolated for the frame. */
	public static float progress(float tickProgress) {
		return previousProgress + (progress - previousProgress) * tickProgress;
	}

	/** Whether this player should be drawn in the raised pose - the local player from this tick's
	 * state, anyone else from their synced aim key or vanilla "using item" state. */
	public static boolean isAiming(PlayerEntity player) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (player == client.player) {
			return aiming;
		}
		if (player.getVehicle() != null || !isWeapon(player.getMainHandStack())) {
			return false;
		}
		return REMOTE_AIM_KEY.contains(player.getId())
				|| (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND);
	}

	private static boolean isWeapon(ItemStack stack) {
		return stack.getItem() instanceof HandheldWeaponItem && stack.contains(ModComponents.WEAPON);
	}

	/** Read straight from the keyboard/mouse rather than KeyBinding.isPressed(): Left Alt is also
	 * bound by Tudur's Vehicle Mod (free look while riding), and a key only reaches one binding. */
	private static boolean isAimKeyDown(MinecraftClient client) {
		if (aimKey == null || aimKey.isUnbound()) {
			return false;
		}
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(aimKey);
		long window = client.getWindow().getHandle();
		if (key.getCategory() == InputUtil.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
		}
		return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
	}

	/** The aim key's physical state regardless of what's held (throwables use it for an underhand
	 * throw). Always false while a screen is open. */
	public static boolean isAimKeyHeldRaw(MinecraftClient client) {
		return client.currentScreen == null && isAimKeyDown(client);
	}

	public static void tick(MinecraftClient client) {
		PlayerEntity player = client.player;
		previousProgress = progress;
		previousSprintProgress = sprintProgress;
		if (player == null) {
			aiming = false;
			scoped = false;
			return;
		}
		ItemStack main = player.getMainHandStack();
		boolean weapon = isWeapon(main);
		boolean available = weapon && player.getVehicle() == null
				&& TvMissileControlState.controlledEntityId == null;
		boolean aimKeyDown = available && client.currentScreen == null && isAimKeyDown(client);
		boolean usingWeapon = available && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND;
		HandheldDefinition heldDef = weapon ? ModDefinitions.HANDHELD.getAny(main.get(ModComponents.WEAPON)) : null;
		int raiseTicks = heldDef != null && heldDef.aim().isPresent() ? heldDef.raiseTicks() : TudursGunsClientConfig.aimTransitionTicks();
		if (usingWeapon && !wasUsingWeapon && progress < 1f) {
			holdRaisedTicks = (int) Math.ceil((1f - progress) * raiseTicks) + 2;
		} else if (holdRaisedTicks > 0) {
			holdRaisedTicks--;
		}
		wasUsingWeapon = usingWeapon;
		if (!available) {
			holdRaisedTicks = 0;
		}
		aiming = aimKeyDown || usingWeapon || holdRaisedTicks > 0;
		ironSightZoom = heldDef != null && heldDef.aim().isPresent() ? heldDef.aim().get().zoom() : 1f;
		// Raising the weapon ends a sprint (ClientPlayerEntityMixin also stops a new one starting while
		// aiming); the sprint state is sent to the server by vanilla as usual.
		if (aiming && player.isSprinting()) {
			player.setSprinting(false);
		}

		WeaponModifiers modifiers = weapon
				? WeaponModifiers.of(main, ModDefinitions.HANDHELD.getAny(main.get(ModComponents.WEAPON)))
				: WeaponModifiers.NONE;
		EquipmentDefinition gear = EquipmentItem.definition(main);
		boolean holdingBinoculars = gear != null && gear.type() == EquipmentDefinition.Type.BINOCULARS && gear.zoom().isPresent()
				&& player.getVehicle() == null;
		Identifier zoomId = null;
		AttachmentDefinition.Zoom zoom = null;
		HandheldDefinition zoomDef = weapon ? ModDefinitions.HANDHELD.getAny(main.get(ModComponents.WEAPON)) : null;
		if (modifiers.hasZoom()) {
			zoomId = modifiers.zoomAttachment();
			zoom = modifiers.zoom();
		} else if (zoomDef != null && zoomDef.aim().flatMap(HandheldDefinition.AimSettings::scope).isPresent()) {
			// The weapon's own built-in scope.
			zoomId = main.get(ModComponents.WEAPON);
			zoom = zoomDef.aim().get().scope().get();
		} else if (holdingBinoculars) {
			zoomId = main.get(ModComponents.EQUIPMENT);
			zoom = gear.zoom().get();
		}
		if (zoom != null) {
			if (!zoomId.equals(scopeId)) {
				scopeId = zoomId;
				scopeZoom = zoom;
				Float remembered = TudursGunsClientConfig.scopeMagnification(scopeId.toString());
				magnification = scopeZoom.clamp(remembered != null ? remembered : scopeZoom.defaultMagnification());
			}
		} else {
			scopeId = null;
			scopeZoom = null;
		}
		binoculars = holdingBinoculars && !weapon;
		rangefinder = binoculars && gear.rangefinder();
		boolean firstPerson = client.options.getPerspective().isFirstPerson();
		if (binoculars) {
			// Held up with use, or the aim key.
			boolean lookingThrough = (player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND) || isAimKeyHeldRaw(client);
			scoped = lookingThrough && scopeZoom != null && firstPerson;
		} else {
			scoped = aiming && aimKeyDown && scopeZoom != null && firstPerson;
		}

		float step = raiseTicks <= 0 ? 1f : 1f / raiseTicks;
		progress = Math.max(0f, Math.min(1f, progress + (aiming ? step : -step)));
		boolean sprintCarry = available && !aiming && player.isSprinting();
		sprintProgress = Math.max(0f, Math.min(1f, sprintProgress + (sprintCarry ? step : -step)));

		// Other players only need the aim KEY; aiming with use is visible to them already. Also sent
		// while holding a throwable: the server uses it to decide on an underhand throw.
		boolean holdingThrowable = player.getMainHandStack().getItem() instanceof ThrowableItem
				|| player.getOffHandStack().getItem() instanceof ThrowableItem;
		boolean sendAimKey = aimKeyDown || (holdingThrowable && player.getVehicle() == null && isAimKeyHeldRaw(client));
		if (sendAimKey != lastSentAimKey && client.getNetworkHandler() != null) {
			lastSentAimKey = sendAimKey;
			ClientPlayNetworking.send(new AimKeyPayload(sendAimKey));
		}
	}

	/** Mouse wheel while looking through a scope: one notch = one step of magnification. */
	public static void scroll(double amount) {
		if (scopeZoom == null || amount == 0) {
			return;
		}
		magnification = scopeZoom.clamp(magnification + (float) Math.signum(amount) * scopeZoom.step());
		TudursGunsClientConfig.setScopeMagnification(scopeId.toString(), magnification);
	}

	/** Overlay texture for the current scope. */
	public static Identifier scopeOverlay() {
		return scopeZoom == null ? AttachmentDefinition.Zoom.DEFAULT_OVERLAY
				: scopeZoom.overlay().orElse(AttachmentDefinition.Zoom.DEFAULT_OVERLAY);
	}
}
