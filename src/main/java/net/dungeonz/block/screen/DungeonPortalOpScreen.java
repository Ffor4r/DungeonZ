package net.dungeonz.block.screen;

import org.apache.commons.lang3.StringUtils;

import net.dungeonz.block.entity.DungeonPortalEntity;
import net.dungeonz.network.DungeonClientPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class DungeonPortalOpScreen extends Screen {

    private static final Text DUNGEON_TYPE_TEXT = Text.translatable("dungeon.op_screen.dungeon_type");
    private static final Text DEFAULT_DIFFICULTY_TEXT = Text.translatable("dungeon.op_screen.default_difficulty");
    private final BlockPos dungeonPortalPos;

    private ButtonWidget doneButton;
    private TextFieldWidget dungeonTypeTextFieldWidget;
    private TextFieldWidget dungeonDefaultDifficultyTextFieldWidget;

    private String defaultDungeonType = "dark_dungeon";
    private String defaultDungeonDifficulty = "normal";

    public DungeonPortalOpScreen(BlockPos dungeonPortalPos) {
        super(NarratorManager.EMPTY);
        this.dungeonPortalPos = dungeonPortalPos;
    }

    @Override
    protected void init() {
        if (client.world != null && client.world.getBlockEntity(this.dungeonPortalPos) != null && client.world.getBlockEntity(this.dungeonPortalPos) instanceof DungeonPortalEntity) {
            DungeonPortalEntity dungeonPortalEntity = (DungeonPortalEntity) client.world.getBlockEntity(this.dungeonPortalPos);
            if (!dungeonPortalEntity.getDungeonType().equals("")) {
                defaultDungeonType = dungeonPortalEntity.getDungeonType();
            }
            if (!dungeonPortalEntity.getDifficulty().equals("")) {
                defaultDungeonDifficulty = dungeonPortalEntity.getDifficulty();
            }
        }

        this.dungeonTypeTextFieldWidget = new TextFieldWidget(this.textRenderer, this.width / 2 - 152, 50, 300, 20, DUNGEON_TYPE_TEXT);
        this.dungeonTypeTextFieldWidget.setMaxLength(128);
        this.dungeonTypeTextFieldWidget.setText(defaultDungeonType);
        this.dungeonTypeTextFieldWidget.setChangedListener(pool -> this.updateDoneButtonState());
        this.addSelectableChild(this.dungeonTypeTextFieldWidget);
        this.dungeonDefaultDifficultyTextFieldWidget = new TextFieldWidget(this.textRenderer, this.width / 2 - 152, 85, 300, 20, DEFAULT_DIFFICULTY_TEXT);
        this.dungeonDefaultDifficultyTextFieldWidget.setMaxLength(128);
        this.dungeonDefaultDifficultyTextFieldWidget.setText(defaultDungeonDifficulty);
        this.dungeonDefaultDifficultyTextFieldWidget.setChangedListener(name -> this.updateDoneButtonState());
        this.addSelectableChild(this.dungeonDefaultDifficultyTextFieldWidget);

        this.doneButton = this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            this.onDone();
        }).dimensions(this.width / 2 - 75, 126, 150, 20).build());
        this.setInitialFocus(this.dungeonTypeTextFieldWidget);
        this.updateDoneButtonState();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String string = this.dungeonTypeTextFieldWidget.getText();
        String string2 = this.dungeonDefaultDifficultyTextFieldWidget.getText();

        this.init(client, width, height);
        this.dungeonTypeTextFieldWidget.setText(string);
        this.dungeonDefaultDifficultyTextFieldWidget.setText(string2);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, DUNGEON_TYPE_TEXT, this.width / 2 - 153, 40, 0xA0A0A0);
        this.dungeonTypeTextFieldWidget.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, DEFAULT_DIFFICULTY_TEXT, this.width / 2 - 153, 75, 0xA0A0A0);
        this.dungeonDefaultDifficultyTextFieldWidget.render(context, mouseX, mouseY, delta);
    }

    private void updateDoneButtonState() {
        this.doneButton.active = !StringUtils.isEmpty(this.dungeonTypeTextFieldWidget.getText()) && !StringUtils.isEmpty(this.dungeonDefaultDifficultyTextFieldWidget.getText());
    }

    private void onDone() {
        this.client.setScreen(null);
        DungeonClientPacket.writeC2SSetDungeonTypePacket(client, this.dungeonTypeTextFieldWidget.getText(), this.dungeonDefaultDifficultyTextFieldWidget.getText(), dungeonPortalPos);
    }

}
