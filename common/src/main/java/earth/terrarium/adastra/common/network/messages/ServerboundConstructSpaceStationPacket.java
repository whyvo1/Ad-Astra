package earth.terrarium.adastra.common.network.messages;

import com.teamresourceful.bytecodecs.base.object.ObjectByteCodec;
import com.teamresourceful.resourcefullib.common.bytecodecs.ExtraByteCodecs;
import com.teamresourceful.resourcefullib.common.networking.base.CodecPacketHandler;
import com.teamresourceful.resourcefullib.common.networking.base.Packet;
import com.teamresourceful.resourcefullib.common.networking.base.PacketContext;
import com.teamresourceful.resourcefullib.common.networking.base.PacketHandler;
import earth.terrarium.adastra.AdAstra;
import earth.terrarium.adastra.api.planets.PlanetApi;
import earth.terrarium.adastra.common.compat.cadmus.CadmusIntegration;
import earth.terrarium.adastra.common.config.AdAstraConfig;
import earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler;
import earth.terrarium.adastra.common.handlers.SpaceStationHandler;
import earth.terrarium.adastra.common.menus.PlanetsMenu;
import earth.terrarium.adastra.common.utils.ModUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public record ServerboundConstructSpaceStationPacket(
    ResourceKey<Level> dimension, Component name) implements Packet<ServerboundConstructSpaceStationPacket> {

    public static final ResourceLocation ID = new ResourceLocation(AdAstra.MOD_ID, "construct_space_station");
    public static final Handler HANDLER = new Handler();

    public static final ResourceLocation SPACE_STATION_STRUCTURE = new ResourceLocation(AdAstra.MOD_ID, "space_station");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public PacketHandler<ServerboundConstructSpaceStationPacket> getHandler() {
        return HANDLER;
    }

    private static class Handler extends CodecPacketHandler<ServerboundConstructSpaceStationPacket> {
        public Handler() {
            super(ObjectByteCodec.create(
                ExtraByteCodecs.DIMENSION.fieldOf(ServerboundConstructSpaceStationPacket::dimension),
                ExtraByteCodecs.COMPONENT.fieldOf(ServerboundConstructSpaceStationPacket::name),
                ServerboundConstructSpaceStationPacket::new
            ));
        }

        @Override
        public PacketContext handle(ServerboundConstructSpaceStationPacket packet) {
            return (player, level) -> {
                if (!(level instanceof ServerLevel serverLevel)) return;
                if (!(player instanceof ServerPlayer serverPlayer)) return;
                if (!(player.containerMenu instanceof PlanetsMenu)) return;

                var planet = PlanetApi.API.getPlanet(packet.dimension);
                if (planet == null) return;

                ServerLevel targetLevel = serverLevel.getServer().getLevel(planet.orbitIfPresent());
                if (targetLevel == null) return;

                if (SpaceStationHandler.isInSpaceStation(serverPlayer, targetLevel)) return;
                if (!SpaceStationHandler.hasIngredients(serverPlayer, targetLevel)) return;
                SpaceStationHandler.consumeIngredients(serverPlayer, targetLevel);

                var pos = player.chunkPosition();

                // Construct space station structure from structure nbt file
                StructureTemplate structure = targetLevel.getStructureManager().getOrCreate(SPACE_STATION_STRUCTURE);
                BlockPos stationPos = BlockPos.containing((pos.getMiddleBlockX() - (structure.getSize().getX() / 2.0f)), 100, (pos.getMiddleBlockZ() - (structure.getSize().getZ() / 2.0f)));
                targetLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(stationPos), 1, stationPos);
                structure.placeInWorld(targetLevel, stationPos, stationPos, new StructurePlaceSettings(), targetLevel.random, 2);

                SpaceStationHandler.constructSpaceStation(serverPlayer, targetLevel, packet.name);

                LaunchingDimensionHandler.addSpawnLocation(player, serverLevel);
                BlockPos middleBlockPosition = pos.getMiddleBlockPosition(AdAstraConfig.atmosphereLeave);
                ModUtils.land((ServerPlayer) player, targetLevel, new Vec3(middleBlockPosition.getX() - 0.5f, middleBlockPosition.getY(), middleBlockPosition.getZ() - 0.5f));

                // Cadmus claiming 3x3 chunks
                if (CadmusIntegration.cadmusLoaded()) {
                    for (int i = -1; i < 2; i++) {
                        for (int j = -1; j < 2; j++) {
                            CadmusIntegration.claim(serverPlayer, new ChunkPos(pos.x + i, pos.z + j));
                        }
                    }
                }
            };
        }
    }
}