package net.spogbot.resourceitems.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.spogbot.resourceitems.mixin.AnvilScreenAccessor;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NameSelectionScreen extends Screen {
    public final Screen parent;
    private final ItemStack targetItem;

    private final List<NameGroup> groups = new ArrayList<>();
    private final List<NameGroup> filteredGroups = new ArrayList<>();
    private final List<ButtonWidget> optionButtons = new ArrayList<>();
    private final List<ButtonWidget> starButtons = new ArrayList<>();

    // === ФИЛЬТР ПО РЕСУРСПАКАМ ===
    private String currentPackFilter = "ALL";
    private List<String> sortedUniquePacks = new ArrayList<>();

    private ButtonWidget filterLeftButton;
    private ButtonWidget filterCenterButton;
    private ButtonWidget filterRightButton;

    // Глобальное избранное
    private static final Set<String> FAVORITE_NAMES = new HashSet<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FAVORITES_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("resourceitems/favorites.json");

    static {
        loadFavorites();
    }

    private static void loadFavorites() {
        if (!Files.exists(FAVORITES_FILE)) {
            saveFavorites();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FAVORITES_FILE)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) FAVORITE_NAMES.addAll(loaded);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveFavorites() {
        try {
            Files.createDirectories(FAVORITES_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FAVORITES_FILE)) {
                GSON.toJson(FAVORITE_NAMES, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TextFieldWidget searchField;
    private String searchQuery = "";

    private int cycleTimer = 0;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private static final int BUTTON_HEIGHT = 25;
    private static final int TOP_MARGIN = 70;
    private static final int BOTTOM_MARGIN = 90; // место под две строки кнопок
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_COLOR_TRACK = 0xFF808080;
    private static final int SCROLLBAR_COLOR_THUMB = 0xFFC0C0C0;

    private boolean isDraggingScrollbar = false;

    public static record NameGroup(ItemStack icon, List<String> names, String resourcePackName) {}

    public NameSelectionScreen(Screen parent, ItemStack targetItem) {
        super(Text.translatable("screen.resourceitems.item_choice.title"));
        this.parent = parent;
        this.targetItem = targetItem;

        for (ItemStack stack : ResourseitemsClient.LOADED_RENAMED_MODELS) {
            if (stack.getItem() == targetItem.getItem()) {
                List<String> names = getNamesForStack(stack);
                if (!names.isEmpty()) {
                    String pack = getResourcePackForStack(stack);
                    groups.add(new NameGroup(stack.copy(), List.copyOf(names), pack));
                }
            }
        }

        collectUniquePacks();
        filterGroups();
    }

    private void collectUniquePacks() {
        Set<String> packSet = new HashSet<>();
        for (NameGroup g : groups) {
            packSet.add(g.resourcePackName());
        }
        sortedUniquePacks = new ArrayList<>(packSet);
        sortedUniquePacks.sort(String.CASE_INSENSITIVE_ORDER);
    }

    private List<String> getNamesForStack(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null || component.isEmpty()) {
            return List.of(stack.getName().getString());
        }
        NbtCompound nbt = component.copyNbt();
        NbtElement element = nbt.get("alternative_names");
        if (element instanceof NbtList list) {
            List<String> names = new ArrayList<>(list.size());
            for (NbtElement e : list) {
                names.add(e.asString().orElse(""));
            }
            if (!names.isEmpty()) return names;
        }
        return List.of(stack.getName().getString());
    }

    private String getResourcePackForStack(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null && !component.isEmpty()) {
            NbtCompound nbt = component.copyNbt();
            if (nbt.contains("pack_name")) {
                String pack = String.valueOf(nbt.getString("pack_name"));
                if (pack.startsWith("Optional[")) {
                    pack = pack.substring(9, pack.length() - 1);
                }
                return pack;
            }
        }
        return Text.translatable("gui.resourceitems.unknown_pack").getString();
    }

    private static boolean isGroupFavorite(NameGroup group) {
        return group.names().stream().anyMatch(FAVORITE_NAMES::contains);
    }

    private void filterGroups() {
        filteredGroups.clear();
        String query = searchQuery.trim().toLowerCase();

        for (NameGroup group : groups) {
            if (!currentPackFilter.equals("ALL") && !group.resourcePackName().equals(currentPackFilter)) continue;
            boolean matches = query.isEmpty() ||
                    group.names().stream().anyMatch(n -> n.toLowerCase().contains(query));
            if (matches) {
                filteredGroups.add(group);
            }
        }

        filteredGroups.sort(Comparator.comparing(NameSelectionScreen::isGroupFavorite).reversed()
                .thenComparing(NameGroup::resourcePackName, String.CASE_INSENSITIVE_ORDER));
    }

    private void cyclePackFilter(boolean forward) {
        if (sortedUniquePacks.isEmpty()) return;

        if (currentPackFilter.equals("ALL")) {
            currentPackFilter = forward ? sortedUniquePacks.get(0) : sortedUniquePacks.get(sortedUniquePacks.size() - 1);
        } else {
            int idx = sortedUniquePacks.indexOf(currentPackFilter);
            if (idx == -1) {
                currentPackFilter = "ALL";
                return;
            }
            currentPackFilter = forward
                    ? (idx + 1 < sortedUniquePacks.size() ? sortedUniquePacks.get(idx + 1) : "ALL")
                    : (idx - 1 >= 0 ? sortedUniquePacks.get(idx - 1) : "ALL");
        }

        scrollOffset = 0;
        clearChildren();
        init();
    }

    @Override
    protected void init() {
        optionButtons.clear();
        starButtons.clear();

        searchField = new TextFieldWidget(this.textRenderer,
                this.width / 2 - 100, 35, 200, 20,
                Text.translatable("gui.resourceitems.name_selection.search"));
        searchField.setMaxLength(64);
        searchField.setText(searchQuery);
        searchField.setPlaceholder(Text.translatable("gui.resourceitems.name_selection.search.placeholder"));
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);

        filterGroups();

        int visibleArea = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        int totalHeight = filteredGroups.size() * BUTTON_HEIGHT;
        this.maxScroll = Math.max(0, totalHeight - visibleArea);

        int index = 0;
        for (NameGroup group : filteredGroups) {
            int y = TOP_MARGIN + index * BUTTON_HEIGHT - scrollOffset;

            ButtonWidget button = ButtonWidget.builder(
                    Text.literal(group.names().get(0)),
                    btn -> openLanguageMenu(group)
            ).dimensions(this.width / 2 - 100, y, 190, 20).build();

            button.visible = y >= TOP_MARGIN - BUTTON_HEIGHT && y <= this.height - BOTTOM_MARGIN;
            this.addDrawableChild(button);
            optionButtons.add(button);

            ButtonWidget starBtn = ButtonWidget.builder(
                    getStarText(group),
                    btn -> toggleFavorite(group)
            ).dimensions(this.width / 2 + 96, y, 24, 20).build();

            starBtn.visible = button.visible;
            this.addDrawableChild(starBtn);
            starButtons.add(starBtn);

            index++;
        }

        int filterY = this.height - 60;
        int centerX = this.width / 2;

        filterLeftButton = ButtonWidget.builder(Text.literal("◀"), btn -> cyclePackFilter(false))
                .dimensions(centerX - 105, filterY, 30, 20).build();
        this.addDrawableChild(filterLeftButton);

        filterCenterButton = ButtonWidget.builder(
                currentPackFilter.equals("ALL") ? Text.translatable("gui.resourceitems.all_packs") : Text.literal(currentPackFilter),
                btn -> {
                    currentPackFilter = "ALL";
                    scrollOffset = 0;
                    clearChildren();
                    init();
                }
        ).dimensions(centerX - 70, filterY, 140, 20).build();
        this.addDrawableChild(filterCenterButton);

        filterRightButton = ButtonWidget.builder(Text.literal("▶"), btn -> cyclePackFilter(true))
                .dimensions(centerX + 75, filterY, 30, 20).build();
        this.addDrawableChild(filterRightButton);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), b -> this.close())
                .dimensions(centerX - 50, this.height - 35, 100, 20)
                .build());

        updateButtonTexts();
        searchField.setFocused(true);
    }

    private Text getStarText(NameGroup group) {
        boolean isFav = isGroupFavorite(group);
        return Text.literal(isFav ? "★" : "☆")
                .styled(style -> style.withColor(isFav ? 0xFFCC00 : 0x777777));
    }

    @Override
    public void tick() {
        super.tick();
        cycleTimer++;
        if (cycleTimer % 60 == 0) {
            updateButtonTexts();
        }
    }

    private void updateButtonTexts() {
        for (int i = 0; i < Math.min(optionButtons.size(), filteredGroups.size()); i++) {
            NameGroup group = filteredGroups.get(i);
            ButtonWidget btn = optionButtons.get(i);

            if (group.names().size() > 1) {
                int idx = (cycleTimer / 60) % group.names().size();
                btn.setMessage(Text.literal(group.names().get(idx)));
            }
        }
    }

    private void toggleFavorite(NameGroup group) {
        boolean currentlyFavorite = isGroupFavorite(group);

        if (currentlyFavorite) {
            FAVORITE_NAMES.removeAll(group.names());
        } else {
            FAVORITE_NAMES.addAll(group.names());
        }

        saveFavorites();
        clearChildren();
        init();
    }

    private void onSearchChanged(String newText) {
        searchQuery = newText;
        scrollOffset = 0;
        clearChildren();
        init();
    }

    private void openLanguageMenu(NameGroup group) {
        this.client.setScreen(new LanguageChoiceScreen(this, group, this::applyNameToAnvil));
    }

    private void applyNameToAnvil(String name) {
        if (parent instanceof AnvilScreen anvilScreen) {
            AnvilScreenAccessor accessor = (AnvilScreenAccessor) anvilScreen;
            TextFieldWidget nameField = accessor.getNameField();
            if (nameField != null) {
                nameField.setText(name);
                accessor.invokeOnRenamed(name);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        scrollOffset = (int) (scrollOffset - verticalAmount * 10);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        clearChildren();
        init();
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (maxScroll > 0 && click.button() == 0) {
            int scrollbarX = this.width - SCROLLBAR_WIDTH - 5;
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            if (click.x() >= scrollbarX && click.x() <= scrollbarX + SCROLLBAR_WIDTH &&
                    click.y() >= scrollbarY && click.y() <= scrollbarY + scrollbarHeight) {
                this.isDraggingScrollbar = true;
                updateScrollFromMouse(click.y());
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.isDraggingScrollbar) {
            updateScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && this.isDraggingScrollbar) {
            this.isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void updateScrollFromMouse(double mouseY) {
        int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        if (scrollbarHeight <= 0) return;

        int totalHeight = maxScroll + scrollbarHeight;
        int thumbHeight = Math.max(20, (scrollbarHeight * scrollbarHeight) / totalHeight);
        double trackDraggableHeight = scrollbarHeight - thumbHeight;
        if (trackDraggableHeight <= 0) return;

        double thumbCenterOffset = mouseY - TOP_MARGIN - (thumbHeight / 2.0);
        double scrollFraction = thumbCenterOffset / trackDraggableHeight;
        scrollFraction = Math.max(0.0, Math.min(1.0, scrollFraction));

        scrollOffset = (int) (scrollFraction * maxScroll);
        clearChildren();
        init();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        int index = 0;
        for (NameGroup group : filteredGroups) {
            int y = TOP_MARGIN + index * BUTTON_HEIGHT - scrollOffset;
            if (y >= TOP_MARGIN - BUTTON_HEIGHT && y <= this.height - BOTTOM_MARGIN) {
                int buttonX = this.width / 2 - 100;
                context.drawItem(group.icon(), buttonX - 25, y + 2);
            }
            index++;
        }

        if (maxScroll > 0) {
            int scrollbarX = this.width - SCROLLBAR_WIDTH - 5;
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;

            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, SCROLLBAR_COLOR_TRACK);

            int visibleArea = scrollbarHeight;
            int totalHeight = maxScroll + visibleArea;
            int thumbHeight = Math.max(20, (visibleArea * visibleArea) / totalHeight);
            int thumbY = scrollbarY + (scrollOffset * (scrollbarHeight - thumbHeight)) / maxScroll;

            context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_COLOR_THUMB);
        }

        for (int i = 0; i < optionButtons.size(); i++) {
            ButtonWidget button = optionButtons.get(i);
            if (button.isMouseOver(mouseX, mouseY)) {
                NameGroup group = filteredGroups.get(i);
                List<Text> tooltipLines = new ArrayList<>();

                int idx = group.names().size() > 1 ? (cycleTimer / 60) % group.names().size() : 0;
                tooltipLines.add(Text.literal(group.names().get(idx)).formatted(Formatting.BOLD));

                if (group.names().size() > 1) {
                    tooltipLines.add(Text.literal(""));
                    tooltipLines.add(Text.translatable("gui.resourceitems.alternative_names").formatted(Formatting.GRAY));
                    int maxNamesToShow = 5;
                    for (int n = 0; n < Math.min(group.names().size(), maxNamesToShow); n++) {
                        tooltipLines.add(Text.literal(" • " + group.names().get(n)).formatted(Formatting.WHITE));
                    }
                    if (group.names().size() > maxNamesToShow) {
                        tooltipLines.add(Text.literal(" • ... и ещё " + (group.names().size() - maxNamesToShow))
                                .formatted(Formatting.GRAY));
                    }
                }

                tooltipLines.add(Text.literal(""));
                tooltipLines.add(Text.translatable("gui.resourceitems.from_pack")
                        .append(": ")
                        .append(Text.literal(group.resourcePackName()).formatted(Formatting.YELLOW)));

                List<ItemStack> compatibleItems = getCompatibleItemsForGroup(group);
                boolean hasCompatible = !compatibleItems.isEmpty();
                if (hasCompatible) {
                    tooltipLines.add(Text.literal(""));
                    tooltipLines.add(Text.translatable("gui.resourceitems.compatible_with").formatted(Formatting.GRAY));
                }

                int maxTextWidth = tooltipLines.stream()
                        .mapToInt(textRenderer::getWidth)
                        .max().orElse(0);

                int textHeight = tooltipLines.size() * textRenderer.fontHeight + 12;

                int iconsHeight = 0;
                int iconsWidth = 0;
                if (hasCompatible) {
                    int iconsPerRow = 8;
                    int numRows = (int) Math.ceil(compatibleItems.size() / (double) iconsPerRow);
                    iconsHeight = numRows * 18 + 8;
                    iconsWidth = Math.min(compatibleItems.size(), iconsPerRow) * 18;
                }

                int totalWidth = Math.max(maxTextWidth, iconsWidth) + 20;
                int totalHeight = textHeight + iconsHeight;

                int tipX = mouseX + 12;
                int tipY = mouseY - 12;
                if (tipX + totalWidth > this.width) tipX = mouseX - totalWidth - 12;
                if (tipY + totalHeight > this.height) tipY = mouseY - totalHeight + 12;
                if (tipY < 4) tipY = 4;

                context.fill(tipX - 3, tipY - 3, tipX + totalWidth + 3, tipY + totalHeight + 3, 0xF0100010);
                context.fillGradient(tipX - 3, tipY - 4, tipX + totalWidth + 3, tipY - 3, 0x505000FF, 0x502800FF);
                context.fillGradient(tipX - 3, tipY + totalHeight + 2, tipX + totalWidth + 3, tipY + totalHeight + 3, 0x502800FF, 0x30000000);

                int textY = tipY + 4;
                for (Text line : tooltipLines) {
                    context.drawTextWithShadow(textRenderer, line, tipX + 6, textY, 0xFFFFFFFF);
                    textY += textRenderer.fontHeight;
                }

                if (hasCompatible) {
                    int iconY = tipY + textHeight + 2;
                    int iconX = tipX + 6;
                    int count = 0;
                    for (ItemStack item : compatibleItems) {
                        context.drawItem(item, iconX, iconY);
                        iconX += 18;
                        count++;
                        if (count % 8 == 0) {
                            iconX = tipX + 6;
                            iconY += 18;
                        }
                    }
                }

                break;
            }
        }
    }

    private List<ItemStack> getCompatibleItemsForGroup(NameGroup group) {
        Set<Item> uniqueItems = new HashSet<>();
        List<ItemStack> compatible = new ArrayList<>();

        Item currentItem = targetItem.getItem();
        compatible.add(new ItemStack(currentItem));
        uniqueItems.add(currentItem);

        for (String name : group.names()) {
            for (ItemStack model : ResourseitemsClient.LOADED_RENAMED_MODELS) {
                Item itemType = model.getItem();
                if (uniqueItems.contains(itemType)) continue;

                if (getNamesForStack(model).contains(name)) {
                    compatible.add(new ItemStack(itemType));
                    uniqueItems.add(itemType);

                    if (compatible.size() >= 12) return compatible;
                }
            }
        }
        return compatible;
    }
}