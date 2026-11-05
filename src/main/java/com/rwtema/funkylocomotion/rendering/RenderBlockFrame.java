package com.rwtema.funkylocomotion.rendering;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.rwtema.funkylocomotion.blocks.BlockFrame;
import com.rwtema.funkylocomotion.blocks.BlockStickyFrame;
import com.rwtema.funkylocomotion.fakes.FakeWorldClient;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Renders frame blocks as a collection of faces instead of a normal cube.
 *
 * <p>
 * The open (sticky) sides are the lattice texture and are culled like a normal
 * block face. The closed sides use the solid texture and are drawn as a
 * double-sided wall, inset by a fraction of a block:
 *
 * <ul>
 * <li>a closed face next to an opaque block stays hidden from that block's side
 * (the block is in front of it), but is visible from the frame's side;</li>
 * <li>through an open opposite face you see the closed face as a solid surface
 * (for example, a frame with a wrenched bottom sitting on grass no longer shows
 * the grass through its open top);</li>
 * <li>the tiny inward inset keeps the closed/open faces from z-fighting with a
 * neighbouring glass block that draws its face on the same plane.</li>
 * </ul>
 */
public class RenderBlockFrame implements ISimpleBlockRenderingHandler {

    private static final double INSET = 1.0D / 1024.0D;

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        tessellator.setBrightness(0xF000F0);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);

        int meta = metadata & 15;
        for (int side = 0; side < 6; side++) {
            IIcon icon = block.getIcon(side, meta);
            double x0 = 0, y0 = 0, z0 = 0;
            switch (side) {
                case 0:
                    y0 += INSET;
                    break;
                case 1:
                    y0 -= INSET;
                    break;
                case 2:
                    z0 += INSET;
                    break;
                case 3:
                    z0 -= INSET;
                    break;
                case 4:
                    x0 += INSET;
                    break;
                case 5:
                    x0 -= INSET;
                    break;
            }
            renderFace(renderer, side, block, x0, y0, z0, icon);

            if (isClosed(block, side, meta)) {
                double bx = x0, by = y0, bz = z0;
                switch (side) {
                    case 0:
                        by -= 1;
                        break;
                    case 1:
                        by += 1;
                        break;
                    case 2:
                        bz -= 1;
                        break;
                    case 3:
                        bz += 1;
                        break;
                    case 4:
                        bx -= 1;
                        break;
                    case 5:
                        bx += 1;
                        break;
                }
                renderFace(renderer, side ^ 1, block, bx, by, bz, icon);
            }
        }

        tessellator.draw();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        int meta = world.getBlockMetadata(x, y, z);
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);

        Tessellator tessellator = Tessellator.instance;
        tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        // Light the frame from its own cell; a closed face against an opaque block
        // would otherwise sample the neighbour's (often dark) cell.
        tessellator.setBrightness(world.getLightBrightnessForSkyBlocks(x, y, z, block.getLightValue(world, x, y, z)));

        for (int side = 0; side < 6; side++) {
            int nx = x + Facing.offsetsXForSide[side];
            int ny = y + Facing.offsetsYForSide[side];
            int nz = z + Facing.offsetsZForSide[side];
            Block neighbor = world.getBlock(nx, ny, nz);
            // While a mover is in flight, a static block along the move axis is
            // no longer adjacent to it: keep the face that will be culled once
            // it arrives. Perpendicular neighbours stay culled as at rest.
            boolean separated = world instanceof FakeWorldClient
                && ((FakeWorldClient) world).ignoresStaticAlongMoveAxis(nx, ny, nz);

            if (neighbor instanceof BlockFrame && !separated) continue;

            boolean closed = isClosed(block, side, meta);
            if (!closed && neighbor.isOpaqueCube() && !separated) continue;

            IIcon icon = block.getIcon(side, meta);
            double x0 = x, y0 = y, z0 = z;
            switch (side) {
                case 0:
                    y0 += INSET;
                    break;
                case 1:
                    y0 -= INSET;
                    break;
                case 2:
                    z0 += INSET;
                    break;
                case 3:
                    z0 -= INSET;
                    break;
                case 4:
                    x0 += INSET;
                    break;
                case 5:
                    x0 -= INSET;
                    break;
            }
            renderFace(renderer, side, block, x0, y0, z0, icon);

            if (closed) {
                double bx = x0, by = y0, bz = z0;
                switch (side) {
                    case 0:
                        by -= 1;
                        break;
                    case 1:
                        by += 1;
                        break;
                    case 2:
                        bz -= 1;
                        break;
                    case 3:
                        bz += 1;
                        break;
                    case 4:
                        bx -= 1;
                        break;
                    case 5:
                        bx += 1;
                        break;
                }
                renderFace(renderer, side ^ 1, block, bx, by, bz, icon);
            }
        }

        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return BlockFrame.renderType;
    }

    private static boolean isClosed(Block block, int side, int meta) {
        return block instanceof BlockStickyFrame && ((BlockStickyFrame) block).isSideClosed(side, meta);
    }

    private static void renderFace(RenderBlocks renderer, int side, Block block, double x, double y, double z,
        IIcon icon) {
        switch (side) {
            case 0:
                renderer.renderFaceYNeg(block, x, y, z, icon);
                break;
            case 1:
                renderer.renderFaceYPos(block, x, y, z, icon);
                break;
            case 2:
                renderer.renderFaceZNeg(block, x, y, z, icon);
                break;
            case 3:
                renderer.renderFaceZPos(block, x, y, z, icon);
                break;
            case 4:
                renderer.renderFaceXNeg(block, x, y, z, icon);
                break;
            case 5:
                renderer.renderFaceXPos(block, x, y, z, icon);
                break;
            default:
        }
    }
}
