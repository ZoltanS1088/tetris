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
 * utana az alakzatot leejti (DROP), ami plusz pontot is ad.
 * <p>
 * <b>Informalt kereses (moho legjobbat-eloszor kereses).</b> Amikor uj
 * alakzat jelenik meg, az agens kifejti az osszes lehetseges allapotot: minden
 * forgatasra es minden oszlopra leejti az alakzatot a palya egy masolatan,
 * majd az igy kapott allapotokat egy heurisztikus fuggvennyel ertekeli
 * ({@link #heuristic}), es a legjobbat valasztja celkent.
 * <p>
 * <b>Heurisztika.</b> Az agens egyetlen, egyszeru szempontot hasznal: az
 * alakzat kerueljon a leheto legmelyebbre (a leerkezesi sor indexe minel
 * nagyobb legyen). Az alacsonyan elhelyezett elemek tobbnyire a lyukas
 * reszeket toltik ki, igy idonkent teli sorok keletkeznek es eltunnek. Mivel
 * a lyukakat es a felulet egyenetlenseget nem veszi figyelembe, a palya
 * idovel megtelik; a jatek jellemzoen nehany szaz (kb. 300-1700) pontnal
 * er veget.
 */
public class Agent extends TetrisPlayer {

  /** Egy alakzatra legfeljebb ennyi mozgato akciot hajtunk vegre, utana leejtjuk. */
  private static final int MAX_MOVES_PER_PIECE = 12;

  /** Az az alakzat-objektum, amelyre a jelenlegi terv keszult (referencia szerint). */
  private Tetromino plannedFor = null;
  /** A cel forgatasi allapot alakmatrixa. */
  private int[][] targetShape = null;
  /** A cel oszlop (az alakzat befoglalo dobozanak bal szele). */
  private int targetCol = 0;
  /** Az aktualis alakzatra eddig vegrehajtott mozgato akciok szama. */
  private int movesDone = 0;

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
   * Visszaadja a kovetkezo akciot. Uj alakzatnal keresessel uj tervet
   * keszit, egyebkent a meglevo tervet koveti.
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
      search();
    }
    int action = nextAction(cur);
    state.apply(action);
    return new TetrisAction(action);
  }

  /**
   * A terv alapjan meghatarozza a kovetkezo akciot: eloszor forgatas, utana
   * vizszintes mozgatas, vegul leejtes (DROP).
   *
   * @param cur az aktualis alakzat
   * @return a kovetkezo akcio kodja
   */
  private int nextAction(Tetromino cur) {
    if (targetShape == null || movesDone >= MAX_MOVES_PER_PIECE) {
      return TetrisGame.DROP;
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
    return TetrisGame.DROP;
  }

  /**
   * Informalt kereses: kifejti az aktualis alakzat osszes (forgatas, oszlop)
   * allapotat, mindegyiket kiertekeli a {@link #heuristic} fuggvennyel, es a
   * legnagyobb erteku allapotot beallitja celkent ({@link #targetShape},
   * {@link #targetCol}). Egyenlo ertek eseten az elsokent talalt marad.
   */
  private void search() {
    int[][] base = cleanBoard();
    int cols = base[0].length;
    Tetromino piece = new Tetromino(state.current());

    double bestScore = Double.NEGATIVE_INFINITY;
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
        double score = heuristic(row);
        if (score > bestScore) {
          bestScore = score;
          targetShape = copy(piece.shape);
          targetCol = col;
        }
      }
    }
  }

  /**
   * Heurisztikus kiertekelo fuggveny: az alakzat leerkezesi sora. Minel
   * melyebbre esik az alakzat, annal nagyobb az ertek.
   *
   * @param row a leerkezesi sor indexe (befoglalo doboz teteje)
   * @return az allapot heurisztikus erteke, nagyobb a jobb
   */
  private static double heuristic(int row) {
    return row;
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
