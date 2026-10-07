package com.duckerquacker.lastaid.common.medical.cold;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Cold never kills directly: ColdSweat's freezing damage and vanilla freeze damage are blocked.
 * <p>
 * The only way to die of cold is a long fight the body loses: common cold stage 3 AND hypothermia stage
 * {@code minHypothermiaStage}+ for {@code coldDeathDelayMinutes}. After that the body (torso) loses {@code damage} HP
 * every {@code damageIntervalSeconds} until the player gets warm.
 */
public final class ColdDeath {
   public static final DirectDamageDistributionAlgorithm DISTRIBUTION = new DirectDamageDistributionAlgorithm(EnumPlayerPart.BODY, false);
   private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> Boolean.FALSE);
   public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> HYPOTHERMIA = net.minecraft.resources.ResourceKey.create(
      net.minecraft.core.registries.Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("lastaid", "hypothermia")
   );
   private static final ResourceLocation CS_COLD = ResourceLocation.fromNamespaceAndPath("cold_sweat", "cold");
   private static final ResourceLocation CS_COLD_SCALING = ResourceLocation.fromNamespaceAndPath("cold_sweat", "cold_scaling");

   private ColdDeath() {
   }

   private static MedicalConfig.ColdDeath cfg() {
      return MedicalConfig.SERVER.coldDeath;
   }

   public static boolean isApplying() {
      return APPLYING.get();
   }

   public static boolean isColdDamage(DamageSource source) {
      return source.is(DamageTypes.FREEZE)
         || source.is(DamageTypeTags.IS_FREEZING)
         || source.typeHolder().unwrapKey().map(key -> key.location().equals(CS_COLD) || key.location().equals(CS_COLD_SCALING)).orElse(false);
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void onIncomingDamage(LivingIncomingDamageEvent event) {
      if (event.getEntity() instanceof Player player
         && !player.level().isClientSide()
         && !isApplying()
         && MedicalConfig.SERVER_SPEC.isLoaded()
         && MedicalConfig.SERVER.enabled.get()
         && cfg().blockDirectColdDamage.get()
         && isColdDamage(event.getSource())) {
         event.setCanceled(true);
      }
   }

   public static boolean deadlyConditions(ServerPlayer player) {
      MedicalConfig.ColdDeath cfg = cfg();
      if (!cfg.allowColdDeath.get()) {
         return false;
      }
      if (Hypothermia.stage(player) < cfg.minHypothermiaStage.get()) {
         return false;
      }
      return !cfg.coldDeathRequiresMaxCold.get() || CommonCold.INSTANCE.state(player).stage >= 3;
   }

   /**
    * Called once per second.
    *
    * @param seconds seconds already spent in the deadly state (before this call)
    * @return the new number of seconds
    */
   public static int tick(ServerPlayer player, int seconds) {
      if (!deadlyConditions(player)) {
         if (seconds >= delaySeconds()) {
            player.displayClientMessage(Component.translatable("lastaid.cold_death.recovering").withStyle(ChatFormatting.AQUA), true);
         }
         return 0;
      }
      seconds++;
      int delay = delaySeconds();
      if (seconds == delay) {
         player.sendSystemMessage(Component.translatable("lastaid.cold_death.start").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
      } else if (seconds == Math.max(1, delay / 2) && delay >= 60) {
         player.displayClientMessage(Component.translatable("lastaid.cold_death.warning").withStyle(ChatFormatting.AQUA), true);
      }
      if (seconds >= delay && (seconds - delay) % cfg().damageIntervalSeconds.get() == 0) {
         float damage = cfg().damage.get().floatValue();
         if (damage > 0.0F) {
            APPLYING.set(Boolean.TRUE);
            try {
               player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(HYPOTHERMIA)), damage);
            } finally {
               APPLYING.set(Boolean.FALSE);
            }
         }
      }
      return seconds;
   }

   public static int delaySeconds() {
      return (int)Math.round(cfg().coldDeathDelayMinutes.get() * 60.0);
   }
}
