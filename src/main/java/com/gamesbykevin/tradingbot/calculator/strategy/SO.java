package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.calculator.Period.Fields;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.strategy.SMA.calculateSMA;

/**
 * Stochastic Oscillator
 */
public final class SO extends Strategy {

    //indicator list
    private List<Double> stochasticOscillator;

    //our market rate
    private List<Double> marketRate;

    //our simple moving average closing price for a long period
    private List<Double> smaPriceLong;

    //our simple moving average closing price for a short period
    private List<Double> smaPriceShort;

    //list of configurable values
    private static final int PERIODS_SO = 12;
    private static final int PERIODS_SMA_SO = 3;
    private static final int PERIODS_SMA_PRICE_LONG = 50;
    private static final int PERIODS_SMA_PRICE_SHORT = 10;
    private static final double OVER_SOLD = 20.0d;
    private static final double OVER_BOUGHT = 80.0d;

    private final int periodsSO, periodsSmaSo, periodsSmaLong, periodsSmaShort;

    public SO() {
        this(PERIODS_SO, PERIODS_SMA_SO, PERIODS_SMA_PRICE_LONG, PERIODS_SMA_PRICE_SHORT);
    }

    public SO(int periodsSO, int periodsSmaSo, int periodsSmaLong, int periodsSmaShort) {

        this.periodsSO = periodsSO;
        this.periodsSmaSo = periodsSmaSo;
        this.periodsSmaLong = periodsSmaLong;
        this.periodsSmaShort = periodsSmaShort;

        //create new list(s)
        this.stochasticOscillator = new ArrayList<>();
        this.marketRate = new ArrayList<>();
        this.smaPriceLong = new ArrayList<>();
        this.smaPriceShort = new ArrayList<>();
    }

    public List<Double> getStochasticOscillator() {
        return this.stochasticOscillator;
    }

    public List<Double> getMarketRate() {
        return this.marketRate;
    }

    public List<Double> getSmaPriceLong() {
        return this.smaPriceLong;
    }

    public List<Double> getSmaPriceShort() {
        return this.smaPriceShort;
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        double soPrevious = getRecent(getStochasticOscillator(), 2);
        double soCurrent = getRecent(getStochasticOscillator());

        double priceCurrent = getRecent(history, Fields.Close);

        //if the close is above the sma averages
        if (priceCurrent > getRecent(getSmaPriceLong()) && priceCurrent > getRecent(getSmaPriceShort())) {

            //if previously oversold, and no longer
            if (soPrevious < OVER_SOLD && soCurrent > OVER_SOLD)
                agent.setBuy(true);
        }

        //display info
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //if the close is now below the sma averages
        if (getRecent(history, Fields.Close) < getRecent(getSmaPriceLong()) && getRecent(history, Fields.Close) < getRecent(getSmaPriceShort())) {

            //if previously over bought, and no longer
            if (getRecent(getStochasticOscillator(), 2) > OVER_BOUGHT && getRecent(getStochasticOscillator()) < OVER_BOUGHT)
                agent.setReasonSell(ReasonSell.Reason_Strategy);
        }

        //display info
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    public void displayData(Agent agent, boolean write) {

        //display the information
        display(agent, "SO: %D ", getStochasticOscillator(), write);
        display(agent, "MR: %K ", getMarketRate(), write);
        display(agent, "SMA: Long ", getSmaPriceLong(), write);
        display(agent, "SMA: Short ", getSmaPriceShort(), write);
    }

    @Override
    public void calculate(List<Period> history) {

        //clear our list(s)
        getMarketRate().clear();

        for (int i = 0; i < history.size(); i++) {

            //we don't have enough data yet
            if (i < periodsSO)
                continue;

            //what is the high and low for our period range
            double high = getMaxPeriod(history, i, true).high;
            double low = getMaxPeriod(history, i, false).low;

            //what is the market value
            double marketValue = ((history.get(i).close - low) / (high - low)) * 100.0d;

            //add to our market rate list
            getMarketRate().add(marketValue);
        }

        //calculate sma for our indicator
        calculateSMA(getMarketRate(), getStochasticOscillator(), periodsSmaSo);

        //calculate sma's for the closing price
        calculateSMA(history, getSmaPriceLong(), periodsSmaLong, Fields.Close);
        calculateSMA(history, getSmaPriceShort(), periodsSmaShort, Fields.Close);
    }

    private Period getMaxPeriod(List<Period> history, int index, boolean high) {

        Period result = null;

        //check these periods for the high or low
        for (int i = index - periodsSO; i < index; i++) {

            //check the current period
            Period current = history.get(i);

            if (result == null) {

                //set a default period to start
                result = current;

            } else {

                //are we looking for the highest high, or lowest low
                if (high) {

                    //if we have a new high, this will be the period to beat
                    if (current.high > result.high)
                        result = current;

                } else {

                    //if we have a new low, this will be the period to beat
                    if (current.low < result.low)
                        result = current;
                }
            }
        }

        //return our result
        return result;
    }
}