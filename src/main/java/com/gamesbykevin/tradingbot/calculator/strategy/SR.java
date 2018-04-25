package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.calculator.Period.Fields;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.strategy.SMA.calculateSMA;

/**
 * Stoch RSI
 */
public class SR extends Strategy {

    //our lists for the stock price
    private List<Double> smaPriceLong, smaPriceShort;

    //our rsi object
    private RSI rsiObj;

    //list of stoch rsi values
    private List<Double> stochRsi;

    //list of configurable values
    private static final int PERIODS_LONG = 50;
    private static final int PERIODS_SHORT = 8;
    private static final int PERIODS_STOCH_RSI = 12;
    private static final double OVER_BOUGHT = .80d;
    private static final double OVER_SOLD = .20d;

    private final int periodsLong, periodsShort, periodsStochRsi;
    private final double overBought, overSold;

    public SR() {
        this(PERIODS_LONG, PERIODS_SHORT, PERIODS_STOCH_RSI, OVER_BOUGHT, OVER_SOLD);
    }

    public SR(int periodsLong, int periodsShort, int periodsStochRsi, double overBought, double overSold) {

        //create our rsi object
        this.rsiObj = new RSI(1, PERIODS_STOCH_RSI, 0, 0);

        //create new lists
        this.stochRsi = new ArrayList<>();
        this.smaPriceLong = new ArrayList<>();
        this.smaPriceShort = new ArrayList<>();

        this.periodsLong = periodsLong;
        this.periodsShort = periodsShort;
        this.periodsStochRsi = periodsStochRsi;
        this.overBought = overBought;
        this.overSold = overSold;
    }

    public List<Double> getStochRsi() {
        return this.stochRsi;
    }

    public List<Double> getSmaPriceLong() {
        return this.smaPriceLong;
    }

    public List<Double> getSmaPriceShort() {
        return this.smaPriceShort;
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //get the current values
        double priceShort = getRecent(getSmaPriceShort());
        double priceLong = getRecent(getSmaPriceLong());
        double stochRsi = getRecent(getStochRsi());
        double closePrice = getRecent(history, Fields.Close);

        //also get the previous values
        double priceShortPrevious = getRecent(getSmaPriceShort(), 2);
        double priceLongPrevious = getRecent(getSmaPriceLong(), 2);
        double stochRsiPrevious = getRecent(getStochRsi(), 2);
        double closePricePrevious = getRecent(history, Fields.Close, 2);

        //if our short sma is greater than our long sma and our current close is less than the short sma
        if (priceShort > priceLong && closePrice < priceShort) {

            //then if the rsi is showing over sold, we should buy
            if (stochRsi < overSold) {

                //last thing we want to verify is that we are at the start of a trend, we don't want to enter a trade late
                if (priceShortPrevious < priceLongPrevious || closePricePrevious > priceShortPrevious || stochRsiPrevious >= overSold)
                    agent.setBuy(true);
            }
        }

        //display our data
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        double priceShort = getRecent(getSmaPriceShort());
        double priceLong = getRecent(getSmaPriceLong());

        //if the price is below the sma we need to sell
        if (priceShort < priceLong)
            agent.setReasonSell(ReasonSell.Reason_Strategy);

        /*
        //if our short sma is less than our long sma and our current close is greater than the short sma
        if (priceShort < priceLong && priceShort < getRecent(history, Fields.Close)) {
            agent.setReasonSell(ReasonSell.Reason_Strategy);

            //then if the rsi is showing over bought, we should sell
            if (getRecent(getStochRsi()) > overBought)
                agent.setReasonSell(ReasonSell.Reason_Strategy);
        }
        */

        //display our data
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    public void displayData(Agent agent, boolean write) {

        //display the information
        this.rsiObj.displayData(agent, write);
        display(agent, "STOCH RSI: ", getStochRsi(), write);
    }

    @Override
    public void calculate(List<Period> history) {

        //calculate
        this.rsiObj.calculate(history);

        //clear our list
        getStochRsi().clear();

        //check every period
        for (int i = 0; i < rsiObj.getRsiVal().size(); i++) {

            //skip until we have enough data
            if (i < periodsStochRsi)
                continue;

            double rsiHigh = -1, rsiLow = 101;

            //check the recent periods for our calculations
            for (int x = i - periodsStochRsi; x < i; x++) {

                //get the current rsi value
                double rsi = rsiObj.getRsiVal().get(x);

                //locate our high and low
                if (rsi < rsiLow)
                    rsiLow = rsi;
                if (rsi > rsiHigh)
                    rsiHigh = rsi;
            }

            //calculate stoch rsi value
            double stochRsi = (rsiObj.getRsiVal().get(i) - rsiLow) / (rsiHigh - rsiLow);

            //add our new value to the list
            getStochRsi().add(stochRsi);
        }

        //calculate our short and long sma values
        calculateSMA(history, smaPriceShort, periodsShort, Fields.Close);
        calculateSMA(history, smaPriceLong, periodsLong, Fields.Close);
    }
}