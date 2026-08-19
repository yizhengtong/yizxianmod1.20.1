package net.minecraft.client.yiz.xian.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * 光明末影之眼：手持时给周围末地传送门框画穿墙发光轮廓（1.20.1 移植版）。
 */
@Mod.EventBusSubscriber(modid = "yizxianmod", value = Dist.CLIENT)
public final class EndPortalGlowRenderer {

    private static final int RANGE = 128;
    private static final int SCAN_TICKS = 100;
    private static int tick = 0;
    private static final Set<BlockPos> frameSet = new HashSet<>();

    /** 主动禁用深度测试的 DepthTestStateShard（1.20.1 的 NO_DEPTH_TEST setup/clear 为空，不主动 disableDepthTest）。 */
    private static final RenderStateShard.DepthTestStateShard NO_DEPTH =
        new RenderStateShard.DepthTestStateShard("always", 519) {
            @Override
            public void setupRenderState() { RenderSystem.disableDepthTest(); }
            @Override
            public void clearRenderState() { RenderSystem.enableDepthTest(); }
        };

    private static final RenderType GLOW_LINES;
    static {
        // 1.20.1：RENDERTYPE_LINES_SHADER/NO_LAYERING 是 protected，改用公开 ShaderStateShard 构造 + 默认 layering
        var s = RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader))
            .setLineState(new RenderStateShard.LineStateShard(java.util.OptionalDouble.of(3.0)))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(NO_DEPTH)
            .createCompositeState(false);
        GLOW_LINES = RenderType.create("end_portal_glow",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            1536, false, false, s);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;
        if (!hasBrightEye(p)) return;

        tick++;
        if (tick % SCAN_TICKS == 1) {
            frameSet.clear();
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            var lvl = p.level(); var c = p.blockPosition();
            for (int dx = -RANGE; dx <= RANGE; dx++)
                for (int dz = -RANGE; dz <= RANGE; dz++)
                    for (int dy = -30; dy <= 30; dy++) {
                        mp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        if (lvl.getBlockState(mp).is(Blocks.END_PORTAL_FRAME))
                            frameSet.add(mp.immutable());
                    }
        }
        if (frameSet.isEmpty()) return;

        Set<EdgeKey> seen = new HashSet<>();
        List<int[]> draw = new ArrayList<>(); // each = {x1,y1,z1, x2,y2,z2}

        for (BlockPos pos : frameSet) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            for (Direction face : Direction.values()) {
                if (frameSet.contains(pos.relative(face))) continue; // 接触面，跳过

                for (int[] e : faceEdges(x, y, z, face)) {
                    if (!seen.add(EdgeKey.of(e))) continue;
                    if (isCoplanarSeam(e, x, y, z, face, frameSet)) continue;
                    draw.add(e);
                }
            }
        }

        Vec3 cam = evt.getCamera().getPosition();
        PoseStack pose = evt.getPoseStack();
        MultiBufferSource.BufferSource bfs = mc.renderBuffers().bufferSource();
        VertexConsumer v = bfs.getBuffer(GLOW_LINES);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var m = pose.last().pose(); // 1.20.1 VertexConsumer 用 Matrix4f
        float r = 0.2f, g = 1.0f, b = 0.8f, a = 1.0f;
        int ir = (int)(r * 255), ig = (int)(g * 255), ib = (int)(b * 255), ia = (int)(a * 255);

        for (int[] e : draw) {
            float nx = e[0] != e[3] ? Math.signum(e[3] - e[0]) : 0f;
            float ny = e[1] != e[4] ? Math.signum(e[4] - e[1]) : 0f;
            float nz = e[2] != e[5] ? Math.signum(e[5] - e[2]) : 0f;
            v.vertex(m, e[0], e[1], e[2]).color(ir, ig, ib, ia).normal(nx, ny, nz).endVertex();
            v.vertex(m, e[3], e[4], e[5]).color(ir, ig, ib, ia).normal(nx, ny, nz).endVertex();
        }

        pose.popPose();
        bfs.endBatch(GLOW_LINES);
    }

    /** 方块某个暴露面的 4 条边（整数网格坐标） */
    private static int[][] faceEdges(int x, int y, int z, Direction face) {
        return switch (face) {
            case DOWN  -> new int[][]{{x,y,z, x+1,y,z},{x+1,y,z, x+1,y,z+1},{x+1,y,z+1, x,y,z+1},{x,y,z+1, x,y,z}};
            case UP    -> new int[][]{{x,y+1,z, x+1,y+1,z},{x+1,y+1,z, x+1,y+1,z+1},{x+1,y+1,z+1, x,y+1,z+1},{x,y+1,z+1, x,y+1,z}};
            case NORTH -> new int[][]{{x,y,z, x+1,y,z},{x+1,y,z, x+1,y+1,z},{x+1,y+1,z, x,y+1,z},{x,y+1,z, x,y,z}};
            case SOUTH -> new int[][]{{x,y,z+1, x+1,y,z+1},{x+1,y,z+1, x+1,y+1,z+1},{x+1,y+1,z+1, x,y+1,z+1},{x,y+1,z+1, x,y,z+1}};
            case WEST  -> new int[][]{{x,y,z, x,y,z+1},{x,y,z+1, x,y+1,z+1},{x,y+1,z+1, x,y+1,z},{x,y+1,z, x,y,z}};
            case EAST  -> new int[][]{{x+1,y,z, x+1,y,z+1},{x+1,y,z+1, x+1,y+1,z+1},{x+1,y+1,z+1, x+1,y+1,z},{x+1,y+1,z, x+1,y,z}};
        };
    }

    /** 判定棱 e 是否是「共面内部接缝」——即 e 两侧各有一个共面相邻单元，e 落在拼接缝上。 */
    private static boolean isCoplanarSeam(int[] e, int x, int y, int z, Direction face, Set<BlockPos> frameSet) {
        int axis = face.getAxis().ordinal();
        int a1 = (axis == 0) ? 1 : 0;
        int a2 = (axis == 0) ? 2 : (axis == 1 ? 2 : 1);
        boolean eAlongA1 = (coord(e, 0, a1) != coord(e, 1, a1));
        int sideAxis = eAlongA1 ? a2 : a1;

        int eSideCoord = coord(e, 0, sideAxis);
        int blockSideCoord = getCoord(x, y, z, sideAxis);
        boolean eAtLowSide = (eSideCoord == blockSideCoord);
        int sideStep = eAtLowSide ? -1 : +1;

        int[] nb = neighborCoord(x, y, z, sideAxis, sideStep);
        BlockPos nbPos = new BlockPos(nb[0], nb[1], nb[2]);
        if (!frameSet.contains(nbPos)) return false;
        if (frameSet.contains(nbPos.relative(face))) return false;

        int[][] nbEdges = faceEdges(nb[0], nb[1], nb[2], face);
        for (int[] ne : nbEdges) {
            if (sameEdgeUnordered(ne, e)) return true;
        }
        return false;
    }

    private static int coord(int[] e, int endpoint, int axis) {
        return e[axis + endpoint * 3];
    }
    private static int getCoord(int x, int y, int z, int axis) {
        return axis == 0 ? x : (axis == 1 ? y : z);
    }
    private static int[] neighborCoord(int x, int y, int z, int axis, int step) {
        if (axis == 0) return new int[]{x + step, y, z};
        if (axis == 1) return new int[]{x, y + step, z};
        return new int[]{x, y, z + step};
    }
    private static boolean sameEdgeUnordered(int[] a, int[] b) {
        boolean f = a[0]==b[0]&&a[1]==b[1]&&a[2]==b[2]&&a[3]==b[3]&&a[4]==b[4]&&a[5]==b[5];
        boolean r = a[0]==b[3]&&a[1]==b[4]&&a[2]==b[5]&&a[3]==b[0]&&a[4]==b[1]&&a[5]==b[2];
        return f || r;
    }

    private record EdgeKey(long lo, long hi) {
        static EdgeKey of(int[] e) {
            long a = packVert(e[0], e[1], e[2]);
            long b = packVert(e[3], e[4], e[5]);
            return a <= b ? new EdgeKey(a, b) : new EdgeKey(b, a);
        }
    }
    private static long packVert(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private static boolean hasBrightEye(Player p) {
        for (ItemStack s : p.getInventory().items)
            if (s.getItem() == net.minecraft.client.yiz.xian.YizxianMod.BRIGHT_ENDER_EYE.get())
                return true;
        return false;
    }
}
