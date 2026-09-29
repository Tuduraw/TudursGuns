package com.example.tudursguns.client.render;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.EquipmentModelData;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/** Soldiers (posted and enemy) - the player model, with a skin of their own, vanilla armor and the
 * held weapon (OBJ armor is added for every humanoid renderer, see TudursGunsClient). */
public class SoldierRenderer<T extends MobEntity> extends BipedEntityRenderer<T, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {

	private final Identifier texture;

	public SoldierRenderer(EntityRendererFactory.Context context, Identifier texture) {
		super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
		this.texture = texture;
		this.addFeature(new ArmorFeatureRenderer<>(this,
				EquipmentModelData.mapToEntityModel(EntityModelLayers.PLAYER_EQUIPMENT, context.getEntityModels(), BipedEntityModel::new),
				context.getEquipmentRenderer()));
	}

	@Override
	public BipedEntityRenderState createRenderState() {
		return new BipedEntityRenderState();
	}

	@Override
	public Identifier getTexture(BipedEntityRenderState state) {
		return this.texture;
	}
}
