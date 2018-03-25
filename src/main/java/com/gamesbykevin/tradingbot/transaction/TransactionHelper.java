package com.gamesbykevin.tradingbot.transaction;

import com.gamesbykevin.tradingbot.agent.Agent;

import com.gamesbykevin.tradingbot.agent.AgentManager;
import com.gamesbykevin.tradingbot.transaction.Transaction.Result;

import static com.gamesbykevin.tradingbot.agent.AgentHelper.formatValue;
import static com.gamesbykevin.tradingbot.agent.AgentManager.displayMessage;

public class TransactionHelper {

    /**
     * The reasons for why we sold
     */
    public enum ReasonSell {

        Reason_Strategy("Sold based on strategy logic"),
        Reason_HardStop("We have hit our hard stop"),
        ;

        private final String description;

        ReasonSell(String description) {
            this.description = description;
        }

        public String getDescription() {
            return this.description;
        }
    }

    public static String getAverageDurationDesc(Agent agent) {
        return "Avg time: " + Transaction.getDurationDesc(getAverageDuration(agent));
    }

    public static long getAverageDuration(Agent agent) {

        //how many transactions
        int count = 0;

        //total duration
        long duration = 0;

        //if empty return 0
        if (agent.getTransactions().isEmpty())
            return 0;

        //check every transaction
        for (Transaction transaction : agent.getTransactions()) {

            if (transaction.getResult() == null)
                continue;

            //keep track of total transactions
            count++;

            //add total duration
            duration += transaction.getDuration();
        }

        //if nothing, return 0
        if (count == 0)
            return 0;

        //return the average duration
        return (duration / count);
    }

    public static void displaySellReasonCount(Agent agent, Result result) {

        //check each reason
        for (ReasonSell sell : ReasonSell.values()) {

            //keep track of the count
            int count = 0;

            //keep track of the money involved
            double amount = 0;

            //look at each transaction
            for (Transaction transaction : agent.getTransactions()) {

                //skip if no match
                if (transaction.getResult() == null || transaction.getResult() != result)
                    continue;

                //if there is a match increase the count
                if (transaction.getReasonSell() == sell) {
                    count++;
                    amount += transaction.getAmount();
                }
            }

            //display the count if greater than 0
            if (count > 0)
                displayMessage(agent, result.toString() + " Sell " + sell.toString() +  " total " + count + ", $" + formatValue(amount) + ". " + sell.getDescription(), true);
        }
    }

    /**
     * Get the total $ amount for our transaction
     * @param result Do we want to check for wins or losses?
     * @return The total $ amount of the specified result
     */
    public static double getAmount(Agent agent, Result result) {

        double amount = 0;

        //check every transaction
        for (Transaction transaction : agent.getTransactions()) {

            if (transaction.getResult() == null)
                continue;

            //if there is a match keep track
            if (transaction.getResult() == result)
                amount += transaction.getAmount();
        }

        //return our result
        return amount;
    }

    /**
     * Get the total count of our transactions
     * @param result Do we want to check for wins or losses?
     * @return The total count of the specified result
     */
    public static int getCount(Agent agent, Result result) {

        int count = 0;

        //check every transaction
        for (Transaction transaction : agent.getTransactions()) {

            if (transaction.getResult() == null)
                continue;

            //if there is a match keep track
            if (transaction.getResult() == result)
                count++;
        }

        //return our result
        return count;
    }

    public static String getDescLost(Agent agent) {
        return "Lost :" + getCount(agent, Result.Lose) + ", $" + formatValue(getAmount(agent, Result.Lose));
    }

    public static String getDescWins(Agent agent) {
        return "Wins :" + getCount(agent, Result.Win) + ", $" + formatValue(getAmount(agent, Result.Win));
    }
}