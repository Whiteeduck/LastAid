package com.duckerquacker.lastaid.api.medical;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface MedicalCondition<S extends ConditionState> {
   int MOVE_NO_SPRINT = 1;
   int MOVE_NO_JUMP = 2;
   int MOVE_CRAWL = 4;

   ResourceLocation getId();

   S createState();

   int getDefaultColor();

   @Nullable
   default ResourceLocation getIconTexture() {
      return null;
   }

   boolean isActive(S s);

   void tick(ServerPlayer serverPlayer, S s, MedicalContext medicalContext);

   default void onDamage(ServerPlayer serverPlayer, S s, DamageContext damageContext, MedicalContext medicalContext) {
   }

   default void onSleepFinished(ServerPlayer serverPlayer, S s, MedicalContext medicalContext) {
   }

   void clear(ServerPlayer serverPlayer, S s);

   ConditionSnapshot snapshot(ServerPlayer serverPlayer, S s, MedicalContext medicalContext);

   default void collectSnapshots(ServerPlayer serverPlayer, S conditionState, MedicalContext medicalContext, List<ConditionSnapshot> list) {
      if (this.isActive((S)conditionState)) {
         list.add(this.snapshot(serverPlayer, (S)conditionState, medicalContext));
      }
   }

   default float incomingDamageMultiplier(ServerPlayer serverPlayer, S s, EnumPlayerPart enumPlayerPart) {
      return 1.0F;
   }

   default boolean blocksHealing(ServerPlayer serverPlayer, S s, EnumPlayerPart enumPlayerPart) {
      return false;
   }

   default int movementFlags(ServerPlayer serverPlayer, S s) {
      return 0;
   }
}
