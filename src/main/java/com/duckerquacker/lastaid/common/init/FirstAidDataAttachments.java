package com.duckerquacker.lastaid.common.init;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class FirstAidDataAttachments {
    private static boolean loggedClientDamageModelSync;

    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LastAid.MODID);

    public static final Supplier<AttachmentType<PlayerDamageModel>> DAMAGE_MODEL = REGISTRY.register("damage_model", () -> AttachmentType.builder(holder -> new PlayerDamageModel())
            .serialize(new DamageModelAttachmentSerializer())
            .sync(new DamageModelSyncHandler())
            .build());

    public static final Supplier<AttachmentType<com.duckerquacker.lastaid.common.medical.MedicalData>> MEDICAL = REGISTRY.register("medical", () -> AttachmentType.builder(holder -> new com.duckerquacker.lastaid.common.medical.MedicalData())
            .serialize(new IAttachmentSerializer<CompoundTag, com.duckerquacker.lastaid.common.medical.MedicalData>() {
                @Override
                public com.duckerquacker.lastaid.common.medical.MedicalData read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    com.duckerquacker.lastaid.common.medical.MedicalData data = new com.duckerquacker.lastaid.common.medical.MedicalData();
                    data.load(tag);
                    return data;
                }

                @Override
                public CompoundTag write(com.duckerquacker.lastaid.common.medical.MedicalData attachment, HolderLookup.Provider provider) {
                    return attachment.save();
                }
            })
            .build());

    private FirstAidDataAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }

    private static final class DamageModelSyncHandler implements AttachmentSyncHandler<PlayerDamageModel> {

        @Override
        public boolean sendToPlayer(net.neoforged.neoforge.attachment.IAttachmentHolder holder, ServerPlayer to) {
            return holder instanceof net.minecraft.world.entity.player.Player;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, PlayerDamageModel attachment, boolean initialSync) {
            buf.writeNbt(attachment.serializeNBT());
        }

        @Override
        public @Nullable PlayerDamageModel read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable PlayerDamageModel previousValue) {
            PlayerDamageModel model = previousValue != null ? previousValue : new PlayerDamageModel();
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                model.deserializeNBT(tag);
            }
            LastAid.isSynced = true;
            if (!loggedClientDamageModelSync) {
                LastAid.LOGGER.info(
                        "Received client damage model attachment sync, holderType={}, reusedModel={}, hasTag={}",
                        holder.getClass().getName(),
                        previousValue != null,
                        tag != null
                );
                loggedClientDamageModelSync = true;
            }
            return model;
        }
    }

    private static final class DamageModelAttachmentSerializer implements IAttachmentSerializer<CompoundTag, PlayerDamageModel> {

        @Override
        public PlayerDamageModel read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            PlayerDamageModel model = new PlayerDamageModel();
            model.deserializeNBT(tag);
            return model;
        }

        @Nullable
        @Override
        public CompoundTag write(PlayerDamageModel attachment, HolderLookup.Provider provider) {
            return attachment.serializeNBT();
        }
    }
}
