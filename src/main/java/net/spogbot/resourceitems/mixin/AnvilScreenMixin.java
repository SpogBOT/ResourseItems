package net.spogbot.resourceitems.mixin;

import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import net.spogbot.resourceitems.client.NameSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends HandledScreen<AnvilScreenHandler> {
    public AnvilScreenMixin(AnvilScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "setup", at = @At("TAIL"))
    protected void onSetup(CallbackInfo ci) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("?"), button -> {
                    ItemStack inputStack = this.handler.getSlot(0).getStack();
                    if (!inputStack.isEmpty()) {
                        this.client.setScreen(new NameSelectionScreen(this, inputStack));
                    }
                })
                .dimensions(this.x + 4, this.y + 45, 20, 20)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("gui.desc.see")))
                .build());
    }
}