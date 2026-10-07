package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

public final class PlayerMedicalContext implements MedicalContext {
   private static final int SLOW_SCAN_INTERVAL = 5;
   AbstractPlayerDamageModel damageModel;
   float bodyC = 36.6F;
   double worldC = Double.NaN;
   double worldTrendC;
   boolean hasTempMod;
   boolean grace;
   boolean wet;
   double insulationFactor = 1.0;
   double feelsLikeC = Double.NaN;
   boolean warmProtection;
   double lastBodyUnits;
   boolean hasLastBody;
   long gameTime;
   int secondsSinceDamage;
   int secondsSinceFight;
   MedicalContext.Activity activity = MedicalContext.Activity.STILL;
   int light;
   boolean hostiles;
   boolean noisy;
   boolean heat;
   boolean jukebox;
   boolean snowstorm;
   private ServerPlayer player;
   private int slowScanCountdown;
   double lastX;
   double lastZ;
   boolean hasLastPos;
   int sprintTicks;
   /** Last limb state sent to clients (see Amputation.tick). */
   byte[] lastLimbState;
   /** Seconds spent in the deadly cold state (see ColdDeath). */
   int coldDeathSeconds;
   int coldDeathDamageTimer;

   void refresh(ServerPlayer serverPlayer, AbstractPlayerDamageModel abstractPlayerDamageModel, MedicalData medicalData) {
      this.player = serverPlayer;
      this.damageModel = abstractPlayerDamageModel;
      ServerLevel serverLevel = serverPlayer.serverLevel();
      this.gameTime = serverLevel.getGameTime();
      this.secondsSinceDamage = (int)Math.min(2147483647L, (this.gameTime - medicalData.lastDamageTick) / 20L);
      this.secondsSinceFight = (int)Math.min(2147483647L, (this.gameTime - medicalData.lastFightTick) / 20L);
      TemperatureCompat temperatureCompat = TemperatureCompat.get();
      this.hasTempMod = temperatureCompat.active();
      this.bodyC = temperatureCompat.bodyCelsius(serverPlayer);
      this.warmProtection = serverPlayer.hasEffect(MedicalRegistry.WARM_PROTECTION);
      if (this.hasTempMod) {
         double d = temperatureCompat.worldCelsius(serverPlayer);
         if (!Double.isNaN(this.worldC)) {
            this.worldTrendC = this.worldTrendC * 0.8 + (d - this.worldC) * 0.2;
         }

         this.worldC = d;
         this.grace = (Boolean)MedicalConfig.SERVER.freezeDuringGrace.get() && temperatureCompat.hasGrace(serverPlayer);
         this.wet = temperatureCompat.isWet(serverPlayer);
         this.insulationFactor = temperatureCompat.coldInsulationRateFactor(serverPlayer);
         double e = temperatureCompat.neutralWorldCelsius(serverPlayer);
         this.feelsLikeC = Double.isNaN(this.worldC) ? Double.NaN : (this.worldC < e ? e + (this.worldC - e) * this.insulationFactor : this.worldC);
      } else {
         this.worldC = Double.NaN;
         this.worldTrendC = 0.0;
         this.grace = false;
         this.wet = false;
         this.insulationFactor = 1.0;
         this.feelsLikeC = Double.NaN;
      }

      double g = this.hasLastPos ? serverPlayer.getX() - this.lastX : 0.0;
      double h = this.hasLastPos ? serverPlayer.getZ() - this.lastZ : 0.0;
      double f = Math.sqrt(g * g + h * h);
      this.lastX = serverPlayer.getX();
      this.lastZ = serverPlayer.getZ();
      this.hasLastPos = true;
      boolean bl = this.gameTime - medicalData.lastJumpTick < 20L;
      boolean bl1 = this.gameTime - medicalData.lastFightTick < 60L;
      if (serverPlayer.isSleeping()) {
         this.activity = MedicalContext.Activity.RESTING_IN_BED;
      } else if (this.sprintTicks > 5 || bl || bl1) {
         this.activity = MedicalContext.Activity.STRAINING;
      } else if (f < 0.15 && !serverPlayer.isPassenger()) {
         this.activity = MedicalContext.Activity.STILL;
      } else if (serverPlayer.isPassenger()) {
         this.activity = MedicalContext.Activity.STILL;
      } else {
         this.activity = MedicalContext.Activity.WALKING;
      }

      this.sprintTicks = 0;
      BlockPos blockPos = BlockPos.containing(serverPlayer.getEyePosition());
      this.light = serverLevel.getMaxLocalRawBrightness(blockPos);
      this.hostiles = !serverLevel.getEntitiesOfClass(Mob.class, serverPlayer.getBoundingBox().inflate(16.0), mob -> mob instanceof Enemy && mob.isAlive())
         .isEmpty();
      this.snowstorm = computeSnowstorm(serverLevel, blockPos);
      if (--this.slowScanCountdown <= 0) {
         this.slowScanCountdown = 5;
         this.heat = scanHeat(serverLevel, serverPlayer.blockPosition());
         this.jukebox = scanJukebox(serverLevel, serverPlayer.blockPosition());
      }

      this.noisy = serverLevel.isThundering() && serverLevel.canSeeSky(blockPos) || this.jukebox || this.gameTime - medicalData.lastNoiseTick < 200L;
   }

   private static boolean computeSnowstorm(ServerLevel serverLevel, BlockPos blockPos) {
      if (!serverLevel.canSeeSky(blockPos)) {
         return false;
      } else {
         return MedicalConfig.SERVER.primalWinterIntegration.get() && ModList.get().isLoaded("primalwinter") && serverLevel.dimension() == Level.OVERWORLD
            ? true
            : serverLevel.isRaining() && ((Biome)serverLevel.getBiome(blockPos).value()).getPrecipitationAt(blockPos) == Precipitation.SNOW;
      }
   }

   private static boolean scanHeat(ServerLevel serverLevel, BlockPos blockPos) {
      MutableBlockPos mutableBlockPos = new MutableBlockPos();

      for (int i = -4; i <= 4; i++) {
         for (int j = -2; j <= 3; j++) {
            for (int k = -4; k <= 4; k++) {
               mutableBlockPos.set(blockPos.getX() + i, blockPos.getY() + j, blockPos.getZ() + k);
               if (serverLevel.isLoaded(mutableBlockPos)) {
                  BlockState blockState = serverLevel.getBlockState(mutableBlockPos);
                  if (isHeatSource(blockState)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public static boolean isHeatSource(BlockState blockState) {
      if (blockState.isAir()) {
         return false;
      } else if (blockState.is(BlockTags.FIRE)) {
         return true;
      } else if (blockState.getFluidState().is(FluidTags.LAVA)) {
         return true;
      } else if (blockState.is(BlockTags.CAMPFIRES)) {
         return blockState.hasProperty(BlockStateProperties.LIT) && (Boolean)blockState.getValue(BlockStateProperties.LIT);
      } else if (blockState.getBlock() instanceof AbstractFurnaceBlock) {
         return (Boolean)blockState.getValue(BlockStateProperties.LIT);
      } else {
         ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
         if (resourceLocation.getPath().equals("blaze_burner")) {
            for (Property property : blockState.getProperties()) {
               if (property.getName().equals("blaze")) {
                  String string = blockState.getValue(property).toString().toLowerCase();
                  return !string.equals("none") && !string.equals("smouldering");
               }
            }
         }

         return false;
      }
   }

   private static boolean scanJukebox(ServerLevel serverLevel, BlockPos blockPos) {
      int i = blockPos.getX() >> 4;
      int j = blockPos.getZ() >> 4;
      double d = 576.0;

      for (int k = -2; k <= 2; k++) {
         for (int l = -2; l <= 2; l++) {
            if (serverLevel.hasChunk(i + k, j + l)) {
               LevelChunk levelChunk = serverLevel.getChunk(i + k, j + l);

               for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                  if (blockEntity instanceof JukeboxBlockEntity jukeboxBlockEntity
                     && jukeboxBlockEntity.getSongPlayer().isPlaying()
                     && blockEntity.getBlockPos().distSqr(blockPos) <= d) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   @Override
   public AbstractPlayerDamageModel damageModel() {
      return this.damageModel;
   }

   @Override
   public float bodyTemperatureC() {
      return this.bodyC;
   }

   @Override
   public boolean hasTemperatureMod() {
      return this.hasTempMod;
   }

   @Override
   public boolean temperatureFrozen() {
      return this.grace;
   }

   @Override
   public long gameTime() {
      return this.gameTime;
   }

   @Override
   public int secondsSinceDamage() {
      return this.secondsSinceDamage;
   }

   @Override
   public int secondsSinceFight() {
      return this.secondsSinceFight;
   }

   @Override
   public MedicalContext.Activity activity() {
      return this.activity;
   }

   @Override
   public int lightLevel() {
      return this.light;
   }

   @Override
   public boolean hostilesNearby() {
      return this.hostiles;
   }

   @Override
   public boolean noisyEnvironment() {
      return this.noisy;
   }

   @Override
   public boolean nearHeatSource() {
      return this.heat;
   }

   @Override
   public boolean inSnowstorm() {
      return this.snowstorm;
   }

   @Override
   public boolean otherPlayerNearby(double d) {
      return isOtherPlayerNearby(this.player, d);
   }

   public static boolean isOtherPlayerNearby(Player player, double d) {
      double e = d * d;

      for (Player player1 : player.level().players()) {
         if (player1 != player && player1.isAlive() && !player1.isSpectator() && player1.distanceToSqr(player) <= e) {
            return true;
         }
      }

      return false;
   }
}
