package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.CalculatorHelper.hasCrossover;
import static com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonBuy;
import static com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

/**
 * Moving average crossover strategy
 */
public class MACS extends Strategy {

    //our list of fast, slow, trending values
    private List<Double> emaFast, emaSlow, emaTrend;

    /**
     * The number of periods for our moving average crossover strategy fast calculations
     */
    public static int PERIODS_MACS_FAST;

    /**
     * The number of periods for our moving average crossover strategy slow calculations
     */
    public static int PERIODS_MACS_SLOW;

    /**
     * The number of periods for our moving average crossover strategy trending calculations
     */
    public static int PERIODS_MACS_TREND;

    private final int periodsFast, periodsSlow, periodsTrend;

    public MACS(int periodsFast, int periodsSlow, int periodsTrend) {

        //call parent with default volume
        super(0);

        //make sure the periods are appropriate
        if (periodsFast > periodsSlow || periodsFast > periodsTrend)
            throw new RuntimeException("The fast periods have to be less than both the slow and trend periods");
        if (periodsSlow > periodsTrend)
            throw new RuntimeException("The slow period has to be less than the trend period");

        //store our period counts
        this.periodsFast = periodsFast;
        this.periodsSlow = periodsSlow;
        this.periodsTrend = periodsTrend;

        //create new list(s)
        this.emaFast = new ArrayList<>();
        this.emaSlow = new ArrayList<>();
        this.emaTrend = new ArrayList<>();
    }

    public MACS() {
        this(PERIODS_MACS_FAST, PERIODS_MACS_SLOW, PERIODS_MACS_TREND);
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //we want a crossover where the fast is greater than slow
        if (hasCrossover(true, emaFast, emaSlow)) {

            //lets also check that the current price is above the trending data
            if (currentPrice > emaTrend.get(emaTrend.size() - 1))
                agent.setReasonBuy(ReasonBuy.Reason_5);
        }

        //display our data for what it is worth
        displayData(agent, agent.getReasonBuy() != null);
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //we want a crossover where the slow is greater than fast
        if (hasCrossover(false, emaFast, emaSlow)) {

            //lets also check that the current price is below the trending data
            if (currentPrice < emaTrend.get(emaTrend.size() - 1))
                agent.setReasonSell(ReasonSell.Reason_6);
        }

        //display our data for what it is worth
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    protected void displayData(Agent agent, boolean write) {

        //display values
        display(agent, "EMA Fast: ", emaFast, periodsFast, write);
        display(agent, "EMA Slow: ", emaSlow, periodsFast, write);
        display(agent, "EMA Trend: ", emaTrend, periodsFast, write);
    }

    @Override
    public void calculate(List<Period> history) {

        //calculate the different ema values
        EMA.calculateEMA(history, emaFast, periodsFast);
        EMA.calculateEMA(history, emaSlow, periodsSlow);
        EMA.calculateEMA(history, emaTrend, periodsTrend);
    }
}