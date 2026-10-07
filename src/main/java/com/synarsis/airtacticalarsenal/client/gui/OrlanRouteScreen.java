package com.synarsis.airtacticalarsenal.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OrlanRouteScreen extends Screen {

    private final OrlanTabletScreen parentScreen;
    private final List<BlockPos> positions;
    private final List<Float> yaws;
    private final List<Boolean> activeStates;
    private final List<List<BlockPos>> routes;
    private final Consumer<List<List<BlockPos>>> onRouteUpdated;

    public OrlanRouteScreen(OrlanTabletScreen parentScreen,
                            List<BlockPos> positions,
                            List<Float> yaws,
                            List<Boolean> activeStates,
                            List<List<BlockPos>> routes,
                            Consumer<List<List<BlockPos>>> onRouteUpdated) {
        super(Component.literal("Orlan Route Setup"));
        this.parentScreen = parentScreen;
        this.positions = positions != null ? positions : new ArrayList<>();
        this.yaws = yaws != null ? yaws : new ArrayList<>();
        this.activeStates = activeStates != null ? activeStates : new ArrayList<>();
        this.routes = routes != null ? routes : new ArrayList<>();
        this.onRouteUpdated = onRouteUpdated;
    }

    @Override
    protected void init() {
        super.init();
    }

    // --- Колбэки кэша высот поверхности (SurfaceHeightCache) ---

    public void onHeightReceived(int x, int z, int y) {
        // Вызывается асинхронно при получении высоты одной точки
    }

    public void onSegmentHeightReceived(int segmentIndex, int maxY) {
        // Вызывается при просчете максимальной высоты рельефа для отрезка маршрута
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Маршрутизатор БПЛА Орлан", centerX, 35, 0x55FF55);
    }

    public void saveAndCallback() {
        if (this.onRouteUpdated != null) {
            this.onRouteUpdated.accept(this.routes);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}