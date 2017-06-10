package me.ichun.mods.sync.client.core;

import me.ichun.mods.ichunutil.client.keybind.KeyEvent;
import me.ichun.mods.sync.client.model.ModelShellConstructor;
import me.ichun.mods.sync.client.render.TileRendererDualVertical;
import me.ichun.mods.sync.common.Sync;
import me.ichun.mods.sync.common.packet.PacketSyncRequest;
import me.ichun.mods.sync.common.packet.PacketUpdatePlayerOnZoomFinish;
import me.ichun.mods.sync.common.shell.ShellState;
import me.ichun.mods.sync.common.tileentity.TileEntityShellStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.*;

public class EventHandlerClient
{
    public static final ResourceLocation txHome = new ResourceLocation("sync", "textures/icon/home.png");

    public HashMap<String, Integer> refusePlayerRender = new HashMap<>();
    public boolean forceRender;

    public boolean radialShow;
    public float radialPlayerYaw;
    public float radialPlayerPitch;
    public double radialDeltaX;
    public double radialDeltaY;
    public int radialTime;

    public long clock;

    public int zoomFace;
    public int zoomTimer;
    public int zoomTimeout;
    public boolean zoom;
    public int zoomX;
    public int zoomY;
    public int zoomZ;
    public int zoomDimension;

    public float zoomPrevYaw;
    public float zoomPrevPitch;

    public double zoomPrevX;
    public double zoomPrevY;
    public double zoomPrevZ;
    public boolean zoomDeath;

    public boolean hideGui;

    public int lockTime;
    public TileEntityShellStorage lockedStorage = null;
    public ArrayList<ShellState> shells = new ArrayList<>();

    public ModelShellConstructor modelShellConstructor = new ModelShellConstructor();

    @SubscribeEvent
    public void onKeyEvent(KeyEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(mc.currentScreen == null)
        {
            if(event.keyBind.isPressed() && event.keyBind.isMinecraftBind())
            {
                if(event.keyBind.keyIndex == mc.gameSettings.keyBindAttack.getKeyCode())
                {
                    double mag = Math.sqrt(Sync.eventHandlerClient.radialDeltaX * Sync.eventHandlerClient.radialDeltaX + Sync.eventHandlerClient.radialDeltaY * Sync.eventHandlerClient.radialDeltaY);
                    double magAcceptance = 0.8D;

                    double radialAngle = -720F;

                    if(mag > magAcceptance)
                    {
                        //is on the radial menu
                        double aSin = Math.toDegrees(Math.asin(Sync.eventHandlerClient.radialDeltaX));

                        if(Sync.eventHandlerClient.radialDeltaY >= 0 && Sync.eventHandlerClient.radialDeltaX >= 0)
                        {
                            radialAngle = aSin;
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY < 0 && Sync.eventHandlerClient.radialDeltaX >= 0)
                        {
                            radialAngle = 90D + (90D - aSin);
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY < 0 && Sync.eventHandlerClient.radialDeltaX < 0)
                        {
                            radialAngle = 180D - aSin;
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY >= 0 && Sync.eventHandlerClient.radialDeltaX < 0)
                        {
                            radialAngle = 270D + (90D + aSin);
                        }
                    }

                    if(mag > 0.9999999D)
                    {
                        mag = Math.round(mag);
                    }

                    ArrayList<ShellState> selectedShells = new ArrayList<>(Sync.eventHandlerClient.shells);

                    Collections.sort(selectedShells);

                    for(int i = selectedShells.size() - 1; i >= 0; i--)
                    {
                        ShellState state = selectedShells.get(i);

                        if(state.playerState == null || state.dimension != mc.theWorld.provider.getDimension() && (Sync.config.allowCrossDimensional == 0 || Sync.config.allowCrossDimensional == 1 && (state.dimension == 1 && mc.theWorld.provider.getDimension() != 1 || state.dimension != 1 && mc.theWorld.provider.getDimension() == 1)))
                        {
                            selectedShells.remove(i);
                        }
                        if(Sync.eventHandlerClient.lockedStorage != null && Sync.eventHandlerClient.lockedStorage.xCoord == state.xCoord && Sync.eventHandlerClient.lockedStorage.yCoord == state.yCoord && Sync.eventHandlerClient.lockedStorage.zCoord == state.zCoord && Sync.eventHandlerClient.lockedStorage.getWorldObj().provider.getDimension() == state.dimension)
                        {
                            selectedShells.remove(i);
                        }
                    }

                    ShellState selected = null;

                    for(int i = 0; i < selectedShells.size(); i++)
                    {

                        float leeway = 360F / selectedShells.size();

                        if(mag > magAcceptance * 0.75D && (i == 0 && (radialAngle < (leeway / 2) && radialAngle >= 0F || radialAngle > (360F) - (leeway / 2)) || i != 0 && radialAngle < (leeway * i) + (leeway / 2) && radialAngle > (leeway * i) - (leeway / 2)))
                        {
                            selected = selectedShells.get(i);
                            break;
                        }
                    }
                    if(selected != null && selected.buildProgress >= Sync.config.shellConstructionPowerRequirement && Sync.eventHandlerClient.lockedStorage != null)
                    {
                        Sync.channel.sendToServer(new PacketSyncRequest(Sync.eventHandlerClient.lockedStorage.xCoord, Sync.eventHandlerClient.lockedStorage.yCoord, Sync.eventHandlerClient.lockedStorage.zCoord, Sync.eventHandlerClient.lockedStorage.getWorldObj().provider.getDimension(), selected.xCoord, selected.yCoord, selected.zCoord, selected.dimension));
                    }

                    Sync.eventHandlerClient.radialShow = false;
                    Sync.eventHandlerClient.lockedStorage = null;
                }
                else if(event.keyBind.keyIndex == mc.gameSettings.keyBindUseItem.getKeyCode())
                {
                    Sync.eventHandlerClient.radialShow = false;
                    Sync.eventHandlerClient.lockedStorage = null;
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (Sync.eventHandlerClient.radialShow) {
            if (!Sync.eventHandlerClient.shells.isEmpty()) {
                Sync.eventHandlerClient.radialDeltaX += event.getX() / 100D;
                Sync.eventHandlerClient.radialDeltaY += event.getY() / 100D;

                double mag = Math.sqrt(Sync.eventHandlerClient.radialDeltaX * Sync.eventHandlerClient.radialDeltaX + Sync.eventHandlerClient.radialDeltaY * Sync.eventHandlerClient.radialDeltaY);
                if(mag > 1.0D) {
                    Sync.eventHandlerClient.radialDeltaX /= mag;
                    Sync.eventHandlerClient.radialDeltaY /= mag;
                }
            }
            if (event.getButton() == 0 || event.getButton() == 1) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (Sync.eventHandlerClient.refusePlayerRender.containsKey(event.getEntityPlayer().getName()) && !Sync.eventHandlerClient.forceRender && Sync.eventHandlerClient.refusePlayerRender.get(event.getEntityPlayer().getName()) < 118) {
            event.getEntityPlayer().lastTickPosX = event.getEntityPlayer().prevPosX = event.getEntityPlayer().posX;
            event.getEntityPlayer().lastTickPosY = event.getEntityPlayer().prevPosY = event.getEntityPlayer() != Minecraft.getMinecraft().thePlayer && Sync.eventHandlerClient.refusePlayerRender.get(event.getEntityPlayer().getName()) > 60 ? 500D : event.getEntityPlayer().posY;
            event.getEntityPlayer().lastTickPosZ = event.getEntityPlayer().prevPosZ = event.getEntityPlayer().posZ;
            event.getEntityPlayer().renderYawOffset = event.getEntityPlayer().rotationYaw;
            event.getEntityPlayer().deathTime = 0;
            if (!event.getEntityPlayer().isEntityAlive()) {
                event.getEntityPlayer().setHealth(1);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientConnect(FMLNetworkEvent.ClientConnectedToServerEvent event)
    {
        Sync.config.resetSession();
        Sync.eventHandlerClient.radialShow = false;
        Sync.eventHandlerClient.zoom = false;
        Sync.eventHandlerClient.lockTime = 0;
        Sync.eventHandlerClient.zoomTimer = -10;
        Sync.eventHandlerClient.zoomTimeout = 0;
        Sync.eventHandlerClient.shells.clear();
        Sync.eventHandlerClient.refusePlayerRender.clear();
        Sync.eventHandlerClient.lockedStorage = null;
    }

    @SubscribeEvent
    public void onRenderGameOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS && Sync.eventHandlerClient.radialShow) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void worldTick(TickEvent.ClientTickEvent event)
    {
        if(event.phase == TickEvent.Phase.END && Minecraft.getMinecraft().theWorld != null)
        {
            Minecraft mc = Minecraft.getMinecraft();
            WorldClient world = mc.theWorld;
            if(radialTime > 0)
            {
                radialTime--;
            }
            if(mc.currentScreen != null)
            {
                radialShow = false;
                lockedStorage = null;
            }

            if(clock != world.getWorldTime() || !world.getGameRules().getBoolean("doDaylightCycle"))
            {
                clock = world.getWorldTime();

                for(ShellState state : shells)
                {
                    state.tick();
                }

                Iterator<Map.Entry<String, Integer>> ite = refusePlayerRender.entrySet().iterator();
                while(ite.hasNext())
                {
                    Map.Entry<String, Integer> e = ite.next();
                    e.setValue(e.getValue() - 1);

                    if(e.getValue() <= 0)
                    {
                        ite.remove();
                    }
                }

                if(lockTime > 0)
                {
                    lockTime--;

                    if(lockedStorage != null)
                    {
                        mc.thePlayer.setLocationAndAngles(lockedStorage.xCoord + 0.5D, lockedStorage.yCoord, lockedStorage.zCoord + 0.5D, (lockedStorage.face - 2) * 90F, 0F);
                    }
                }

                if(zoomDimension == world.provider.getDimension())
                {
                    if(zoomTimer == 60)
                    {
                        TileEntity te = world.getTileEntity(zoomX, zoomY, zoomZ);
                        if(te instanceof TileEntityShellStorage)
                        {
                            TileEntityShellStorage ss = (TileEntityShellStorage)te;
                            ss.occupied = true;
                        }
                    }
                    if(zoomTimer > -5 && !zoomDeath)
                    {
                        mc.thePlayer.setLocationAndAngles(Sync.eventHandlerClient.zoomX + 0.5D, Sync.eventHandlerClient.zoomY, Sync.eventHandlerClient.zoomZ + 0.5D, (Sync.eventHandlerClient.zoomFace - 2) * 90F, 0F);
                    }
                    if(zoomTimer > -10)
                    {
                        zoomTimer--;
                    }
                    if(zoomTimer == 0)
                    {
                        Sync.channel.sendToServer(new PacketUpdatePlayerOnZoomFinish(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch));

                        mc.thePlayer.sendPlayerAbilities();
                    }
                }
                else if(zoomTimer > -10)
                {
                    if(zoomTimer > 60 && zoomTimer < 70)
                    {
                        zoomTimer = 60;
                    }
                    zoomTimeout++;
                    if(zoomTimeout >= 100)
                    {
                        zoomTimer = -10;
                        zoomTimeout = 0;
                        zoom = false;
                    }
                }

                if(lockedStorage != null)
                {
                    double d3 = mc.thePlayer.posX - (lockedStorage.xCoord + 0.5D);
                    double d4 = mc.thePlayer.getEntityBoundingBox().minY - lockedStorage.yCoord;
                    double d5 = mc.thePlayer.posZ - (lockedStorage.zCoord + 0.5D);
                    double dist = (double)MathHelper.sqrt_double(d3 * d3 + d4 * d4 + d5 * d5);

                    if(dist >= 0.3D || world.getTileEntity(lockedStorage.xCoord, lockedStorage.yCoord, lockedStorage.zCoord) != lockedStorage)
                    {
                        radialShow = false;
                        lockedStorage = null;
                    }
                }
            }
        }
        //		world.spawnParticle("explode", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, 0.0D, 0.0D, 0.0D);
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();

        if(mc.theWorld != null)
        {
            if(event.phase == TickEvent.Phase.START)
            {
                if(radialShow)
                {
                    Mouse.getDX();
                    Mouse.getDY();
                    mc.mouseHelper.deltaX = mc.mouseHelper.deltaY = 0;
                    if(mc.getRenderViewEntity() instanceof EntityLivingBase)
                    {
                        ((EntityLivingBase)mc.getRenderViewEntity()).prevRotationYawHead = ((EntityLivingBase)mc.getRenderViewEntity()).rotationYawHead = radialPlayerYaw;
                    }
                    mc.getRenderViewEntity().prevRotationYaw = mc.getRenderViewEntity().rotationYaw = radialPlayerYaw;
                    mc.getRenderViewEntity().prevRotationPitch = mc.getRenderViewEntity().rotationPitch = radialPlayerPitch;
                }

                updateZoom(mc.getRenderViewEntity(), event.renderTickTime, false);
            }
            else
            {
                updateZoom(mc.getRenderViewEntity(), event.renderTickTime, true);

                if(radialShow)
                {
                    double mag = Math.sqrt(Sync.eventHandlerClient.radialDeltaX * Sync.eventHandlerClient.radialDeltaX + Sync.eventHandlerClient.radialDeltaY * Sync.eventHandlerClient.radialDeltaY);
                    double magAcceptance = 0.8D;

                    ScaledResolution reso = new ScaledResolution(mc);

                    float prog = MathHelper.clamp_float((3F - radialTime + event.renderTickTime) / 3F, 0.0F, 1.0F);

                    float rad;

                    int radius = 80;
                    radius *= Math.pow(prog, 0.5D);

                    if(!mc.gameSettings.hideGUI)
                    {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);

                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPushMatrix();
                        GL11.glLoadIdentity();

                        GL11.glMatrixMode(GL11.GL_PROJECTION);
                        GL11.glPushMatrix();
                        GL11.glLoadIdentity();

                        int roundness = 100;

                        double zLev = 0.05D;

                        final int stencilBit = MinecraftForgeClient.reserveStencilBit();

                        if(stencilBit >= 0)
                        {
                            GL11.glEnable(GL11.GL_STENCIL_TEST);

                            GL11.glColorMask(false, false, false, false);

                            final int stencilMask = 1 << stencilBit;

                            GL11.glStencilMask(stencilMask);
                            GL11.glStencilFunc(GL11.GL_ALWAYS, stencilMask, stencilMask);
                            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);

                            rad = (mag > magAcceptance ? 0.85F : 0.82F) * prog * (257F / (float)reso.getScaledHeight());

                            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                            GL11.glVertex3d(0, 0, zLev);
                            for(int i = 0; i <= roundness; i++)
                            {
                                double angle = Math.PI * 2 * i / roundness;
                                GL11.glVertex3d(Math.cos(angle) * reso.getScaledHeight_double() / reso.getScaledWidth_double() * rad, Math.sin(angle) * rad, zLev);
                            }
                            GL11.glEnd();

                            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, stencilMask);

                            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                            rad = 0.44F * prog * (257F / (float)reso.getScaledHeight());

                            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                            GL11.glVertex3d(0, 0, zLev);
                            for(int i = 0; i <= roundness; i++)
                            {
                                double angle = Math.PI * 2 * i / roundness;
                                GL11.glVertex3d(Math.cos(angle) * reso.getScaledHeight_double() / reso.getScaledWidth_double() * rad, Math.sin(angle) * rad, zLev);
                            }
                            GL11.glEnd();

                            GL11.glStencilMask(0x00);
                            GL11.glStencilFunc(GL11.GL_EQUAL, stencilMask, stencilMask);

                            GL11.glColorMask(true, true, true, true);
                        }

                        rad = (mag > magAcceptance ? 0.85F : 0.82F) * prog * (257F / (float)reso.getScaledHeight());

                        GL11.glColor4f(0.0F, 0.0F, 0.0F, mag > magAcceptance ? 0.6F : 0.4F);

                        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                        GL11.glVertex3d(0, 0, zLev);
                        for(int i = 0; i <= roundness; i++)
                        {
                            double angle = Math.PI * 2 * i / roundness;
                            GL11.glVertex3d(Math.cos(angle) * reso.getScaledHeight_double() / reso.getScaledWidth_double() * rad, Math.sin(angle) * rad, zLev);
                        }
                        GL11.glEnd();

                        if(stencilBit >= 0)
                        {
                            GL11.glDisable(GL11.GL_STENCIL_TEST);
                        }

                        MinecraftForgeClient.releaseStencilBit(stencilBit);

                        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                        GL11.glPopMatrix();
                        GL11.glMatrixMode(GL11.GL_MODELVIEW);
                        GL11.glPopMatrix();

                        GL11.glDisable(GL11.GL_BLEND);

                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                    }

                    GL11.glPushMatrix();

                    double radialAngle = -720F;

                    if(mag > magAcceptance)
                    {
                        //is on the radial menu
                        double aSin = Math.toDegrees(Math.asin(Sync.eventHandlerClient.radialDeltaX));

                        if(Sync.eventHandlerClient.radialDeltaY >= 0 && Sync.eventHandlerClient.radialDeltaX >= 0)
                        {
                            radialAngle = aSin;
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY < 0 && Sync.eventHandlerClient.radialDeltaX >= 0)
                        {
                            radialAngle = 90D + (90D - aSin);
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY < 0 && Sync.eventHandlerClient.radialDeltaX < 0)
                        {
                            radialAngle = 180D - aSin;
                        }
                        else if(Sync.eventHandlerClient.radialDeltaY >= 0 && Sync.eventHandlerClient.radialDeltaX < 0)
                        {
                            radialAngle = 270D + (90D + aSin);
                        }
                    }

                    if(mag > 0.9999999D)
                    {
                        mag = Math.round(mag);
                    }

                    GL11.glDepthMask(true);

                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glEnable(GL11.GL_ALPHA_TEST);

                    ArrayList<ShellState> selectedShells = new ArrayList<ShellState>(shells);

                    Collections.sort(selectedShells);

                    for(int i = selectedShells.size() - 1; i >= 0; i--)
                    {
                        ShellState state = selectedShells.get(i);

                        if(state.playerState == null || state.dimension != mc.theWorld.provider.getDimension() && (Sync.config.allowCrossDimensional == 0 || Sync.config.allowCrossDimensional == 1 && (state.dimension == 1 && mc.theWorld.provider.getDimension() != 1 || state.dimension != 1 && mc.theWorld.provider.getDimension() == 1)))
                        {
                            selectedShells.remove(i);
                        }
                        if(lockedStorage != null && lockedStorage.xCoord == state.xCoord && lockedStorage.yCoord == state.yCoord && lockedStorage.zCoord == state.zCoord && lockedStorage.getWorldObj().provider.getDimension() == state.dimension)
                        {
                            selectedShells.remove(i);
                        }
                    }

                    ShellState selected = null;

                    Sync.eventHandlerClient.forceRender = true;
                    for(int i = 0; i < selectedShells.size(); i++)
                    {
                        double angle = Math.PI * 2 * i / selectedShells.size();

                        angle -= Math.toRadians(90D);

                        float leeway = 360F / selectedShells.size();

                        boolean selectedState = false;
                        if(mag > magAcceptance * 0.75D && (i == 0 && (radialAngle < (leeway / 2) && radialAngle >= 0F || radialAngle > (360F) - (leeway / 2)) || i != 0 && radialAngle < (leeway * i) + (leeway / 2) && radialAngle > (leeway * i ) - (leeway / 2)))
                        {
                            selectedState = true;
                            selected = selectedShells.get(i);
                        }

                        drawEntityOnScreen(selectedShells.get(i), selectedShells.get(i).playerState, reso.getScaledWidth() / 2 + (int)(radius * Math.cos(angle)), (reso.getScaledHeight() + 32) / 2 + (int)(radius * Math.sin(angle)), 16 * prog + (float)(selectedState ? 6 * mag : 0), 2, 2, event.renderTickTime, selectedState);
                    }
                    Sync.eventHandlerClient.forceRender = false;

                    drawSelectedShellText(reso, selected);

                    GL11.glPopMatrix();
                }
            }
        }
    }

    public void updateZoom(Entity ent, float f, boolean revert)
    {
        if(zoom && zoomTimer <= 0 || zoomTimer <= -10)
        {
            return;
        }

        if(zoomDeath && Minecraft.getMinecraft().currentScreen instanceof GuiGameOver)
        {
            Minecraft.getMinecraft().thePlayer.setHealth(1);
            Minecraft.getMinecraft().thePlayer.deathTime = 0;
            Minecraft.getMinecraft().displayGuiScreen(null);
        }

        if(!revert && Minecraft.getMinecraft().currentScreen != null && !(Minecraft.getMinecraft().currentScreen instanceof GuiGameOver || Minecraft.getMinecraft().currentScreen instanceof GuiChat))
        {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }

        float prog = MathHelper.clamp_float((60 - zoomTimer + f) / 60, 0.0F, 1.0F);

        if(zoom)
        {
            prog = 1.0F - prog;
        }

        float rotProg = 1.0F - MathHelper.clamp_float((float)Math.pow(1.0F - MathHelper.clamp_float(prog / (zoomDeath ? 0.2F : 0.333F), 0.0F, 1.0F), 4D), 0.0F, 1.0F);

        float disProg = 1.0F - MathHelper.clamp_float((float)Math.pow(1.0F - MathHelper.clamp_float(prog / (zoomDeath ? 0.2F : 0.333F), 0.0F, 1.0F), 2D), 0.0F, 1.0F);

        float posYProg = (float)Math.pow(MathHelper.clamp_float((prog - 0.250F) / 0.750F, 0.0F, 1.0F), 2D);

        float pitchProg = (float)Math.pow(MathHelper.clamp_float((prog - 0.100F) / 0.2F, 0.0F, 1.0F), 2D);

        if(!revert)
        {
            Mouse.getDX();
            Mouse.getDY();

            zoomPrevYaw = ent.rotationYaw;
            zoomPrevPitch = ent.rotationPitch;

            zoomPrevX = ent.posX;
            zoomPrevY = ent.posY;
            zoomPrevZ = ent.posZ;

            hideGui = Minecraft.getMinecraft().gameSettings.hideGUI;
            Minecraft.getMinecraft().gameSettings.hideGUI = true;
        }
        ent.prevRotationYaw += (revert ? -1 : 1) * (zoomDeath ? (180F * rotProg) : (zoomFace - 2) * 90F + 180F * rotProg - zoomPrevYaw);
        ent.rotationYaw += (revert ? -1 : 1) * (zoomDeath ? (180F * rotProg) : (zoomFace - 2) * 90F + 180F * rotProg - zoomPrevYaw);
        if(ent instanceof EntityLivingBase)
        {
            ((EntityLivingBase)ent).prevRotationYawHead = ent.prevRotationYaw;
                    ((EntityLivingBase)ent).rotationYawHead = ent.rotationYaw;
        }

        ent.prevRotationPitch += (revert ? -1 : 1) * (90F * pitchProg);
        ent.rotationPitch += (revert ? -1 : 1) * (90F * pitchProg);

        if(revert)
        {
            ent.prevRotationPitch = ent.rotationPitch = zoomPrevPitch;
            Minecraft.getMinecraft().gameSettings.hideGUI = hideGui;
        }

        ent.lastTickPosY += (revert ? -1 : 1) * (600D * posYProg);
        ent.prevPosY += (revert ? -1 : 1) * (600D * posYProg);
        ent.posY += (revert ? -1 : 1) * (600D * posYProg);

        switch(zoomFace)
        {
            case 0:
            {
                ent.lastTickPosZ -= (revert ? -1 : 1) * (1.5D * disProg);
                ent.prevPosZ -= (revert ? -1 : 1) * (1.5D * disProg);
                ent.posZ -= (revert ? -1 : 1) * (1.5D * disProg);
                break;
            }
            case 1:
            {
                ent.lastTickPosX += (revert ? -1 : 1) * (1.5D * disProg);
                ent.prevPosX += (revert ? -1 : 1) * (1.5D * disProg);
                ent.posX += (revert ? -1 : 1) * (1.5D * disProg);
                break;
            }
            case 2:
            {
                ent.lastTickPosZ += (revert ? -1 : 1) * (1.5D * disProg);
                ent.prevPosZ += (revert ? -1 : 1) * (1.5D * disProg);
                ent.posZ += (revert ? -1 : 1) * (1.5D * disProg);
                break;
            }
            case 3:
            {
                ent.lastTickPosX -= (revert ? -1 : 1) * (1.5D * disProg);
                ent.prevPosX -= (revert ? -1 : 1) * (1.5D * disProg);
                ent.posX -= (revert ? -1 : 1) * (1.5D * disProg);
                break;
            }
        }

        if(zoomDeath)
        {
            double motionX = (double)(-MathHelper.sin(zoomPrevYaw / 180.0F * (float)Math.PI) * MathHelper.cos(zoomPrevPitch / 180.0F * (float)Math.PI));
            double motionZ = (double)(MathHelper.cos(zoomPrevYaw / 180.0F * (float)Math.PI) * MathHelper.cos(zoomPrevPitch / 180.0F * (float)Math.PI));

            ent.lastTickPosX += (revert ? -1 : 1) * (motionX * 2D * disProg);
            ent.prevPosX += (revert ? -1 : 1) * (motionX * 2D * disProg);
            ent.posX += (revert ? -1 : 1) * (motionX * 2D * disProg);

            ent.lastTickPosZ += (revert ? -1 : 1) * (motionZ * 2D * disProg);
            ent.prevPosZ += (revert ? -1 : 1) * (motionZ * 2D * disProg);
            ent.posZ += (revert ? -1 : 1) * (motionZ * 2D * disProg);
        }
    }

    private void drawShellInfo(ShellState state, boolean selected)
    {
        if(Sync.config.showAllShellInfoInGui <= 0)
        {
            return;
        }
        if(radialShow)
        {
            GL11.glPushMatrix();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(770, 771);

            GL11.glTranslatef(0F, 0F, 100F);

            if(state != null)
            {
                float scaleee = 0.75F;
                GL11.glScalef(scaleee, scaleee, scaleee);
                String prefix = (selected ? TextFormatting.YELLOW.toString() : "");
                String string;
                if(!state.name.equalsIgnoreCase(""))
                {
                    string = state.name;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(prefix + string, (int)(-5 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(prefix + string) / 2) * scaleee), 5, 16777215);
                }
                if(Sync.config.showAllShellInfoInGui == 2)
                {
                    GL11.glScalef(scaleee, scaleee, scaleee);

                    string = Integer.toString(state.xCoord);
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(prefix + string, state.xCoord < 0 ? 2 : 8, -52, 16777215);
                    string = Integer.toString(state.yCoord);
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(prefix + string, state.yCoord < 0 ? 2 : 8, -42, 16777215);
                    string = Integer.toString(state.zCoord);
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(prefix + string, state.zCoord < 0 ? 2 : 8, -32, 16777215);
                    string = state.dimName;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(prefix + string, 8, -22, 16777215);
                }
                if(state.isHome)
                {
                    Minecraft.getMinecraft().getTextureManager().bindTexture(txHome);

                    double pX = 10.5D;
                    double pY = -10.5D;
                    double size = 12D;

                    GL11.glColor4f(0.95F, 0.95F, 0.95F, 1.0F);

                    Tessellator tessellator = Tessellator.instance;
                    tessellator.setColorRGBA(240, 240, 240, 255);

                    tessellator.startDrawingQuads();
                    tessellator.addVertexWithUV(pX, pY + size, 0.0D, 0.0D, 0.999D);
                    tessellator.addVertexWithUV(pX + size, pY + size, 0.0D, 1.0D, 0.999D);
                    tessellator.addVertexWithUV(pX + size, pY, 0.0D, 1.0D, 0.0D);
                    tessellator.addVertexWithUV(pX, pY, 0.0D, 0.0D, 0.0D);
                    tessellator.draw();
                }
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glDisable(GL11.GL_BLEND);

            GL11.glPopMatrix();
        }
    }

    private void drawShellConstructionPercentage(ShellState state)
    {
        if(radialShow)
        {
            GL11.glPushMatrix();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(770, 771);

            GL11.glTranslatef(0F, 0F, 100F);

            if(state != null)
            {
                if(state.buildProgress < Sync.config.shellConstructionPowerRequirement)
                {
                    GL11.glPushMatrix();
                    float scaleee = 1.5F;
                    GL11.glScalef(scaleee, scaleee, scaleee);
                    String name = TextFormatting.RED.toString() + (int)Math.floor(state.buildProgress / Sync.config.shellConstructionPowerRequirement * 100) + "%";
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(6 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), -14, 16777215);

                    GL11.glPopMatrix();
                }
                else if(state.isConstructor)
                {
                    GL11.glPushMatrix();
                    float scaleee = 0.75F;
                    GL11.glScalef(scaleee, scaleee, scaleee);
                    String name = I18n.format("gui.done");
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(-3 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), -14, 0xffc800);

                    GL11.glPopMatrix();
                }
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glDisable(GL11.GL_BLEND);

            GL11.glPopMatrix();
        }
    }


    private void drawSelectedShellText(ScaledResolution reso, ShellState state)
    {
        if(radialShow)
        {
            GL11.glPushMatrix();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(770, 771);

            GL11.glTranslatef(reso.getScaledWidth() / 2F, (reso.getScaledHeight() - 20) / 2F, 100F);

            if(state != null)
            {
                GL11.glPushMatrix();
                float scaleee = 1F;
                GL11.glScalef(scaleee, scaleee, scaleee);
                int height = 5;
                if(state.name.equalsIgnoreCase(""))
                {
                    String name = TextFormatting.YELLOW.toString() + state.xCoord + ", " + state.yCoord + ", " + state.zCoord;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), height, 16777215);
                    name = TextFormatting.YELLOW.toString() + state.dimName;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), height + 10, 16777215);
                }
                else
                {
                    String name = TextFormatting.YELLOW.toString() + state.xCoord + ", " + state.yCoord + ", " + state.zCoord;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), height - 5, 16777215);
                    name = TextFormatting.YELLOW.toString() + state.name;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), height + 5, 16777215);
                    name = TextFormatting.YELLOW.toString() + state.dimName;
                    Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * scaleee), height + 15, 16777215);
                }
                GL11.glPopMatrix();
            }
            else
            {
                String name = I18n.format("gui.cancel");
                Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(name, (int)(0 - (Minecraft.getMinecraft().fontRendererObj.getStringWidth(name) / 2) * 1.0F), 10, 16777215);
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glDisable(GL11.GL_BLEND);

            GL11.glPopMatrix();
        }
    }

    private void drawEntityOnScreen(ShellState state, EntityLivingBase ent, int posX, int posY, float scale, float par4, float par5, float renderTick, boolean isSelected)
    {
        if(ent != null)
        {
            boolean hideGui = Minecraft.getMinecraft().gameSettings.hideGUI;

            Minecraft.getMinecraft().gameSettings.hideGUI = true;

            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glPushMatrix();

            GL11.glDisable(GL11.GL_ALPHA_TEST);

            GL11.glTranslatef((float)posX, (float)posY, 50.0F);

            if(Sync.config.showAllShellInfoInGui == 2)
            {
                GL11.glTranslatef(-8F, 0.0F, 0.0F);
            }

            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            float f2 = ent.renderYawOffset;
            float f3 = ent.rotationYaw;
            float f4 = ent.rotationPitch;
            float f5 = ent.rotationYawHead;

            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-((float)Math.atan((double)(par5 / 40.0F))) * 20.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(15.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(25.0F, 0.0F, 1.0F, 0.0F);

            ent.renderYawOffset = (float)Math.atan((double)(par4 / 40.0F)) * 20.0F;
            ent.rotationYaw = (float)Math.atan((double)(par4 / 40.0F)) * 40.0F;
            ent.rotationPitch = -((float)Math.atan((double)(par5 / 40.0F))) * 20.0F;
            ent.rotationYawHead = ent.renderYawOffset;
            GL11.glTranslatef(0.0F, ent.yOffset, 0.0F);

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            float viewY = Minecraft.getMinecraft().getRenderManager().playerViewY;
            Minecraft.getMinecraft().getRenderManager().playerViewY = 180.0F;
            if(!(state.isConstructor && state.buildProgress < Sync.config.shellConstructionPowerRequirement))
            {
                Minecraft.getMinecraft().getRenderManager().doRenderEntity(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, false);
            }
            else
            {
                GL11.glPushMatrix();

                float bodyScale = 0.5F;

                GL11.glScalef(bodyScale, -bodyScale, -bodyScale);

                GL11.glTranslatef(0.0F, -1.48F, 0.0F);

                Minecraft.getMinecraft().renderEngine.bindTexture(TileRendererDualVertical.txShellConstructor);

                modelShellConstructor.rand.setSeed(Minecraft.getMinecraft().thePlayer.getName().hashCode());
                modelShellConstructor.txBiped = Minecraft.getMinecraft().thePlayer.getLocationSkin();
                modelShellConstructor.renderConstructionProgress(Sync.config.shellConstructionPowerRequirement > 0 ? MathHelper.clamp_float(state.buildProgress + state.powerReceived * renderTick, 0.0F, Sync.config.shellConstructionPowerRequirement) / (float)Sync.config.shellConstructionPowerRequirement : 1.0F, 0.0625F, false, true);

                GL11.glPopMatrix();
            }

            GL11.glTranslatef(0.0F, -0.22F, 0.0F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 255.0F * 0.8F, 255.0F * 0.8F);
            Tessellator.instance.setBrightness(240);

            Minecraft.getMinecraft().getRenderManager().playerViewY = viewY;
            ent.renderYawOffset = f2;
            ent.rotationYaw = f3;
            ent.rotationPitch = f4;
            ent.rotationYawHead = f5;

            GL11.glPopMatrix();

            RenderHelper.disableStandardItemLighting();

            GL11.glPushMatrix();

            GL11.glTranslatef((float)posX, (float)posY, 50.0F);

            drawShellInfo(state, isSelected);

            drawShellConstructionPercentage(state);

            GL11.glPopMatrix();

            GL11.glEnable(GL11.GL_ALPHA_TEST);

            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

            Minecraft.getMinecraft().gameSettings.hideGUI = hideGui;
        }
    }
}