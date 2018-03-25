package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.calculator.Period.Fields;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.CalculatorHelper.hasCrossover;
import static com.gamesbykevin.tradingbot.calculator.strategy.SMA.calculateSMA;

public class EMA extends Strategy {

    //list of ema values for our long period
    private List<Double> emaLong;

    //list of ema values for our short period
    private List<Double> emaShort;

    /**
     * How many periods to calculate long ema
     */
    public static final int PERIODS_EMA_LONG = 26;

    /**
     * How many periods to calculate short ema
     */
    public static final int PERIODS_EMA_SHORT = 12;

    //how many periods
    private final int periodsLong, periodsShort;

    public EMA(int periodsLong, int periodsShort) {

        //call parent with default value
        super(0);

        //if long is less than short throw exception
        if (periodsLong <= periodsShort)
            throw new RuntimeException("The long periods are less than the short. L=" + periodsLong + ", S=" + periodsShort);

        //store our periods
        this.periodsLong = periodsLong;
        this.periodsShort = periodsShort;

        //create our lists
        this.emaLong = new ArrayList<>();
        this.emaShort = new ArrayList<>();
    }

    public EMA() {
        this(PERIODS_EMA_LONG, PERIODS_EMA_SHORT);
    }

    public List<Double> getEmaShort() {
        return this.emaShort;
    }

    public List<Double> getEmaLong() {
        return this.emaLong;
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //if we have a bullish crossover, we expect price to go up
        if (hasCrossover(true, getEmaShort(), getEmaLong()))
            agent.setBuy(true);

        //display data
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //if we have a bearish crossover, we expect price to go down
        if (hasCrossover(false, getEmaShort(), getEmaLong()))
            agent.setReasonSell(ReasonSell.Reason_Strategy);

        //display data
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    protected void displayData(Agent agent, boolean write) {

        //display the recent ema values which we use as a signal
        display(agent, "EMA Short: ", getEmaShort(), (periodsShort / 2), write);
        display(agent, "EMA Long: ", getEmaLong(), (periodsShort / 2),write);
    }

    @Override
    public void calculate(List<Period> history) {

        //calculate ema for short and long periods
        calculateEMA(history, getEmaShort(), periodsShort);
        calculateEMA(history, getEmaLong(), periodsLong);
    }

    private static double calculateEMA(List<Period> history, int current, int periods, double emaPrevious) {

        //what is our multiplier
        final float multiplier = ((float)2 / ((float)periods + 1.0f));

        //this close price is the current price
        final double currentPrice = history.get(current).close;

        //calculate our ema
        final double ema;

        if (emaPrevious != 0) {

            ema = ((currentPrice - emaPrevious) * multiplier) + emaPrevious;

        } else {

            //calculate simple moving average since there is no previous ema
            final double sma = calculateSMA(history, current - 1, periods, Fields.Close);

            ema = ((currentPrice - sma) * multiplier) + sma;
        }

        //return our result
        return ema;
    }

    protected static void calculateEMA(List<Period> history, List<Double> emaList, int periods) {

        //clear our list
        emaList.clear();

        //for an accurate ema we want to calculate as many data points as possible
        for (int i = 0; i < history.size(); i++) {

            //skip if we can't go back far enough
            if (i <= periods)
                continue;

            //either the ema will be 0 or we get the most recent
            final double previousEma = (emaList.isEmpty()) ? 0 : emaList.get(emaList.size() - 1);

            //calculate the ema for the current period
            final double ema = calculateEMA(history, i, periods, previousEma);

            //add it to the list
            emaList.add(ema);
        }
    }
}