package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.OrlanModel;
import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class OrlanRenderer extends GeoEntityRenderer<OrlanEntity> {

    public OrlanRenderer(Context context) {
        super(context, new OrlanModel());
        this.shadowRadius = 1.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, OrlanEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        Vec3 motion = animatable.getDeltaMovement();

        if (motion.lengthSqr() > 1.0E-4) {
            double motionX = motion.x;
            double motionY = motion.y;
            double motionZ = motion.z;
            double horizontalLength = Math.sqrt(motionX * motionX + motionZ * motionZ);

            double yawRad = Math.atan2(motionX, motionZ);
            float yawDeg = (float) Math.toDegrees(yawRad) + 180.0F;

            double pitchRad = Math.atan2(-motionY, horizontalLength);
            float pitchDeg = (float) Math.toDegrees(pitchRad);

            poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDeg));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(-animatable.getYRot() + 90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-animatable.getXRot()));
        }

        // Логика падения: крен + правильный штопор
        if (animatable.getIsFalling()) {
            float roll = animatable.getRenderRoll(partialTick);
            // 1. Применяем крен вбок
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

            // 2. Добавляем вращение волчком вокруг оси движения (штопор)
            float spin = (animatable.tickCount + partialTick) * 32.0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        }
    }
}