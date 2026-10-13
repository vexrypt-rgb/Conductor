# Mixin census: the 60 most-downloaded Fabric mods for Minecraft 1.21.4 on Modrinth (2026-10-13)

Made with `tools/convert/conductor_convert.py --census` (1.21.4 intermediary and Mojang mappings). Nested jars count as mods of their own, so Fabric API's ~40 modules weigh in individually. A mod is counted once per class it targets. Mods larger than 8 MB were skipped (fabric-language-kotlin, essential, ambientsounds, biomes-o-plenty).

Mods in the sample: 3dskinlayers appleskin architectury-api badoptimizations balm c2me-fabric chat-heads cloth-config clumps collective continuity controlling creativecore debugify dynamic-fps enchantment-descriptions entity-model-features entityculling entitytexturefeatures fabric-api fancymenu ferrite-core forge-config-api-port fzzy-config geckolib immediatelyfast inventory-profiles-next iris jade konkrete krypton lambdynamiclights language-reload lithium malilib melody modernfix modmenu moreculling mouse-tweaks no-chat-reports not-enough-animations owo-lib placeholder-api presence-footsteps puzzles-lib reeses-sodium-options searchables shulkerboxtooltip simple-voice-chat sodium-extra sodium sound-physics-remastered terrablender veinminer-client veinminer xaeros-minimap xaeros-world-map yacl zoomify 

Jars read: 60 (plus nested: 214 mods in all); with Mixins: 141; distinct Minecraft classes targeted: 893

| Rank | Minecraft class | Mods | Area |
|---|---|---|---|
| 1 | `net.minecraft.client.Minecraft` | 50 | game loop |
| 2 | `net.minecraft.client.multiplayer.ClientPacketListener` | 28 | client networking |
| 3 | `net.minecraft.client.renderer.GameRenderer` | 24 | rendering |
| 4 | `net.minecraft.client.multiplayer.ClientLevel` | 21 | client networking |
| 5 | `net.minecraft.server.MinecraftServer` | 21 | server |
| 6 | `net.minecraft.world.entity.Entity` | 20 | entities |
| 7 | `net.minecraft.client.renderer.LevelRenderer` | 19 | rendering |
| 8 | `net.minecraft.server.players.PlayerList` | 19 | server |
| 9 | `net.minecraft.client.Options` | 18 | client |
| 10 | `net.minecraft.server.level.ChunkMap` | 17 | server world/chunks |
| 11 | `net.minecraft.client.gui.Gui` | 16 | GUI/HUD |
| 12 | `net.minecraft.world.entity.player.Player` | 16 | player |
| 13 | `net.minecraft.client.MouseHandler` | 15 | client |
| 14 | `net.minecraft.client.gui.components.DebugScreenOverlay` | 15 | GUI/HUD |
| 15 | `net.minecraft.server.level.ServerPlayer` | 15 | server world/chunks |
| 16 | `net.minecraft.world.entity.LivingEntity` | 15 | entities |
| 17 | `net.minecraft.client.KeyboardHandler` | 14 | client |
| 18 | `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` | 14 | GUI/HUD |
| 19 | `net.minecraft.client.gui.screens.Screen` | 13 | GUI/HUD |
| 20 | `net.minecraft.world.level.block.entity.BlockEntity` | 13 | blocks |
| 21 | `net.minecraft.client.KeyMapping` | 12 | input |
| 22 | `net.minecraft.client.gui.GuiGraphics` | 12 | GUI/HUD |
| 23 | `net.minecraft.server.level.ServerLevel` | 12 | server world/chunks |
| 24 | `net.minecraft.world.level.chunk.LevelChunk` | 12 | chunks |
| 25 | `net.minecraft.client.renderer.entity.EntityRenderer` | 11 | rendering |
| 26 | `net.minecraft.world.level.Level` | 11 | world |
| 27 | `net.minecraft.client.renderer.entity.EntityRenderDispatcher` | 10 | rendering |
| 28 | `net.minecraft.client.resources.model.ModelManager` | 10 | client |
| 29 | `net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl` | 9 | client networking |
| 30 | `net.minecraft.commands.Commands` | 9 | other |
| 31 | `net.minecraft.network.Connection` | 9 | networking |
| 32 | `net.minecraft.server.network.ServerConfigurationPacketListenerImpl` | 9 | networking |
| 33 | `net.minecraft.world.item.ItemStack` | 9 | items |
| 34 | `net.minecraft.client.gui.screens.worldselection.CreateWorldScreen` | 8 | GUI/HUD |
| 35 | `net.minecraft.client.multiplayer.MultiPlayerGameMode` | 8 | client networking |
| 36 | `net.minecraft.client.multiplayer.chat.ChatListener` | 8 | client networking |
| 37 | `net.minecraft.client.player.LocalPlayer` | 8 | local player |
| 38 | `net.minecraft.client.renderer.ItemInHandRenderer` | 8 | rendering |
| 39 | `net.minecraft.client.renderer.entity.ItemRenderer` | 8 | rendering |
| 40 | `net.minecraft.client.renderer.entity.LivingEntityRenderer` | 8 | rendering |
| 41 | `net.minecraft.client.renderer.texture.TextureManager` | 8 | rendering |
| 42 | `net.minecraft.locale.Language` | 8 | other |
| 43 | `net.minecraft.world.level.block.Block` | 8 | blocks |
| 44 | `net.minecraft.client.renderer.texture.TextureAtlas` | 7 | rendering |
| 45 | `net.minecraft.client.resources.language.LanguageManager` | 7 | client |
| 46 | `net.minecraft.client.sounds.SoundEngine` | 7 | client |
| 47 | `net.minecraft.server.ReloadableServerResources` | 7 | server |
| 48 | `net.minecraft.server.level.ServerChunkCache` | 7 | server world/chunks |
| 49 | `net.minecraft.server.network.ServerGamePacketListenerImpl` | 7 | networking |
| 50 | `net.minecraft.server.packs.repository.PackRepository` | 7 | server |
| 51 | `net.minecraft.world.item.crafting.RecipeManager` | 7 | items |
| 52 | `net.minecraft.client.gui.components.EditBox` | 6 | GUI/HUD |
| 53 | `net.minecraft.client.gui.screens.ConnectScreen` | 6 | GUI/HUD |
| 54 | `net.minecraft.client.player.AbstractClientPlayer` | 6 | local player |
| 55 | `net.minecraft.client.renderer.entity.state.EntityRenderState` | 6 | rendering |
| 56 | `net.minecraft.client.sounds.SoundManager` | 6 | client |
| 57 | `net.minecraft.server.level.ChunkHolder` | 6 | server world/chunks |
| 58 | `net.minecraft.server.packs.repository.Pack` | 6 | server |
| 59 | `net.minecraft.server.packs.resources.MultiPackResourceManager` | 6 | server |
| 60 | `net.minecraft.server.packs.resources.SimpleReloadInstance` | 6 | server |
