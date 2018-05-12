package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.calculator.indicator.momentun.CCI;
import com.gamesbykevin.tradingbot.calculator.indicator.trend.ADX;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.List;

/**
 * Commodity Channel Index / Average Directional Index
 */
public class CA extends Strategy {

    //our indicator objects
    private CCI objCCI;
    private ADX objADX;

    //configurable values
    private static final int PERIODS_CCI = 4;
    private static final int PERIODS_ADX = 50;
    private static final double TREND = 15.0d;
    private static final float CCI_LOW = -100;
    private static final float CCI_HIGH = 100;

    public CA() {
        this(PERIODS_CCI, PERIODS_ADX);
    }

    public CA(int periodsCCI, int periodsADX) {

        //create new indicator objects
        this.objADX = new ADX(periodsADX);
        this.objCCI = new CCI(periodsCCI);
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //if adx is below the trend and cci is below -100
        if (getRecent(objADX.getAdx()) < TREND && getRecent(objCCI.getCCI()) < CCI_LOW) {

            //get the current candle
            Period period = history.get(history.size() - 1);

            //if the candle is bullish we will buy
            if (period.open < period.close)
                agent.setBuy(true);
        }

        //display our data
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //get the current candle
        Period period = history.get(history.size() - 1);

        //if adx is below the trend and cci is above 100
        if (getRecent(objADX.getAdx()) < TREND && getRecent(objCCI.getCCI()) > CCI_HIGH) {

            //if the candle is bearish we will sell
            if (period.open > period.close)
                agent.setReasonSell(ReasonSell.Reason_Strategy);
        }

        //if adx is trending
        if (getRecent(objADX.getAdx()) >= TREND) {

            //if dm+ is below dm-
            if (getRecent(objADX.getDmPlusIndicator()) < getRecent(objADX.getDmMinusIndicator())) {

                //cci is below -100 and the current period closed bullish
                if (getRecent(objCCI.getCCI()) <= CCI_LOW && period.open < period.close)
                    adjustHardStopPrice(agent, currentPrice);

            } else if (getRecent(objADX.getDmPlusIndicator()) > getRecent(objADX.getDmMinusIndicator())) {

                //cci is above 100 and the current period closed bearish
                if (getRecent(objCCI.getCCI()) >= CCI_HIGH && period.open > period.close)
                    adjustHardStopPrice(agent, currentPrice);
            }
        }


        //display our data
        displayData(agent, agent.getReasonSell() != null);
    }


    @Override
    public void displayData(Agent agent, boolean write) {

        //display info
        objADX.displayData(agent, write);
        objCCI.displayData(agent, write);
    }

    @Override
    public void calculate(List<Period> history, int newPeriods) {

        //perform calculations
        objADX.calculate(history, newPeriods);
        objCCI.calculate(history, newPeriods);
    }

    @Override
    public void cleanup() {
        objADX.cleanup();
        objCCI.cleanup();
    }
}