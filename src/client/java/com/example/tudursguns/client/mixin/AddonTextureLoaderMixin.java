package com.example.tudursguns.client.mixin;

import com.example.tudursvehiclemod.asset.AddonPaths;
import com.example.tudursvehiclemod.client.render.AddonTextureLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Tudur's Vehicle Mod finds a texture in an addon folder (tudursvehiclemod-addons/<pack>/) only under
 * assets/<namespace>/textures/vehicle/ - addon folders aren't resource packs, so nothing else there is
 * seen at all. This mod's definitions name their textures freely (textures/handheld/..., icons,
 * scope overlays), so every PNG under assets/<namespace>/textures/ is indexed too: the vehicle mod then
 * loads it the same way, the first time something draws with it. */
@Mixin(value = AddonTextureLoader.class, remap = false)
public abstract class AddonTextureLoaderMixin {

	@Shadow
	@Final
	private static Map<Identifier, Path> INDEX;

	@Inject(method = "loadAll", at = @At("TAIL"))
	private static void tudursguns$indexAllTextures(CallbackInfo ci) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			for (Path namespaceDir : AddonPaths.listSubdirectories(addonDir.resolve("assets"))) {
				String namespace = namespaceDir.getFileName().toString();
				Path texturesDir = namespaceDir.resolve("textures");
				if (!Files.isDirectory(texturesDir)) {
					continue;
				}
				try (var files = Files.walk(texturesDir)) {
					files.filter(file -> file.toString().endsWith(".png")).forEach(file -> {
						String relative = texturesDir.relativize(file).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
						Identifier id = Identifier.tryParse(namespace, "textures/" + relative);
						if (id != null) {
							INDEX.putIfAbsent(id, file);
						}
					});
				} catch (Exception e) {
					// The vehicle mod's own scan has already reported an unreadable folder; nothing more to do.
				}
			}
		}
	}
}
