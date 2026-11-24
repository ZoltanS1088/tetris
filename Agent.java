import java.util.Arrays;
import java.util.Random;

import game.tetris.TetrisAction;
import game.tetris.TetrisGame;
import game.tetris.TetrisPlayer;
import game.tetris.utils.TetrisState;
import game.tetris.utils.Tetromino;

/**
 * Tetris jatekot jatszo agens.
 * <p>
 * Mukodes vazlata:
 * <ol>
 *   <li>Amikor uj alakzat jelenik meg, az agens vegigprobalja az osszes
 *       forgatas es oszlop kombinaciot ({@link #findBestPlacement()}).</li>
 *   <li>Minden jeloltet leejt a palya egy tiszta masolatan, torli a teli
 *       sorokat, majd a kapott allast pontozza ({@link #evaluate}).</li>
 *   <li>A legjobb jelolt cel-alakjat es cel-oszlopat megjegyzi, majd a
 *       kovetkezo lepesekben forgatassal, balra/jobbra mozgatassal odaviszi
 *       az alakzatot, vegul leejti (DROP), ami plusz pontot is ad.</li>
 * </ol>
 * A szimulacio a keretrendszer statikus segedfuggvenyeit hasznalja
 * ({@link TetrisState#checkItem}, {@link TetrisState#addItem},
 * {@link TetrisState#getTakenRow}, {@link TetrisState#clearRow}).
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
   * @param random veletlenszam-generator (orokolt, nem allitjuk at)
   * @param state  a jatekallapot masolata
   */
  public Agent(int color, Random random, TetrisState state) {
    super(color, random, state);
  }

  /**
   * Visszaadja a kovetkezo akciot. Uj alakzatnal ujratervez, egyebkent
   * a meglevo tervet kovetve forgat, mozgat, vegul leejt.
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
      findBestPlacement();
    }
    int action = nextAction(cur);
    state.apply(action);
    return new TetrisAction(action);
  }

  /**
   * A terv alapjan meghatarozza a kovetkezo akciot: eloszor forgatas
   * (amig fent van az alakzat), utana vizszintes mozgatas, vegul DROP.
   * Ha a mozgatas valamiert nem sikerul (akadaly), a lepeskorlat utan leejt.
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
   * Vegigprobalja az aktualis alakzat osszes forgatasat es oszlopat, es a
   * legjobb pontszamu elhelyezest beallitja celkent
   * ({@link #targetShape}, {@link #targetCol}).
   */
  private void findBestPlacement() {
    int[][] base = cleanBoard();
    int cols = base[0].length;
    Tetromino piece = new Tetromino(state.current());

    double bestScore = Double.NEGATIVE_INFINITY;
    targetShape = null;

    for (int r = 0; r < 4; r++) {
      if (r > 0) {
        piece.rotate();
      }
      // Az O alakzat minden forgatasa azonos, felesleges ujraszamolni.
      if (r > 0 && "O".equals(piece.type)) {
        break;
      }
      // Negativ oszlop is lehetseges, ha az alakzat bal oszlopai uresek.
      for (int col = -piece.shape.length + 1; col < cols; col++) {
        if (!TetrisState.checkItem(base, 0, col, piece)) {
          continue;
        }
        int row = dropRow(base, piece, col);
        int[][] b = copy(base);
        TetrisState.addItem(b, row, col, piece, TetrisGame.TAKEN);
        int eroded = erodedCells(b, row, piece);
        clearFullRows(b);
        double score = evaluate(b, row, piece, eroded);
        if (score > bestScore) {
          bestScore = score;
          targetShape = copy(piece.shape);
          targetCol = col;
        }
      }
    }
  }

  /**
   * A jatekallapot palyajanak masolata, amelyrol eltavolitjuk az eso
   * alakzatot (TAKEN-kent van rajta) es az arnyekot (GHOST).
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

  /** Suly: az alakzat leerkezesi magassaga (alacsonyabb a jobb). */
  private static final double W_LANDING = -4.500158825082766;
  /** Suly: "erodalt" cellak = torolt sorok * az alakzat torolt cellai. */
  private static final double W_ERODED = 3.4181268101392694;
  /** Suly: soron beluli ures/teli valtasok szama. */
  private static final double W_ROW_TRANS = -3.2178882868487753;
  /** Suly: oszlopon beluli ures/teli valtasok szama. */
  private static final double W_COL_TRANS = -9.348695305445199;
  /** Suly: lyukak szama (ures cella, amely felett van teli cella). */
  private static final double W_HOLES = -7.899265427351652;
  /** Suly: kutak kumulalt melysege (1+2+...+d minden kutra). */
  private static final double W_WELLS = -3.3855972247263626;

  /**
   * Kiertekelo fuggveny: a Dellacherie / El-Tetris fele jellemzok
   * sulyozott osszege. Nagyobb ertek jobb allast jelent.
   * <ul>
   *   <li>leerkezesi magassag: az alakzat kozepenek magassaga a palya aljatol</li>
   *   <li>erodalt cellak: torolt sorok szama szorozva az alakzat torolt cellainak szamaval</li>
   *   <li>sor- es oszlopatmenetek: mennyire "toredezett" a palya (a fal es a
   *       palya alja telinek szamit)</li>
   *   <li>lyukak: ures cellak, amelyek felett ugyanabban az oszlopban van teli cella</li>
   *   <li>kutak: olyan ures cellak, amelyeknek mindket oldalan teli cella vagy fal van</li>
   * </ul>
   *
   * @param b      a palya az elhelyezes es sortorles utan
   * @param row    a leerkezesi sor (befoglalo doboz teteje)
   * @param piece  az elhelyezett alakzat
   * @param eroded az erodalt cellak szama
   * @return pontszam, nagyobb a jobb
   */
  private double evaluate(int[][] b, int row, Tetromino piece, int eroded) {
    int rows = b.length;
    int cols = b[0].length;

    // Leerkezesi magassag: az alakzat legfelso es legalso cellajanak atlaga.
    int top = Integer.MAX_VALUE;
    int bottom = Integer.MIN_VALUE;
    for (int i = 0; i < piece.shape.length; i++) {
      for (int j = 0; j < piece.shape[i].length; j++) {
        if (piece.shape[i][j] == 1) {
          top = Math.min(top, row + i);
          bottom = Math.max(bottom, row + i);
        }
      }
    }
    double landing = rows - (top + bottom) / 2.0;

    // Soratmenetek: a falak telinek szamitanak.
    int rowTrans = 0;
    for (int i = 0; i < rows; i++) {
      boolean prev = true;
      for (int j = 0; j < cols; j++) {
        boolean filled = b[i][j] != TetrisGame.EMPTY;
        if (filled != prev) {
          rowTrans++;
        }
        prev = filled;
      }
      if (!prev) {
        rowTrans++;
      }
    }

    // Oszlopatmenetek (a palya alja telinek szamit) es lyukak.
    int colTrans = 0;
    int holes = 0;
    for (int j = 0; j < cols; j++) {
      boolean prev = false;
      boolean seenFilled = false;
      for (int i = 0; i < rows; i++) {
        boolean filled = b[i][j] != TetrisGame.EMPTY;
        if (filled != prev && i > 0) {
          colTrans++;
        }
        if (filled) {
          seenFilled = true;
        } else if (seenFilled) {
          holes++;
        }
        prev = filled;
      }
      if (!prev) {
        colTrans++;
      }
    }

    // Kutak: ures cella, amelynek bal es jobb szomszedja teli vagy fal.
    int wells = 0;
    for (int j = 0; j < cols; j++) {
      int depth = 0;
      for (int i = 0; i < rows; i++) {
        boolean empty = b[i][j] == TetrisGame.EMPTY;
        boolean leftFull = j == 0 || b[i][j - 1] != TetrisGame.EMPTY;
        boolean rightFull = j == cols - 1 || b[i][j + 1] != TetrisGame.EMPTY;
        if (empty && leftFull && rightFull) {
          depth++;
          wells += depth;
        } else if (!empty) {
          depth = 0;
        }
      }
    }

    return W_LANDING * landing
        + W_ERODED * eroded
        + W_ROW_TRANS * rowTrans
        + W_COL_TRANS * colTrans
        + W_HOLES * holes
        + W_WELLS * wells;
  }

  /**
   * Megszamolja az "erodalt" cellakat a sortorles elott: a teli sorok
   * szamat szorozza az alakzat azon cellainak szamaval, amelyek ezekben a
   * sorokban vannak.
   *
   * @param b     a palya az alakzat lerakasa utan, sortorles elott
   * @param row   a leerkezesi sor (befoglalo doboz teteje)
   * @param piece az elhelyezett alakzat
   * @return erodalt cellak szama
   */
  private static int erodedCells(int[][] b, int row, Tetromino piece) {
    int fullRows = 0;
    int pieceCells = 0;
    for (int i = 0; i < piece.shape.length; i++) {
      int r = row + i;
      if (r < 0 || r >= b.length) {
        continue;
      }
      boolean full = true;
      for (int j = 0; j < b[r].length; j++) {
        if (b[r][j] == TetrisGame.EMPTY) {
          full = false;
          break;
        }
      }
      if (full) {
        fullRows++;
        for (int j = 0; j < piece.shape[i].length; j++) {
          if (piece.shape[i][j] == 1) {
            pieceCells++;
          }
        }
      }
    }
    return fullRows * pieceCells;
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