package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.xian.entity.XieyulongEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 邪狱龙飞行移动控制。
 *
 * <p>飞行时朝目标点三维移动（绕 Y/X 轴转向 + 前进速度），无目标待命时悬停在
 * 地面以上 {@value #FLYING_HEIGHT} 格；非飞行状态退回原版地面移动控制。</p>
 */
public class XieyulongFlightMoveControl extends MoveControl {

    private static final float FLYING_HEIGHT = 8.0f;
    private static final float MAX_VERT_SPEED = 0.15f;

    private final XieyulongEntity dragon;

    public XieyulongFlightMoveControl(XieyulongEntity dragon) {
        super(dragon);
        this.dragon = dragon;
    }

    @Override
    public void tick() {
        if (!this.dragon.isFlying()) {
            super.tick();
            return;
        }
        if (this.operation == Operation.MOVE_TO) {
            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.0 && hDist < 0.8) {
                this.operation = Operation.WAIT;
                return;
            }
            float targetYRot = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYRot, 15.0f));
            if (hDist > 0.3) {
                float targetXRot = (float) (-(Mth.atan2(dy, hDist) * (180.0 / Math.PI)));
                this.mob.setXRot(this.rotlerp(this.mob.getXRot(), targetXRot, 12.0f));
            }
            float speed = (float) (this.speedModifier * this.dragon.getFlyingSpeed());
            float yawRad = this.mob.getYRot() * (float) Math.PI / 180.0f;
            double vx = -Math.sin(yawRad) * (double) speed;
            double vz = Math.cos(yawRad) * (double) speed;
            double vy = Mth.clamp(dy * 0.08, -MAX_VERT_SPEED, MAX_VERT_SPEED);
            this.mob.setDeltaMovement(vx, vy, vz);
        } else if (this.operation == Operation.WAIT) {
            // 悬停：垂直校正到地面以上 FLYING_HEIGHT 格
            double groundY = this.getGroundY(this.mob.getX(), this.mob.getZ());
            double vertDiff = (groundY + FLYING_HEIGHT) - this.mob.getY();
            Vec3 vel = this.mob.getDeltaMovement();
            double vertCorrection = Mth.clamp(vertDiff * 0.08, -0.4, 0.4);
            if (this.mob.onGround()) {
                vertCorrection = Math.max(vertCorrection, 0.2);
            }
            this.mob.setDeltaMovement(vel.x * 0.96, vel.y * 0.94 + vertCorrection, vel.z * 0.96);
            if (Math.abs(this.mob.getDeltaMovement().x) < 0.005) {
                this.mob.setDeltaMovement(0.0, this.mob.getDeltaMovement().y, this.mob.getDeltaMovement().z);
            }
            if (Math.abs(this.mob.getDeltaMovement().z) < 0.005) {
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().x, this.mob.getDeltaMovement().y, 0.0);
            }
        }
    }

    private double getGroundY(double x, double z) {
        return this.mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }
}
