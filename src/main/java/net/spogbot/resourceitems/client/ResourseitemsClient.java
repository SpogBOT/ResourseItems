package net.spogbot.resourceitems.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ResourseitemsClient implements ClientModInitializer {

    private static final RegistryKey<ItemGroup> TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of("resourcemodels", "models_tab")
    );

    private static final RegistryKey<ItemGroup> RENAMED_TAB_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of("resourcemodels", "renamed_models_tab")
    );

    public static final List<ItemStack> LOADED_MODELS = new ArrayList<>();
    public static final List<ItemStack> LOADED_RENAMED_MODELS = new ArrayList<>();

    private static final Set<Identifier> SEEN_MODELS = new HashSet<>();
    private static final Set<String> SEEN_RENAMED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        Registry.register(Registries.ITEM_GROUP, TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.PAINTING))
                .displayName(Text.translatable("gui.title.items"))
                .entries((context, entries) -> {
                    for (ItemStack stack : LOADED_MODELS) {
                        entries.add(stack);
                    }
                })
                .build());

        Registry.register(Registries.ITEM_GROUP, RENAMED_TAB_KEY, FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NAME_TAG))
                .displayName(Text.translatable("gui.title.renames"))
                .entries((context, entries) -> {
                    for (ItemStack stack : LOADED_RENAMED_MODELS) {
                        entries.add(stack);
                    }
                })
                .build());

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of("resourcemodels", "model_scanner");
            }

            @Override
            public void reload(ResourceManager manager) {
                LOADED_MODELS.clear();
                LOADED_RENAMED_MODELS.clear();
                SEEN_MODELS.clear();
                SEEN_RENAMED.clear();

                Set<String> packsWithItems = new HashSet<>();
                Map<Identifier, List<Resource>> allItemsResources = manager.findAllResources("items", path -> true);
                for (List<Resource> resourcesList : allItemsResources.values()) {
                    for (Resource res : resourcesList) {
                        packsWithItems.add(res.getPackId());
                    }
                }

                scanAndProcess(manager, "items", packsWithItems);
                scanAndProcess(manager, "models/item", packsWithItems);

                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.world != null && client.player != null && client.player.networkHandler != null) {
                    ItemGroup.DisplayContext context = new ItemGroup.DisplayContext(
                            client.player.networkHandler.getEnabledFeatures(),
                            client.options.getOperatorItemsTab().getValue(),
                            client.world.getRegistryManager()
                    );

                    ItemGroup modelsTab = Registries.ITEM_GROUP.get(TAB_KEY);
                    if (modelsTab != null) modelsTab.updateEntries(context);

                    ItemGroup renamedTab = Registries.ITEM_GROUP.get(RENAMED_TAB_KEY);
                    if (renamedTab != null) renamedTab.updateEntries(context);
                }
            }

            private void scanAndProcess(ResourceManager manager, String startingPath, Set<String> packsWithItems) {
                Map<Identifier, List<Resource>> resourcesMap = manager.findAllResources(
                        startingPath,
                        path -> path.getPath().endsWith(".json")
                );

                for (Map.Entry<Identifier, List<Resource>> entry : resourcesMap.entrySet()) {
                    Identifier resourceId = entry.getKey();
                    String fullPath = resourceId.getPath();
                    String modelPath = fullPath.substring(0, fullPath.length() - 5);

                    String modelIdStr;
                    if (startingPath.equals("items")) {
                        modelIdStr = modelPath.substring(5);
                    } else {
                        modelIdStr = modelPath.substring(11);
                    }
                    if (modelIdStr.startsWith("/")) modelIdStr = modelIdStr.substring(1);
                    if (modelIdStr.isEmpty()) continue;

                    Identifier modelId = Identifier.of(resourceId.getNamespace(), modelIdStr);

                    for (Resource resource : entry.getValue()) {
                        boolean isRenamedModelFile = false;

                        try (InputStream is = resource.getInputStream();
                             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                            if (root.has("model") && root.get("model").isJsonObject()) {
                                JsonObject modelObj = root.getAsJsonObject("model");

                                if (modelObj.has("type") && modelObj.get("type").getAsString().endsWith("select") &&
                                        modelObj.has("component") && modelObj.get("component").getAsString().endsWith("custom_name") &&
                                        modelObj.has("cases")) {

                                    isRenamedModelFile = true;
                                    Identifier baseItemId = Identifier.of(resourceId.getNamespace(), modelIdStr);
                                    Item baseItem = Registries.ITEM.get(baseItemId);

                                    if (baseItem != Items.AIR) {
                                        JsonArray cases = modelObj.getAsJsonArray("cases");
                                        for (JsonElement caseEl : cases) {
                                            if (!caseEl.isJsonObject()) continue;
                                            JsonObject caseObj = caseEl.getAsJsonObject();

                                            if (caseObj.has("when")) {
                                                JsonElement whenEl = caseObj.get("when");
                                                List<String> allNames = new ArrayList<>();

                                                if (whenEl.isJsonArray()) {
                                                    for (JsonElement e : whenEl.getAsJsonArray()) {
                                                        allNames.add(e.getAsString());
                                                    }
                                                } else if (whenEl.isJsonPrimitive()) {
                                                    allNames.add(whenEl.getAsString());
                                                }

                                                if (!allNames.isEmpty()) {
                                                    String primaryName = allNames.get(0);

                                                    String uniqueKey = resource.getPackId() + ":" + baseItemId + ":" + primaryName;

                                                    if (SEEN_RENAMED.add(uniqueKey)) {
                                                        ItemStack renamedStack = new ItemStack(baseItem);

                                                        NbtList nameList = new NbtList();
                                                        for (String name : allNames) {
                                                            nameList.add(NbtString.of(name));
                                                        }
                                                        NbtCompound data = new NbtCompound();
                                                        data.put("alternative_names", nameList);

                                                        String packId = resource.getPackId();
                                                        String packName = packId.replace("file/", "").replace(".zip", "");
                                                        data.putString("pack_name", packName);

                                                        renamedStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
                                                        renamedStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(primaryName));

                                                        LOADED_RENAMED_MODELS.add(renamedStack);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if (isRenamedModelFile) continue;

                        // Обычные модели (paper)
                        if (resourceId.getNamespace().equals("minecraft")) continue;
                        if (startingPath.equals("models/item") && !packsWithItems.contains(resource.getPackId())) continue;

                        if (!SEEN_MODELS.add(modelId)) continue;

                        String fileName = modelId.getPath().substring(modelId.getPath().lastIndexOf('/') + 1)
                                .replace('_', ' ');

                        ItemStack stack = new ItemStack(Items.PAPER);
                        stack.set(DataComponentTypes.ITEM_MODEL, modelId);
                        stack.set(DataComponentTypes.CUSTOM_NAME,
                                Text.literal(fileName).styled(style -> style.withItalic(false)));

                        LOADED_MODELS.add(stack);
                    }
                }
            }
        });
    }
}