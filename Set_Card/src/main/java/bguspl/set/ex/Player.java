package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    public volatile boolean legalset;
    public volatile boolean f;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    public int[] playerSet;
    public volatile int tokens;
    public volatile boolean sleeping;
    public long sleeptime;

    public int TokenNum=0;//This is for testing

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.tokens = 3;
        this.playerSet = new int[3];
        this.legalset = false;
        this.sleeptime =0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            // TODO implement main player loop
            if (tokens == 0 && f) {
                 tokens= 3;
                if (legalset)
                    point();
                else
                    penalty();
                f = false;
                sleeping = false;
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                int slot1 = 0, slot2 = 0, slot3 = 0;
                int[] set = new int[3];
                    while (!table.haStarted || sleeping) {
                        if (terminate)
                            return;
                    }
                    Random r = new Random();
                    List nonEmptySlots = new LinkedList();
                    for (int i = 0; i < 12; i++)
                        if (table.slotToCard[i] != null)
                            nonEmptySlots.add(i);
                    if (nonEmptySlots.size() > 0)
                        slot1 = (int) nonEmptySlots.remove(r.nextInt(nonEmptySlots.size()));
                    if (nonEmptySlots.size() > 0)
                        slot2 = (int) nonEmptySlots.remove(r.nextInt(nonEmptySlots.size()));
                    if (nonEmptySlots.size() > 0)
                        slot3 = (int) nonEmptySlots.remove(r.nextInt(nonEmptySlots.size()));

                    if (table.slotToCard[slot1] != null)
                        set[0] = table.slotToCard[slot1];
                    if (table.slotToCard[slot2] != null)
                        set[1] = table.slotToCard[slot2];
                    if (table.slotToCard[slot3] != null)
                        set[2] = table.slotToCard[slot3];
                    keyPressed(slot1);
                    keyPressed(slot2);
                    keyPressed(slot3);

                    if (!env.util.testSet(set)) {
                        keyPressed(slot1);
                        keyPressed(slot2);
                        keyPressed(slot3);
                    }

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        synchronized (this){
            this.notifyAll();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (sleeping || !table.haStarted)
            return;
        synchronized (table) {
            if(!table.haStarted)
                return;
            if(!table.playerToSlots.containsKey(id))
                table.playerToSlots.put(id, new ConcurrentHashMap<>());
            int tmpTokens = 3 - table.playerToSlots.get(id).size();
            if (table.slotToPlayers.get(slot).contains(id)) {
                table.removeToken(id, slot);
                if (tmpTokens < 3) {
                    tmpTokens++;
                }
            } else {
                if (tmpTokens > 0) {
                    table.placeToken(id, slot);
                    tmpTokens--;
                }
            }
            if (tmpTokens == 0) {
                if (!table.playerToSlots.get(id).contains(slot))
                    return;
                legalset = false;
                synchronized (this) {
                    synchronized (table.playersQueue) {
                        table.playersQueue.add(this);
                        table.playersQueue.notifyAll();
                    }
                    try {
                        sleeping = true;
                        this.wait();
                    } catch (InterruptedException ignored) {}
                    f = true;
                }
                tokens = tmpTokens;
            }
        }
    }
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        sleeping = true;
        long pointtime = env.config.pointFreezeMillis;
        while (pointtime > 0) {
            env.ui.setFreeze(this.id, pointtime);
            if (pointtime<1000){
                try {
                    Thread.sleep(pointtime);
                } catch (InterruptedException ex) {
                }
                pointtime=0;
            }else {
                pointtime -= 1000;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
        }
        env.ui.setFreeze(this.id, 0);
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        sleeping = false;
        tokens = 3;
        sleeptime=env.config.pointFreezeMillis;

    }
    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
            sleeping = true;
            long penaltytime = env.config.penaltyFreezeMillis;
            while (penaltytime > 0) {
                env.ui.setFreeze(this.id, penaltytime);
                if (penaltytime < 1000) {
                    try {
                        Thread.sleep(penaltytime);
                    } catch (InterruptedException ex) {
                    }
                    penaltytime = 0;
                } else {
                    penaltytime -= 1000;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                    }
                }
            }
            env.ui.setFreeze(this.id, 0);
            sleeping = false;
        }


    public int getScore() {
        return score;
    }

    public int score() {
        return  score;
    }
    public void presskey(int slot){
        if (tokens>0){
            if (this.table.playerToSlots.get(id).contains(slot))
                table.removeToken(id,slot);
            else table.placeToken(id,slot);
        }
    }
    public void keyPreesTest(int slot){
        TokenNum++;
    }
}
