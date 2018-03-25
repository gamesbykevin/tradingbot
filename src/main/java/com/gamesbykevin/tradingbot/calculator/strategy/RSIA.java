package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;

import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.CalculatorHelper.hasCrossover;
import static com.gamesbykevin.tradingbot.calculator.strategy.ADX.PERIODS_ADX;
import static com.gamesbykevin.tradingbot.calculator.strategy.ADX.TREND_ADX;
import static com.gamesbykevin.tradingbot.calculator.strategy.RSI.*;

/**
 * RSI Adx
 */
public class RSIA extends Strategy {

    //our adx object reference
    private ADX adxObj;

    //our rsi object reference
    private RSI rsiObj;

    public RSIA() {

        //call parent
        super(PERIODS_RSI);

        //create objects
        this.adxObj = new ADX(PERIODS_ADX, TREND_ADX);
        this.rsiObj = new RSI(PERIODS_RSI);
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        //get the most recent rsi value
        double rsi = getRecent(rsiObj.getRsi());

        //if we are at or below the support line, let's check if we are in a good place to buy
        if (rsi <= SUPPORT_LINE) {

            //if dm plus crosses above dm minus, that is our signal to buy
            if (hasCrossover(true, adxObj.getDmPlusIndicator(), adxObj.getDmMinusIndicator()))
                agent.setBuy(true);
        }

        //display our data
        displayData(agent, agent.hasBuy());
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //get the most recent rsi value
        double rsi = getRecent(rsiObj.getRsi());

        //let's see if we are above resistance line before selling
        if (rsi >= RESISTANCE_LINE) {

            //if the minus has crossed below the plus that is our signal to sell
            if (hasCrossover(false, adxObj.getDmPlusIndicator(), adxObj.getDmMinusIndicator()))
                agent.setReasonSell(ReasonSell.Reason_Strategy);
        }

        //display our data
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    public void displayData(Agent agent, boolean write) {

        rsiObj.displayData(agent, write);
        adxObj.displayData(agent, write);
    }

    @Override
    public void calculate(List<Period> history) {

        //calculate
        rsiObj.calculate(history);
        adxObj.calculate(history);
    }
}