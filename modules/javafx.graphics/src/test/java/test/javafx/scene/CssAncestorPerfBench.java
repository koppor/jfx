/*
 * Benchmark harness for evaluating JDK-8268657 fix variants.
 * NOT for upstreaming - local measurement only.
 *
 * Usage:
 *   ./gradlew --no-daemon :graphics:test --tests "test.javafx.scene.CssAncestorPerfBench"
 *
 * Prints lines "BENCH,<scenario>,<param>,<millis>" to stdout and appends them
 * to /tmp/jfx-css-bench.csv so results survive gradle's output capturing.
 * The test task uses forkEvery = 1, so this class gets its own warm JVM.
 *
 * To compare fix variants, swap the CSS engine files onto this same base and
 * re-run (the bench file itself is variant-independent):
 *   - baseline: this branch as-is (upstream master at edf20b532fa9)
 *   - Maran23 fix: git checkout ae490e64c5aa -- modules/javafx.graphics/src/main/java/javafx/scene/CssStyleHelper.java
 *     (from https://github.com/Maran23/jfx branch 8268657-Looked-up-color-fails-for--fx-background-color-in-JavaFX-CSS-file)
 *   - koppor fix: git checkout origin/fix/JDK-8268657-resolveRef-stale-ancestor -- \
 *     modules/javafx.graphics/src/main/java/javafx/scene/CssStyleHelper.java \
 *     modules/javafx.graphics/src/main/java/javafx/scene/Node.java
 *
 * Scenario notes:
 *   - deepPlainChainCold is the probe for super-linear ancestor discovery. Plain
 *     layout panes with no matching CSS rules and no inheriting CssMetaData end up
 *     without a CssStyleHelper by design, so a nested chain of them exercises the
 *     helper-less-ancestor path. Expected: flat ~0.5ms per depth on master; a fix
 *     that recursively re-runs createStyleHelper for helper-less ancestors without
 *     memoization shows ~2^depth growth (x4 per +2 depth).
 *   - the remaining scenarios (wide-tree REAPPLY, repeated deep-leaf re-resolution,
 *     re-parenting churn) guard the hot paths; variants should stay within JVM noise
 *     of each other there.
 */

package test.javafx.scene;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.sun.javafx.css.StyleManager;
import javafx.css.CssParser;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CssAncestorPerfBench {

    private static final String OUT = "/tmp/jfx-css-bench.csv";

    private static void resetStyleManager() {
        StyleManager sm = StyleManager.getInstance();
        sm.userAgentStylesheetContainers.clear();
        sm.platformUserAgentStylesheetContainers.clear();
        sm.stylesheetContainerMap.clear();
        sm.cacheContainerMap.clear();
        sm.hasDefaultUserAgentStylesheet = false;
    }

    @BeforeEach
    public void setup() {
        resetStyleManager();
        CssParser.errorsProperty().clear();
    }

    @AfterAll
    public static void cleanupOnce() {
        resetStyleManager();
    }

    private static void report(String scenario, String param, double millis) {
        String line = String.format("BENCH,%s,%s,%.3f", scenario, param, millis);
        System.out.println(line);
        try (FileWriter w = new FileWriter(OUT, true)) {
            w.write(line + "\n");
        } catch (IOException e) {
            // stdout line is the fallback
        }
    }

    private static String toBase64(String css) {
        return "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    private static final String LOOKUP_THEME = toBase64("""
        .root { -c0: #102030; -c1: #203040; -c2: #304050; -c3: #405060; }
        .mid  { -fx-opacity: 1.0; }
        .leaf {
            -fx-background-color: -c0, -c1;
            -fx-border-color: -c2;
        }
        """);

    /** chain of {@code depth} UNSTYLED panes below the scene root, styled leaf at the end */
    private static Scene buildPlainChain(int depth) {
        StackPane root = new StackPane();
        Pane current = root;
        for (int i = 0; i < depth; i++) {
            Pane p = new Pane();
            current.getChildren().add(p);
            current = p;
        }
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        current.getChildren().add(leaf);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(LOOKUP_THEME);
        Stage stage = new Stage();
        stage.setScene(scene);
        return scene;
    }

    /**
     * Scenario 1: cold CSS pass over a deep chain of style-helper-less panes.
     * Detects super-linear behavior in ancestor discovery during createStyleHelper.
     */
    @Test
    @Order(1)
    public void deepPlainChainCold() {
        // warmup
        buildPlainChain(4).getRoot().applyCss();

        int[] depths = {4, 8, 12, 16, 18, 20, 22, 24};
        for (int d : depths) {
            double best = Double.MAX_VALUE;
            for (int rep = 0; rep < 3; rep++) {
                resetStyleManager();
                Scene scene = buildPlainChain(d);
                long t0 = System.nanoTime();
                scene.getRoot().applyCss();
                long t1 = System.nanoTime();
                best = Math.min(best, (t1 - t0) / 1e6);
                if (best > 8000) {
                    break;
                }
            }
            report("deepPlainChainCold", "depth=" + d, best);
            if (best > 8000) {
                report("deepPlainChainCold", "aborted-at-depth=" + d, best);
                return;
            }
        }
    }

    private static Scene buildWideTree(boolean styledIntermediates) {
        StackPane root = new StackPane();
        List<Pane> level = new ArrayList<>();
        level.add(root);
        for (int l = 0; l < 3; l++) {
            List<Pane> next = new ArrayList<>();
            for (Pane p : level) {
                for (int i = 0; i < 8; i++) {
                    Pane child = new Pane();
                    if (styledIntermediates) {
                        child.getStyleClass().add("mid");
                    }
                    p.getChildren().add(child);
                    next.add(child);
                }
            }
            level = next;
        }
        // 512 leaves
        for (Pane p : level) {
            StackPane leaf = new StackPane();
            leaf.getStyleClass().add("leaf");
            p.getChildren().add(leaf);
        }
        Scene scene = new Scene(root);
        scene.getStylesheets().add(LOOKUP_THEME);
        Stage stage = new Stage();
        stage.setScene(scene);
        return scene;
    }

    private static double timedFullReapplyPasses(Scene scene, int iterations) {
        String dummy = toBase64(".unused-class-xyz { -fx-opacity: 0.99; }");
        double[] times = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            scene.getStylesheets().add(dummy);
            scene.getRoot().applyCss();
            scene.getStylesheets().remove(dummy);
            scene.getRoot().applyCss();
            long t1 = System.nanoTime();
            times[i] = (t1 - t0) / 1e6;
        }
        java.util.Arrays.sort(times);
        return times[iterations / 2];
    }

    /**
     * Scenario 2a: full REAPPLY passes over a wide tree (585 nodes, depth 4) with
     * UNSTYLED intermediates - the realistic layout-container case.
     */
    @Test
    @Order(2)
    public void wideTreeReapplyPlainIntermediates() {
        Scene scene = buildWideTree(false);
        scene.getRoot().applyCss();
        timedFullReapplyPasses(scene, 5); // warmup
        report("wideTreeReapplyPlain", "median-of-20", timedFullReapplyPasses(scene, 20));
    }

    /**
     * Scenario 2b: same but all intermediates styled (they own style helpers) -
     * isolates createStyleHelper + lookup resolution cost without the
     * helper-less-ancestor path.
     */
    @Test
    @Order(3)
    public void wideTreeReapplyStyledIntermediates() {
        Scene scene = buildWideTree(true);
        scene.getRoot().applyCss();
        timedFullReapplyPasses(scene, 5); // warmup
        report("wideTreeReapplyStyled", "median-of-20", timedFullReapplyPasses(scene, 20));
    }

    /**
     * Scenario 3: repeated re-resolution of one deep leaf (styled ancestors).
     * The leaf sits below 24 styled panes; each iteration forces REAPPLY of the
     * leaf only. Measures the per-resolution cost of the ancestor discovery
     * (cached vs live walk) on the resolveRef path.
     */
    @Test
    @Order(4)
    public void deepLeafRepeatedResolve() {
        StackPane root = new StackPane();
        Pane current = root;
        for (int i = 0; i < 24; i++) {
            Pane p = new Pane();
            p.getStyleClass().add("mid");
            current.getChildren().add(p);
            current = p;
        }
        StackPane leaf = new StackPane();
        leaf.getStyleClass().add("leaf");
        current.getChildren().add(leaf);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(LOOKUP_THEME);
        Stage stage = new Stage();
        stage.setScene(scene);
        scene.getRoot().applyCss();

        Runnable oneIteration = () -> {
            leaf.getStyleClass().remove("leaf");
            leaf.applyCss();
            leaf.getStyleClass().add("leaf");
            leaf.applyCss();
        };
        for (int i = 0; i < 200; i++) {
            oneIteration.run(); // warmup
        }
        int iterations = 2000;
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            oneIteration.run();
        }
        long t1 = System.nanoTime();
        report("deepLeafRepeatedResolve", "total-2000-iters", (t1 - t0) / 1e6);
    }

    /**
     * Scenario 4: a styled subtree is moved back and forth between two parents,
     * with a CSS pass after each move. Exercises helper reuse and ancestor cache
     * refresh on re-parenting.
     */
    @Test
    @Order(5)
    public void reparentChurn() {
        StackPane root = new StackPane();
        Pane holderA = new Pane();
        holderA.getStyleClass().add("mid");
        Pane holderB = new Pane();
        holderB.getStyleClass().add("mid");
        root.getChildren().addAll(holderA, holderB);

        // small styled subtree: 3 levels x fanout 3 = 39 nodes, leaves with lookups
        Pane subtree = new Pane();
        subtree.getStyleClass().add("mid");
        List<Pane> level = List.of(subtree);
        for (int l = 0; l < 2; l++) {
            List<Pane> next = new ArrayList<>();
            for (Pane p : level) {
                for (int i = 0; i < 3; i++) {
                    Pane child = new Pane();
                    child.getStyleClass().add("mid");
                    p.getChildren().add(child);
                    next.add(child);
                }
            }
            level = next;
        }
        for (Pane p : level) {
            StackPane leaf = new StackPane();
            leaf.getStyleClass().add("leaf");
            p.getChildren().add(leaf);
        }
        holderA.getChildren().add(subtree);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(LOOKUP_THEME);
        Stage stage = new Stage();
        stage.setScene(scene);
        scene.getRoot().applyCss();

        Runnable moveOnce = () -> {
            Parent from = subtree.getParent();
            Pane to = (from == holderA) ? holderB : holderA;
            ((Pane) from).getChildren().remove(subtree);
            to.getChildren().add(subtree);
            scene.getRoot().applyCss();
        };
        for (int i = 0; i < 100; i++) {
            moveOnce.run(); // warmup
        }
        int iterations = 400;
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            moveOnce.run();
        }
        long t1 = System.nanoTime();
        report("reparentChurn", "total-400-iters", (t1 - t0) / 1e6);
    }

    /** sanity: the lookup theme actually resolves without CSS errors on this variant */
    @Test
    @Order(6)
    public void sanityNoCssErrors() {
        Scene scene = buildWideTree(false);
        scene.getRoot().applyCss();
        report("sanityCssErrorCount", "errors", CssParser.errorsProperty().size());
    }
}
