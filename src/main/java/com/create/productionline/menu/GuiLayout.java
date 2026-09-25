package com.create.productionline.menu;

/**
 * Pixel geometry of the three container GUIs, in the 176x196 space every screen
 * blits its background into.
 *
 * <p>Each background is hand-drawn and has the same shape: a recessed <b>well</b>
 * near the top where the machine's own slots live, a groove at
 * {@link #DIVIDER_Y} (a dark line plus a white one under it) and the player
 * inventory below that. The numbers here were measured off the shipped PNGs —
 * the 140-grey fill of each well and the 18 px cell frames — and both the menus
 * (slot positions) and the screens (text, buttons) read them from this one
 * place, so a slot can never drift away from the well it is drawn in.
 *
 * <p>Why a shared class: the wells are fixed art, so a slot or a text line that
 * sits a few pixels off is invisible to every resource check we have and only
 * shows up in game (that is exactly how the loader grid ended up flush left with
 * an 18 px hole on the right, and how the dismantler's hint came to be printed
 * across its own well). {@code SelfTest} now asserts the invariants instead of
 * trusting the eye.
 *
 * <p>Conventions: {@code slot.x/slot.y} is the cell's top-left corner, the
 * Create-style frame is drawn one pixel outside it (18x18 at
 * {@code slot.x - 1, slot.y - 1}), and a text line is 8 px tall on a 9 px
 * advance.
 */
public final class GuiLayout {

    private GuiLayout() {
    }

    /** Both backgrounds are 176x196; the PNGs are 256x256 with the rest transparent. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 196;

    /** The drawn groove: a dark line at this y, a white one at {@code y + 1}. */
    public static final int DIVIDER_Y = 99;
    /** "Inventory" label and the player's own grid, below the groove. */
    public static final int INVENTORY_LABEL_Y = 102;
    public static final int PLAYER_SLOTS_Y = 112;

    /** One cell: 18 px frame, item drawn at its top-left corner. */
    public static final int SLOT_FRAME = 18;
    /** Text line advance; a glyph is 8 px tall, so the last line may start here. */
    public static final int TEXT_LINE = 9;
    public static final int TEXT_MAX_Y = DIVIDER_Y - 8;

    // --- Scheme Loader: 2 rows x 8 columns, cells edge to edge ----------------

    /** Inner bounds of the loader's well (its 140-grey fill, frame excluded). */
    public static final int LOADER_WELL_LEFT = 7;
    public static final int LOADER_WELL_TOP = 14;
    public static final int LOADER_WELL_RIGHT = 168;
    public static final int LOADER_WELL_BOTTOM = 55;

    public static final int LOADER_COLUMNS = 8;
    public static final int LOADER_ROWS = 2;

    /**
     * Title baseline. This panel's well starts at y=13 while the other two start lower, so it
     * needs one row more clearance: a glyph is 8 px tall and at y=6 its bottom row landed
     * exactly on the well's top edge — the "the box presses on the title" seen in game.
     */
    public static final int LOADER_TITLE_Y = 5;

    /** Status text: 6 px under the well, i.e. clear of its bottom highlight row. */
    public static final int LOADER_TEXT_Y = LOADER_WELL_BOTTOM + 6;

    // --- Dismantler: two slots on the computer's own spacing ------------------

    /** Inner bounds of the dismantler's well. */
    public static final int DISMANTLER_WELL_LEFT = 43;
    public static final int DISMANTLER_WELL_TOP = 21;
    public static final int DISMANTLER_WELL_RIGHT = 132;
    public static final int DISMANTLER_WELL_BOTTOM = 46;

    /** Hint text under the well; the button below it, both clear of the groove. */
    public static final int DISMANTLER_TEXT_Y = DISMANTLER_WELL_BOTTOM + 5;
    public static final int DISMANTLER_BUTTON_Y = 80;
    public static final int DISMANTLER_BUTTON_WIDTH = 70;
    public static final int DISMANTLER_BUTTON_HEIGHT = 16;
    public static final int DISMANTLER_TEXT_MAX_Y = DISMANTLER_BUTTON_Y - TEXT_LINE;

    // --- Production Computer: three slots in a row ----------------------------

    /** Inner bounds of the computer's well (wider but shorter than the loader's). */
    public static final int COMPUTER_WELL_LEFT = 7;
    public static final int COMPUTER_WELL_TOP = 17;
    public static final int COMPUTER_WELL_RIGHT = 168;
    public static final int COMPUTER_WELL_BOTTOM = 42;

    public static final int COMPUTER_BUTTON_Y = COMPUTER_WELL_BOTTOM + 4;
    public static final int COMPUTER_BUTTON_WIDTH = 60;
    public static final int COMPUTER_BUTTON_HEIGHT = 16;
    public static final int COMPUTER_TEXT_Y = COMPUTER_BUTTON_Y + SLOT_FRAME;
    public static final int COMPUTER_SLOT_Y = centredFrameY(COMPUTER_WELL_TOP, COMPUTER_WELL_BOTTOM, 1, 0) + 1;

    /** Frame spacing of the wider (non-grid) rows: 18 px cells, 18 px apart. */
    private static final int SPACED_GAP = SLOT_FRAME;

    // --- slot positions -------------------------------------------------------
    // Every grid below is centred inside its well: the wells are fixed art, so
    // "flush left" is what a missing margin looks like.

    public static int loaderSlotX(int column) {
        int first = centredFrameX(LOADER_WELL_LEFT, LOADER_WELL_RIGHT, LOADER_COLUMNS, 0);
        return first + column * SLOT_FRAME + 1;
    }

    public static int loaderSlotY(int row) {
        int first = centredFrameY(LOADER_WELL_TOP, LOADER_WELL_BOTTOM, LOADER_ROWS, 0);
        return first + row * SLOT_FRAME + 1;
    }

    public static int dismantlerItemX() {
        return centredFrameX(DISMANTLER_WELL_LEFT, DISMANTLER_WELL_RIGHT, 2, SPACED_GAP) + 1;
    }

    public static int dismantlerSchemeX() {
        return dismantlerItemX() + SLOT_FRAME + SPACED_GAP;
    }

    public static int dismantlerSlotY() {
        return centredFrameY(DISMANTLER_WELL_TOP, DISMANTLER_WELL_BOTTOM, 1, 0) + 1;
    }

    public static int computerSlotX(int index) {
        int first = centredFrameX(COMPUTER_WELL_LEFT, COMPUTER_WELL_RIGHT, 3, SPACED_GAP);
        return first + index * (SLOT_FRAME + SPACED_GAP) + 1;
    }

    /** Frame width of {@code count} cells with {@code gap} pixels between them. */
    static int gridWidth(int count, int gap) {
        return count * SLOT_FRAME + (count - 1) * gap;
    }

    /** x of the first cell's frame when the grid is centred in {@code left..right}. */
    static int centredFrameX(int left, int right, int count, int gap) {
        return left + (right - left + 1 - gridWidth(count, gap)) / 2;
    }

    /** y of the first cell's frame when the grid is centred in {@code top..bottom}. */
    static int centredFrameY(int top, int bottom, int count, int gap) {
        return top + (bottom - top + 1 - gridWidth(count, gap)) / 2;
    }
}
