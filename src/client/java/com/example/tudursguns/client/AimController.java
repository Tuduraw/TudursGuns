package com.example.tudursguns.client;

import com.example.tudursguns.handheld.AttachmentDefinition;
import com.example.tudursguns.handheld.HandheldDefinitions;
import com.example.tudursguns.item.HandheldWeaponItem;
import com.example.tudursguns.network.AimKeyPayload;
import com.example.tudursguns.registry.ModComponents;
import com.example.tudursguns.weapon.WeaponModifiers;
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
	private static boolean lastSentAimKey;
	private static Identifier scopeId;
	private static AttachmentDefinition.Zoom scopeZoom;
	private static float magnification = 1f;

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
		if (player == null) {
			aiming = false;
			scoped = false;
			return;
		}
		ItemStack main = player.getMainHandStack();
		boolean weapon = isWeapon(main);
		boolean available = weapon && player.getVehicle() == null
				&& com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId == null;
		boolean aimKeyDown = available && client.currentScreen == null && isAimKeyDown(client);
		boolean usingWeapon = available && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND;
		aiming = aimKeyDown || usingWeapon;

		WeaponModifiers modifiers = weapon
				? WeaponModifiers.of(main, HandheldDefinitions.getAny(main.get(ModComponents.WEAPON)))
				: WeaponModifiers.NONE;
		if (modifiers.hasZoom()) {
			if (!modifiers.zoomAttachment().equals(scopeId)) {
				scopeId = modifiers.zoomAttachment();
				scopeZoom = modifiers.zoom();
				Float remembered = TudursGunsClientConfig.scopeMagnification(scopeId.toString());
				magnification = scopeZoom.clamp(remembered != null ? remembered : scopeZoom.defaultMagnification());
			}
		} else {
			scopeId = null;
			scopeZoom = null;
		}
		scoped = aiming && aimKeyDown && scopeZoom != null && client.options.getPerspective().isFirstPerson();

		float step = 1f / TudursGunsClientConfig.aimTransitionTicks();
		progress = Math.max(0f, Math.min(1f, progress + (aiming ? step : -step)));

		// Other players only need the aim KEY; aiming with use is visible to them already. Also sent
		// while holding a throwable: the server uses it to decide on an underhand throw.
		boolean holdingThrowable = player.getMainHandStack().getItem() instanceof com.example.tudursguns.item.ThrowableItem
				|| player.getOffHandStack().getItem() instanceof com.example.tudursguns.item.ThrowableItem;
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
