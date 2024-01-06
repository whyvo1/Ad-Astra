package earth.terrarium.adastra.common.network.messages;

import com.teamresourceful.bytecodecs.base.ByteCodec;
import com.teamresourceful.bytecodecs.base.object.ObjectByteCodec;
import com.teamresourceful.resourcefullib.common.bytecodecs.ExtraByteCodecs;
import com.teamresourceful.resourcefullib.common.networking.base.CodecPacketHandler;
import com.teamresourceful.resourcefullib.common.networking.base.Packet;
import com.teamresourceful.resourcefullib.common.networking.base.PacketContext;
import com.teamresourceful.resourcefullib.common.networking.base.PacketHandler;
import earth.terrarium.adastra.AdAstra;
import earth.terrarium.adastra.api.planets.PlanetApi;
import earth.terrarium.adastra.common.config.AdAstraConfig;
import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.adastra.common.handlers.LaunchingDimensionHandler;
import earth.terrarium.adastra.common.menus.PlanetsMenu;
import earth.terrarium.adastra.common.utils.ModUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record ServerboundLandPacket(ResourceKey<Level> dimension,
                                    boolean tryPreviousLocation) implements Packet<ServerboundLandPacket> {

    public static final ResourceLocation ID = new ResourceLocation(AdAstra.MOD_ID, "land");
    public static final Handler HANDLER = new Handler();

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public PacketHandler<ServerboundLandPacket> getHandler() {
        return HANDLER;
    }

    private static class Handler extends CodecPacketHandler<ServerboundLandPacket> {
        public Handler() {
            super(ObjectByteCodec.create(
                ExtraByteCodecs.DIMENSION.fieldOf(ServerboundLandPacket::dimension),
                ByteCodec.BOOLEAN.fieldOf(ServerboundLandPacket::tryPreviousLocation),
                ServerboundLandPacket::new
            ));
        }

        @Override
        public PacketContext handle(ServerboundLandPacket packet) {
            return (player, level) -> {
                if (!(level instanceof ServerLevel serverLevel)) return;
                if (!(player.containerMenu instanceof PlanetsMenu)) return;

                var planet = PlanetApi.API.getPlanet(packet.dimension);
                if (planet == null) return;

                boolean landingNormally = packet.tryPreviousLocation() && player.getVehicle() instanceof Rocket;
                GlobalPos newPos = landingNormally ? LaunchingDimensionHandler.getSpawningLocation(player, serverLevel, planet)
                    .orElse(null) : null;

                var server = serverLevel.getServer();
                ServerLevel targetLevel = newPos == null ? server.getLevel(planet.dimension()) : server.getLevel(newPos.dimension());
                if (targetLevel == null) {
                    throw new IllegalStateException(String.format("Dimension %s does not exist! Try restarting your %s!",
                        planet.dimension(), server.isDedicatedServer() ? "server" : "singleplayer world"));
                }

                LaunchingDimensionHandler.addSpawnLocation(player, serverLevel);
                BlockPos targetPos = newPos != null ? newPos.pos() : player.blockPosition();
                ModUtils.land((ServerPlayer) player, targetLevel, new Vec3(targetPos.getX(), AdAstraConfig.atmosphereLeave, targetPos.getZ()));
            };
        }
    }
}