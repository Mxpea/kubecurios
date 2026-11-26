package com.aurelith.kubecurios;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

final class CurioWearTime {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, KubeCurios.MODID);
    private static final Supplier<AttachmentType<CurioWearTime>> TIME = ATTACHMENTS.register("wear_time", () ->
            AttachmentType.builder(CurioWearTime::new).serialize(new IAttachmentSerializer<CurioWearTime>() {
                @Override
                public CurioWearTime read(IAttachmentHolder holder, ValueInput input) {
                    var time = new CurioWearTime();
                    for (String item : input.keySet()) {
                        long total = input.getLongOr(item, 0);
                        if (total > 0 && Identifier.tryParse(item) != null) {
                            time.items.put(item, new Counter(total));
                        }
                    }
                    return time;
                }

                @Override
                public boolean write(CurioWearTime time, ValueOutput output) {
                    time.items.forEach((item, counter) -> output.putLong(item, counter.total));
                    return !time.items.isEmpty();
                }
            }).copyOnDeath().build());

    private static final class Counter {
        long total;
        // Deduplicate multiple slots for this item without persisting a session clock.
        long lastTick = Long.MIN_VALUE;

        Counter(long total) {
            this.total = total;
        }
    }

    private final Map<String, Counter> items = new HashMap<>();

    static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    static void tick(LivingEntity wearer, String item, long tick) {
        var counter = wearer.getData(TIME).items.computeIfAbsent(item, ignored -> new Counter(0));
        if (counter.lastTick != tick) {
            counter.lastTick = tick;
            if (counter.total < Long.MAX_VALUE) {
                counter.total++;
            }
        }
    }

    static long get(LivingEntity wearer, String item) {
        var time = wearer.getExistingDataOrNull(TIME);
        var counter = time == null ? null : time.items.get(item);
        return counter == null ? 0 : counter.total;
    }
}
