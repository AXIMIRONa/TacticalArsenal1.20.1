package com.synarsis.airtacticalarsenal.client.renderer;

import com.synarsis.airtacticalarsenal.client.model.ShahedModel;
import com.synarsis.airtacticalarsenal.entity.ShahedEntity;
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
public class ShahedRenderer extends GeoEntityRenderer<ShahedEntity> {

    // Длительность плавного "клевка носом" при переходе в штопор (в тиках).
    // Должна совпадать с прогрессией, которую сервер использует для health/urona анимации (35 тиков).
    private static final float NOSE_DIVE_RAMP_TICKS = 35.0F;
    private static final float NOSE_DIVE_MAX_ANGLE = 45.0F;
    private static final float ROLL_SPEED_DEG_PER_TICK = 15.0F;

    public ShahedRenderer(Context context) {
        super(context, new ShahedModel());
        this.shadowRadius = 1.5F;
    }

    @Override
    public void preRender(PoseStack poseStack, ShahedEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha) {

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);

        if (animatable.getIsFalling()) {
            applyCrashOrientation(poseStack, animatable, partialTick);
        } else {
            applyFlightOrientation(poseStack, animatable);
        }
    }

    /**
     * Обычный полёт: ориентируем модель строго по вектору фактического движения.
     * Не тронуто по логике — эта часть у тебя и так работала правильно.
     */
    private void applyFlightOrientation(PoseStack poseStack, ShahedEntity animatable) {
        Vec3 motion = animatable.getDeltaMovement();

        if (motion.lengthSqr() > 1.0E-4) {
            double horizontalLength = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            float yawDeg = (float) Math.toDegrees(Math.atan2(motion.z, motion.x)) - 90.0F;
            float pitchDeg = (float) Math.toDegrees(Math.atan2(motion.y, horizontalLength));

            poseStack.mulPose(Axis.YP.rotationDegrees(-yawDeg));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(-animatable.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(animatable.getXRot()));
        }
    }

    /**
     * Штопор при уничтожении. Три источника ориентации сведены в ОДНУ непротиворечивую систему:
     *  1) yaw   — из вектора спирального движения (тот же знак, что и в полёте);
     *  2) pitch — плавно нарастающий "клевок носом" (0° -> 45°), а не мгновенный скачок;
     *  3) roll  — штопор вокруг носовой оси, знак синхронизирован со знаком yaw.
     */
    private void applyCrashOrientation(PoseStack poseStack, ShahedEntity animatable, float partialTick) {
        // Время падения отсчитываем от начала штопора (destroyAnimTicks), а НЕ от animatable.tickCount.
        // tickCount — это возраст сущности с момента спавна, он может быть равен, скажем, 400+.
        // Если считать rollAngle от tickCount, то в момент перехода в IS_DESTROYED крен
        // мгновенно "телепортируется" в произвольный угол вместо 0 — это и есть дерготня в момент сбития.
        float fallTime = animatable.getDestroyAnimTicks() + partialTick;

        // 1) YAW: берём из реального вектора спирального смещения (deltaMovement), а не из
        // отдельно вычисляемого entity.getYRot(). Это гарантирует, что модель "смотрит"
        // ровно туда, куда её реально несёт по спирали, без рассинхрона с физикой.
        Vec3 motion = animatable.getDeltaMovement();
        float yawDeg = motion.lengthSqr() > 1.0E-4
                ? (float) Math.toDegrees(Math.atan2(motion.z, motion.x)) - 90.0F
                : animatable.getYRot();

        poseStack.mulPose(Axis.YP.rotationDegrees(-yawDeg));

        // 2) PITCH: клевок носом вниз плавно нарастает за NOSE_DIVE_RAMP_TICKS тиков,
        // а не применяется сразу на полные 45° — это убирает визуальный "рывок"
        // в момент, когда getIsFalling() становится true.
        float noseDiveProgress = Math.min(1.0F, fallTime / NOSE_DIVE_RAMP_TICKS);
        poseStack.mulPose(Axis.XP.rotationDegrees(NOSE_DIVE_MAX_ANGLE * noseDiveProgress));

        // 3) ROLL: штопор вокруг носовой (локальной Z) оси после применения yaw и pitch.
        // Знак ОБЯЗАН совпадать со знаком, который мы использовали для yaw (там мы взяли -yawDeg).
        // Раньше roll всегда крутился в "сыром" положительном направлении (time * 15),
        // не учитывающем это соглашение о знаках — из-за этого штопор визуально закручивался
        // в сторону, противоположную развороту дрона. Исправлено на -fallTime, чтобы крен
        // был согласован с направлением фактического разворота.
        float rollAngle = (-fallTime * ROLL_SPEED_DEG_PER_TICK) % 360.0F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollAngle));
    }
}

