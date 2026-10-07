package com.synarsis.airtacticalarsenal.client.gui;

import com.synarsis.airtacticalarsenal.entity.OrlanEntity;
import com.synarsis.airtacticalarsenal.network.LaunchOrlanFromTabletPacket;
import com.synarsis.airtacticalarsenal.network.NetworkHandler;
import com.synarsis.airtacticalarsenal.network.RemoveLauncherFromTabletPacket;
import com.synarsis.airtacticalarsenal.network.UpdateOrlanRoutePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.*;

public class OrlanTabletScreen extends Screen {

    private final List<String> savedCoords;
    private final List<BlockPos> launcherPositions;
    private final List<Float> launcherYawList;
    private final List<Boolean> hasDroneList;
    private final List<List<BlockPos>> savedRoutes;

    private int selectedLauncherIndex = -1;
    private final Set<Integer> checkedLaunchers = new HashSet<>();
    private int selectedCameraIndex = -1;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_LAUNCHERS = 5;

    private Button launchButton;
    private Button routeButton;
    private Button removeButton;
    private Button checkAllButton;

    private final List<Button> launcherButtons = new ArrayList<>();
    private final List<Button> checkButtons = new ArrayList<>();
    private final List<Button> cameraButtons = new ArrayList<>();

    private String statusMessage = "";
    private static final double MAX_TABLET_DISTANCE = 30.0;

    public OrlanTabletScreen(List<String> savedCoords, List<BlockPos> launcherPositions,
                             List<Float> launcherYawList, List<Boolean> hasDroneList,
                             List<List<BlockPos>> savedRoutes) {
        super(Component.literal("Планшет управления Орлан"));
        this.savedCoords = savedCoords;
        this.launcherPositions = launcherPositions;
        this.launcherYawList = launcherYawList;
        this.hasDroneList = hasDroneList;
        this.savedRoutes = savedRoutes;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.launcherButtons.clear();
        this.checkButtons.clear();
        this.cameraButtons.clear();

        int startX = this.width / 2 - 200;
        int startY = this.height / 2 - 110;

        // Основные кнопки
        this.launchButton = this.addRenderableWidget(Button.builder(Component.literal("Запуск"), b -> onLaunchPressed())
                .bounds(startX + 10, startY + 180, 80, 20).build());

        this.routeButton = this.addRenderableWidget(Button.builder(Component.literal("Маршрут"), b -> onRoutePressed())
                .bounds(startX + 100, startY + 180, 80, 20).build());

        this.removeButton = this.addRenderableWidget(Button.builder(Component.literal("Удалить"), b -> onRemovePressed())
                .bounds(startX + 190, startY + 180, 80, 20).build());

        this.checkAllButton = this.addRenderableWidget(Button.builder(Component.literal("Все"), b -> toggleCheckAll())
                .bounds(startX + 10, startY + 15, 35, 18).build());

        // Скролл списков
        this.addRenderableWidget(Button.builder(Component.literal("▲"), b -> scrollUp())
                .bounds(startX + 250, startY + 35, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("▼"), b -> scrollDown())
                .bounds(startX + 250, startY + 145, 20, 20).build());

        rebuildLauncherButtons();
        rebuildCameraButtons();
        updateButtonStates();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            rebuildLauncherButtons();
        }
    }

    private void scrollDown() {
        if (scrollOffset + MAX_VISIBLE_LAUNCHERS < launcherPositions.size()) {
            scrollOffset++;
            rebuildLauncherButtons();
        }
    }

    private void rebuildLauncherButtons() {
        for (Button b : launcherButtons) this.removeWidget(b);
        for (Button b : checkButtons) this.removeWidget(b);
        launcherButtons.clear();
        checkButtons.clear();

        int startX = this.width / 2 - 200;
        int startY = this.height / 2 - 110;

        int end = Math.min(scrollOffset + MAX_VISIBLE_LAUNCHERS, launcherPositions.size());
        for (int i = scrollOffset; i < end; i++) {
            final int index = i;
            int yPos = startY + 40 + (i - scrollOffset) * 22;

            boolean isChecked = checkedLaunchers.contains(index);
            Button checkBtn = this.addRenderableWidget(Button.builder(Component.literal(isChecked ? "[X]" : "[ ]"), b -> toggleCheck(index))
                    .bounds(startX + 10, yPos, 25, 20).build());
            checkButtons.add(checkBtn);

            String label = (index + 1) + ". " + savedCoords.get(index);
            if (index < hasDroneList.size() && hasDroneList.get(index)) {
                label += " [БПЛА]";
            }
            Button lBtn = this.addRenderableWidget(Button.builder(Component.literal(label), b -> selectLauncher(index))
                    .bounds(startX + 40, yPos, 205, 20).build());
            lBtn.active = (index != selectedLauncherIndex);
            launcherButtons.add(lBtn);
        }
        updateCheckAllButton();
    }

    private void rebuildCameraButtons() {
        for (Button b : cameraButtons) this.removeWidget(b);
        cameraButtons.clear();

        int startX = this.width / 2 + 80;
        int startY = this.height / 2 - 110;

        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) return;

        List<OrlanEntity> orlans = Minecraft.getInstance().level.getEntitiesOfClass(OrlanEntity.class,
                Minecraft.getInstance().player.getBoundingBox().inflate(2000.0));

        int yOffset = 0;
        for (OrlanEntity orlan : orlans) {
            final int entityId = orlan.getId();
            BlockPos pos = orlan.blockPosition();
            String label = "Орлан #" + entityId + " (" + pos.getX() + "," + pos.getZ() + ")";

            Button cBtn = this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                this.selectedCameraIndex = entityId;
                updateDroneRoute(entityId, new int[]{pos.getX(), pos.getY(), pos.getZ()});
            }).bounds(startX, startY + 40 + yOffset, 120, 20).build());

            cameraButtons.add(cBtn);
            yOffset += 22;
            if (yOffset > 130) break;
        }
    }

    private void toggleCheck(int index) {
        if (checkedLaunchers.contains(index)) {
            checkedLaunchers.remove(index);
        } else {
            checkedLaunchers.add(index);
        }
        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void toggleCheckAll() {
        if (checkedLaunchers.size() == launcherPositions.size()) {
            checkedLaunchers.clear();
        } else {
            for (int i = 0; i < launcherPositions.size(); i++) {
                checkedLaunchers.add(i);
            }
        }
        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void updateCheckAllButton() {
        if (checkAllButton != null) {
            boolean all = !launcherPositions.isEmpty() && checkedLaunchers.size() == launcherPositions.size();
            checkAllButton.setMessage(Component.literal(all ? "[X]" : "Все"));
        }
    }

    private String getPhaseIcon(OrlanEntity.FlightPhase phase) {
        if (phase == null) return "§7?";
        return switch (phase) {
            case LIFTING_OFF -> "ВЗЛЕТ";
            case LAUNCHING -> "ЗАПУСК";
            case CLIMBING -> "НАБОР";
            case CRUISING -> "КРУИЗ";
            case PATROLLING -> "ПАТРУЛЬ";
            case RETURNING -> "ВОЗВРАТ";
            case LANDING -> "ПОСАДКА";
            case LANDED -> "ПОСАЖЕН";
            default -> "НЕИЗВЕСТНО";
        };
    }

    private void updateDroneRoute(int entityId, int[] orlanData) {
        if (Minecraft.getInstance().level == null) return;

        net.minecraft.world.entity.Entity droneEntity = Minecraft.getInstance().level.getEntity(entityId);
        BlockPos dronePos;
        float droneYaw = 0f;

        if (droneEntity != null) {
            dronePos = droneEntity.blockPosition();
            droneYaw = droneEntity.getYRot();
        } else {
            dronePos = new BlockPos(orlanData[0], orlanData[1], orlanData[2]);
        }

        BlockPos centerPos = dronePos;
        List<BlockPos> positions = new ArrayList<>();
        positions.add(centerPos);
        List<Float> yaws = new ArrayList<>();
        yaws.add(droneYaw);
        List<Boolean> drones = new ArrayList<>();
        drones.add(true);
        List<List<BlockPos>> routes = new ArrayList<>();
        routes.add(new ArrayList<>());

        final int droneEntityId = entityId;
        OrlanRouteScreen routeScreen = new OrlanRouteScreen(this, positions, yaws, drones, routes,
                updatedRoutes -> {
                    if (!updatedRoutes.isEmpty()) {
                        List<BlockPos> newRoute = updatedRoutes.get(0);
                        NetworkHandler.sendToServer(new UpdateOrlanRoutePacket(droneEntityId, newRoute));
                        statusMessage = "§aМаршрут обновлён!";
                    }
                });

        Minecraft.getInstance().setScreen(routeScreen);
    }

    private void selectLauncher(int index) {
        this.selectedLauncherIndex = index;
        rebuildLauncherButtons();
        updateButtonStates();
        this.statusMessage = "";
    }

    private boolean isPlayerInRange(int launcherIdx) {
        if (Minecraft.getInstance().player == null || launcherIdx < 0 || launcherIdx >= launcherPositions.size()) return false;
        BlockPos lPos = launcherPositions.get(launcherIdx);
        double dist = Minecraft.getInstance().player.position().distanceTo(
                new net.minecraft.world.phys.Vec3(lPos.getX() + 0.5, lPos.getY() + 0.5, lPos.getZ() + 0.5));
        return dist <= MAX_TABLET_DISTANCE;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedLauncherIndex >= 0 && selectedLauncherIndex < launcherPositions.size();
        boolean hasDrone = hasSelection && selectedLauncherIndex < hasDroneList.size() && hasDroneList.get(selectedLauncherIndex);
        boolean hasRoute = hasSelection && selectedLauncherIndex < savedRoutes.size() && !savedRoutes.get(selectedLauncherIndex).isEmpty();
        boolean inRange = hasSelection && isPlayerInRange(selectedLauncherIndex);

        this.launchButton.active = hasSelection && hasDrone && hasRoute && inRange;
        this.removeButton.active = hasSelection;
        this.routeButton.active = hasSelection || !checkedLaunchers.isEmpty();

        if (hasSelection && !inRange) {
            statusMessage = "§cПУ слишком далеко!";
        } else if (statusMessage.startsWith("§cПУ слишком")) {
            statusMessage = "";
        }
    }

    private void onRoutePressed() {
        List<Integer> indices = new ArrayList<>();
        if (!checkedLaunchers.isEmpty()) {
            indices.addAll(checkedLaunchers);
            Collections.sort(indices);
        } else if (selectedLauncherIndex >= 0) {
            indices.add(selectedLauncherIndex);
        } else {
            return;
        }

        List<BlockPos> positions = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        List<Boolean> drones = new ArrayList<>();
        List<List<BlockPos>> routes = new ArrayList<>();

        for (int idx : indices) {
            positions.add(launcherPositions.get(idx));
            yaws.add(idx < launcherYawList.size() ? launcherYawList.get(idx) : 0f);
            drones.add(idx < hasDroneList.size() && hasDroneList.get(idx));
            routes.add(idx < savedRoutes.size() ? new ArrayList<>(savedRoutes.get(idx)) : new ArrayList<>());
        }

        final List<Integer> absoluteIndices = new ArrayList<>(indices);
        Minecraft.getInstance().setScreen(new OrlanRouteScreen(this, positions, yaws, drones, routes,
                updatedRoutes -> {
                    for (int i = 0; i < absoluteIndices.size() && i < updatedRoutes.size(); i++) {
                        int absIdx = absoluteIndices.get(i);
                        if (absIdx < savedRoutes.size()) {
                            savedRoutes.set(absIdx, updatedRoutes.get(i));
                        }
                    }
                    updateButtonStates();
                }));
    }

    private void onLaunchPressed() {
        if (selectedLauncherIndex < 0 || selectedLauncherIndex >= launcherPositions.size()) return;

        BlockPos launcherPos = launcherPositions.get(selectedLauncherIndex);
        NetworkHandler.sendToServer(new LaunchOrlanFromTabletPacket(launcherPos, 0, 0));

        if (selectedLauncherIndex < hasDroneList.size()) {
            hasDroneList.set(selectedLauncherIndex, false);
        }

        rebuildLauncherButtons();
        updateButtonStates();
    }

    private void onRemovePressed() {
        if (selectedLauncherIndex < 0 || selectedLauncherIndex >= launcherPositions.size()) return;

        BlockPos posToRemove = launcherPositions.get(selectedLauncherIndex);
        NetworkHandler.sendToServer(new RemoveLauncherFromTabletPacket(posToRemove));

        int removed = selectedLauncherIndex;
        savedCoords.remove(removed);
        launcherPositions.remove(removed);
        if (removed < hasDroneList.size()) hasDroneList.remove(removed);
        if (removed < savedRoutes.size()) savedRoutes.remove(removed);

        Set<Integer> newChecked = new HashSet<>();
        for (int idx : checkedLaunchers) {
            if (idx < removed) newChecked.add(idx);
            else if (idx > removed) newChecked.add(idx - 1);
        }
        checkedLaunchers.clear();
        checkedLaunchers.addAll(newChecked);

        if (scrollOffset > 0 && scrollOffset >= launcherPositions.size() - MAX_VISIBLE_LAUNCHERS + 1) {
            scrollOffset = Math.max(0, launcherPositions.size() - MAX_VISIBLE_LAUNCHERS);
        }

        selectedLauncherIndex = -1;
        rebuildLauncherButtons();
        updateButtonStates();
        updateCheckAllButton();

        if (launcherPositions.isEmpty()) {
            statusMessage = "§cНет привязанных ПУ";
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int startX = this.width / 2 - 200;
        int startY = this.height / 2 - 110;

        // Задний фон панели
        graphics.fill(startX, startY, startX + 400, startY + 220, 0xCC000000);

        // Заголовки
        graphics.drawString(this.font, "Пусковые установки", startX + 50, startY + 18, 0xFFFFFF);
        graphics.drawString(this.font, "Камеры в воздухе", startX + 270, startY + 18, 0xFFFFFF);

        if (!statusMessage.isEmpty()) {
            graphics.drawString(this.font, statusMessage, startX + 10, startY + 205, 0xFFFF00);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}