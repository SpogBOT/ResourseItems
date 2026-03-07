package net.spogbot.resourceitems.mixin;

import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AnvilScreen.class)
public interface AnvilScreenAccessor {
    @Accessor("nameField")
    TextFieldWidget getNameField();

    @Invoker("onRenamed")
    void invokeOnRenamed(String name);
}