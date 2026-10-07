package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Client-only blood stains on the top of blocks under bleeding players (stage 2+), and splashes when a bleeding
 * player gets hit. Every client makes its own from the synced bleeding stage; stains fade over a few minutes (faster
 * on snow, which also shows them brighter) and are gone after a relog. Capped, oldest first.
 */
public final class BloodDecals {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("lastaid", "textures/misc/blood.png");
   private static final ArrayDeque<Decal> DECALS = new ArrayDeque<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static ClientLevel decalLevel;

   private record Decal(double x, double y, double z, float size, float angle, int variant, long born, long lifetime, boolean snow) {
   }

   private BloodDecals() {
   }

   private static int maxDecals() {
      return MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.bleeding.maxBloodDecals.get() : 200;
   }

   private static long lifetimeMs(boolean snow) {
      double minutes = MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.bleeding.decalFadeMinutes.get() : 5.0;
      double snowMult = MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.bleeding.decalFadeSnowMultiplier.get() : 2.0;
      return (long)(minutes * 60000.0 / (snow ? snowMult : 1.0));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != decalLevel) {
         DECALS.clear();
         decalLevel = level;
      }
      if (level == null || mc.player == null || mc.isPaused() || maxDecals() <= 0) {
         return;
      }
      long now = System.currentTimeMillis();
      DECALS.removeIf(decal -> now - decal.born > decal.lifetime);
      for (Player player : level.players()) {
         if (player.distanceToSqr(mc.player) > 64.0 * 64.0 || player.isSpectator()) {
            continue;
         }
         int stage = LimbVisualState.clientBleed(player.getId());
         if (stage <= 0) {
            continue;
         }
         // hit while bleeding: splashes around
         if (player.hurtTime == 9) {
            for (int i = 0; i < 3 + stage; i++) {
               drop(level, player.position().add((RANDOM.nextDouble() - 0.5) * 4.0, 0.0, (RANDOM.nextDouble() - 0.5) * 4.0), 0.12F + RANDOM.nextFloat() * 0.15F, now);
            }
         }
         int interval = switch (stage) {
            case 1 -> 0;
            case 2 -> 40;
            case 3 -> 7;
            default -> 3;
         };
         if (interval > 0 && (player.tickCount + player.getId()) % interval == 0) {
            float size = stage >= 4 ? 0.3F + RANDOM.nextFloat() * 0.25F : 0.15F + RANDOM.nextFloat() * 0.2F;
            drop(level, player.position().add((RANDOM.nextDouble() - 0.5) * 0.6, 0.0, (RANDOM.nextDouble() - 0.5) * 0.6), size, now);
         }
      }
   }

   /** Puts a stain on the first solid top surface at / under this position. */
   private static void drop(ClientLevel level, Vec3 at, float size, long now) {
      BlockPos pos = BlockPos.containing(at.x, at.y + 0.3, at.z);
      for (int i = 0; i < 4; i++) {
         BlockState state = level.getBlockState(pos);
         if (!state.getFluidState().isEmpty()) {
            return;
         }
         // the visible top: snow layers count (they have no collision at 1 layer), grass and flowers don't
         VoxelShape shape = state.canBeReplaced() && !state.is(Blocks.SNOW) ? net.minecraft.world.phys.shapes.Shapes.empty() : state.getShape(level, pos);
         if (!shape.isEmpty()) {
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            if (top <= at.y + 0.6) {
               boolean snow = state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW);
               DECALS.addLast(new Decal(at.x, top + 0.003 + RANDOM.nextFloat() * 0.002, at.z, size, RANDOM.nextFloat() * Mth.TWO_PI, RANDOM.nextInt(4), now, lifetimeMs(snow), snow));
               while (DECALS.size() > maxDecals()) {
                  DECALS.removeFirst();
               }
               return;
            }
         }
         pos = pos.below();
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || DECALS.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level == null) {
         return;
      }
      Vec3 cam = event.getCamera().getPosition();
      PoseStack poseStack = event.getPoseStack();
      poseStack.pushPose();
      poseStack.translate(-cam.x, -cam.y, -cam.z);
      Matrix4f matrix = poseStack.last().pose();
      PoseStack.Pose pose = poseStack.last();
      MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
      RenderType type = RenderType.entityTranslucent(TEXTURE);
      VertexConsumer consumer = buffers.getBuffer(type);
      long now = System.currentTimeMillis();
      Iterator<Decal> it = DECALS.iterator();
      while (it.hasNext()) {
         Decal decal = it.next();
         if (cam.distanceToSqr(decal.x, decal.y, decal.z) > 48.0 * 48.0) {
            continue;
         }
         float age = (float)(now - decal.born) / decal.lifetime;
         float alpha = Mth.clamp(1.0F - age * age, 0.0F, 1.0F) * (decal.snow ? 0.95F : 0.8F);
         if (alpha <= 0.01F) {
            continue;
         }
         // snow shows blood bright red, stone and dirt darker
         float r = decal.snow ? 0.72F : 0.42F;
         float g = decal.snow ? 0.04F : 0.02F;
         float b = decal.snow ? 0.05F : 0.03F;
         int light = LevelRenderer.getLightColor(level, BlockPos.containing(decal.x, decal.y + 0.1, decal.z));
         float u0 = decal.variant * 0.25F;
         float u1 = u0 + 0.25F;
         float s = decal.size;
         float cos = Mth.cos(decal.angle) * s;
         float sin = Mth.sin(decal.angle) * s;
         float x = (float)decal.x;
         float y = (float)decal.y;
         float z = (float)decal.z;
         vertex(consumer, matrix, pose, x - cos - sin, y, z - sin + cos, r, g, b, alpha, u0, 1.0F, light);
         vertex(consumer, matrix, pose, x + cos - sin, y, z + sin + cos, r, g, b, alpha, u1, 1.0F, light);
         vertex(consumer, matrix, pose, x + cos + sin, y, z + sin - cos, r, g, b, alpha, u1, 0.0F, light);
         vertex(consumer, matrix, pose, x - cos + sin, y, z - sin - cos, r, g, b, alpha, u0, 0.0F, light);
      }
      buffers.endBatch(type);
      poseStack.popPose();
   }

   private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, float x, float y, float z, float r, float g, float b, float a, float u, float v, int light) {
      consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      DECALS.clear();
      decalLevel = null;
   }
}
