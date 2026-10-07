package com.synarsis.airtacticalarsenal.client;

import com.synarsis.airtacticalarsenal.client.gui.OrlanTabletScreen;
import com.synarsis.airtacticalarsenal.client.gui.ShahedTabletScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void openShahedTabletScreen(List<BlockPos> positions, List<Boolean> drones,
                                              List<BlockPos> targets, List<Float> yawList, Map<Integer, int[]> coords,
                                              List<List<BlockPos>> routes, List<int[]> flyingShaheds) {
        Minecraft.getInstance().setScreen(new ShahedTabletScreen(positions, drones, targets, yawList, coords, routes, flyingShaheds));
    }

    public static void openOrlanTabletScreen(List<BlockPos> positions, List<Boolean> drones,
                                             List<Float> yawList, Map<Integer, int[]> coords,
                                             List<int[]> activeOrlans, List<List<BlockPos>> routes) {

        // Преобразуем позиции ПУ в читаемый список строк для интерфейса планшета
        List<String> formattedCoords = new ArrayList<>();
        if (positions != null) {
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                formattedCoords.add(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            }
        }

        // Передаем аргументы в строгом соответствии с конструктором OrlanTabletScreen:
        // (savedCoords, launcherPositions, launcherYawList, hasDroneList, savedRoutes)
        Minecraft.getInstance().setScreen(new OrlanTabletScreen(
                formattedCoords,
                positions,
                yawList,
                drones,
                routes
        ));
    }
}