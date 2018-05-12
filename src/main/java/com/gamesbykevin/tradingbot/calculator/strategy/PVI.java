package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.utils.CalculatorHelper.hasCrossover;
import static com.gamesbykevin.tradingbot.calculator.indicator.trend.EMA.calculateEma;

/**
 * Positive volume index
 */
public class PVI extends Strategy {

    //our cumulative and ema lists
    private List<Double> pviCumulative, pviEma;

    //list of configurable values
    private static final int PERIODS_EMA = 200;

    private final int periodsEma;

    public PVI() {
        this(PERIODS_EMA);
    }

    public PVI(int periodsEMA) {

        this.periodsEma = periodsEMA;

        //create new lists
        this.pviCumulative = new ArrayList<>();
        this.pviEma = new ArrayList<>();
    }

    public List<Double> getPviCumulative() {
        return this.pviCumulative;
    }

    public List<Double> getPviEma() {
        return this.pviEma;
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //if we have crossover and cumulative value is > than previous period value that is signal to buy
        if (hasCrossover(true, getPviCumulative(), getPviEma()) && getRecent(getPviCumulative()) > getRecent(getPviCumulative(), 2))
            agent.setBuy(true);

        //display our data
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //if we have crossover and cumulative value is < than previous period value that is signal to sell
        if (getRecent(getPviCumulative()) < getRecent(getPviEma()) && getRecent(getPviCumulative()) < getRecent(getPviCumulative(), 2))
            agent.setReasonSell(ReasonSell.Reason_Strategy);

        //adjust our hard stop price to protect our investment
        if (getRecent(getPviCumulative()) < getRecent(getPviEma()))
            adjustHardStopPrice(agent, currentPrice);

        //display our data
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    public void displayData(Agent agent, boolean write) {

        //display the information
        display(agent, "PVI Cum: ", getPviCumulative(), write);
        display(agent, "PVI Ema: ", getPviEma(),        write);
    }

    @Override
    public void calculate(List<Period> history, int newPeriods) {

        //start at 1,000
        double pvi = 1000;

        //where do we start?
        int start = getPviCumulative().isEmpty() ? 0 : history.size() - newPeriods;

        //check all of our periods
        for (int i = start; i < history.size(); i++) {

            //we have to check the previous period
            if (i < 1)
                continue;

            //get the current and previous periods
            Period current = history.get(i);
            Period previous = history.get(i - 1);

            //calculate the percent volume change between the periods
            double changeVolume = ((current.volume - previous.volume) / previous.volume) * 100d;

            //calculate the percent price change between the periods
            double changePrice = ((current.close - previous.close) / previous.close) * 100.0d;

            //we only update cumulative pvi if the volume increases
            if (changeVolume > 0) {
                pvi += changePrice;
            }

            //add the nvi value to our list
            getPviCumulative().add(pvi);
        }

        //now that we have our standard list, let's calculate ema
        calculateEma(getPviEma(), getPviCumulative(), newPeriods, periodsEma);
    }

    @Override
    public void cleanup() {
        cleanup(getPviEma());
        cleanup(getPviCumulative());
    }
}