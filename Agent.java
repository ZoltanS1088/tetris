import java.util.Arrays;
import java.util.Random;

import game.tetris.TetrisAction;
import game.tetris.TetrisGame;
import game.tetris.TetrisPlayer;
import game.tetris.utils.TetrisState;
import game.tetris.utils.Tetromino;

/**
 * Tetris jatekot jatszo, celvezerelt agens, amely informalt (heurisztikus)
 * keresessel valasztja ki az alakzatok helyet.
 * <p>
 * <b>Agens felepitese.</b> Az agens a kornyezetet (a palyat es az eso
 * alakzatot) a {@link TetrisState} masolatan keresztul eszleli, es minden
 * lepesben egy akciot ad vissza. Belso allapotkent egy tervet tarol: a cel
 * forgatast es a cel oszlopot. Amig a tervet nem erte el, forgat es mozgat,
 * utana az alakzatot elengedi.
 * <p>
 * <b>Informalt kereses (moho legjobbat-eloszor kereses).</b> Amikor uj
 * alakzat jelenik meg, az agens kifejti az osszes lehetseges allapotot: minden
 * forgatasra es minden oszlopra leejti az alakzatot a palya egy masolatan,
 * torli a teli sorokat, majd a kapott allapotot egy heurisztikus fuggvennyel
 * ertekeli ({@link #heuristic}). A legjobb heurisztikus erteku allapotot
 * valasztja celkent. A heurisztika negy egyszeru jellemzo sulyozott osszege:
 * <ul>
 *   <li>osszesitett magassag (az oszlopok magassaganak osszege),</li>
 *   <li>torolt sorok szama,</li>
 *   <li>lyukak szama (ures cella, amely felett teli cella van),</li>
 *   <li>egyenetlenseg (szomszedos oszlopok magassagkulonbsegeinek osszege).</li>
 * </ul>
 * <p>
 * <b>Celok.</b> Az agensnek ket celja van, a pontszamtol fuggoen:
 * <ol>
 *   <li><i>Pontgyujtes</i>: amig a pontszam {@link #TARGET_SCORE} alatt van,
 *       a fenti heurisztikaval jatszik, es az alakzatokat leejti (DROP), ami
 *       plusz pontot ad. Azokat a lepeseket kihagyja, amelyek a pontszamot
 *       {@link #MAX_SCORE} fole vinnek.</li>
 *   <li><i>Befejezes</i>: a celpontszam elerese utan mar nem gyujt pontot.
 *       Olyan helyet keres, ahol nem torlodik sor es az alakzat a leheto
 *       legmagasabban all meg, es nem ejti le az alakzatot (nincs leejtesi
 *       pont). Igy a jatek hamar veget er, a pontszam pedig a kivant
 *       500-600 pontos savban marad.</li>
 * </ol>
 */
public class Agent extends TetrisPlayer {

  /** Ennyi pont elerese utan valt az agens befejezo uzemmodra. */
  private static final int TARGET_SCORE = 500;
  /** Ezt a pontszamot nem szeretnenk tullepni. */
  private static final int MAX_SCORE = 600;
  /** Egy alakzatra legfeljebb ennyi mozgato akciot hajtunk vegre. */
  private static final int MAX_MOVES_PER_PIECE = 12;
  /** Sorok torleseert jaro alappont (1, 2, 3, 4 sor), szorozva a szinttel. */
  private static final int[] LINE_POINTS = {0, 40, 100, 300, 1200};

  /** Heurisztika sulya: osszesitett magassag. */
  private static final double W_HEIGHT = -0.51;
  /** Heurisztika sulya: torolt sorok szama. */
  private static final double W_LINES = 0.76;
  /** Heurisztika sulya: lyukak szama. */
  private static final double W_HOLES = -0.36;
  /** Heurisztika sulya: egyenetlenseg. */
  private static final double W_BUMPINESS = -0.18;

  /** Az az alakzat-objektum, amelyre a jelenlegi terv keszult (referencia szerint). */
  private Tetromino plannedFor = null;
  /** A cel forgatasi allapot alakmatrixa. */
  private int[][] targetShape = null;
  /** A cel oszlop (az alakzat befoglalo dobozanak bal szele). */
  private int targetCol = 0;
  /** Az aktualis alakzatra eddig vegrehajtott mozgato akciok szama. */
  private int movesDone = 0;
  /** Igaz, ha az agens mar a befejezo uzemmodban van. */
  private boolean finishing = false;

  /**
   * Letrehozza az agenst.
   *
   * @param color  jatekos szine (nem hasznalt)
   * @param random veletlenszam-generator (orokolt, nem hasznaljuk)
   * @param state  a jatekallapot masolata
   */
  public Agent(int color, Random random, TetrisState state) {
    super(color, random, state);
  }

  /**
   * Visszaadja a kovetkezo akciot. Uj alakzatnal kivalasztja az aktualis
   * celt, es keresessel uj tervet keszit, egyebkent a meglevo tervet koveti.
   *
   * @param remainingTime a hatralevo gondolkodasi ido (nanomasodperc)
   * @return a kivalasztott akcio
   */
  @Override
  public TetrisAction getAction(long remainingTime) {
    Tetromino cur = state.current();
    if (cur != plannedFor) {
      plannedFor = cur;
      movesDone = 0;
      if (state.score >= TARGET_SCORE) {
        finishing = true;
      }
      search();
    }
    int action = nextAction(cur);
    state.apply(action);
    return new TetrisAction(action);
  }

  /**
   * A terv alapjan meghatarozza a kovetkezo akciot: eloszor forgatas, utana
   * vizszintes mozgatas, vegul elengedes. Pontgyujtes kozben az elengedes
   * leejtes (DROP), befejezes kozben varakozas (NONE).
   *
   * @param cur az aktualis alakzat
   * @return a kovetkezo akcio kodja
   */
  private int nextAction(Tetromino cur) {
    int release = finishing ? TetrisGame.NONE : TetrisGame.DROP;
    if (targetShape == null || movesDone >= MAX_MOVES_PER_PIECE) {
      return release;
    }
    movesDone++;
    if (!Arrays.deepEquals(cur.shape, targetShape)) {
      return TetrisGame.ROTATE;
    }
    if (state.offsetj < targetCol) {
      return TetrisGame.RIGHT;
    }
    if (state.offsetj > targetCol) {
      return TetrisGame.LEFT;
    }
    return release;
  }

  /**
   * Informalt kereses: kifejti az aktualis alakzat osszes (forgatas, oszlop)
   * allapotat, mindegyiket kiertekeli, es a legjobbat beallitja celkent
   * ({@link #targetShape}, {@link #targetCol}).
   * <p>
   * Pontgyujtes kozben a {@link #heuristic} erteket maximalizalja, es kihagyja
   * azokat az allapotokat, amelyek a pontszamot {@link #MAX_SCORE} fole
   * vinnek (ha van mas lehetoseg). Befejezeskor a sortorles nelkuli, minel
   * magasabban megallo elhelyezest valasztja.
   */
  private void search() {
    int[][] base = cleanBoard();
    int cols = base[0].length;
    Tetromino piece = new Tetromino(state.current());

    double bestScore = Double.NEGATIVE_INFINITY;
    boolean bestAllowed = false;
    targetShape = null;

    for (int r = 0; r < 4; r++) {
      if (r > 0) {
        piece.rotate();
      }
      for (int col = -piece.shape.length + 1; col < cols; col++) {
        if (!TetrisState.checkItem(base, 0, col, piece)) {
          continue;
        }
        int row = dropRow(base, piece, col);
        int[][] b = copy(base);
        TetrisState.addItem(b, row, col, piece, TetrisGame.TAKEN);
        int lines = clearFullRows(b);

        double score;
        boolean allowed;
        if (finishing) {
          // Befejezes: ne toroljunk sort, es alljon meg minel feljebb az alakzat.
          allowed = lines == 0;
          score = -row;
        } else {
          int gain = Math.max(0, row - state.offseti)
              + LINE_POINTS[Math.min(lines, 4)] * Math.max(1, state.level);
          allowed = state.score + gain <= MAX_SCORE;
          score = heuristic(b, lines);
        }

        // A megengedett allapot mindig jobb, mint a nem megengedett.
        if ((allowed && !bestAllowed) || (allowed == bestAllowed && score > bestScore)) {
          bestScore = score;
          bestAllowed = allowed;
          targetShape = copy(piece.shape);
          targetCol = col;
        }
      }
    }
  }

  /**
   * Heurisztikus kiertekelo fuggveny: negy jellemzo sulyozott osszege.
   * Nagyobb ertek jobb allast jelent.
   *
   * @param b     a palya az elhelyezes es a sortorles utan
   * @param lines a torolt sorok szama
   * @return az allapot heurisztikus erteke
   */
  private static double heuristic(int[][] b, int lines) {
    int rows = b.length;
    int cols = b[0].length;
    int[] heights = new int[cols];
    int holes = 0;

    for (int j = 0; j < cols; j++) {
      boolean seenFilled = false;
      for (int i = 0; i < rows; i++) {
        if (b[i][j] != TetrisGame.EMPTY) {
          if (!seenFilled) {
            heights[j] = rows - i;
            seenFilled = true;
          }
        } else if (seenFilled) {
          holes++;
        }
      }
    }

    int aggregate = 0;
    int bumpiness = 0;
    for (int j = 0; j < cols; j++) {
      aggregate += heights[j];
      if (j > 0) {
        bumpiness += Math.abs(heights[j] - heights[j - 1]);
      }
    }

    return W_HEIGHT * aggregate
        + W_LINES * lines
        + W_HOLES * holes
        + W_BUMPINESS * bumpiness;
  }

  /**
   * A jatekallapot palyajanak masolata, amelyrol eltavolitjuk az eso
   * alakzatot es az arnyekot (GHOST).
   *
   * @return tiszta palya, csak a mar lerakott elemekkel
   */
  private int[][] cleanBoard() {
    int[][] b = copy(state.board);
    TetrisState.addItem(b, state.offseti, state.offsetj, state.current(), TetrisGame.EMPTY);
    for (int i = 0; i < b.length; i++) {
      for (int j = 0; j < b[i].length; j++) {
        if (b[i][j] == TetrisGame.GHOST) {
          b[i][j] = TetrisGame.EMPTY;
        }
      }
    }
    return b;
  }

  /**
   * Megadja, melyik sorig esik le az alakzat az adott oszlopban, ha a
   * 0. sorbol indul.
   *
   * @param b     palya
   * @param piece alakzat
   * @param col   oszlop (befoglalo doboz bal szele)
   * @return a leerkezesi sor indexe (befoglalo doboz teteje)
   */
  private static int dropRow(int[][] b, Tetromino piece, int col) {
    int row = 0;
    while (TetrisState.checkItem(b, row + 1, col, piece)) {
      row++;
    }
    return row;
  }

  /**
   * Torli a teli sorokat a palyarol (helyben).
   *
   * @param b palya
   * @return a torolt sorok szama
   */
  private static int clearFullRows(int[][] b) {
    int cleared = 0;
    int idx;
    while ((idx = TetrisState.getTakenRow(b)) != -1) {
      TetrisState.clearRow(b, idx);
      cleared++;
    }
    return cleared;
  }

  /**
   * Ketdimenzios tomb melymasolata.
   *
   * @param a forras
   * @return masolat
   */
  private static int[][] copy(int[][] a) {
    int[][] c = new int[a.length][];
    for (int i = 0; i < a.length; i++) {
      c[i] = a[i].clone();
    }
    return c;
  }
}
