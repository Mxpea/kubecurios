package com.aurelith.kubecurios;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

public final class MyCuriosKubeJSPlugin implements KubeJSPlugin {
    private static final Identifier CURIO = Identifier.fromNamespaceAndPath(KubeCurios.MODID, "curio");

    @Override
    public void init() {
        CurioSlotWhitelist.registerPredicate();
    }

    @Override
    public void registerBuilderTypes(BuilderTypeRegistry registry) {
        registry.of(Registries.ITEM, types -> types.add(CURIO, CurioBuilder.class, CurioBuilder::new));
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        CurioSlotTypeWrapper.register(registry);
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType.isStartup()) {
            CurioScripts.begin();
        }
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType.isStartup()) {
            CurioScripts.freeze();
        }
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("CuriosUtils", MyCuriosUtils.class);
        if (bindings.type().isStartup()) {
            bindings.add("CuriosWhitelist", CurioSlotWhitelist.class);
            CurioScripts.bind(bindings);
        }
    }
}
