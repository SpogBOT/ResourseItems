package net.spogbot.resourceitems.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.function.Consumer;

public class LanguageChoiceScreen extends Screen {
    private final Screen parent;
    private final NameSelectionScreen.NameGroup group;
    private final Consumer<String> onSelect;

    // 0: Основная рука, 1: Вторая рука, 2: Голова
    private int renderMode = 0;

    public LanguageChoiceScreen(Screen parent, NameSelectionScreen.NameGroup group, Consumer<String> onSelect) {
        super(Text.translatable("screen.resourceitems.language_choice.title"));
        this.parent = parent;
        this.group = group;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int y = 60;
        // Центральные кнопки
        for (String name : group.names()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), btn -> {
                if (this.client != null && parent instanceof NameSelectionScreen ns) {
                    this.client.setScreen(ns.parent);
                }
                onSelect.accept(name);
            }).dimensions(this.width / 2 - 100, y, 200, 20).build());
            y += 30;
        }

        // Вычисляем позицию кнопки слота: посередине между левым краем (40) и кнопкой "Back"
        int left = 40;
        int right = this.width / 2 - 50 - 20; // правый край — перед "Back" с отступом 20
        int buttonCenter = (left + right) / 2;
        int slotX = buttonCenter - 80; // центрируем кнопку шириной 160

        this.addDrawableChild(ButtonWidget.builder(getSlotText(), btn -> {
            renderMode = (renderMode + 1) % 3;
            btn.setMessage(getSlotText());
        }).dimensions(slotX, this.height - 35, 160, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 50, this.height - 35, 100, 20)
                .build());
    }

    private Text getSlotText() {
        return switch (renderMode) {
            case 0 -> Text.translatable("gui.choose.hand");
            case 1 -> Text.translatable("gui.choose.offhand");
            default -> Text.translatable("gui.choose.head");
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (this.client != null && this.client.player != null) {
            // Вычисляем позицию модельки: четко над кнопкой слота, центрирована
            int left = 40;
            int right = this.width / 2 - 50 - 20;
            int buttonCenter = (left + right) / 2;
            int boxWidth = 160; // ширина бокса как у кнопки
            int boxX1 = buttonCenter - boxWidth / 2;
            int boxX2 = buttonCenter + boxWidth / 2;
            int boxY1 = 40;
            int boxY2 = this.height - 60; // над кнопкой с отступом

            int entitySize = this.height / 6;

            ItemStack mainHand = this.client.player.getMainHandStack();
            ItemStack offHand = this.client.player.getOffHandStack();
            ItemStack head = this.client.player.getEquippedStack(EquipmentSlot.HEAD);

            clearPlayerSlots();
            switch (renderMode) {
                case 0 -> this.client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, group.icon());
                case 1 -> this.client.player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, group.icon());
                case 2 -> this.client.player.equipStack(EquipmentSlot.HEAD, group.icon());
            }

            InventoryScreen.drawEntity(
                    context,
                    boxX1, boxY1,
                    boxX2, boxY2,
                    entitySize,
                    0.0625f,
                    mouseX - (float)(boxX1 + boxX2) / 2.0f,
                    mouseY - (float)(boxY1 + boxY2) / 2.0f + 235,
                    this.client.player
            );

            this.client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, mainHand);
            this.client.player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, offHand);
            this.client.player.equipStack(EquipmentSlot.HEAD, head);
        }
    }

    private void clearPlayerSlots() {
        if (this.client.player == null) return;
        this.client.player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
        this.client.player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, ItemStack.EMPTY);
        this.client.player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}