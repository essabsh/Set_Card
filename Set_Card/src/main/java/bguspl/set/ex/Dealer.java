package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    public timer timer;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    public static volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.timer = new timer(env);
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    public void placecards(){
        placeCardsOnTable();
    }
    public void removecards(){
        removeAllCardsFromTable();
    }
    @Override
    public void run() {
        for (int i = 0; i < players.length; i++) {
            Thread t1 = new Thread(players[i], "player" + i);
            t1.start();
        }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
               while (!shouldFinish()) {
                   //shuffle deck
                   Collections.shuffle(deck);
                   placeCardsOnTable();
                   updateTimerDisplay(true);
                   timerLoop();
                   if (timer.time<=0)
                   this.timer.time = env.config.turnTimeoutMillis;
                   env.ui.setCountdown(this.timer.time, this.timer.time<env.config.turnTimeoutWarningMillis);
                   removeAllCardsFromTable();
               }
            announceWinners();
            terminate();
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        this.timer=new timer(env);
        Thread timer = new Thread(this.timer, "timer");
        timer.start();
        while ( !terminate && this.timer.time > 0) {
            sleepUntilWokenOrTimeout(this.timer.time);
            if (!table.playersQueue.isEmpty()) {
                Player claimer = table.playersQueue.poll();
                boolean isLegal = removeCardsFromTable(claimer);
                if (isLegal) {
                    if(this.timer.time > 0)
                        this.timer.time = env.config.turnTimeoutMillis;
                    placeCardsOnTable();
                    updateTimerDisplay(true);
                }
            }
        }
        try{
            timer.interrupt();
            timer.join();
        }catch(InterruptedException ex){}
    }



    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        timer.timerterminate=true;
        synchronized (table.playersQueue){
            table.playersQueue.notifyAll();
        }
        for (int i = 0; i < players.length; i++) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private boolean removeCardsFromTable(Player player) {
        boolean res = false;
        int i = 0;
        int[] set = new int[3];
        for (Integer slot : table.playerToSlots.get(player.id).keySet()) {
                if (table.slotToCard[slot] != null)
                    set[i++] = table.slotToCard[slot];
        }
        if (env.util.testSet(set)) {
            player.legalset = true;
            res = true;
            for (Integer slot : table.playerToSlots.get(player.id).keySet())
                table.removeCard(slot);
        }
        synchronized (player) {
            player.notifyAll();
        }
        return res;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> empty = new LinkedList<Integer>();
        for (int i = 0; i < table.slotToCard.length; i++)
            if (table.slotToCard[i] == null)
                empty.add(i);
        Collections.shuffle(empty);
        for (int i = 0; i < empty.size() && !deck.isEmpty(); i++)
            table.placeCard(deck.remove(0), empty.get(i));
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout(long time) {
        synchronized (table.playersQueue) {
            try {
                table.haStarted = true;
                if (time<0)
                    time=0;
                table.playersQueue.wait(time);
                table.haStarted = false;
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
    }
/**
* Returns all the cards from the table to the deck.
*/
    private void removeAllCardsFromTable() {
        LinkedList<Integer> empty = new LinkedList<Integer>();
        for (int i = 0; i < table.slotToCard.length; i++)
            if (table.slotToCard[i] != null)
                empty.add(i);
        Collections.shuffle(empty);
        for (int i = 0; i < empty.size(); i++) {
            deck.add(table.slotToCard[empty.get(i)]);
            table.removeCard(empty.get(i));
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int[] maxid;
        int maxscore = 0;
        int length = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].getScore() > maxscore) {
                maxscore = players[i].getScore();
            }
        }
        for (int i = 0; i < players.length; i++) {
            if (players[i].getScore() == maxscore)
                length++;
        }
        maxid = new int[length];
        int j = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].getScore() == maxscore) {
                maxid[j] = players[i].id;
                j++;
            }
        }
        env.ui.announceWinner(maxid);
    }
    
}


