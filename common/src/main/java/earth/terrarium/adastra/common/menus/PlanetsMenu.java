package earth.terrarium.adastra.common.menus;

import com.mojang.datafixers.util.Pair;
import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.adastra.common.handlers.SpaceStationHandler;
import earth.terrarium.adastra.common.handlers.base.SpaceStation;
import earth.terrarium.adastra.common.menus.base.PlanetsMenuProvider;
import earth.terrarium.adastra.common.network.NetworkHandler;
import earth.terrarium.adastra.common.network.messages.ServerboundConstructSpaceStationPacket;
import earth.terrarium.adastra.common.planets.AdAstraData;
import earth.terrarium.adastra.common.planets.Planet;
import earth.terrarium.adastra.common.recipes.SpaceStationRecipe;
import earth.terrarium.adastra.common.recipes.base.IngredientHolder;
import earth.terrarium.adastra.common.registry.ModMenus;
import earth.terrarium.adastra.common.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

public class PlanetsMenu extends AbstractContainerMenu {

    protected int tier = 255;
    protected final Inventory inventory;
    protected final Player player;
    protected final Level level;
    protected final Map<ResourceKey<Level>, Map<UUID, Set<SpaceStation>>> spaceStations;
    protected final Map<ResourceKey<Level>, List<Pair<ItemStack, Integer>>> ingredients;
    protected final Set<GlobalPos> spawnLocations;
    protected final boolean canConstruct;

    public PlanetsMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, PlanetsMenuProvider.createSpaceStationsFromBuf(buf), PlanetsMenuProvider.createSpawnLocationsFromBuf(buf));
    }

    public PlanetsMenu(int containerId, Inventory inventory, Map<ResourceKey<Level>, Map<UUID, Set<SpaceStation>>> spaceStations, Set<GlobalPos> spawnLocations) {
        super(ModMenus.PLANETS.get(), containerId);
        this.inventory = inventory;
        player = inventory.player;
        level = player.level();
        if (player.getVehicle() instanceof Rocket vehicle) {
            tier = vehicle.tier();
        }

        this.spaceStations = spaceStations;
        this.ingredients = getSpaceStationRecipes();
        this.canConstruct = SpaceStationHandler.hasIngredients(player, level);
        this.spawnLocations = spawnLocations;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public int tier() {
        return tier;
    }

    public Player player() {
        return player;
    }

    public Map<ResourceKey<Level>, List<Pair<ItemStack, Integer>>> ingredients() {
        return ingredients;
    }

    public boolean canConstruct() {
        return canConstruct;
    }

    private Map<ResourceKey<Level>, List<Pair<ItemStack, Integer>>> getSpaceStationRecipes() {
        List<SpaceStationRecipe> spaceStationRecipes = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SPACE_STATION_RECIPE.get());
        Map<ResourceKey<Level>, List<Pair<ItemStack, Integer>>> recipes = new HashMap<>(spaceStationRecipes.size());
        for (var recipe : spaceStationRecipes) {
            for (IngredientHolder holder : recipe.ingredients()) {
                int count = 0;
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    var stack = inventory.getItem(i);
                    if (holder.ingredient().test(stack)) {
                        count += stack.getCount();
                    }
                }
                recipes.computeIfAbsent(recipe.dimension(), k -> new ArrayList<>()).add(new Pair<>(holder.ingredient().getItems()[0].copyWithCount(holder.count()), count));
            }
        }
        return recipes;
    }

    /**
     * Checks if the player is in a space station at or within 2 chunks of the player's current position.
     *
     * @param dimension The dimension to check.
     * @return True if the player is in a space station, false otherwise.
     */
    public boolean isInSpaceStation(ResourceKey<Level> dimension) {
        var pos = player.chunkPosition();
        var allStations = spaceStations.get(dimension);
        if (allStations == null) return false;
        for (var stations : allStations.values()) {
            for (var station : stations) {
                if (station.position().getChessboardDistance(pos) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<SpaceStation> getOwnedSpaceStations(ResourceKey<Level> dimension) {
        var allStations = spaceStations.get(dimension);
        if (allStations == null) return List.of();
        Set<SpaceStation> stations = allStations.get(player.getUUID());
        if (stations == null) return List.of();
        return stations.stream()
            .sorted(Comparator.comparingInt(station -> {
                var pos = station.position();
                return pos.getChessboardDistance(player.chunkPosition());
            })).toList();
    }

    public void constructSpaceStation(ResourceKey<Level> dimension, Component name) {
        if (!canConstruct) return;
        NetworkHandler.CHANNEL.sendToServer(new ServerboundConstructSpaceStationPacket(dimension, name));
    }

    public BlockPos getLandingPos(ResourceKey<Level> dimension, boolean tryPreviousLocation) {
        boolean landingNormally = tryPreviousLocation && player.getVehicle() instanceof Rocket;
        if (!landingNormally) player.blockPosition();

        for (var pos : spawnLocations) {
            if (pos.dimension().equals(dimension)) {
                return pos.pos();
            }
        }

        return player.blockPosition();
    }

    public Component getPlanetName(ResourceKey<Level> dimension) {
        return Component.translatable("planet.%s.%s".formatted(dimension.location().getNamespace(), dimension.location().getPath()));
    }

    public List<Planet> getSortedPlanets() {
        return AdAstraData.planets().values().stream()
            .sorted(Comparator.comparingInt(Planet::tier).thenComparing(p -> getPlanetName(p.dimension()).getString()))
            .toList();
    }
}
